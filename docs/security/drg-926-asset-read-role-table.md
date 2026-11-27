# DRG-926 CAM installed-base read role table (C16)

Role authorization table for all seven `contract-asset-management` installed-base `GET` routes introduced by `#713`, `#719`, and `#740`.

Role catalog and policy references:

- [`infra/entra/opencrm-identity.json`](../../infra/entra/opencrm-identity.json) (declared Entra `opencrm-api` app-role catalog including `InstalledBase.Read` at line 50, delegated scope `access_as_user`, and `idtyp` optional claim)
- [`docs/security/drg-514-write-route-role-table.md`](drg-514-write-route-role-table.md) (existing C16 role-table catalog convention; note that `ADR-016` does not list `InstalledBase.Read`)
- [`docs/runbooks/identity-go-live.md`](../runbooks/identity-go-live.md) (Entra role assignment runbook and installed-base verification checklist)
- [`docs/runbooks/asset-read-access.md`](../runbooks/asset-read-access.md) (installed-base read access contract, fail-closed `dev` behavior, and tenant/account scope boundary)
- Companion table for `#742`: [`docs/security/drg-926-from-active-assets-role-table.md`](drg-926-from-active-assets-role-table.md)

## Allowed vs. refused roles (all 7 routes)

Every route below enters `InstalledBaseReadScope.current()` -> `InstalledBaseReadAccess.require()` (structurally guarded by `AssetReadEndpointCoverageTest`). No `OpenCrmSecurityCustomizer` is added in CAM because the existing service-layer gate enforces role and delegated-scope checks in both `jwt` and `dev` modes.

- **Allowed (`200 OK`):** Delegated human user JWT carrying app role `InstalledBase.Read` (`ROLE_INSTALLED_BASE_READ`) **and** a non-blank delegated `scp` claim (for example `access_as_user`).
- **Refused (`403 Forbidden`):** Every other declared app role in `infra/entra/opencrm-identity.json` without `InstalledBase.Read` — `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`, `Internal.Invoke` — plus a delegated user token with no app role, an unknown role, or an app-only/ambiguous JWT (explicit `idtyp=app`, missing `scp`, or blank `scp`) even if the token carries `InstalledBase.Read`.
- **Unauthenticated:** `401 Unauthorized` from `OpenCrmSecurityAutoConfiguration` in `jwt` mode; `403 Forbidden` from `InstalledBaseReadAccess` when no security filter is present or under `opencrm.security.mode=dev`.

| Origin PR | Route | Controller / Service | Allowed | Refused (`403`) | Unauthenticated |
| --- | --- | --- | --- | --- | --- |
| `#713` | `GET /api/v1/assets` | `AssetController.list` -> `AssetQueryService.list` | Delegated user with `InstalledBase.Read` + non-blank `scp` | `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`, `Internal.Invoke`, no app role, unknown role, app-only/missing-`scp`/blank-`scp` JWT (even with `InstalledBase.Read`) | `401` (`jwt`), `403` (`dev` / standalone) |
| `#713` | `GET /api/v1/assets/{id}` | `AssetController.get` -> `AssetQueryService.get` | Same | Same | Same |
| `#719` | `GET /api/v1/assets/{assetId}/history` | `AssetQueryController.history` -> `AssetHistoryProjection.history` | Same | Same | Same |
| `#719` | `GET /api/v1/assets/{assetId}/supersede-chain` | `AssetQueryController.supersedeChain` -> `AssetHistoryProjection.supersedeChain` | Same | Same | Same |
| `#719` | `GET /api/v1/assets?accountId&asOf` | `AssetQueryController.installedBase` -> `AssetHistoryProjection.installedBase` | Same | Same | Same |
| `#740` | `GET /api/v1/assets/reports/upcoming-renewals` | `AssetReportController.upcomingRenewals` -> `AssetReportService.upcomingRenewals` | Same | Same | Same |
| `#740` | `GET /api/v1/assets/reports/churn` | `AssetReportController.churn` -> `AssetReportService.churn` | Same | Same | Same |

## Enforcement code (existing, unchanged)

- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/InstalledBaseReadAccess.java`
- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/InstalledBaseReadScope.java`
- `backend/libs/security/src/main/java/com/opencrm/security/web/OpenCrmSecurityAutoConfiguration.java`

## Defensive test coverage

All tests below run in the Docker-free `Backend Unit` gate (`contract-asset-management`):

- `AssetReadRoleMatrixSecurityTest` — full `springSecurityFilterChain` (`jwt` mode, real RS256 `TestTokens`) across all 7 routes: anonymous (`401`), every refused role declared in `infra/entra/opencrm-identity.json` (`CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin`, `Internal.Invoke`), roleless user (`403`), unknown role (`403`), app-only service token with `InstalledBase.Read` (`403`), allowed delegated user with `InstalledBase.Read` alone and combined with `CPQ.User` (`200`), plus a manifest-sync assertion against `infra/entra/opencrm-identity.json`.
- `AssetReadAuthorizationTest` — direct service-layer checks across all 7 methods (anonymous, `CPQ.User`, explicit/inferred app-only JWT with role, allowed delegated user).
- `AssetReadHttpAuthorizationTest` — standalone MockMvc HTTP checks (`403` / `200`).
- `AssetReadJwtFilterTest` — `jwt` filter chain checks for app-only, ambiguous (no `scp`), blank `scp`, and delegated user tokens.
- `AssetReadDevModeTest` — `opencrm.security.mode=dev` permitAll filter still fails closed with `403` for anonymous requests across all 7 routes.
- `AssetReadEndpointCoverageTest` — structural check that every asset read controller method enters `InstalledBaseReadScope`.

## Explicit scope boundaries

- **Tenant and account isolation (DRG-906 / C17):** Tenant filtering via JWT `tid` (`InstalledBaseReadScope`, `404` on item/history/supersede-chain and empty on list/as-of/reports) landed in PR `#1077` (`AssetReadTenantScopeTest`, `AssetReadCrossTenantHttpTest`, `AssetReadCrossTenantIntegrationTest`). Account-level isolation (`DRG-906 AC2`, `drg` Q11) remains an open owner decision and is outside this table.
- **`quote-service` `from-active-assets` renewal mode (`#742` / DRG-658):** Documented separately in [`docs/security/drg-926-from-active-assets-role-table.md`](drg-926-from-active-assets-role-table.md); continuation route gates remain deferred pending owner decisions `D-A`, `D-B`, and `D-C`, and the `HttpAssetReadClient` `401`/`403` -> `403` error-mapping fix is tracked separately.
