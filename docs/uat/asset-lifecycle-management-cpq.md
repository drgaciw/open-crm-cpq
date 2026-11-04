# Asset lifecycle management: UAT coverage and gaps

This is a reconstructed test outline, not the originally requested host-local specification. It does not certify asset lifecycle behavior. The Angular origin defaults to `http://localhost:4200` and can be changed with `UAT_BASE_URL`; requests traverse its configured proxy and gateway.

Run the browser suite with `cd frontend && npm run e2e:uat -- --suite asset-lifecycle --no-jev`. It requires a running UI and the services needed by each check. Results are specific to that deployment and its test data; this suite is not wired into CI. Do not interpret a blocked or gap result as a pass.

| Check | What is observed | What remains unverified |
| --- | --- | --- |
| Quotes and Deals shells | A visible heading on each page, with the harness browser-health monitor | Full workflows and data correctness |
| Installed base and history reads | A successful response with an array body | Asset creation, event history, as-of behavior, authorization and routing when reads fail |
| Renewal and churn reads | Successful, correctly shaped account report bodies | Renewal/churn calculations and exclusion rules |
| Quote detail | An API-seeded draft is opened in the UI and read back through the API | Creating the quote through the UI and activating a contract |
| Renewal-from-assets and installed-base UI | Recorded as GAP; neither workflow is exercised | Controls, navigation, data, and end-to-end behavior |
| S4 apply hold | Recorded as BLOCKED: this suite has no safe apply fixture or deployed readiness evidence | Actual `ASSET_LIFECYCLE_HOLD` behavior, audit, atomicity and replay |
| Null-start apply | Recorded as BLOCKED: the browser suite neither runs the backend test nor inspects the ledger | `START_DATE_MISSING` and no ledger mutation in this environment |

401, 403, 404, validation errors, and 503 do not prove that the intended CAM route was reached: these may originate in the gateway or another intermediary. Successful response shape alone also does not prove the underlying lifecycle invariants. Verify `InstalledBase.Read` with both authorized and unauthorized requests against a known deployment and fixture.

The intended lifecycle acceptance remains broader: idempotent asset creation on authoritative activation; effective-dated amendments, termination, and supersession; correct history and as-of reads; renewal and churn semantics; and audit/outbox consistency. These need real, authorized fixtures, observable mutations and replay tests before sign-off. The S4 hold and acceptance-time writer hold are containment, not evidence those features are complete. See the repository's hold runbooks before any mutation.

`DocumentApplyNullStartDateTest` can be run separately with the appropriate Java/Maven environment; a source file's presence does not establish that the test ran, passed, or covered a live deployment. Record its command and result separately, including whether integration tests and browser UAT actually ran.

## CRM API runner

Run `cd frontend && npm run e2e:uat:asset-lifecycle-crm-api` against an authorized test deployment; configure `UAT_BASE_URL`, `UAT_CAM_BASE_URL` and `UAT_QUOTE_BASE_URL` for CRM, CAM and quote-service. Optional service-specific bearer tokens are `UAT_CRM_BEARER_TOKEN`, `UAT_CAM_BEARER_TOKEN` and `UAT_QUOTE_BEARER_TOKEN`. Use distinct trusted service origins for credentials. Do not include tokens in evidence or logs. Fixture writes are disabled by default. Setting `UAT_ALLOW_WRITES=true` creates CRM and quote data and does not delete it; use only a disposable, explicitly authorized test environment and clean up according to its procedures.

The runner records rejected reads and unexercised S4 and null-start checks as BLOCKED. A successful shaped read establishes only the observed response, not complete lifecycle behavior. The two node tests for the runner use synthetic responses and are not live UAT.
