# S4 per-role apply authorization

**Status: Proposed.** This file frames a decision. It adopts nothing. The owner slot below is blank. Filling it takes drg's words and a time.

**Story:** [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628). Follow-up from S4 (#685), AC-S4-10 "roles to be confirmed".

**Main sha read:** `bf41c230f6309964a25ef5c2c12e84c41b0c37de`. Line numbers below are that tip.

**Scope.** Which ADR-016 roles may call document apply, and the 401/403 list a later code story would need. Writing this frame authorizes no code, no migration, no security-config edit, no change to `EntraRoleAuthorities`, no flag, and no DRG-625 enablement. DRG-626 hook specifics stay out. `Internal.Invoke` appears as one row in each table.

The role tables and the 401/403 lists in this file are the fixture. No JSON fixture is added.

## AC1. What apply requires today

`jwt` mode is the rule this frame means by "today".

The controller exposes one route and does not check a role itself:

- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/controller/DocumentApplyController.java` lines 26–30: the class comment says apply is internal, that `/internal/**` already requires `Internal.Invoke`, that tenant is the verified token `tid`, and that the route is not on the gateway and is not rate limited.
- Same file, line 34: `PATH` is `POST /internal/v1/amendments/{amendmentId}:apply`.
- Same file, lines 43–50: `apply` takes the amendment id, optional pins, and an optional correlation id. It reads `SecurityContextHolder` and passes `tenantOrNull`, `caller`, and `authorities` into `DocumentApplyOrchestrator.apply`. There is no `@PreAuthorize` and no `@Secured`.
- Same file, lines 53–66: `tid` comes from the JWT claim. A missing, blank, or non-UUID `tid` becomes a null tenant.
- Same file, lines 75–83: `authorities` is the sorted, space-joined granted authorities. The method does not filter them.

The role gate is the shared security chain, which CAM does not replace:

- `backend/libs/security/src/main/java/com/opencrm/security/web/OpenCrmSecurityAutoConfiguration.java` lines 44–45: in `jwt` mode, `/internal/**` needs a service token with app role `Internal.Invoke`.
- Same file, lines 91–94: the JWT converter is `EntraRoleAuthorities`, and `internalAuthority` is `EntraRoleAuthorities.authorityFor(properties.getInternalRole())`.
- Same file, line 102: `requestMatchers("/internal/**").hasAuthority(internalAuthority)`.
- Same file, lines 103–105: customizer rules run after that matcher, then `/api/v1/**` and every other request need an authenticated token.
- `backend/libs/security/src/main/java/com/opencrm/security/web/OpenCrmSecurityProperties.java` lines 28–29: `internalRole` defaults to `Internal.Invoke`.
- `backend/libs/security/src/main/java/com/opencrm/security/web/OpenCrmSecurityCustomizer.java` lines 6–10: a customizer cannot open `/internal/**`.

`dev` mode is a different chain on the same class, lines 86–89: every request is permitted, with a warning, and startup is refused when `opencrm.env` is `prod` or `production`. That mode is local only. It is not option A.

After the chain admits the caller, the orchestrator still enforces tenant and document state. A null tenant is `403 TENANT_REQUIRED` (`DocumentApplyOrchestrator.java` lines 98–100). That check is outside the role decision. Options A, B, and C leave it as it is.

What the existing chain test already records (`DocumentApplyAuthorizationTest.java` lines 89–126), for this path:

| Caller | Result |
|---|---|
| No token | 401, `WWW-Authenticate` starts with `Bearer`. The orchestrator is not called. |
| User token `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`, or `Legal` | 403. The orchestrator is not called. |
| Service token with no `Internal.Invoke` | 403. The orchestrator is not called. |
| Service token `quote-service` with `Internal.Invoke` | Admitted. The test expects 200 when the orchestrator returns a result, and the command carries `ROLE_INTERNAL_INVOKE` and the token `tid`. |

## Role names

ADR-016 decision 7 (`docs/adr/ADR-016-identity-authentication-service-trust.md` line 70) names the user app roles `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, and `Admin`. Decision 4 (same file, lines 50–54) requires a service token carrying `Internal.Invoke` on `/internal/v1/**`. The implementation log records the same gate as `/internal/**` needs `ROLE_INTERNAL_INVOKE` (line 150). ADR-016 is **Accepted** (2026-09-23). This frame does not accept a new ADR and does not edit that one.

`EntraRoleAuthorities.authorityFor` (`EntraRoleAuthorities.java` lines 46–67) maps an app role to `ROLE_<UPPER_SNAKE>`. The mapping test records:

| App role | Authority |
|---|---|
| `CPQ.User` | `ROLE_CPQ_USER` |
| `DealDesk` | `ROLE_DEAL_DESK` |
| `SalesVP` | `ROLE_SALES_VP` |
| `CFO` | `ROLE_CFO` |
| `Admin` | `ROLE_ADMIN` |
| `Internal.Invoke` | `ROLE_INTERNAL_INVOKE` |

`Legal` appears in the apply chain test and is not one of those six. Any other app role is the same kind of row: refused in every option below.

## How to read the tables

Each option is **Status: Proposed**. "Allow" means the role gate admits the caller. It does not mean the document applies: tenant, `EXECUTED`, digest, and the other orchestrator checks still run. "Refuse" is only the role gate, with the HTTP status a later test would assert.

Anonymous means no authenticated caller (no token, or a token the resource server rejects). That row is 401 in every option. The chain returns 401 before a role is considered.

A caller who holds more than one role is allowed when any held role's cell is allow, except where the option says a service principal is refused even if user roles are also present.

## Option A. Today's rule, internal-only

**Status: Proposed.**

`Internal.Invoke` is the only role the gate admits. The path stays `POST /internal/v1/amendments/{amendmentId}:apply`. The chain rule stays `hasAuthority(ROLE_INTERNAL_INVOKE)`.

| App role | Authority | Role gate | HTTP |
|---|---|---|---|
| (none, anonymous) | — | refuse | 401 |
| `Internal.Invoke` | `ROLE_INTERNAL_INVOKE` | allow | admitted (existing test expects 200 when the orchestrator returns a result) |
| `CPQ.User` | `ROLE_CPQ_USER` | refuse | 403 |
| `DealDesk` | `ROLE_DEAL_DESK` | refuse | 403 |
| `SalesVP` | `ROLE_SALES_VP` | refuse | 403 |
| `CFO` | `ROLE_CFO` | refuse | 403 |
| `Admin` only | `ROLE_ADMIN` | refuse | 403 |
| any other app role, including `Legal` | — | refuse | 403 |

Negative tests this option implies. Each refused row is one test. The allow row is the one positive control.

1. No `Authorization` header. Expect 401. Expect the orchestrator not to be called.
2. User token with only `CPQ.User`. Expect 403. Orchestrator not called.
3. User token with only `DealDesk`. Expect 403.
4. User token with only `SalesVP`. Expect 403.
5. User token with only `CFO`. Expect 403.
6. User token with only `Admin`. Expect 403.
7. User token with only `Legal`. Expect 403.
8. Service token with no `Internal.Invoke`. Expect 403.
9. Service token with `Internal.Invoke`. Expect the orchestrator to be called, with `ROLE_INTERNAL_INVOKE` on the command.

`DocumentApplyAuthorizationTest.unauthenticatedAndRolesWithoutInternalInvokeAreTheBaselineRefusal` and `internalInvokeReachesTheOrchestratorWithTheTokenTenant` already cover this list on main. A later story that keeps A reuses those tests. It does not add a new role.

## Option B. Per-role allow list matching S5 / DRG-583

**Status: Proposed.**

The allow set matches the DRG-583 table: `ROLE_CPQ_USER`, `ROLE_DEAL_DESK`, `ROLE_SALES_VP`, and `ROLE_CFO`. `Admin` alone is refused. A service principal is refused. Anonymous is 401.

Cross-reference, not a change. Draft PR [#691](https://github.com/drgaciw/open-crm-cpq/pull/691) (branch `cursor/drg-583-lifecycle-change-7db3`) holds that table in code that is not on this main:

- `LifecycleAuthority.ALLOWED` is those four authorities. `Admin` is refused because administering the system is not a commercial action (class comment on that type).
- `LifecycleChangeService.requireUser` (that branch, lines 351–367): no caller is 401 `AUTHENTICATION_REQUIRED`; a caller who is not a user is 403 `LIFECYCLE_CHANGE_MUST_BE_A_USER`; a user outside `ALLOWED` is 403 `LIFECYCLE_ROLE_REQUIRED`.
- `LifecycleChangeServiceTest.authorization` (that branch, lines 378–396) asserts anonymous 401, an `Internal.Invoke` service token 403, and Admin-only 403.

This file does not edit those types, that test, or that PR. Error-code strings above are the DRG-583 names, cited so the tables stay consistent. They are not applied to document apply by this frame.

| App role | Authority | Role gate | HTTP |
|---|---|---|---|
| (none, anonymous) | — | refuse | 401 |
| `Internal.Invoke` (service principal) | `ROLE_INTERNAL_INVOKE` | refuse | 403 |
| `CPQ.User` | `ROLE_CPQ_USER` | allow | admitted |
| `DealDesk` | `ROLE_DEAL_DESK` | allow | admitted |
| `SalesVP` | `ROLE_SALES_VP` | allow | admitted |
| `CFO` | `ROLE_CFO` | allow | admitted |
| `Admin` only | `ROLE_ADMIN` | refuse | 403 |
| any other app role, including `Legal` | — | refuse | 403 |

A service principal is refused even when the same token also lists `CPQ.User`, `DealDesk`, `SalesVP`, or `CFO`, matching the DRG-583 user-principal check. A user who holds `Admin` plus one allowed role is admitted.

Negative tests this option implies:

1. No authentication. Expect 401.
2. Service token with `Internal.Invoke` and no user principal. Expect 403. Orchestrator not called.
3. Service token with `Internal.Invoke` and also `CPQ.User`. Expect 403.
4. User with only `Admin`. Expect 403.
5. User with only `Legal`. Expect 403.
6. User with an authenticated token and no app role. Expect 403.
7. User with only `CPQ.User`. Expect admitted.
8. User with only `DealDesk`. Expect admitted.
9. User with only `SalesVP`. Expect admitted.
10. User with only `CFO`. Expect admitted.
11. User with `Admin` and `DealDesk`. Expect admitted.

On today's chain, a user token cannot pass line 102 of `OpenCrmSecurityAutoConfiguration`, because a user token does not carry `Internal.Invoke`. A later code story that implements B has to admit those four user authorities. That story is not this file. This file does not edit the security config.

## Option C. Option B plus an amount threshold

**Status: Proposed.**

Option C starts from option B, then applies an amount threshold. The example in the brief is: DealDesk below X; SalesVP and CFO above. X is unset. This file sets no currency and no number.

Proposed reading of that example, still not adopted:

- "Below X" means the document amount is strictly less than X.
- "Above" means the document amount is greater than or equal to X, so the boundary has a single owner.
- `CPQ.User` keeps option B's allow at every amount, including a missing amount. The example names no band for `CPQ.User`.
- `DealDesk` is allowed only when the amount is below X.
- `SalesVP` and `CFO` are allowed only when the amount is at or above X. A cumulative reading (those two also allowed below X) is a different reading of "above". It is not the table.
- A missing amount fails closed for any caller whose only path to allow is a band.
- Service principals, `Admin` only, and any other app role stay refused at every amount. The amount is not consulted.

| App role | Authority | Below X | At or above X | Amount missing | HTTP when refused |
|---|---|---|---|---|---|
| (none, anonymous) | — | refuse | refuse | refuse | 401 |
| `Internal.Invoke` (service principal) | `ROLE_INTERNAL_INVOKE` | refuse | refuse | refuse | 403 |
| `CPQ.User` | `ROLE_CPQ_USER` | allow | allow | allow | — |
| `DealDesk` | `ROLE_DEAL_DESK` | allow | refuse | refuse | 403 |
| `SalesVP` | `ROLE_SALES_VP` | refuse | allow | refuse | 403 |
| `CFO` | `ROLE_CFO` | refuse | allow | refuse | 403 |
| `Admin` only | `ROLE_ADMIN` | refuse | refuse | refuse | 403 |
| any other app role, including `Legal` | — | refuse | refuse | refuse | 403 |

A user who holds `DealDesk` and `SalesVP` is allowed on both sides of X, because any held role's cell is enough. A service principal is still refused on both sides.

Negative tests this option implies. X in the test is a fixture value the later story chooses. It is not chosen here.

1. No authentication, any amount. Expect 401.
2. Service token with `Internal.Invoke`, amount below X and amount at or above X. Expect 403 both times.
3. User `Admin` only, both sides of X. Expect 403.
4. User `Legal` only, both sides of X. Expect 403.
5. User `CPQ.User` only, amount below X, amount at X, amount above X, and amount omitted. Expect admitted each time.
6. User `DealDesk` only, amount below X. Expect admitted.
7. User `DealDesk` only, amount equal to X. Expect 403.
8. User `DealDesk` only, amount above X. Expect 403.
9. User `DealDesk` only, amount omitted. Expect 403.
10. User `SalesVP` only, amount below X. Expect 403.
11. User `SalesVP` only, amount equal to X and amount above X. Expect admitted.
12. User `SalesVP` only, amount omitted. Expect 403.
13. User `CFO` only, the same three amount cases as `SalesVP`. Same expectations.
14. User `DealDesk` and `SalesVP`, amount below X and amount at or above X. Expect admitted both times.

Which amount field is compared, and in which currency, is unset. A later story that implements C names that field. This frame does not.

## Proposed default

**Status: Proposed.**

Default: A until DRG-625 enablement, then B.

Reason: apply stays internal-only until that enablement, and the later allow list matches the DRG-583 person roles because no amount threshold is set.

Owner decision: ___ (drg, time)

The sequence is the proposal. DRG-625 enablement stays an owner gate. This file does not enable the endpoint, does not change a flag, and does not edit the security config or `EntraRoleAuthorities`. Option C is framed and is not the default.

## What a later code story would still have to show

DoD v0.4 item 16 (C16) applies here as a frame only. The tables and the numbered 401/403 lists are that frame. They are not executed tests, and this change adds none.

DoD v0.4 item 23 (C15) applies as this record: three options, the proposed default, and the blank owner slot.

DoD v0.4 item 6 (C14) applies as the ADR citation: ADR-016 is already Accepted; this file accepts no further ADR. The human gate for accepting a decision (G07) stays with drg.
