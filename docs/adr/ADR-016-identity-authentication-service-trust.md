# ADR-016: Identity, authentication and service-to-service trust

**Status:** Proposed. This is documentation only; it changes no code, YAML or runtime behavior.
**Date:** 2026-09-22
**Deciders:** Pending (project owner). Drafted from the [GitNexus ADR review](../forge/39-adr-gitnexus-review-2026-09-22.md) (finding F1).
**Relates to:**
- [ADR-004](ADR-004-csrf-edge-boundary.md) (edge CSRF; this ADR covers the auth model it left out of scope)
- [ADR-009](ADR-009-gateway-edge-only.md)
- [ADR-011](ADR-011-shared-maven-libs-published-language.md) (`libs/security`)
- [ADR-013](ADR-013-quote-bff-approvals-single-writer.md) (approval actor)
- [ADR-015](ADR-015-jev-advisory-decision-support.md)
- [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (actor on events)

**Does not authorize:**
- changing any `SecurityConfig`
- enabling JWT validation on domain services
- removing `dev` permit-all chains
- creating IdP tenants or app registrations
- FE login work
- changing ADR-004's CSRF posture

Accepting this ADR makes the target model the contract. Implementation still needs a wave plan.

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

## Decision (Proposed)

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

## Rollout (after Acceptance and a wave plan)

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

Still open (steps 1–5):

- No service except the gateway is a resource server, so every `!dev` `/api/v1/**` call is still refused.
- `/internal/**` is still `permitAll()` in pricebook and CE.
- approval-routing still trusts the `approverId` request parameter from its caller, and there is no tier↔role check.

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
- **Integration test:** quote→approval-routing approve under the prod profile with a test issuer, where the persisted approver equals the token `sub`.

## Evidence (HEAD `4a29d31`)

- `backend/services/*/src/main/java/com/opencrm/*/config/SecurityConfig.java`: 10 chains. Only `gateway-service` calls `oauth2ResourceServer`. `@Profile("dev")` is absent in approval-routing, configuration-engine, contract-asset-management and document-generation.
- `backend/services/gateway-service/src/main/resources/application.yml`: `spring.security.oauth2.resourceserver.jwt.issuer-uri` defaults to `…/common/v2.0`; `azure.activedirectory.b2c.enabled: false`.
- `backend/services/pricebook-service/.../config/SecurityConfig.java` and `backend/services/configuration-engine/.../config/SecurityConfig.java`: `/internal/**` `permitAll()`.
- `backend/services/quote-service/.../config/RestClientConfig.java`, `backend/services/pricing-service/.../config/PriceBookClientConfig.java` and `backend/services/configuration-engine/.../config/ProductServiceClientConfig.java`: no auth interceptors.
- `backend/services/quote-service/.../approval/ApprovalService.java` (`resolveActor`) and `.../approval/ApprovalResponseRequest.java` (`approver`).
- `backend/libs/security/pom.xml` (starters) and `backend/libs/security/src/main/java/com/opencrm/security/SecurityUtils.java` (`JwtAuthenticationToken` extraction).

## Sources

Checked 2026-09-22; recheck when the ADR is Accepted.

- [Azure AD B2C FAQ: end of sale 1 May 2025, support until at least May 2030](https://learn.microsoft.com/en-us/azure/active-directory-b2c/faq)
- [Migrate from Azure AD B2C to Microsoft Entra External ID](https://learn.microsoft.com/en-us/entra/external-id/customers/migrate-from-b2c-to-external-id)
- [RFC 10017: OAuth 2.0 for Browser-Based Applications (BCP 212, Aug 2026)](https://www.rfc-editor.org/info/rfc10017/)
