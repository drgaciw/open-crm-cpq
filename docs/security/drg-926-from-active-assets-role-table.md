# DRG-926 `quote-service` `from-active-assets` renewal mode role table (C16)

Current-contract role table for the `FROM_ACTIVE_ASSETS` renewal continuation mode introduced by `#742` (`DRG-658`) in `quote-service`.

Role catalog and policy references:

- [`infra/entra/opencrm-identity.json`](../../infra/entra/opencrm-identity.json) (declared Entra `opencrm-api` app-role catalog including `InstalledBase.Read` at line 50, delegated scope `access_as_user`, and `idtyp` optional claim)
- [`docs/security/drg-514-write-route-role-table.md`](drg-514-write-route-role-table.md) (existing C16 role-table catalog convention; note that `ADR-016` does not list `InstalledBase.Read`)
- [`docs/runbooks/identity-go-live.md`](../runbooks/identity-go-live.md) (Entra role assignment runbook)
- [`docs/runbooks/asset-read-access.md`](../runbooks/asset-read-access.md) (installed-base read access contract and `dev` mode note)
- Companion table for CAM's seven read routes: [`docs/security/drg-926-asset-read-role-table.md`](drg-926-asset-read-role-table.md)

## Today's contract (as implemented on `main`)

`quote-service` exposes `FROM_ACTIVE_ASSETS` on the existing continuation endpoints (`QuoteContinuationController`):

- `POST /api/v1/quotes/continuations` (`author`, creates a draft `RENEWAL` quote)
- `POST /api/v1/quotes/continuations/preview` (`preview`, returns a `QuoteContinuationPolicy.Plan` without saving a quote)
- Downstream lifecycle validation: `QuoteContinuationService.validate()` re-enters `AssetRenewalDrafts.validate()` -> `price()` during quote `calculate` and `accept` (`QuoteService`). The Kafka `draftRenewal()` consumer does **not** use `FROM_ACTIVE_ASSETS`.

Authorization today is split across two layers:

1. **`quote-service` HTTP chain (`OpenCrmSecurityAutoConfiguration`):** Both continuation routes sit on the authenticated baseline (`.authenticated()`). There is **no route-level role matcher** (`OpenCrmSecurityCustomizer`) on `/api/v1/quotes/continuations` or `/api/v1/quotes/continuations/preview` today. An unauthenticated request in `jwt` mode receives `401 Unauthorized`.
2. **Downstream CAM asset read (`AssetRenewalDrafts` -> `HttpAssetReadClient`):**
   - `AssetRenewalDrafts.price()` first reads the target subscription schedule from CAM `/internal/**` via `subscriptionScheduleRestClient` using `quote-service`'s own service token (`OutboundAuth.serviceToken()`, `Internal.Invoke`), consistent with all continuation modes.
   - `AssetRenewalDrafts.price()` then reads the account's active installed base from CAM `GET /api/v1/assets?accountId&asOf` via `assetReadRestClient` (`OutboundAuth.relayUserToken()`), relaying the caller's bearer token.
   - CAM's `InstalledBaseReadAccess.require()` enforces **`InstalledBase.Read` (`ROLE_INSTALLED_BASE_READ`) plus a non-blank delegated `scp` claim** on that relayed token.

| Action (`renewalSourceMode = FROM_ACTIVE_ASSETS`) | Route-level gate in `quote-service` | Effective allowed caller (enforced by CAM on relayed token) | Refused caller set (at CAM) | Current response when CAM refuses (known defect) | Unauthenticated (`jwt`) |
| --- | --- | --- | --- | --- | --- |
| `POST /api/v1/quotes/continuations` (`author`) | Authenticated baseline only (no role gate today) | Any authenticated delegated human user holding `InstalledBase.Read` + non-blank `scp` (regardless of other app roles today) | `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`, `Internal.Invoke` without `InstalledBase.Read`; no app role; unknown role; app-only / missing-`scp` / blank-`scp` JWT (even with `InstalledBase.Read`) | **`502 Bad Gateway` (`ASSET_READ_UNAVAILABLE`)** — see known defect below | `401 Unauthorized` |
| `POST /api/v1/quotes/continuations/preview` (`preview`) | Authenticated baseline only (no role gate today) | Same | Same | **`502 Bad Gateway` (`ASSET_READ_UNAVAILABLE`)** — see known defect below | `401 Unauthorized` |

