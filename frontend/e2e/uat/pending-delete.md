# DRG-758 seller Delete / approver inbox UAT

Run only against an explicitly isolated local test deployment with synthetic data,
using this PR's quote-service and frontend. The narrow runner does not launch or
reconfigure services, provision identities, change policy, or bypass authentication.
A loopback address alone is not proof of isolation: confirm its gateway/services
and databases belong to the disposable test deployment before setting the flag.

Prerequisites:

- Frontend → gateway → quote, approval-routing, product, pricebook and pricing
  services available with their normal authentication and sanctioned APIs.
- Two authenticated synthetic users in the same test tenant: a seller able to
  create fixtures and submit a DEAL_DESK request, and a distinct DEAL_DESK approver.
  Export each login's Playwright storage state to a private path outside the repo.
  The runner requires distinct gateway `OPENCRM_SESSION` cookies (the documented default). Configure the correct
  actor IDs from the supported test identity setup; it checks the submitted
  request's `requestedBy` against the seller ID. Distinct cookies alone cannot
  prove distinct server principals: the test operator must establish which user
  owns each login, particularly the approver (there is no who-am-I API here).
- Playwright's Chromium installed through the existing supported browser setup.

Cookie selection uses the browser-facing `UAT_BASE_URL` host and transport, the
gateway's fixed root path `/`, and expiry. Secure cookies require HTTPS except
for the documented Chromium localhost testing exception. TLS termination at an
ingress does not make the internal gateway URL the cookie origin. If the isolated
stack explicitly configures a custom gateway cookie name, pass
`UAT_SESSION_COOKIE_NAME=<exact-name>`; there is no fallback to `SESSION` or an
arbitrary cookie. Empty/invalid overrides and ambiguous matching cookies block.
Cookie values and storage-state parse errors are never printed. These checks do
not replace verification of distinct synthetic principals and their roles.

From `frontend`:

```sh
UAT_BASE_URL=http://localhost:4200 \
UAT_ISOLATED_FIXTURES=1 \
UAT_SELLER_STORAGE_STATE=/private/test-seller.json \
UAT_APPROVER_STORAGE_STATE=/private/test-approver.json \
UAT_SELLER_ACTOR='<synthetic seller actor id>' \
UAT_APPROVER_ACTOR='<distinct synthetic approver actor id>' \
node e2e/uat-pending-delete.mjs
```

The runner probes availability with GETs, creates fresh `UAT-<runId>-` fixtures
through `draftWithLine`, and submits through the existing BFF until DRG-756 adds
seller submission UI. The submit response supplies the canonical routing ID.
It then opens Quote Detail as seller, clicks More → Delete → Delete, and waits
for that browser DELETE's 409 `QUOTE_APPROVAL_PENDING`. It checks the banner,
seller URL, full quote snapshot and same original pending request before opening
that exact request's quote link in the separate approver's inbox. No approval
decision or cancellation is made. No API DELETE substitutes for the UI click.

Evidence is written by `createReport` to `.ci-tmp/drg-758-browser.json`;
failures can include a screenshot. A blocked or failed run exits nonzero. The
single-session `uat-full` entry records BLOCKED for this check; use the narrow
runner for role-separated evidence. Other existing suites are unchanged.

Tracked fixtures are reported from the per-run ledger as residue for evidence
review. This is not a complete inventory of created IDs: approval requests, for
example, are not tracked. There is no broad sweep, database purge, forced pending
quote deletion, or dependent-data teardown. Dispose of the isolated test stack
through its normal owner-approved lifecycle after review. Never use this runner
against a production endpoint, a forwarded production stack, or live customer data.

Local harness verification (not live product/browser evidence):

```sh
node --test e2e/lib/pending-delete-session.test.mjs e2e/uat/workflows/pending-delete.test.mjs e2e/lib/api.test.mjs
node --check e2e/uat-pending-delete.mjs
```

The historical-orphan backend regression remains unchanged. This scenario does
not validate cancellation races, retries/reconciliation, policy authority, or
server authorization changes.

## Local evidence, 2026-11-17

The follow-up was developed from PR #1009 head
`15e6d2bce1da6b4db62f4d76188bf0857b065098`. The new workflow and API-helper
tests passed 25/25. The workflow negatives cover wrong status/code, quote and
request mutation, missing banner, seller navigation, missing canonical request,
wrong seller identity, non-isolated/single-session setup, and unrelated responses.

Live execution remains **BLOCKED**: this workspace had no Docker containers and
no frontend/gateway listeners, no designated isolated CRM/CPQ deployment, and no
authenticated seller/approver storage states. `node e2e/uat-pending-delete.mjs`
stopped before browser launch with the isolation prerequisite; report
`liveBrowserPassed=false`, BLOCKED=1, residue empty. No app records were created.
These local harness tests are not evidence of successful live browser or
server-enforced role separation. Release still needs the live run above. npm audit
comparison is left to Release; this follow-up changes no dependencies or audit settings.
