# Runbook: identity go-live (ADR-016 step 5)

This runbook takes an environment from "the code is ready" to "OpenCRM authenticates with Microsoft Entra ID in production". It is the go-live gate of [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md): the Entra configuration, the environment of every service, the checks that prove it works, and the rollback exercise that must be recorded before go-live.

The Entra configuration is also a file, [`infra/entra/opencrm-identity.json`](../../infra/entra/opencrm-identity.json). It is the source of truth; this runbook explains how to apply it. `SecurityBaselineFitnessTest` fails the build when that file and the code disagree, for example when the code checks an app role the file does not declare.

**Who does what.** An Entra administrator (Application Administrator, plus Privileged Role Administrator for admin consent and app role assignments) does sections 1 to 3. The platform team does sections 4 to 6. The service owner signs off section 7 and the rollback record in section 8.

## 0. Before you start

- A workforce Entra tenant. Note its tenant id: the issuer is `https://login.microsoftonline.com/<tenant-id>/v2.0`.
- An AKS cluster with the OIDC issuer and workload identity enabled. Note the OIDC issuer URL (`az aks show -g <rg> -n <cluster> --query oidcIssuerProfile.issuerUrl -o tsv`).
- The Kubernetes namespace OpenCRM runs in, and one service account per service, named after the service (`gateway-service`, `quote-service`, and so on).
- The public host of the SPA and gateway, for example `crm.example.com`, with TLS at the ingress.
- Redis for the gateway's sessions (section 5).

## 1. The API app registration: `opencrm-api`

One registration represents the whole API. Every service accepts its tokens (ADR-016 decision 5).

| Setting | Value | Why |
|---------|-------|-----|
| Application ID URI | `api://opencrm-api` | Scopes are requested as `api://opencrm-api/...` |
| `requestedAccessTokenVersion` (manifest, `api` section) | `2` | The services accept the v2.0 issuer only. v2.0 tokens carry the API's **client id** as `aud` |
| Exposed delegated scope | `access_as_user` | User tokens need a delegated scope (`scp`). The services tell users from services by it |
| Optional claim, access token | `idtyp` | Marks app-only tokens as `idtyp=app`, so service tokens are recognised explicitly |
| App roles | `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin` for users; `Internal.Invoke` for applications | See the manifest for the descriptions. The code maps `DealDesk` to `ROLE_DEAL_DESK` and so on |
| Enterprise application: assignment required | Yes | Only assigned users and workloads get tokens for the API |

Record the API's **Application (client) ID**. Every service needs it in `OPENCRM_SECURITY_JWT_AUDIENCES` (section 4). The services refuse to start in production without it, because without it they would refuse every token.

## 2. The gateway app registration: `opencrm-gateway`

The gateway signs users in (ADR-016 decision 2). It is a confidential web client with no secret.

- **Platform**: Web. Redirect URI `https://<crm-host>/login/oauth2/code/entra`, one per environment. For a developer's local run against a test tenant, `http://localhost:4200/login/oauth2/code/entra`, never in the production registration.
- **API permissions**: `opencrm-api` / `access_as_user` (delegated). **Grant admin consent** for the tenant. The gateway requests `api://opencrm-api/.default`; without consent each user would see a consent prompt, or be blocked where user consent is disabled.
- **Credential**: a federated identity credential for the gateway's service account. No client secret, no certificate:
  ```bash
  az ad app federated-credential create --id <gateway-app-client-id> --parameters '{
    "name": "aks-gateway-service",
    "issuer": "<aks-oidc-issuer-url>",
    "subject": "system:serviceaccount:<namespace>:gateway-service",
    "audiences": ["api://AzureADTokenExchange"]
  }'
  ```
- Annotate the `gateway-service` service account with `azure.workload.identity/client-id: <gateway-app-client-id>`, and label the gateway pods `azure.workload.identity/use: "true"`.

## 3. Workload identities and role assignments

Each service that calls another service has its own identity. The manifest's `workloads` lists them, with what each one calls. Today they are quote-service, pricing-service, configuration-engine and ai-service.

For each of them:

1. Create a user-assigned managed identity, then a federated credential for the service's Kubernetes service account:
   ```bash
   az identity create -g <rg> -n opencrm-<service>
   az identity federated-credential create -g <rg> --identity-name opencrm-<service> \
     --name aks-<service> --issuer <aks-oidc-issuer-url> \
     --subject system:serviceaccount:<namespace>:<service> --audience api://AzureADTokenExchange
   ```
2. Annotate the service account with `azure.workload.identity/client-id: <identity client id>`, and label the pods `azure.workload.identity/use: "true"`. The webhook then injects `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_AUTHORITY_HOST` and `AZURE_FEDERATED_TOKEN_FILE`. The services need no other identity configuration.
3. **quote-service and pricing-service only**: assign the `Internal.Invoke` app role of `opencrm-api` to the identity's service principal. They are the only callers of `/internal/**`:
   ```powershell
   New-MgServicePrincipalAppRoleAssignment -ServicePrincipalId <opencrm-api-sp-object-id> `
     -PrincipalId <identity-principal-id> -ResourceId <opencrm-api-sp-object-id> -AppRoleId <Internal.Invoke-role-id>
   ```
   Managed identity tokens are cached, so a new role assignment can take hours to appear in tokens. Assign roles well before the rollout.

**People.** Assign the app roles to groups, not individuals:

| App role | Assign to | Decides |
|----------|-----------|---------|
| `CPQ.User` | everyone who uses OpenCRM | no approvals, and no quote reviews |
| `DealDesk` | the deal desk | `DEAL_DESK` and `MANAGER` approvals, and quote reviews |
| `SalesVP` | sales vice presidents | `SALES_VP`, `DIRECTOR` and `VP_SALES`, and every Deal Desk tier, and quote reviews |
| `CFO` | finance leadership | every tier, and quote reviews |
| `Admin` | system administrators | no approvals and no quote reviews (segregation of duties) |

A quote review is the step from `IN_REVIEW` to `APPROVED` (or `REJECTED`) on the quote itself. It needs one of `DealDesk`, `SalesVP` or `CFO`, and the quote's creator cannot pass their own quote (`SELF_REVIEW`). Failing one's own quote is allowed. Two rules follow from the four-eyes checks, which have no exceptions (owner decisions 1 and 3):

- A person who creates quotes and holds `DealDesk`, `SalesVP` or `CFO` still needs a different reviewer for those quotes.
- **The `CFO` group needs at least two people in production.** A `CFO`-tier approval raised by a CFO can be decided only by another holder of the role, so a group of one blocks it.

The services check that a caller is authenticated, not that it holds `CPQ.User`. With assignment required, though, a user with no role gets no token. Assign `CPQ.User` to every user, and the approver roles on top of it; a user in several groups gets every role in the `roles` claim.

## 4. The environment of every service

| Variable | Services | Value |
|----------|----------|-------|
| `OPENCRM_ENV` | all | `prod`: the production marker. It refuses `dev` mode, an incomplete identity configuration and client secrets |
| `OPENCRM_SECURITY_JWT_ISSUERURI` | all | `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `OPENCRM_SECURITY_JWT_AUDIENCES` | all | `<opencrm-api client id>,api://opencrm-api` |
| `MANAGEMENT_SERVER_PORT` | all | `9091`: the port of the actuator, apart from the service port (section 5). Under `OPENCRM_ENV=prod` a service that exposes anything beyond `health` and `info` on its service port refuses to start, so this is required |
| `OPENCRM_GATEWAY_LOGIN_CLIENTID` | gateway-service | the gateway app's client id |
| `SERVER_FORWARD_HEADERS_STRATEGY` | gateway-service | `framework` (section 5) |
| `DB_PASSWORD` (`DB_PASS` in ai-service) | every service with a database | from the secret store, for example Key Vault through the CSI driver. The committed configuration has no default outside the `dev` and `test` profiles, so a missing value stops the service at startup |
| `REDIS_HOST`, `REDIS_PORT` | gateway-service | the session store |