### Known defect: CAM `401`/`403` currently mapped to `502 ASSET_READ_UNAVAILABLE`

When CAM refuses the relayed token with `401` or `403`, `HttpAssetReadClient.installedBase(...)` (`backend/services/quote-service/src/main/java/com/opencrm/quote/client/HttpAssetReadClient.java`, lines 49–54) catches the exception generically and throws `BusinessException("Cannot read active assets; retry or choose a valid account", HttpStatus.BAD_GATEWAY, "ASSET_READ_UNAVAILABLE")`.

- This is a **known defect against [`ADR-017`](../adr/ADR-017-synchronous-inter-service-communication.md) D-06** (which calls for passing a callee's 4xx error status through rather than translating it to `502`).
- Fixing `HttpAssetReadClient` (mapping CAM `401`/`403` -> `403` with a distinct error code while keeping `502` for 5xx/unparseable responses) and adding `quote-service` refused-caller `403` tests are **tracked in a separate lane** and are intentionally not changed in this PR.
- Existing tests on `main` covering today's client and relay behavior:
  - `RestClientConfigAuthTest.relaysToApiV1` (verifies `assetReadRestClient` relays the user bearer token to CAM `/api/v1/assets`)
  - `HttpAssetReadClientTest.aRefusalDoesNotBecomeAnEmptyInstalledBase` (verifies a CAM `403` throws `BusinessException` rather than returning an empty installed base)
- **`dev` mode note:** Under `opencrm.security.mode=dev`, `quote-service` permits unauthenticated requests and relays no user token, CAM refuses with `403`, and `HttpAssetReadClient` currently returns `502 ASSET_READ_UNAVAILABLE`. Use `jwt` mode locally to exercise `from-active-assets` (see [`docs/runbooks/asset-read-access.md`](../runbooks/asset-read-access.md)).

## Deferred route gate — open owner questions (`drg`)

Adding a route-level role gate on `/api/v1/quotes/continuations` or `/api/v1/quotes/continuations/preview` is **deferred** pending owner decisions. No gate is added and no roles are prescribed here.

- **D-A (Gate continuation routes at all?):** Today any authenticated caller can author or preview a continuation. Adding an `OpenCrmSecurityCustomizer` gate changes behavior across **all** continuation modes (`AMENDMENT`, `EXPANSION`, `EXTENSION`, `RENEWAL` from terms, and `FROM_ACTIVE_ASSETS`) and would refuse `Admin` and roleless authenticated callers that are accepted today.
- **D-B (If gated, does `preview` get the same gate as `author`?):** `POST /api/v1/quotes/continuations/preview` writes no quote, but returns priced renewal lines derived from the installed base.
- **D-C (Who must hold `InstalledBase.Read` at `calculate` and `accept`?):** `QuoteContinuationService.validate()` re-runs the `AssetRenewalDrafts` read path at quote `calculate` and `accept` using whoever's token is present on those calls. Owner decision is needed on whether secondary actors (reviewers/approvers/acceptors, or any non-user path if one exists) must hold `InstalledBase.Read` or how that step should behave.

## Explicit scope boundaries

- **Tenant and account isolation (DRG-906 / C17):** Downstream CAM enforces tenant filtering from the relayed token's `tid` (`PR #1077`); account-level isolation (`DRG-906 AC2`, `drg` Q11) remains separate and open.
- **No production changes in this PR:** `HttpAssetReadClient`, `QuoteContinuationController`, `QuoteContinuationService`, and `AssetRenewalDrafts` are unchanged.
