# Installed-base read access

Asset list, item, history, supersede-chain, as-of reads, upcoming-renewal reports, and asset-churn reports require a human user with the Entra app role `InstalledBase.Read`, mapped to `ROLE_INSTALLED_BASE_READ`. JWT-backed reads also require a nonblank delegated `scp` claim. An authenticated caller with only general OpenCRM roles is denied; app-only and ambiguous JWTs without a delegated scope are denied even if they carry the role. The checked-in identity manifest declares this User role; it must still be provisioned and assigned in each actual Entra tenant before those users can read assets.

In dev mode the web filter permits anonymous requests and does not authenticate bearer tokens; anonymous asset reads still fail closed with 403. To exercise asset reads from a local browser, use JWT mode with a configured test tenant and an assigned role rather than expecting a bearer token to authenticate in dev mode. Test-only injected authenticated user contexts with the role remain supported.

This is a coarse capability granting read access across all accounts. It does not implement tenant isolation or account-scoped authorization. Grant it only to users authorized to read the complete installed base, and do not enable broader production access until a separate account-scope policy is enforced and tested. Service principals are not granted this role by the manifest.

The currently merged asset creation and history features have independent lifecycle and replay gaps; this access guard does not make those records complete or reliable. See the post-merge reviews on #713 and #719 before enabling downstream reliance.
