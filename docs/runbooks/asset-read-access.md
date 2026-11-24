# Installed-base read access

Asset list, item, history, supersede-chain, as-of reads, upcoming-renewal reports, and asset-churn reports require a human user with the Entra app role `InstalledBase.Read`, mapped to `ROLE_INSTALLED_BASE_READ`. JWT-backed reads also require a nonblank delegated `scp` claim. An authenticated caller with only general OpenCRM roles is denied; app-only and ambiguous JWTs without a delegated scope are denied even if they carry the role. The checked-in identity manifest declares this User role; it must still be provisioned and assigned in each actual Entra tenant before those users can read assets.

The tenant is taken from the authenticated principal's `tid` claim (`SecurityUtils` user context). It is never read from a header, body, or query parameter. `accountId`, `subscriptionId`, and `contractLineId` only narrow results inside that tenant. They cannot widen it. There is no account-grant source of truth yet (Decision Q11). A caller who holds `InstalledBase.Read` still sees every account in their tenant. Account isolation is a follow-up and is not this slice.

`tid` is the interim per-deployment tenant (ADR-016 / DRG-632), stored on `subscriptions.tenant_id`. Asset reads join `assets` to `subscriptions` and keep a row only when `subscriptions.tenant_id` equals the caller `tid`.

Fail closed:

- A caller with no `tid`, or a `tid` that is not a GUID, sees no rows. Item, history, and supersede-chain reads return 404 with the same body as a missing id. Lists, as-of reads, and both reports return an empty result.
- Assets whose subscription `tenant_id` is null are invisible to every read. Null means the provenance `tid` was absent or not a GUID when the subscription was created (DRG-662). That includes quote-service renewal-from-assets (`HttpAssetReadClient` sends the caller's token and is not changed here): those assets produce an empty installed base, so no renewal lines are built. Renewals still use DRG-549 pricing. Any data fix that backfills null tenants is S7-class (G04) and is out of scope.

A missing role stays 403. Cross-tenant reads return 404 or an empty result and do not reveal whether the row exists. Hops on a supersede chain that leave the caller's tenant are omitted.

In dev mode the web filter permits anonymous requests and does not authenticate bearer tokens; anonymous asset reads still fail closed with 403. To exercise asset reads from a local browser, use JWT mode with a test `tid` that matches `subscriptions.tenant_id` on the seeded rows. Anonymous dev reads stay 403. Non-dev profiles are not weakened. Test-only injected authenticated user contexts with the role remain supported, and they still need a GUID `tid` or the read is empty.

Rate limiting for `/api/v1/assets/**` is the gateway default `RequestRateLimiter` (Redis) on every route. Calls that reach contract-asset-management directly, skipping the gateway, are not limited. That residual is for Security to accept or reject.

The currently merged asset creation and history features have independent lifecycle and replay gaps; this access guard does not make those records complete or reliable. See the post-merge reviews on #713 and #719 before enabling downstream reliance. The legacy acceptance writer and the S4 asset-lifecycle hold are unchanged.
