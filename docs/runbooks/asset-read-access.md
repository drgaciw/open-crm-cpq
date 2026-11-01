# Installed-base read access

Asset list, item, history, supersede-chain, and as-of reads require the Entra app role `InstalledBase.Read`, mapped to `ROLE_INSTALLED_BASE_READ`. An authenticated caller with only general OpenCRM roles is denied. The checked-in identity manifest declares this User role; it must still be provisioned and assigned in each actual Entra tenant before those users can read assets.

This is a coarse capability granting read access across all accounts. It does not implement tenant isolation or account-scoped authorization. Grant it only to users authorized to read the complete installed base, and do not enable broader production access until a separate account-scope policy is enforced and tested. Service principals are not granted this role by the manifest.

The currently merged asset creation and history features have independent lifecycle and replay gaps; this access guard does not make those records complete or reliable. See the post-merge reviews on #713 and #719 before enabling downstream reliance.
