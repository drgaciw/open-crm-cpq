# ADR-016: Identity, authentication and service-to-service trust

**Status:** Accepted on 2026-09-23 by the project owner ("Proceed with ADR-016"). It is being implemented in rollout order, one PR per step. Steps 0 to 5 are done in the code; see [Implementation log](#implementation-log). Going live still needs the environment work of the [identity go-live runbook](../runbooks/identity-go-live.md), including the recorded rollback exercise.
**Date:** 2026-09-22 (proposed), 2026-09-23 (accepted)
**Deciders:** Project owner. Drafted from the [GitNexus ADR review](../forge/39-adr-gitnexus-review-2026-09-22.md) (finding F1).
**Relates to:**
- [ADR-004](ADR-004-csrf-edge-boundary.md) (edge CSRF; this ADR covers the auth model it left out of scope)
- [ADR-009](ADR-009-gateway-edge-only.md)
- [ADR-011](ADR-011-shared-maven-libs-published-language.md) (`libs/security`)
- [ADR-013](ADR-013-quote-bff-approvals-single-writer.md) (approval actor)
- [ADR-015](ADR-015-jev-advisory-decision-support.md)
- [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (actor on events)

**What acceptance authorizes.** While this ADR was Proposed, it authorized no change to `SecurityConfig`, JWT validation, the `dev` chains or FE login. Acceptance makes the target model the contract, and the [Rollout](#rollout) is the wave plan, one PR per step. Two things stay outside the code PRs:
- creating the Entra tenant objects (app registrations, app roles, workload identities), which is step 5 and an operations task;
- ADR-004's CSRF posture, which this ADR does not change.

---

## Context

ADR-004 settled *browser CSRF* at the edge and put "full Azure AD B2C resource-server rollout" out of scope. Nothing else records how users and services authenticate. On HEAD `4a29d31`, confirmed by GitNexus and `rg`, the code implies a model that it does not implement:

| Layer | Today | Consequence |
|-------|-------|-------------|
| Browser → gateway | The SPA has no OIDC library and no auth interceptor (`frontend/package.json`, `libs/shared/data-access`). The gateway `dev` chain permits all. The gateway `!dev` chain needs a JWT (`oauth2ResourceServer`) with the default issuer `https://login.microsoftonline.com/common/v2.0`, and no audience validation is configured | No working production login path. The multi-tenant `common` default is not a safe issuer |
| Gateway → services | Spring Cloud Gateway forwards headers unchanged. There is no `TokenRelay` and no header stripping | Services receive whatever the browser sent |
| Domain services, `!dev` | All 9 servlet chains require `.authenticated()`, and **none** configures a mechanism (no `oauth2ResourceServer`, basic or custom filter) | No request can authenticate outside `dev` |
| Domain services, `dev` | crm, pricing, pricebook, product and quote have a `dev` permit-all chain. **approval-routing, configuration-engine, contract-asset-management and document-generation have a single chain** | Their `/api/v1/**` rejects unauthenticated callers in every profile. The UAT specs record approval-routing and CE as "down" (`frontend/e2e/uat/specs/approvals.mjs`), so the approval write path has not run end-to-end |
| `/internal/**` | `permitAll()` in pricebook (`/internal/v1/price-books/resolve-batch`) and CE (`/internal/v1/configurations/evaluate`). The gateway rewrites a public path onto CE's internal endpoint | Network placement is the only control |
| Service → service | Five `RestClient`s (quote→pricing, quote→approval-routing, quote→**gateway**, pricing→pricebook, CE→product) send no credentials. No interceptors exist | Calls to the four single-chain services should be rejected. The hairpin through the gateway also hits edge CSRF |
| Internal shared secret | The crm-service `SecurityConfig` Javadoc promises a `SECURITY_INTERNAL_API_KEY` header check | **Not implemented.** The only hit is the comment |
| Identity propagation | `libs/security` `SecurityUtils` expects a `JwtAuthenticationToken` (`sub`, `email`, `name`, `tid`, authorities) | **1** call site in the whole backend (quote `ApprovalService.resolveActor`) |
| Actor integrity | `ApprovalService.resolveActor(request.getApprover())` **prefers the approver named in the request body** over the authenticated principal, then falls back to `"system"`. approval-routing's Kafka consumer records `requesterId("system")` | A caller can approve or reject as anyone. There is no segregation-of-duties evidence |
| Authorization | No `@PreAuthorize` or `@Secured`, and no `hasRole` checks in any service's main code (only the helper definitions in `libs/security`). Approval tiers (`DEAL_DESK`/`SALES_VP`/`CFO`, `MANAGER`/`DIRECTOR`/`VP_SALES`) have no matching authority check | Tier routing is advisory only |
| ai-service | No `SecurityFilterChain` | Its posture comes from Boot defaults plus whatever `libs/security` puts on the classpath |
| `libs/security` dependencies | Pulls `spring-boot-starter-security`, `oauth2-resource-server`, `oauth2-client`, jjwt and `spring-cloud-azure-starter-active-directory-b2c` into all 11 services | Heavy, implicit runtime behavior. This contradicts ADR-011 rule 5 |
| IdP | Configuration targets Azure AD B2C (`AZURE_AD_B2C_*`) | Microsoft stopped selling B2C to new customers on 1 May 2025. It supports existing tenants until at least May 2030 and positions **Microsoft Entra External ID** as the successor. B2C and External ID are *customer* identity products, while CPQ/CRM users are internal staff |

Because no production authentication path works today, this is effectively a **greenfield decision**: there are no users, sessions or tokens to migrate.

## Decision

1. **Identity provider.** Internal users (sales, deal desk, approvers, admins) authenticate against the organization's **workforce Microsoft Entra ID tenant**. Azure AD B2C is not adopted for new work. External (partner or customer) access is out of scope and would need its own ADR, most likely with Entra External ID. The issuer must be **tenant-specific** (never `common`), and the audience must be validated.

2. **Browser pattern: backend-for-frontend (BFF) at the gateway.** The gateway becomes a confidential OAuth 2.0 client (authorization code + PKCE) and holds tokens server-side. Sessions live in the existing Redis through Spring Session. The browser only receives an `HttpOnly; Secure; SameSite=Lax` session cookie. ADR-004's edge Cookie CSRF becomes load-bearing and stays as-is. Angular already sends `X-XSRF-TOKEN` for relative `/api` calls. This follows [RFC 10017 / BCP 212](https://www.rfc-editor.org/info/rfc10017/) (Aug 2026), which recommends the BFF pattern for browser apps with sensitive data. The gateway already declares `spring-boot-starter-oauth2-client`.

3. **Gateway → services: token relay.** The gateway applies `TokenRelay` to domain routes and strips any inbound identity headers (`X-User-*`, `X-Actor*`, `Authorization` from the browser) before routing.

4. **Every service is a resource server (zero trust inside the perimeter).**
   - `libs/security` provides one auto-configured baseline `SecurityFilterChain`. It validates JWTs (tenant issuer, audience `api://opencrm-api`, clock skew) and maps the Entra `roles` claim to authorities.
   - `/actuator/health` and `/actuator/info` are public.
   - `/api/v1/**` requires an authenticated user token.
   - `/internal/v1/**` requires a **service** token carrying app role `Internal.Invoke`. It is never `permitAll`.
   - Services may *add* rules and may not replace the baseline.
   - The `dev` profile keeps an explicit permit-all mode that logs a startup `WARN` and refuses to start when a production marker (for example `OPENCRM_ENV=prod`) is set.

5. **Service-to-service calls.**
   - **User-initiated synchronous calls** (quote→approval-routing approve/reject, quote→pricing) **relay the caller's token** through a `libs/security` `RestClient` interceptor, so downstream services see the real actor. There is one API app registration (`opencrm-api`) with scopes, and every service accepts that audience. Per-service audiences and on-behalf-of exchange are deferred until a service needs a narrower blast radius.
   - **Machine-initiated calls** (schedulers, Kafka consumers, the outbox relay) use **client-credentials** tokens for the calling service's own identity. In AKS this means workload identity federation, with no client secrets in config.
   - Domain services **never call the gateway**. The quote→gateway `POST /api/v1/configuration/validate` hairpin is replaced by a direct call to CE `/internal/v1/configurations/evaluate`. The resilience policy for these calls belongs to ADR-017 (reserved).

6. **Actor integrity.**
   - Actor fields (approver, requester, `acceptedBy`, `decidedBy`) come **only** from the verified token, through `SecurityUtils.getCurrentUserId()`.
   - Identity fields in request bodies (for example `ApprovalResponseRequest.approver`) are removed, or rejected when they differ from the principal.
   - `"system"` is replaced by `service:<client-id>`, which is valid only under a service token.
   - Events carry the actor as *data* ([ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) envelope), never as authority.

7. **Coarse authorization now, fine-grained later.**
   - Entra app roles are `CPQ.User`, `DealDesk`, `SalesVP`, `CFO` and `Admin`. The `CPQ.User` role in the token maps to Spring authority `ROLE_CPQ_USER`, and likewise for the others.
   - approval-routing, the canonical writer under ADR-013, enforces that the approver's role satisfies the request's tier on approve/reject.
   - The quote BFF authenticates but does not duplicate this check.
   - Record-level and territory authorization is deferred to a later ADR.

8. **`libs/security` becomes the security starter**, which amends ADR-011 rule 5 for this module only.
   - It owns the baseline chain, the JWT authority converter, the `RestClient` interceptors (relay and client-credentials) and `SecurityUtils`.
   - The Azure AD B2C starter and jjwt are removed (Spring's Nimbus decoder is enough).
   - `oauth2-client` moves to the gateway only.

9. **ai-service** adopts the baseline chain. No service runs without an explicit chain.

## Options considered

| Option | Verdict |
|--------|---------|
| **A. BFF at gateway + token relay + per-service JWT validation + client-credentials for machine calls (selected)** | Real actor identity end-to-end. Each service enforces its own boundary, which survives flat AKS networking. Reuses Redis, oauth2-client and edge CSRF. Con: every service validates tokens (JWKS cache; cheap), and app registrations and roles must be administered |
| B. Gateway-trust: services trust the network or a shared header (the crm Javadoc intent) | One compromised pod or misrouted call gets full access. A shared secret sprawls across 11 services. The actor is an unverifiable header. **Rejected** |
| C. Service-mesh mTLS for workload identity + gateway-injected user header | Strong workload identity, but user identity in headers is spoofable unless mesh authorization policy is strict. It also adds a platform dependency before AKS. **Deferred**: compatible with A as an extra layer (ADR-022) |
| D. SPA holds tokens (MSAL, bearer from the browser) | Tokens are exposed to XSS, which RFC 10017 discourages for this data class. It would also make ADR-004's cookie CSRF moot. **Rejected** |
| E. Keep Azure AD B2C | End-of-sale product aimed at consumer identity; the users are employees. **Rejected** |

## Scope and boundaries

- **ADR-004 stands.** Cookie CSRF stays at the edge only. Domain services stay `STATELESS` + `csrf.disable()`, because they receive bearer tokens and no cookies.
- **ADR-013 topology is unchanged.** This ADR only fixes *who* the actor is and *whether* the call authenticates. It does not change the dual-read flag (ADR-005).
- **ADR-015 is unchanged.** Jev state never contains user identifiers, and actor IDs are not added to `jev_decision_log`.
- Network policy and mesh belong to ADR-022 (reserved). Timeouts, retries and circuit breakers belong to ADR-017 (reserved).
- Existing holds (Activities, NgRx, Kafka S2B-9/#241, subscriptions FE) are unchanged.

## Rollout

One PR per step, in order. Progress is recorded in the [Implementation log](#implementation-log).

0. **Tests first.** Add prod-profile slice tests per service:
   - anonymous → 401
   - valid test JWT → 200
   - `/internal/**` without the service role → 403
   - add a test proving `ApprovalService` ignores or rejects a body `approver` that differs from the principal.
   Most of these fail today, and they are the acceptance evidence.
1. **Baseline chain.** Add the baseline chain and authority converter to `libs/security`, behind `opencrm.security.mode=dev|jwt` (`jwt` everywhere except `dev`). Delete the per-service chain duplicates.
2. **Service-to-service.** Add the `RestClient` interceptors (relay and client-credentials). Remove the quote→gateway hairpin. Protect `/internal/**`.
3. **Gateway BFF.** Add `oauth2Login`, Spring Session on Redis, `TokenRelay` and the header-strip filter. The FE only gains 401→login redirect handling.
4. **Actor and roles.** Enforce actor integrity and tier↔role checks in approval-routing. Add service identities to consumers and schedulers.
5. **Go-live gate.**
   - Entra app registrations, app roles and workload identities are documented.
   - No secrets are in YAML.
   - The prod-marker guard is verified.
   - A rollback exercise is recorded. Rollback means redeploying the previous version; `mode=dev` is never allowed in prod.

### Pre-acceptance hardening (2026-09-22)

Branch `fix/adr016-phase0-service-trust`. These changes tighten behaviour without making any new trust decision, so they did not wait for Acceptance:

- **Actor integrity (part of step 0 and step 4).**
  - `ApprovalService.resolveActor` takes the actor from the authenticated principal.
  - A body `approver` or `requestedBy` naming anyone else is refused with 403 `ACTOR_MISMATCH`.
  - With no principal, the call is refused with 401 `AUTHENTICATION_REQUIRED`, unless `approvals.allow-unverified-actor` is set. Only the `dev` profile sets it.
  - `SecurityUtils` no longer treats an `AnonymousAuthenticationToken` as a user, so a permitAll chain cannot yield the actor `anonymousUser`.
  - Unit tests: `ApprovalServiceActorTest` and `SecurityUtilsTest`.
- **Dev chains.**
  - approval-routing, configuration-engine, contract-asset-management and document-generation now have the `@Profile("dev")` permitAll chain that the other six services already had.
  - Their `!dev` chains are unchanged line for line, and each is guarded by a `SecurityConfigNonDevProfileTest`.
  - This closes the local-dev 403s. It does not close F1.

Still open on 2026-09-22 (steps 1–5):

- No service except the gateway is a resource server, so every `!dev` `/api/v1/**` call is still refused.
- `/internal/**` is still `permitAll()` in pricebook and CE.
- approval-routing still trusts the `approverId` request parameter from its caller, and there is no tier↔role check.

## Implementation log

### Steps 0 and 1: the baseline chain (2026-09-23)

**One chain, in `libs/security`.** `OpenCrmSecurityAutoConfiguration` is auto-configured ahead of Boot's security auto-configuration and is the only servlet `SecurityFilterChain` (decisions 4 and 8).
- `opencrm.security.mode`: when unset, the mode is `dev` under the `dev` profile and `jwt` otherwise.
- `dev` mode permits everything and logs a WARN. It refuses to start when `opencrm.env` (`OPENCRM_ENV`) is `prod` or `production`.
- `jwt` mode, in rule order:
  1. `/actuator/health/**` and `/actuator/info` are public.
  2. `/internal/**` needs `ROLE_INTERNAL_INVOKE`.
  3. Service rules come next.
  4. `/api/v1/**` and everything else need an authenticated token.
- The chain is stateless with CSRF off (ADR-004).
- A service adds rules with an `OpenCrmSecurityCustomizer` bean. Because customizer rules come after the `/internal/**` rule, a customizer cannot open `/internal/**`, and a test proves it.

**Token validation (decision 1).** `OpenCrmJwtDecoders` builds a lazy Nimbus decoder: the identity provider is contacted on the first token, not at startup.
- The issuer must be tenant-specific. `common`, `organizations` and `consumers` are refused at startup.
- `aud` must contain one of `opencrm.security.jwt.audiences` (default `api://opencrm-api`). An empty list is refused at startup.
- `exp` and `nbf` are checked with a 60 s clock skew.
- Configuration uses relaxed binding: `OPENCRM_SECURITY_JWT_ISSUERURI`, and optionally `OPENCRM_SECURITY_JWT_JWKSETURI` and `OPENCRM_SECURITY_JWT_AUDIENCES`.
- With no issuer, the service still starts. It logs an ERROR and answers every token with 401, which matches the pre-step-1 state where every `!dev` `/api/v1/**` call was refused. With the production marker set, a missing issuer stops startup instead.

**Roles (decision 7).** `EntraRoleAuthorities` maps each value in the `roles` claim to `ROLE_<UPPER_SNAKE>`:

| App role | Authority |
|----------|-----------|
| `CPQ.User` | `ROLE_CPQ_USER` |
| `DealDesk` | `ROLE_DEAL_DESK` |
| `SalesVP` | `ROLE_SALES_VP` |
| `Internal.Invoke` | `ROLE_INTERNAL_INVOKE` |

Scopes keep Spring's `SCOPE_` mapping, and the principal name is `sub`. Spring Security 7 also grants a `FACTOR_BEARER` authority; role checks ignore it.

**Removed or changed.**
- The nine domain `SecurityConfig` classes are deleted, along with their 16 `SecurityConfig*ProfileTest`s. The gateway's reactive chain stays until step 3.
- ai-service now runs the baseline (decision 9). Before this it had no chain of its own and fell back to Boot's default chain (form login and HTTP Basic against a generated password). Its `@ComponentScan` of `com.opencrm.security` is gone, because the library arrives as auto-configuration.
- `libs/security` no longer carries the Azure AD B2C starter, jjwt or `oauth2-client` (decision 8). Only the gateway still declares `oauth2-client` and B2C, and step 3 removes B2C there.

**Decisions made during implementation.**
- **`/internal/**` stays open in pricebook-service and configuration-engine until step 2.** Their callers have no service token yet: pricing-service calls pricebook for price-book lookups, and quote-service reaches CE through the gateway rewrite. Step 2 is where this ADR protects `/internal/**`. Until then, those two services set `opencrm.security.legacy-open-internal: true`. The flag:
  - logs a WARN;
  - is refused under the production marker;
  - is pinned to exactly those two services by `SecurityBaselineFitnessTest`, so step 2 has to delete it.
- **API docs are private in `jwt` mode.** The old `!dev` chains made `/api-docs/**` and Swagger UI public. The baseline does not, unless a service sets `opencrm.security.permit-api-docs: true`. The docs stay reachable in `dev` mode.
- **Unauthenticated requests get 401**, with a `WWW-Authenticate: Bearer` challenge. The old chains returned 403 because they had no authentication mechanism.

**Evidence.** Everything below runs in the Docker-free unit gate.
- `libs/security` tests:
  - `OpenCrmSecurityJwksTest` runs the production decoder against a local JWKS endpoint. It checks that the principal is `sub` with the mapped roles, that forged and wrong-audience tokens get 401, that a customizer can add an Admin-only rule but cannot open `/internal/**`, and that no session is created.
  - `OpenCrmJwtDecodersTest`, `OpenCrmSecurityModeTest`, `EntraRoleAuthoritiesTest` and `OpenCrmSecurityDevAndUnconfiguredTest`.
  - `SecurityBaselineFitnessTest` stands in for the ArchUnit rules, as a source scan like ADR-018's `EventContractFitnessTest`. It checks four things:
    - no service declares its own servlet chain or `@EnableWebSecurity`;
    - no `permitAll()` on `/internal`;
    - no committed `opencrm.security.mode: dev`;
    - every servlet service depends on `libs/security`.
- **Step 0: one `SecurityBaselineTest` per servlet service (10).** They extend `AbstractSecurityBaselineTest` from the `libs/security` test-jar. Each test loads the service's own `application.yml` outside `dev` and signs real RS256 tokens with `TestTokens`. It checks:
  - health is public;
  - an anonymous request gets 401;
  - a user token gets 200;
  - expired, forged, wrong-audience and wrong-issuer tokens get 401;
  - `/internal/**` gives 403 to a user token and 200 to an `Internal.Invoke` service token. In pricebook and CE it stays open until step 2.
- The actor-spoof test from step 0 is `ApprovalServiceActorTest` (pre-acceptance hardening).

**Still open after step 1.**
- **Step 2:** `RestClient` relay and client-credentials interceptors; removal of the quote→gateway hairpin; service tokens for the `/internal/**` callers; deletion of `legacy-open-internal`.
- **Step 3:** the gateway BFF. It also replaces the gateway's multi-tenant `common` issuer default and drops B2C.
- **Step 4:** tier↔role checks in approval-routing, `service:<client-id>` actors, and request DTOs without identity fields.
- **Step 5:** the go-live gate.
- Until step 3 there is no login in any environment, so every non-dev `/api/v1/**` call is still refused, exactly as before step 1.

### Step 2: service-to-service calls (2026-09-23)

**Outbound authentication in `libs/security`** (`com.opencrm.security.client`, auto-configured). Every `RestClient` that calls another OpenCRM service takes one of two `OutboundAuth` interceptors:

| Interceptor | Use for | Token sent |
|-------------|---------|------------|
| `relayUserToken()` | user-initiated calls to another service's `/api/v1/**` | the caller's bearer token, so the downstream service sees the real actor. With no user token (a Kafka consumer, a scheduler, another thread) it sends the service's own token |
| `serviceToken()` | calls to `/internal/**` | always the service's own token, because only service identities hold `Internal.Invoke` |

- An `Authorization` header the caller set is left alone.
- With no token available, the call goes out without one and a WARN is logged once. That is the `dev` case, where every service permits all.
- With the production marker set in `jwt` mode, a missing token raises `ServiceTokenException`. It is a `RestClientException`, so each client's existing error handling applies.

**Service tokens** come from `ClientCredentialsServiceTokenProvider`, which runs the client-credentials grant against the Entra token endpoint without `oauth2-client` (decision 8).
- **Credential.** AKS workload identity federation: the projected service-account token named by `AZURE_FEDERATED_TOKEN_FILE` is the `jwt-bearer` client assertion. It is re-read for every token request because kubelet rotates it.
- **No configuration in AKS.** Client id, tenant, authority and token file default from `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_AUTHORITY_HOST` and `AZURE_FEDERATED_TOKEN_FILE`, which the workload identity webhook injects. The scope defaults to `api://opencrm-api/.default`. Everything can be overridden under `opencrm.security.client.*`.
- **Caching.** Tokens are cached and refreshed 5 minutes before expiry. If a refresh fails, the cached token is used until it expires.
- **Secrets.** A client secret is accepted only for local tests against a real tenant, and refused at startup under the production marker.
- **Hardening.** The token endpoint must use https. Errors report the OAuth `error` code, never the description, the secret or the assertion.

**Service-to-service calls after step 2:**

| Caller → called | Path | Token |
|-----------------|------|-------|
| quote → pricing | `/api/v1/pricing/execute`, `/api/v1/pricing/simulate` | user (relay) |
| quote → approval-routing | `/api/v1/approvals/**` | user (relay) |
| quote → configuration-engine | `/internal/v1/configurations/evaluate` | service. Before step 2 this went through the gateway |
| pricing → pricebook | `/internal/v1/price-books/resolve-batch` | service |
| configuration-engine → product | `/api/v1/configuration/rules/**` | user (relay) |
| ai-service → gateway (agent tools) | `/api/v1/pricing/execute`, `/api/v1/quotes/*/line-items`, `/api/v1/configuration/validate` | user (relay) |
| gateway → configuration-engine | `/api/v1/configuration/validate` rewritten to `/api/v1/configurations/evaluate`. Before step 2 it was rewritten to the internal path | the browser's |

External APIs (OpenRouter, Jev and the ERP adapter) keep their own credentials.

**Removed or changed.**
- **The quote→gateway hairpin is gone.** quote-service calls configuration-engine directly, configured by `services.configuration-engine.url` (`CONFIGURATION_ENGINE_URL`). `services.gateway.url` (`GATEWAY_SERVICE_URL`) is removed.
- **`/internal/**` is protected in every service.** The step-1 `legacy-open-internal` flag is deleted, along with the pricebook and configuration-engine settings that used it.
- **configuration-engine also serves evaluate on a public path.** `POST /api/v1/configurations/evaluate` joins the internal path. The gateway's `/api/v1/configuration/validate` and `/api/v1/cpq/configuration/validate` shims now rewrite to the public path, because the internal path would refuse the user's token.
- **pricing-service's gRPC listener is off by default** (`grpc.server.port: ${PRICING_GRPC_PORT:-1}`).

**Decisions made during implementation.**
- **`/internal/**` always gets a service token, even for user-initiated calls.** Decision 5 says user-initiated calls relay the user's token, but decision 4 reserves `/internal/**` for `Internal.Invoke`, which user tokens never carry. So pricing→pricebook and quote→configuration-engine send the service token, and the called service sees the calling service, not the user. Both calls are reads that record no actor. A call that must carry the actor goes to `/api/v1/**`.
- **The gRPC listener was an unauthenticated side door.** pricing-service ran a gRPC server on port 9090, outside the servlet chain. Nothing in the repo calls it: configuration-engine declares a client but never uses it. This ADR had not listed it. It is now off by default, and turning it on requires authenticated gRPC first.
- **ai-service still calls the gateway.** Its agent tools use the gateway's agent contract, the same one external agents use (F3), and those calls now relay the user's token. This is an allowlisted exception to "domain services never call the gateway". Step 3 decides between two options: the BFF gateway keeps a bearer-token lane for agents, or ai-service calls the services directly.
- **Correction to decision 1: in v2.0 access tokens, `aud` is the API's client ID.** Entra sets `aud` to the application (client) ID GUID in v2.0 tokens, and to the App ID URI (`api://opencrm-api`) in v1.0 tokens. The tenant issuer `…/v2.0` means v2.0 tokens. Production must therefore set `OPENCRM_SECURITY_JWT_AUDIENCES` to include the API's client ID; keeping `api://opencrm-api` in the list does no harm. Service tokens are still requested with the scope `api://opencrm-api/.default`. Step 5's go-live gate checks this.
- **The security context does not cross threads.** A user-initiated call made on another thread (`@Async` or an executor) sends the service token. Step 4 decides whether any call that records an actor needs the context propagated.

**Evidence.** Everything below runs in the Docker-free unit gate.
- `ServiceToServiceContractTest` (libs/security) sends a calling `RestClient` against a called service that runs the baseline chain in-process, with real RS256 tokens. It shows:
  - `serviceToken()` reaches `/internal/**`;
  - `relayUserToken()` reaches `/api/v1/**` as the user, and gets 403 on `/internal/**`;
  - without a user, `relayUserToken()` calls as the service.
- `ClientCredentialsServiceTokenProviderTest` runs against a local token endpoint. It checks the exact form fields of the workload-identity request, caching, refresh with a re-read assertion, the stale-token fallback, error handling that leaks no secrets, and the https-only rule.
- `OutboundAuthTest` and `ServiceTokenProvidersTest`.
- Per service:
  - `RestClientConfigAuthTest` (quote-service): the user's token goes to pricing and approval-routing, and the service token goes to configuration-engine.
  - `PricingConfigBeansTest` (pricing-service): the service token goes to pricebook.
  - Every `SecurityBaselineTest`, pricebook's and configuration-engine's included, now expects `/internal/**` to refuse anonymous callers and user tokens.
- `SecurityBaselineFitnessTest` gains four checks:
  - every HTTP client a service builds takes an `OutboundAuth` interceptor, unless it calls an allowlisted external API;
  - only allowlisted services reference the gateway;
  - the gateway never routes onto `/internal/**`;
  - no gRPC listener is on by default.

**Still open after step 2.**
- **Step 3:** the gateway BFF, including the ai-service→gateway decision.
- **Step 4:** actor integrity and tier↔role checks. Consumers and schedulers already call out with their service identity through the relay fallback; recording `service:<client-id>` as their actor is step 4.
- **Step 5:** the go-live gate. It now also covers a workload identity for every calling service, with the `Internal.Invoke` app role assigned, and `OPENCRM_SECURITY_JWT_AUDIENCES` including the API's client ID.
- Until step 3 there is still no login outside `dev`.

### Step 3: the gateway BFF (2026-09-23)

**Two ways in, one chain.** The gateway runs one reactive chain with two lanes:

| Lane | Caller | Credential | CSRF (ADR-004) |
|------|--------|------------|----------------|
| Browser (BFF) | the Angular SPA | the session cookie `OPENCRM_SESSION` (`HttpOnly`, `Secure`, `SameSite=Lax`). Tokens stay in the server-side session | required on every state-changing request |
| Bearer | agents (F3) and ai-service's agent tools | `Authorization: Bearer`, validated like every service validates it (tenant issuer, audience, clock skew) | exempt |

**Login (decision 2).**
- The login is on when `OPENCRM_GATEWAY_LOGIN_CLIENTID` names the gateway's Entra app registration. It is the authorization-code flow with PKCE (S256); Spring Security adds `state` and `nonce`.
- **Registration.** It is built from the tenant issuer, without discovery: the authorize, token and JWKS endpoints, and `end_session_endpoint` for logout. It declares no userinfo endpoint. So the gateway reads identity from the validated ID token, never sends the API access token to Microsoft Graph, and does not call Entra at startup.
- **Issuer.** It must be a tenant-specific v2.0 issuer. `common`, `organizations` and `consumers` are refused (decision 1).
- **Scopes.** `openid profile offline_access api://opencrm-api/.default`. The access token is for the OpenCRM API, and the refresh token lets the gateway renew it without the user.
- **Confidential client without a secret.** The gateway authenticates to the token endpoint, for code redemption and for refresh, with the workload-identity assertion from `AZURE_FEDERATED_TOKEN_FILE`. It is re-read for every request, as for step 2's service tokens. A client secret works only for local runs against a real tenant, and is refused under the production marker.
- **After login** the browser returns to the page it was navigating to (Spring Security's saved request), or to `/` when the login started from an API call. Spring Security changes the session ID when it stores the login, which prevents session fixation.
- **Logout.** `POST /logout` (with the CSRF token) ends the session and redirects to Entra's end-session endpoint (RP-initiated logout), which returns the browser to `/`.

**Sessions.** Spring Session keeps the WebSession in the existing Redis. The session holds the security context and the authorized client (access and refresh tokens). The idle timeout is the default 30 minutes.

**How the SPA learns to log in.**
- A page navigation is redirected to the login. That is a GET that accepts HTML, is not under `/api/` and is not an XHR.
- Every other unauthenticated request gets 401 with `WWW-Authenticate: Bearer` and the header `X-OpenCRM-Login: /oauth2/authorization/entra`. CORS exposes the header.
- The SPA's `authRedirectInterceptor` (`libs/shared/data-access`) sends the browser to that path when a 401 carries the header. It follows only a same-origin path, and redirects at most once every 30 seconds, so a broken login cannot loop.
- That is the only FE change, as step 3 intended. For local runs, `nx serve` proxies `/oauth2`, `/login/oauth2` and `/logout` to the gateway without changing the origin.

**Token relay and header strip (decision 3).** `IdentityRelayFilter` is a global filter that runs before routing:
- it removes `Authorization` and every `X-User-*`, `X-Actor*`, `X-Auth-*`, `X-Forwarded-User`, `X-Remote-User` and `X-MS-Client-Principal*` header the caller sent;
- it then sets exactly one `Authorization: Bearer`: the validated bearer-lane token, or the session's access token;
- the session's token is renewed with the refresh token when it nears expiry. If renewal fails, the call gets 401 with the login hint, and the SPA logs in again;
- anonymous and `dev` requests go upstream with no identity at all.

**Other changes.**
- **Actuator.** Only `/actuator/health/**` and `/actuator/info` are public now, as in every service since step 1. Before, all of `/actuator/**` was public at the gateway. API docs are public only when `opencrm.security.permit-api-docs` is set.
- **Production marker.** It now also refuses a gateway without the login. In `jwt` mode without the login, the gateway logs a WARN and offers the bearer lane only.
- **B2C is gone** (decision 1): the starter, its exclusions and settings, the `common` issuer default, and the compose variable. The gateway has no identity defaults at all.
- `org.springframework.security` logs at INFO. DEBUG would log session and token handling.

**The ai-service decision (open since step 2): keep the bearer lane.** ai-service's agent tools keep calling the gateway's agent contract with the relayed user token, the same contract external agents use (F3). Calling the services directly would copy the gateway's CPQ alias rewrites into ai-service and give agents two contracts. The fitness allowlist entry stays, with this reason.

**Decisions made during implementation.**
- **One filter instead of Spring Cloud Gateway's `TokenRelay`.** `TokenRelay` only adds the session's token. Stripping forged headers would need a second filter whose order matters, and the bearer lane would need a third. One ordered filter does all three. It is global rather than per route, so a new route cannot forget it.
- **Bearer requests are exempt from CSRF.** ADR-004 protects cookie-authenticated browsers. A request that carries `Authorization: Bearer` is authenticated by that token, not by a cookie, so the CSRF check adds nothing and would break agents. A cross-site page cannot add that header without a CORS preflight, which the origin allow-list refuses.
- **Consent for `api://opencrm-api/.default`.** With `.default`, Entra asks for consent to the API permissions listed on the gateway's registration. For an internal application a tenant admin grants that consent once (step 5). Otherwise each user sees a consent prompt, or is blocked where user consent is disabled.
- **Behind the ingress.** The redirect URI is `{baseUrl}/login/oauth2/code/entra`. Behind a TLS-terminating ingress, `{baseUrl}` is only right if the gateway trusts the forwarded headers. Step 5 sets that together with the ingress.

**Evidence.** Everything below runs in the Docker-free unit gate.
- `GatewaySecurityChainTest` (11 tests):
  - with the login: an anonymous API call gets 401 and the hint; a page navigation is redirected to the login; the login is an authorization-code request with PKCE, `nonce` and the API scope; bearer GET and POST pass without CSRF; forged, expired and wrong-audience tokens get 401; a cookie POST without the CSRF token gets 403; health is public;
  - without the login: 401 without a hint; the production marker refuses a gateway without the login, and a client secret;
  - `dev`: everything is permitted, and CSRF still applies.
- `IdentityRelayFilterTest` (6): with no authentication, every inbound identity header is removed; the bearer token is relayed and nothing else; the session token is relayed through the authorized-client manager; a session that cannot be renewed gets 401 and the hint, and nothing is forwarded, while an OAuth2 error from further down the chain is not mistaken for one; the filter runs before any route filter.
- `IdentityRelayRoutingTest` (2), through real Spring Cloud Gateway routing to an upstream HTTP server: a request with a bearer token and forged `X-User-Id` and `X-Actor` reaches the upstream with only the bearer token; in `dev` it reaches it with no identity.
- `GatewayLoginConfigurationTest` (6): the registration's endpoints, PKCE, no userinfo and the end-session metadata; multi-tenant, missing and v1.0 issuers are refused; the credential is the workload identity, and a secret only outside production; a local secret uses `client_secret_post`; a refresh authenticates with the re-read assertion against a local token endpoint; the session cookie's attributes.
- `GatewayApplicationStartupTest` (3): the packaged configuration starts in the default and `dev` profiles with the relay filter; with the login it starts with Redis-backed sessions and the `OPENCRM_SESSION` cookie.
- `auth-redirect.interceptor.spec.ts` (6): the SPA follows the hint and still reports the error; it ignores a 401 without the hint, other errors and off-site hints; it redirects at most once every 30 seconds.
- `SecurityBaselineFitnessTest` gains two checks: the gateway trusts one tenant (no multi-tenant issuer, no B2C in its configuration or POM), and gateway routes add no identity headers while the relay filter exists.

**Still open after step 3.**
- **Step 4:** actor integrity and tier↔role checks in approval-routing, and `service:<client-id>` actors.
- **Step 5:** the go-live gate. It now also covers:
  - the gateway's app registration: a Web redirect URI per environment, a federated identity credential for the gateway's service account, and the API's delegated permission with admin consent;
  - forwarded-header trust at the ingress, so `{baseUrl}` is the public https origin;
  - Redis availability and persistence for sessions, since Redis now affects login;
  - how Prometheus scrapes `/actuator/prometheus`, which now needs a token here as at every service (a management port or a scraper identity).
- **FE sign-out.** The SPA has no sign-out control yet. `POST /logout` needs the XSRF token, as a header or a `_csrf` form field.

### Step 4: actors and roles (2026-09-23)

**Who the actor is (decision 6).** `libs/security` now answers "who is calling" in one place.
- `SecurityUtils.getCurrentCaller()` returns a `Caller`, a person or a service:
  - **A person is identified by the Entra object id `oid`, not by `sub`.** Entra makes `sub` pairwise: each client application gets a different value for the same person. The same approver signed in through the gateway and through an agent client would otherwise be two people. `oid` is the same in every application of the tenant. `sub` stays as the fallback for a token without `oid`.
  - **A service is an app-only token**: `idtyp=app`, or, without that optional claim, a token that names a client (`azp`, or `appid` in v1.0) but carries no delegated scope. Entra puts `scp` only in user tokens. Its actor is `service:<client-id>`.
  - `getCurrentUser()` now returns people only. A service token is not a user.
- **`CurrentActor`** (auto-configured) is the value to record: the caller, or, when there is no caller, this service itself. That is `service:<client-id>` of its workload identity, or `service:<application-name>` locally. It covers Kafka consumers, schedulers and async work, and replaces the literal `"system"`.
- **JPA auditing now records someone.** Every service had `@EnableJpaAuditing` but no `AuditorAware`, so `created_by` and `updated_by` were always empty. `libs/security` now provides the auditor, backed by `CurrentActor`.
- **The event actor (ADR-018 D4)** is now `user:<oid>`, `service:<client-id>` for a calling service, or this service.

**Identity no longer comes from the request.**

| Where | Before | After |
|-------|--------|-------|
| approval-routing approve and reject | `approverId` query parameter | the token's user |
| approval-routing submit | `requesterId` in the body, else `"system"` | the caller, user or `service:<client-id>` |
| approval-routing discount consumer | `requesterId("system")` | this service |
| quote BFF submit, approve and reject | body `requestedBy`/`approver`, checked against the principal (phase 0) | fields removed; the BFF relays the token and sends no identity |
| configuration-engine sessions | `userId` query parameter on create and list | the caller; the list shows the caller's own sessions |
| quote documents | `generatedBy("system")` | the actor |
| AI audit log (ai-service) | `created_by` never set | the actor |
| `approvals.allow-unverified-actor` | a dev-profile flag, which `APPROVALS_ALLOW_UNVERIFIED_ACTOR` could turn on anywhere | removed: only `opencrm.security.mode=dev` allows unverified actors, and the production marker refuses dev mode |

**Who may decide (decision 7).** approval-routing checks every approve and reject (`ApprovalAuthority`):

| Tier | May decide |
|------|-----------|
| `DEAL_DESK`, `MANAGER` | DealDesk, SalesVP, CFO |
| `SALES_VP`, `DIRECTOR`, `VP_SALES` | SalesVP, CFO |
| `CFO` | CFO |
| any other tier | no one (fails closed) |

- A higher role satisfies a lower tier.
- Refusals: 401 `AUTHENTICATION_REQUIRED` without a caller; 403 `APPROVER_MUST_BE_A_USER` for a service token; 403 `APPROVER_ROLE_REQUIRED`; 403 `SELF_APPROVAL` for the requester.
- The quote BFF passes approval-routing's 401, 403 and 409 through with its code and message. Before, every error from approval-routing became a 502.
- In `dev` mode, a decision without a caller goes through, is recorded as this service, and logs a WARN.

**Two bypasses closed.**
- **The quote-level decision.** `POST /api/v1/quotes/{id}/approve|reject` could decide a `PENDING_APPROVAL` quote directly. That skipped approval-routing and its role check, and left its approval open. It now answers 409 `APPROVAL_PENDING`. The SPA offers Approve and Reject on the quote only in review; pending approvals are decided in the approvals inbox. Passing a review (`IN_REVIEW`) is unchanged.
- **Legacy local approvals.** An approval that approval-routing does not know could be decided in quote-service, with no role check. It is now history only: 409 `LEGACY_APPROVAL_READ_ONLY`. Reads (ADR-005 dual-read) are unchanged.

**Decisions made during implementation.**
- **`oid`, not `sub`, identifies a person.** The acceptance criterion "the persisted approver equals the token `sub`" is amended to `oid`.
- **Admin and CPQ.User decide nothing.** Administering the system is not approving deals.
- **`DIRECTOR` maps to SalesVP.** The discount path's `DIRECTOR` tier has no role of its own, so it maps to the next role up rather than down.
- **The requester cannot decide their own approval.** Decision 7 does not say this, but the requester is now reliable, so four-eyes can be enforced. Recorded here for the owner to confirm.
- **The policy is code, not configuration.** Changing who may approve is a reviewed change.
- **Background work is attributed to the service** (open since step 2). The security context is not propagated to other threads, consumers or schedulers. A user is recorded where they act, on the request thread; document-generation's async job, for example, records its completion as the service.
- **Identity fields are removed, not rejected.** Decision 6 allows either. A client that still sends one is ignored, since unknown JSON properties are, so a spoofing attempt no longer earns a 403 as in phase 0. It simply has no effect.

**Evidence.** Everything below runs in the Docker-free unit gate.
- `ApprovalDecisionSecurityTest` (approval-routing, 4 tests) uses the real chain, real RS256 tokens and the real controller, service and mapper:
  - the persisted approver is the token's `oid`, and an `approverId` parameter is ignored;
  - a role below the tier gets 403, as do the requester and a service token;
  - the requester of a submit is the token's user, and a `requesterId` in the body is ignored.
- `ApprovalServiceTest` (approval-routing): the tier-by-role matrix (15 cases), self-approval, service tokens, 401, `dev` mode, and service requesters. `DiscountApprovalEventConsumerTest`: the consumer requests as the service.
- `libs/security`: `SecurityUtilsTest` (8), `OpenCrmActorAutoConfigurationTest` (3), `SecurityContextEventActorResolverTest` (5). `ServiceToServiceContractTest` now checks that the called service sees the user's `oid` and `service:quote-service`.
- quote-service: `ApprovalServiceActorTest` (5): the BFF sends no identity, shows the approver approval-routing recorded, passes refusals through, and refuses legacy decisions. `ApprovalRoutingClientTest` (+2) and `QuoteServiceReviewDecisionTest` (3).
- configuration-engine `ConfigurationSessionControllerTest`: sessions belong to the caller, and `userId` parameters are ignored. ai-service `AgentAuditLoggerTest`: every audit row records the actor.
- FE: the quote detail spec (Approve and Reject only in review); the quotes and approvals libraries pass.
- `SecurityBaselineFitnessTest` gains three checks: no request parameter or header names an actor; no request type has an identity field; no code records a hard-coded actor (`"system"`, or a string literal passed to an actor field).
- Test helpers: `TestTokens` now mints Entra-shaped tokens (users with `oid`, `scp` and `azp`; services with `azp`, `idtyp=app` and `oid`), and `TestCallers` puts such a caller into the security context.

**Still open after step 4.**
- **Step 5**, the go-live gate. It now also covers:
  - the `idtyp` optional claim on the API's access tokens, which makes service detection explicit;
  - assigning the DealDesk, SalesVP and CFO app roles to the right groups, since they now decide approvals;
  - existing rows keep their old actors (`"system"`, body values, or nothing); there is no backfill.
- **For the owner:**
  - who may pass a review: `IN_REVIEW` → `APPROVED` has no tier, and any authenticated user can do it, as before;
  - the discount tier mapping (`MANAGER`, `DIRECTOR`, `VP_SALES`) and the four-eyes rule above;
  - nothing yet decides whether a quote needs a tiered approval at all. That is a pricing rule, outside this ADR.
- **Record-level authorization** stays deferred (decision 7): who may see or change whose quotes and configuration sessions.

### Step 5: the go-live gate (2026-09-24)

The gate had four items: the Entra app registrations, roles and workload identities documented; no secrets in YAML; the production-marker guard verified; a rollback exercise recorded. Steps 2 to 4 added their own. Everything the repository can do is done and checked by the build. What needs a tenant and a cluster is in the [identity go-live runbook](../runbooks/identity-go-live.md), with a checklist and a record to fill in.

**The Entra configuration is a file the build checks.** [`infra/entra/opencrm-identity.json`](../../infra/entra/opencrm-identity.json) describes:
- the API registration: `api://opencrm-api`, v2.0 tokens, the `access_as_user` scope, the `idtyp` optional claim, and six app roles with their member types (`Internal.Invoke` for applications, the rest for users), with assignment required;
- the gateway registration: its web redirect path, the admin-consented API permission, and a federated credential for its service account;
- the four workloads that call other services, with `Internal.Invoke` for quote-service and pricing-service, and the environment of every service.

`SecurityBaselineFitnessTest` fails when the file and the code disagree:
- every role the code checks is declared, with the right member type;
- the token version is 2, `idtyp` is requested, and a delegated scope exists;
- the redirect path matches the gateway's registration id;
- every service that calls another has a workload identity, and every `/internal` caller has `Internal.Invoke`;
- the file holds no credential.

**A startup gate in every service (`OpenCrmIdentityPreflight`)**, the gateway included.
- Each service logs one line: mode, production marker, issuer, audiences and service identity.
- Under the production marker a service refuses to start, listing every problem at once, when:
  - the mode is not `jwt`;
  - the issuer is missing or multi-tenant;
  - the audiences lack the API's client id, a GUID;
  - a client secret is configured.
- **Why the client id.** Step 2 found that v2.0 tokens carry the API's client id as `aud`. A deployment that lists only `api://opencrm-api` would refuse every token: an outage made of configuration. It now fails at startup instead.
- Every servlet service proves this with its own `application.yml` (`AbstractSecurityBaselineTest.productionMarkerGuards`): the `dev` profile is refused, a missing client id is refused, and the complete configuration starts.

**No secrets in YAML.**
- Every service defaulted its database password to `postgres` (`${DB_PASSWORD:postgres}`; `${DB_PASS:postgres}` in ai-service). A deployment that missed the variable would have tried a well-known password without a word.
- The default now lives in a `dev | test` profile document. The default profile requires the variable, so a missing one stops the service at startup.
- `SecurityBaselineFitnessTest` checks every service's configuration: outside the `dev`, `test` and `local` profiles, no key named like a secret (password, secret, api-key, token and so on) has a literal value or a non-empty default.

**A go-live blocker found and fixed: the SPA never received the `XSRF-TOKEN` cookie.**
- WebFlux defers the CSRF token and writes the cookie only when something subscribes to it, and nothing in the gateway did.
- So once the BFF went live, every cookie-authenticated state change from the SPA would have been refused with 403: every POST, and sign-out. `dev` mode had the same fault.
- The gateway now subscribes to the token on every request, in a filter after the CSRF filter. `GatewayCsrfCookieTest` failed before the fix and passes after it. ADR-004 has a note.

**Behind the ingress.** With `SERVER_FORWARD_HEADERS_STRATEGY=framework` on the gateway, the login's redirect URI is the public https origin. Without it, forwarded headers are not trusted. Both are tested on the packaged gateway (`GatewayApplicationStartupTest`). The ingress must overwrite the `X-Forwarded-*` headers, not append to what the client sent.

**Sign-out (open since step 3).**
- The toolbar has Sign out. `SessionService` posts a form to `/logout` with the CSRF token in the `_csrf` field. As a page navigation, the browser follows the gateway's redirect to Entra's end-session endpoint.
- When no Entra session is left, the gateway returns to `/`, instead of Spring's default `/login?logout`, which the SPA does not have.

**Rollback.** The runbook (section 8) has the procedure and the record to fill in. Rollback means redeploying version N-1 with the same configuration, never `mode=dev`, which the production marker refuses. The exercise itself needs staging, so it is not done here; it remains a go-live item.

**Evidence.**
- libs/security: `OpenCrmIdentityPreflightTest` (5); `productionMarkerGuards` in every service's `SecurityBaselineTest`; `SecurityBaselineFitnessTest` gains two checks: no secrets in configuration, and the Entra manifest matches the code.
- gateway: `GatewayCsrfCookieTest` (2: the cookie is issued and accepted; sign-out with it lands on `/`); `GatewayApplicationStartupTest` (+1: forwarded headers behind the ingress).
- FE: `session.service.spec.ts` (3) and the toolbar spec (+1).

**Still open: needs an environment, or the owner.**
- **In each environment:** apply sections 1 to 5 of the runbook, pass the section 7 checklist, and record the section 8 rollback exercise. These are the only gate items not done.
- **For the owner:** how Prometheus scrapes metrics; who may pass an `IN_REVIEW` review; the discount-tier mapping and the four-eyes rule.
- **Record-level authorization** stays deferred (decision 7).

## Consequences

- **Positive:**
  - One verifiable identity from browser to database row, so approvals gain audit-grade actor evidence.
  - Services are safe even when reachable directly.
  - `/internal` stops being an anonymous side door.
  - `libs/security` finally carries the convention ADR-011 claims.
  - Tokens never reach the browser.
- **Negative:**
  - Entra administration (app registrations, roles, workload identities).
  - Every service needs JWKS reachability.
  - Local dev needs either `dev` mode or a test issuer (for example a Testcontainers-hosted mock OIDC).
  - The gateway becomes stateful (session in Redis), so Redis availability now affects login.
- **Residual:**
  - Coarse roles only until the fine-grained authorization ADR.
  - Per-service audiences are deferred.

## Acceptance and fitness functions

This ADR is implemented only when all of the following pass in CI. A successful manual login is not evidence.

- **Slice tests:** anonymous 401, service-role 403, and an actor-spoof rejection for every service in the prod profile.
- **ArchUnit rules:**
  - Every `SecurityFilterChain` bean comes from `libs/security` or is registered as an addition to it.
  - No `permitAll()` on `/internal/**`.
  - No `/api/v1` request DTO exposes `approver`, `requesterId`, `actor` or `decidedBy`.
- **GitNexus Cypher:**
  - Every service with a `RestClient` bean imports the `libs/security` interceptor.
  - Every service that records an actor calls `SecurityUtils.getCurrentUserId`.
- **Integration test:** quote→approval-routing approve under the prod profile with a test issuer, where the persisted approver equals the token's user (`oid`, amended in step 4 from `sub`, which Entra makes pairwise per client).

Status on 2026-09-24, after step 5:

| Check | State |
|-------|-------|
| Slice tests: anonymous 401 and service-role 403 | **Done** for all 10 servlet services (`SecurityBaselineTest`), pricebook and CE included since step 2 |
| Slice test: actor-spoof rejection | **Done** in step 4: approval-routing ignores an `approverId` parameter or `requesterId` field and records the token's user (`ApprovalDecisionSecurityTest`); the quote BFF sends no identity (`ApprovalServiceActorTest`) |
| First two "ArchUnit" rules (chain origin, no `/internal` `permitAll`) | **Done** as the source-scan `SecurityBaselineFitnessTest`, which the unit gate runs |
| DTO identity-field rule | **Done** in step 4 as source scans in `SecurityBaselineFitnessTest`: no request parameter, header or request type carries an identity |
| "Every service with a `RestClient` bean imports the `libs/security` interceptor" | **Done** in step 2, as a source scan in `SecurityBaselineFitnessTest` rather than GitNexus Cypher, because the index is unreliable (review §7). It covers every HTTP client a service builds, with external APIs allowlisted |
| "Every service that records an actor calls `SecurityUtils.getCurrentUserId`" | **Done** in step 4: actors come from `CurrentActor` (which reads `SecurityUtils`) and the JPA auditor; the fitness test refuses hard-coded actors |
| quote→approval-routing integration test with a test issuer | **Done** in step 4, in two halves: approval-routing persists the token's `oid` as approver through the real chain (`ApprovalDecisionSecurityTest`), and quote-service relays the user's token to it (`RestClientConfigAuthTest`) |
| Tier↔role check on approve and reject (decision 7) | **Done** in step 4: `ApprovalAuthority`, with the requester unable to decide their own approval |
| Go-live: app registrations, app roles and workload identities documented | **Done** in step 5: `infra/entra/opencrm-identity.json` and the runbook; `SecurityBaselineFitnessTest` keeps the file in step with the code |
| Go-live: no secrets in YAML | **Done** in step 5: database password defaults moved to `dev \| test`; `SecurityBaselineFitnessTest` refuses secret values and defaults elsewhere |
| Go-live: the production-marker guard verified | **Done** in step 5: `OpenCrmIdentityPreflight` at startup, and `productionMarkerGuards` in every service's `SecurityBaselineTest` |
| Go-live: rollback exercise recorded | **Open**: needs staging; procedure and record in runbook section 8 |
| Gateway: login, one relayed token, no forged identity headers | **Done** in step 3: GatewaySecurityChainTest, IdentityRelayFilterTest, and IdentityRelayRoutingTest through real routing. SecurityBaselineFitnessTest checks one tenant, no B2C and no identity-adding route filters |

## Evidence at drafting (HEAD `4a29d31`)

This is the state the ADR was written against. Steps 1 and 2 changed much of it; see the [Implementation log](#implementation-log).

- `backend/services/*/src/main/java/com/opencrm/*/config/SecurityConfig.java`: 10 chains. Only `gateway-service` calls `oauth2ResourceServer`. `@Profile("dev")` is absent in approval-routing, configuration-engine, contract-asset-management and document-generation.
- `backend/services/gateway-service/src/main/resources/application.yml`: `spring.security.oauth2.resourceserver.jwt.issuer-uri` defaults to `…/common/v2.0`; `azure.activedirectory.b2c.enabled: false`.
- `backend/services/pricebook-service/.../config/SecurityConfig.java` and `backend/services/configuration-engine/.../config/SecurityConfig.java`: `/internal/**` `permitAll()`.
- `backend/services/quote-service/.../config/RestClientConfig.java`, `backend/services/pricing-service/.../config/PriceBookClientConfig.java` and `backend/services/configuration-engine/.../config/ProductServiceClientConfig.java`: no auth interceptors.
- `backend/services/quote-service/.../approval/ApprovalService.java` (`resolveActor`) and `.../approval/ApprovalResponseRequest.java` (`approver`).
- `backend/libs/security/pom.xml` (starters) and `backend/libs/security/src/main/java/com/opencrm/security/SecurityUtils.java` (`JwtAuthenticationToken` extraction).

## Sources

Checked 2026-09-22 and rechecked on acceptance, 2026-09-23. Nothing had changed: B2C end of sale on 1 May 2025, support until at least May 2030, and RFC 10017 published 21 Aug 2026 as BCP 212.

- [Azure AD B2C FAQ: end of sale 1 May 2025, support until at least May 2030](https://learn.microsoft.com/en-us/azure/active-directory-b2c/faq)
- [Migrate from Azure AD B2C to Microsoft Entra External ID](https://learn.microsoft.com/en-us/entra/external-id/customers/migrate-from-b2c-to-external-id)
- [RFC 10017: OAuth 2.0 for Browser-Based Applications (BCP 212, Aug 2026)](https://www.rfc-editor.org/info/rfc10017/)
- [Access token claims reference: `aud` is the API's client ID in v2.0 tokens](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference) (step 2 correction to decision 1, checked 2026-09-23)
- [Use Microsoft Entra Workload ID with AKS: the injected environment, and re-reading `AZURE_FEDERATED_TOKEN_FILE` for every exchange](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview) (step 2, checked 2026-09-23)
- [Authorization code flow: PKCE `code_verifier`, and `client_assertion` for confidential web apps](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow) (step 3, checked 2026-09-23)
- [Scopes and permissions: when `.default` prompts for consent](https://learn.microsoft.com/en-us/entra/identity-platform/scopes-oidc#the-default-scope) (step 3, checked 2026-09-23)
- [Microsoft.Identity.Web credentials: `SignedAssertionFilePath`, the Kubernetes workload-identity assertion, as a web app's client credential](https://learn.microsoft.com/en-us/entra/msidweb/authentication/credentials-overview) (step 3, checked 2026-09-23)
- [Workload identity on AKS: the managed identity, its federated credential and the service account annotation](https://learn.microsoft.com/en-us/azure/aks/workload-identity-deploy-cluster) (step 5, checked 2026-09-24)
- [A federated identity credential on an app registration, for the gateway's login](https://learn.microsoft.com/en-us/entra/workload-id/workload-identity-federation-create-trust) (step 5, checked 2026-09-24)
- [Assigning an app role (`Internal.Invoke`) to a managed identity, and the token caching delay](https://learn.microsoft.com/en-us/entra/identity/managed-identities-azure-resources/assign-app-role-managed-identity-powershell) (step 5, checked 2026-09-24)
