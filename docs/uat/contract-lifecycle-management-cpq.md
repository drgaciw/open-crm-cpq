# Contract lifecycle management: UAT coverage and gaps

This outline was reconstructed from repository material because the requested host-local specification was unavailable. It is not a substitute for that specification or a contract-lifecycle sign-off. The browser suite runs with `cd frontend && npm run e2e:uat -- --suite contract-lifecycle --no-jev` against the configured UI origin, proxy and gateway. Its results apply only to the deployment and fixtures actually tested.

| Check | What can be observed | What remains unverified |
| --- | --- | --- |
| Quotes shell and draft | Visible heading and the status of a created fixture | Acceptance, lifecycle processing and production authorization |
| Subscriptions read | Successful paginated content response | Record completeness, tenant scoping and mutation behavior |
| Deprecated renew | HTTP 410 with `SUBSCRIPTION_RENEW_DEPRECATED` JSON error code | Successor continuation behavior and resulting state |
| Continuation | BLOCKED without an authorized request and real subscription and asset fixture | Draft creation, pricing and application |
| Amendment and ERP | BLOCKED until authorized gateway and ERP fixtures are exercised | Ownership, routing and downstream behavior |
| S4 hold and null-start | BLOCKED: this browser suite neither applies a document nor runs the backend test | Deployed hold behavior, `START_DATE_MISSING` and absence of ledger mutations |
| Backend/frontend contracts | BLOCKED: browser UAT does not execute contract tests | JSON names, types, nullability and enum compatibility |

A 401, 403, 404 or generic validation error may originate in an intermediary and does not establish that the intended service or operation was reached. A source file's presence does not establish a test ran, passed or covered a deployment. Run backend tests separately and record their actual command and outcome. The shared-model tests check selected source field names; they are not serialization-aware contract tests. A passing process or a report containing BLOCKED/GAP outcomes is not a lifecycle pass.

Full acceptance requires authorized tenant-scoped fixtures, actual continuation and apply execution, event and ledger observations, error and replay behavior, effective-date and ordering coverage, and end-to-end contract verification. Follow existing holds and use a disposable authorized environment for any mutation. Record created fixture cleanup and any residue; browser UAT is not wired into CI.