Never set `OPENCRM_SECURITY_CLIENT_CLIENTSECRET`, `OPENCRM_GATEWAY_LOGIN_CLIENTSECRET` or `opencrm.security.mode`. Under `OPENCRM_ENV=prod` the first two stop the service, and `mode=dev` is refused.

## 5. Ingress, sessions and metrics

- **Ingress.** It terminates TLS and must **set** `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-Port`, overwriting whatever the client sent. The gateway trusts them only with `SERVER_FORWARD_HEADERS_STRATEGY=framework`, and builds the login's redirect URI from them (`https://<crm-host>/login/oauth2/code/entra`). Set it on the gateway only; the domain services are not behind the ingress.
- **Redis** holds the gateway's sessions (Spring Session, cookie `OPENCRM_SESSION`, 30 minutes idle). If Redis is lost, users sign in again; nothing else is lost. Use a highly available tier, TLS, and an eviction policy that does not evict sessions under normal load.
- **Metrics and probes** (decided: a management port, no scraper identity). Every service serves its actuator on a second port, `MANAGEMENT_SERVER_PORT` (`9091`), and nothing else. There `/actuator/health` (with `liveness` and `readiness`), `/actuator/info` and `/actuator/prometheus` need no token, and every other path answers `403`: `metrics`, `env`, the gateway's route inspection, the API. The service port serves no actuator endpoint at all. Because the management port has no credential, only the network protects it: apply [`infra/k8s/opencrm-management-port-networkpolicy.yaml`](../../infra/k8s/opencrm-management-port-networkpolicy.yaml) (adapt the two namespace names), confirm that the cluster's network plugin enforces NetworkPolicy, and never route the port through the ingress or the gateway. Point the pods' probes and Prometheus at the management port (the comments in that file show the fields). A service started with `OPENCRM_ENV=prod` and without the port refuses to start, and says how to fix it; to run without metrics, set `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info` instead. Without a management port the actuator shares the service port and `/actuator/prometheus` is readable with any user token, which is why production does not run that way.

## 6. Deploy

1. Apply sections 1 to 5 in the target environment.
2. Deploy all services. Each one logs a single line at startup; check that every pod says the same:
   ```
   ADR-016 identity: mode=jwt, production=true, issuer=https://login.microsoftonline.com/<tenant-id>/v2.0,
   audiences=[<api client id>, api://opencrm-api], management port=9091, service identity=<client id> (workload identity)
   ```
   A service with an incomplete configuration does not start; its log names every problem at once (`ADR-016 go-live gate (opencrm.env=prod): ...`).

## 7. Verify (go-live checklist)

Tick every line in the environment, and keep the evidence (screenshots, log lines, request ids) with the change record.

- [ ] Every pod logs the identity line in section 6, with `production=true`, `management port=9091` and a workload identity where the manifest lists one.
- [ ] Metrics: Prometheus shows every service's target `up` on port `9091`. From a pod in the monitoring namespace, `GET :9091/actuator/prometheus` returns `200`; from a pod in any other namespace the connection is refused or times out; `GET :9091/actuator/metrics` returns `403`; and `GET` `/actuator/prometheus` on the service port returns `401` anonymously and `404` with a user token.
- [ ] An anonymous `GET /api/v1/quotes` gets `401`, `WWW-Authenticate: Bearer` and `X-OpenCRM-Login: /oauth2/authorization/entra`.
- [ ] Opening the SPA redirects to Entra and back; the browser holds `OPENCRM_SESSION` (`HttpOnly`, `Secure`) and `XSRF-TOKEN`, and never a token.
- [ ] The access token in the gateway's session, inspected in a test tenant: `ver` `2.0`, `aud` the API's client id, `scp` `access_as_user`, the user's `roles`, and `oid`.
- [ ] Creating a quote (a POST from the SPA) succeeds: the SPA sends `X-XSRF-TOKEN`.
- [ ] Approvals: a `DealDesk` user decides a `DEAL_DESK` approval; the same user gets `403 APPROVER_ROLE_REQUIRED` on a `SALES_VP` approval; a requester gets `403 SELF_APPROVAL` on their own request. The recorded approver is the user's `oid`.
- [ ] Quote review: a `DealDesk`, `SalesVP` or `CFO` user passes a quote that is `IN_REVIEW` (created by somebody else); a `CPQ.User` gets `403 REVIEWER_ROLE_REQUIRED`; the quote's creator, even with `DealDesk`, gets `403 SELF_REVIEW` on approve.
- [ ] Discount tiers: the discount path's approvals arrive with one of `MANAGER`, `DIRECTOR` or `VP_SALES` (owner decision 2 waits on its owner's confirmation). An approval stuck with no possible decider names an unknown tier.
- [ ] Superseded approvals: when the discount figures for a quote line change while its approval is `PENDING`, the old request shows as `SUPERSEDED`, only the new one is decidable, and deciding the old one answers `409 APPROVAL_SUPERSEDED`.
- [ ] The `CFO` group has two or more members.
- [ ] Service calls: a quote's configuration check (quote-service to configuration-engine `/internal`) and price-book lookups (pricing-service to pricebook-service `/internal`) succeed; the same `/internal` path with a user token gets `403`.
- [ ] Sign-out (toolbar) ends the session at the gateway and at Entra, and returns to the app.
- [ ] In staging, with the production configuration, a service started with `SPRING_PROFILES_ACTIVE=dev` refuses to start, and so does one without `OPENCRM_SECURITY_JWT_AUDIENCES`.

