# DRG-452 — Quote alternatives and seller preference

Scope: side-by-side persisted quotes for one opportunity, with a durable primary preference. This preference does not accept a quote, change its approval/pricing state or feed the forecast. Authentic margin remains unavailable until the cost foundation lands; this slice must not present the stored `40.0` placeholder as Finance evidence.

## Source and boundary

[Linear DRG-452](https://linear.app/drgamtd-workspace/issue/DRG-452) is Backlog. Its [Notion design mirror](https://app.notion.com/p/3e3f7cb4e4df8108a4f3c794c5927985), last edited 2026-10-09 and unverified, repeats the same two acceptance criteria: compare totals, margin and key attributes; any quote can be marked primary. Neither source supplies an existing implementation. Both were read 2026-11-06.

The orchestrator assigned a bounded implementation and recorded these choices: primary means salesperson preference, any nondeleted alternative may be selected regardless of lifecycle status, the last committed selection wins, unavailable margin is explicit, and monetary comparisons require matching currencies. Existing holds and delivery gates remain.

Work binds to `C:/Users/dgorn/open-crm-cpq`, source base `7492fd09`, branch `feat/parallel-story-wave-20261106`. The 36-file November 5 wave is preserved. No existing `Quote`, `QuoteService`, `QuoteMapper`, quote DTO, shared model, gateway configuration, asset writer or lifecycle code is changed by this lane.

## API and persistence contract

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/quotes/{id}/comparison` | Resolve the anchor under the verified seller's workforce partition and return its opportunity alternatives. |
| `POST /api/v1/quotes/{id}/primary` | Validate that same scope, persist the anchor as the preferred alternative and return the refreshed comparison. No request-body identity/group fields are used. |

The anchor must have a nonblank `dealExternalId`. Alternatives must share exact workforce `tenantId`, opportunity and customer. If any alternative in that workforce/opportunity has another customer, both read and write fail closed rather than hiding the inconsistent member and overwriting a shared preference. Missing, deleted, other-workforce and legacy null-partition anchors return 404. Anonymous/missing-context requests are refused; service callers cannot impersonate a seller. The existing coarse authenticated access policy applies; Entra `tid` is a weak workforce partition, not verified commercial account tenancy or new record-level authorization.

Groups are limited to 50 alternatives. The repository fetches at most 51; overflow is an explicit refusal, never a silently truncated comparison or a hidden primary. Deleted quotes/lines are excluded. A stored primary that was deleted or moved out of the group is returned as absent. Group selection does not adopt null-partition legacy rows or backfill them.

The response projects quote identity, status/type, currency/region, duration/dates, subtotal/discount/tax/total, ARR/TCV/ACV, ramp-staleness and product/type/quantity/unit/billing attributes. It reads existing values without repricing. Each margin is `{status: UNAVAILABLE, value: null, reason: ...}`; neither stored waterfall data nor a literal placeholder is a real cost authority. Missing metrics remain absent/null, not zero. `sameCurrency` is false when currencies differ or are missing; the UI must not rank or calculate monetary deltas across currencies.

Root allocated quote-schema `V1_17__quote_primary_selections.sql` locally after current main's V1_16 inventory. It is separate from CRM's already allocated V16. Reconcile other workers' migration reservations again before publication. A new JDBC preference table has one row per workforce/opportunity, a quote FK, current selected actor and timestamp. PostgreSQL `ON CONFLICT` serializes concurrent initial writes. A row lock keeps the selected anchor stable while validating and writing. It never changes the quote entity or its version, pricing, approval, lifecycle, event/outbox or forecast fields.

Application rollback retains the unused preference table and data. Destructive removal requires reverting callers and preserving preference data first; the migration documents that operator-only step. No migration or real-data operation is executed here.

## Graph and ownership evidence

Local GitNexus status is current at source `7492fd09`, indexed 2026-11-05. Concept queries `QuoteComparison` and `primaryQuote` were empty; `ComparisonService` was not found. Confirming source searches found neither a comparison route/client/surface nor a primary field. Exact contexts of quote repository/DTO and Deal established existing identifiers and missing behavior.

New comparison service/repositories/controller/DTO returned impact UNKNOWN and were confirmed absent by text before creation. This is new-symbol confirmation, not a LOW-risk verdict. Preliminary existing `QuoteRepository` depth-one impact was MEDIUM with 11 consumer/import entries, so the final design leaves it unchanged. `QuotesDetailComponent` depth-one impact was LOW with two importers (quote routes/index); `QUOTES_ROUTES` returned LOW with zero resolved callers, and text inspection confirmed the dynamic CRM import and existing `:id` route. The migration inventory test's JUnit entry and versions list were inspected after its LOW/zero graph result before adding V1_17. These bounded reports do not replace the final complete graph review.

The lead owns new backend comparison code/tests/migration and this plan. After the catalog lead released a concurrency slot, child `quote_comparison_frontend` implemented the dedicated comparison model/client/component/tests, quote-library route and detail entry. It independently reviewed the backend and found no actionable defect within the bounded contract. The backend lead independently reviewed its frontend, prompting symmetric current-route reconciliation after a late mutation transport error. Another child, `catalog_defaults_review`, independently reviewed the other two delivery lanes and reported concrete defaults findings to root. Root reserves application shell/shared models, serializes builds/indexing and integrates the wave.

The new route is `/quotes/:id/compare`, ahead of the existing detail route. A quote with a linked opportunity has a keyboard-native detail link to it. The component uses signals/OnPush, semantic table headers and native controls; it shows loading, invalid-id, error, no-alternative and one-alternative states. Primary writes are serialized; superseded GETs are cancelled, and old-route mutation success or transport failure refetches current scope. It keeps each currency labelled without ranking/conversion and never fabricates missing metric/margin values. It uses a dedicated local HTTP client/model, preserving shared DTOs and app shell.

## Acceptance and validation

| Acceptance | Required evidence |
| --- | --- |
| Totals and key attributes side by side | Service tests retain persisted values and exclude deleted lines; HTTP/client contracts preserve currency, term and product attributes; component tests assert table content. |
| Margin comparison | Explicit unavailable state with null value, including stored `40.0` snapshot regression. Actual cost-based margin is not implemented or claimed. |
| Any quote primary | Select either alternative, including accepted status, without lifecycle mutation; actual PostgreSQL preference reload, concurrent first writes and transaction rollback. |
| Scope and limits | Foreign/legacy/deleted anchors refused, mixed customers refused before write, and overflow refused. PostgreSQL tests execute actual Spring Data filters rather than relying only on mocks. |
| Usable interface | Loading/error/empty/invalid route states, accessible column headers/actions and keyboard-native controls; block overlapping writes and prevent stale GET/mutation responses from overwriting current scope. |

Focused Maven verification passed 13 tests (eight service, three HTTP, two existing migration-inventory cases), with zero failures/errors/skips. Command: `mvnw.cmd -B -Punit-tests -pl services/quote-service -am "-Dtest=QuoteComparisonServiceTest,QuoteComparisonControllerTest,LifecycleChangeMigrationGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Log: ignored `.gitnexus/drg452-focused.log`. An initial PowerShell argument-parsing failure happened before compilation and is not feature regression evidence. Tests and implementation were authored before the serialized build slot became available; no original failing-first feature execution is claimed.

Frontend verification passed all 168 quote tests, including three new client tests, ten component tests and one detail-entry regression. Quote lint passed with 16 existing warnings and none in new comparison files. Library and focused new comparison-spec TypeScript checks passed, as did the CRM production build. The whole quote-spec TypeScript check has four untouched fixture errors: `quote-documents-panel.component.spec.ts:108`, `quote.service.spec.ts:425`, and `quotes-list-webmcp.spec.ts:110,127`. Those fixtures were not changed or excluded from unit execution. A temporary ignored focused config verifies the new comparison specs; it does not turn the whole spec check green.

Frontend logs under ignored `.gitnexus/` are `20261106-drg452-frontend-test.log`, `20261106-drg452-frontend-lint.log`, `20261106-drg452-frontend-tsc-lib.log`, `20261106-drg452-frontend-tsc-focused-spec.log` and `20261106-drg452-frontend-build.log`. The full spec failure is retained as `20261106-drg452-frontend-tsc-spec.log`.

Seven Docker/PostgreSQL cases are authored in two integration classes and all compiled in the assembled unit-profile build. The Docker engine is currently unavailable; those cases were not executed. The assembled reactor passed 468 quote-service unit cases. Standalone HTTP tests exercise real controller/service/advice with persistence mocks and principal fixtures, not the full security filter chain. Browser/UAT and chaos remain separate from CI; no frontend typecheck target or frontend PR-check stage is added. Exact-head CI, CPQ Decision and owner merge gates remain distinct from local results, and no story-Done transition is made here.

Root browser verification passed five journeys against the actual production-built SPA with synthetic in-memory HTTP fixtures: table values/unavailable margin, keyboard primary selection with delayed-response locking and reload, refused write preservation, read-error retry, and mixed-currency/keyboard region behavior. There were zero page errors. The fixture starts and closes its own ephemeral loopback static server and headless browser, never forwards to a real API or uses port 4200, and proves no backend authorization, database or real primary-preference persistence. Script: `scripts/verification/quote-comparison-browser-acceptance.mjs`; report/screenshot: ignored `.gitnexus/quote-comparison-browser/`.

Visual inspection of that built screen identified absent generated utility spacing. The frontend child added scoped component styles for the table, buttons and focus states without changing shared styles. A production rebuild passed, then the browser journeys and a computed cell-padding check passed again. This styling change leaves behavior tests unchanged. Final build log: `.gitnexus/20261106-drg452-frontend-build-scoped-css.log`.
