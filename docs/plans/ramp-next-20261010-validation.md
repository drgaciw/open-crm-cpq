# R3/R4 wave validation — 2026-10-10

The isolated eight-service acceptance run passed at `b180052dcd7fe74df3561cf1434a7d9939b68484`: **12 validation groups, 83 HTTP requests, two actual browser checks, zero cleanup errors**. PostgreSQL, Kafka, gateway, pricing, pricebook, quote, catalog, configuration, approval and contract services were exercised with synthetic data and verified JWT identities. This is local acceptance evidence, not a production rollout certification.

## Verified behavior

- Gateway rejects absent, expired and wrong-audience bearer identities. Real quote/pricing/pricebook calculation, authenticated configuration validation and canonical Kafka approval binding work together.
- Identical pending quote edits preserve pricing/approval; changed edits invalidate the snapshot and supersede canonical approval. Actual browser save/reload checks pass with no page errors or failed quote responses. Stale submit and late approval decisions are rejected.
- A distinct verified VP completes tiered review; accepted quotes freeze their priced schedule. Real outbox/Kafka consumption persists sold contract periods and the acceptance ledger.
- Target-linked expansion preserves the existing subscription and adds its line at revision 1: TCV 16800, active ARR 33600, exit ARR 42000. The 20% inherited discount is below the strict >25% escalation gate; ordinary review still rejects the author and requires a distinct VP before send/accept.
- Hybrid migration creates real canonical catalog products and a separate draft pricebook, adds its entries, then publishes it. Calculation/reload, mandatory Deal Desk review, send/accept and Kafka consumption retain TCV 43800, ACV 14600, ARR 18000, exit ARR 14400 and the first-period 3000 credit. The recurring net monthly rate remains 1000; accepted root monthly total is 1500, distinct from TCV.
- Process-only ramp flag-off returns 422 without a write for ramp authoring and preserves ordinary flat-quote calculation. Production defaults, legacy read and Jev holds are unchanged.

## Reproduced defects and fixes

PR594 corrects a harness expectation that waited for canonical PENDING at a 20% expansion discount. The actual failed run had nine passing validation groups, 95 HTTP requests and two browser checks. The corrected ordinary-review flow subsequently passed.

PR595 adds forward migration `V1_8__allow_canonical_ramp_product_types.sql`: the shipped catalog CHECK rejected supported ONPREM_SUPPORT and HYBRID_MIGRATION_RAMP. V1 is unchanged; all 20 legacy values remain valid. Three real PostgreSQL/Flyway regression cases were run RED (two expected CHECK violations, one negative-domain pass), then GREEN: fresh canonical persistence, upgrade from 1.7 with legacy rows preserved, and rejection of invalid type/category/status/billing-frequency values. Clean product/common/security packaging passed 388 unit tests; these overlap the full reactor totals below.

PR595 also corrects the hybrid fixture that attempted to append entries to the previously published pricebook. The real API returned PRICE_BOOK_PUBLISHED_IMMUTABLE. The final passing run uses an independent draft book and preserves that policy.

## Build and artifact provenance

The earlier full clean backend reactor at `b054da3af9678a98d03aa4f202a4f3d401b23222` passed 1871 tests across 275 suites and 16 reactor modules, with no failures/errors/skips. Separate actual contract PostgreSQL integration tests (11) and Kafka redelivery test (1) passed. The three new catalog PostgreSQL cases passed separately; do not add overlapping component reactor counts to the 1871 total.

The final runtime used seven unchanged JARs from that clean reactor and the rebuilt product JAR from `95ba9d59309c88111a13d049f153ebc1f7f5a5b1` (SHA256 `D9A6F2DC0DAE8BE8413C4D726985C5B6D51C87109821229DEA7CF6C6934EA9FA`). The only production backend delta is the forward catalog migration. Later harness revisions do not change packaged backend sources. All eight JAR hashes, existing backend source hashes, new migration/test Git blobs, and runtime script hash were verified. Three LF/CRLF checkout differences matched the exact committed Git blobs and were recorded explicitly. The compiled frontend came from the retained independently audited quality worktree with 533 tracked source hashes and 120 build-file hashes verified.

## Review limits and remaining rollout gates

Pre-edit exact harness impacts were LOW with one direct caller/one process. Fresh complete scoped all/staged/compare reviews covered the 14 Java/JavaScript symbols across three code/test files, without partial/truncated output. SQL executable mapping remains UNKNOWN/cross-language; actual PostgreSQL/Flyway RED/GREEN and the live product API provide its independent proof. Global GitNexus FTS, dispatch and process caps remain limits. The older broad comparison hit the 1000-symbol listing cap; a narrower complete 859-symbol comparison and component gates supplement it, not erase that limitation.

Real Entra browser PKCE/session login, actual customer notification, downstream CRM delivery, external ERP delivery, real catalog-to-pricing replication, rollback fault injection, historical distinct-event ordering and broader browser/chaos validation remain open. Catalog reference rows were synthetic isolated SQL fixtures; activation used the local ERP placeholder. This live run does not certify scheduled RampApplied publication or every renewal/amendment/actual-day/cancellation/race path; retained unit/PostgreSQL evidence is separate. Docker integration tests are separate from unit-only profiles. Frontend CI's allowlist omits CPQ/shared-ui/new projects; browser UAT/chaos is not wired into CI. Empty CPQ fallbacks during unrelated CRM-only UAT provide no feature proof. R3/R4 rollout status therefore remains In Progress.

Runtime logs, raw result/provenance/graph JSON, both browser results, failed attempts and owned synthetic database evidence are retained under the named wave's evidence archive. Only manifest-owned services and containers were stopped. Primary user edits, stash, backups, untracked files and older dirty worktrees are preserved.