## 8. Rollback exercise (record before go-live)

Rollback means redeploying the previous version of the services, with its configuration. It never means `opencrm.security.mode=dev`: the production marker refuses dev mode, and dev mode would permit every request.

The configuration is compatible in both directions for the steps of ADR-016: older versions ignore the new variables, and this version's variables are a superset. The one exception is a version from before step 5, which still has the `postgres` database password default; it starts either way because `DB_PASSWORD` is set.

Run it in staging, with the production configuration:

1. Deploy version N (this release). Pass section 7.
2. Redeploy version N-1 (the image tags from the previous release, same configuration). Record the time from the command to all pods ready.
3. Check: health is `UP`; a signed-in user can read quotes; existing sessions either survive or send the user to sign in again, with no error page.
4. Redeploy version N. Pass section 7 again.

Record:

| Field | Value |
|-------|-------|
| Date, environment | |
| Version N / N-1 (image tags) | |
| Time to roll back / to roll forward | |
| Sessions after rollback (kept or re-login) | |
| Problems seen, and follow-ups | |
| Performed by / signed off by | |

## 9. Decisions (recorded 2026-09-28)

The four identity decisions were taken by the owner's delegate; the [decision sheet](../forge/40-owner-decisions-adr-016-018.md) has the reasoning and the record. What each means for this runbook:

| Decision | Outcome | Where it applies here |
|----------|---------|-----------------------|
| 1. Who may pass a quote review | Deal Desk, Sales VP or CFO; never the quote's creator | Section 3 (role table), section 7 |
| 2. Discount-tier mapping | Kept. Owed: the discount-path owner confirms the tier names | Section 3, section 7 |
| 3. Four-eyes | Always, no exceptions; the `CFO` group has two or more people | Section 3, section 7 |
| 4. Prometheus | A management port with no token, protected by network policy | Sections 4, 5, 6, 7 |

**Still owed by people:** the discount-path owner's confirmation (decision 2), and staffing the `CFO` group (decision 3). The SPA does not yet hide the review and approval buttons by role, because it cannot learn the user's roles; the server refuses and the SPA shows the refusal. That needs a session-info endpoint on the gateway and is a follow-up, not part of go-live.

## References

- [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md), [ADR-004](../adr/ADR-004-csrf-edge-boundary.md), [gateway environment](../CRM_GATEWAY_ENV.md).
- [Workload identity on AKS](https://learn.microsoft.com/azure/aks/workload-identity-deploy-cluster), [federated credentials on an app registration](https://learn.microsoft.com/entra/workload-id/workload-identity-federation-create-trust), [assigning an app role to a managed identity](https://learn.microsoft.com/entra/identity/managed-identities-azure-resources/assign-app-role-managed-identity-powershell).
