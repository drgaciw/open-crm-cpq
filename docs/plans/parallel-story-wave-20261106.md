# Parallel delivery wave — 2026-11-06

The owner requested the next series of Linear/Notion stories with maximum agent parallelism. This wave uses root plus three implementation leads, then reuses freed slots for nested test/frontend/review agents. Builds and index writes are serialized; independent source edits and read-only analysis run concurrently.

## Baseline and queue verification

Source base is `7492fd099e71b55ecc20ab783367fa546c227b5e`, branch `feat/parallel-story-wave-20261106`, checkout `C:/Users/dgorn/open-crm-cpq`. The preceding wave's 36 uncommitted files are inherited intact: all hashes matched `.gitnexus/parallel-wave-20261106-baseline-manifest.json` before work began. Its DRG-520 migration, gateway changes, activity client, shared frontend configuration and documentation are reserved against concurrent edits. This wave does not claim that work has merged.

The [Linear project](https://linear.app/drgamtd-workspace/project/open-crm-cpq-5a50ace0d150) supplies delivery status; [Notion staffing](https://app.notion.com/p/410f7cb4e4df812bafabe9ac08a9e738) and the [delegation record](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82) supply staffing/decision context. The imported stories explicitly require acceptance to be reverified against current code. Graph and source checks confirmed actual gaps before implementation assignments.

Fresh staffing reads report DRG-511 in draft PR #846 with CI failed/HOLD, DRG-517 in draft PR #837 with CI failed/HOLD, and DRG-514a stopped/unpushed behind publication gates. They retain their existing owners. The DRG-514a note now mentions “714/V16 / no V17”: this is a possible collision with the prior local activity V16, not permission to renumber another worker's migration. No new CRM migration is introduced here; any future publication must reconcile that collision at the existing serialized migration gate.

Fetched `origin/main` remained `0a3f0f8e` (November 4). Ten registered worktrees remain; no new worktree is allocated. Approximately 5.2 GiB RAM was free, no Java/running target jar was observed, and Docker's Linux-engine pipe was unavailable. No services or containers are started by this wave.

## Assigned stories and ownership

| Lead | Story / source | Authorized deliverable | Exclusive code ownership |
| --- | --- | --- | --- |
| `notion_queue` | [DRG-423](https://linear.app/drgamtd-workspace/issue/DRG-423), [design](https://app.notion.com/p/3e3f7cb4e4df8108b972caf17cbc02a8) | DTO catalog reads, sellable-product read, explicit bundle-resolution adapter and conditional HTTP cache validators. | New product-service catalog read/cache classes and tests; `drg-423-catalog-serving-20261106.md`. |
| `linear_queue` | [DRG-427](https://linear.app/drgamtd-workspace/issue/DRG-427), [design](https://app.notion.com/p/3e3f7cb4e4df81889eacc19abe5b6c68) | Apply authoritative declared defaults without overriding manual choices; validate detached candidate atomically; persist visible provenance. | Configuration-engine defaults/client/controller/service, necessary session entity/DTO selection mutations and tests; local CE V1_5; `drg-427-safe-defaults-20261106.md`. |
| `repo_readiness` | [DRG-452](https://linear.app/drgamtd-workspace/issue/DRG-452), [design](https://app.notion.com/p/3e3f7cb4e4df8108a4f3c794c5927985) | Same-opportunity quote comparison and persistent salesperson-primary preference, with explicit unavailable margin. | New quote comparison package/store/tests, local quote V1_17, new comparison component/client/models and bounded quote-library route/detail entry; `drg-452-quote-comparison-20261106.md`. |
| Root | Integration and review | Scope decisions, baseline protection, build/index scheduling, shared contracts and final evidence. | This plan; shared configuration only if separately needed. |

All plan filenames above live under `docs/plans/`. No agent may edit another lead's files or the prior-wave protected files without coordination. Product options DTOs needed by defaults already exist, so the defaults lead consumes them without changing product-service contracts. The quote lead uses dedicated comparison projections rather than editing shared Quote/Mapper/Dto/Service contracts. App shell, sidebar, global models and gateway remain root-owned.

## Bounded product decisions

**Catalog:** keep existing DTO/API signatures. A content digest validates the semantic response data and pagination, excluding volatile response timestamps. Weak ETags, `X-Catalog-Version` and private revalidation support client caching; the content token is not a published catalog snapshot (DRG-421). Cache advice must not turn errors into 304 responses. The explicit bundle-resolve adapter uses the existing composition DTO; nested expansion remains DRG-420. A real HTTP performance harness may measure GetProduct; mocked unit timing cannot establish the required p95 below 100 ms.

**Defaults:** use the authoritative catalog's declared default options and group min/max/type constraints. Preserve manual selections; reject malformed, insufficient, ambiguous or conflicting defaults rather than inventing a chooser or silently dropping constraints. Evaluate a detached complete candidate before mutating managed session state. Persist defaulted selection-key provenance, expose it in the DTO, and clear the marker when a user explicitly replaces/removes that key. This does not authorize a new solver or a product-options API.

**Comparison:** group alternatives by the quote's existing workforce partition, `dealExternalId` and `customerExternalId`. This uses current recorded references and coarse access, not a new claim of verified commercial customer tenancy. Legacy quotes without workforce context fail closed. Primary means the seller's preferred alternative for that group; it does not accept a quote, update a forecast, change approvals or mutate commercial lifecycle. Store the preference atomically in a separate quote-owned table. Compare numeric totals only within a common currency. Show margin unavailable when authoritative costs are absent; never expose the estimated 40.0 placeholder as real margin.

Locally allocate `configuration-engine/V1_5__configuration_default_provenance.sql` and `quote-service/V1_17__quote_primary_selections.sql`. Their service-specific inventories currently end at 1.4 and 1.16 respectively. These local allocations are not remote reservations; reconcile all migration claims before publication. No CRM V17 or modification of the earlier activity V16 is authorized here.

## Execution and verification gates

- Local GitNexus CLI only, explicitly bound to this checkout. MCP and port 4200 target the other checkout. Graph query/context precedes code-flow inspection; upstream impact precedes existing-symbol edits. UNKNOWN requires source confirmation. HIGH/CRITICAL must be reported to root and warned to the user before editing.
- Initial bounded session/route/controller preparation returned LOW/MEDIUM where resolvable; new classes returned UNKNOWN/not found and were confirmed absent. These are scoped preparation results, not a final all-clear.
- Run failing-first checks where meaningful, then focused tests, affected-module regression, frontend lint/type/template checks where applicable, and a complete structured graph change audit including new files. Excluded Docker tests remain explicitly unrun. Do not disable assertions to obtain green.
- Root grants a build token for a bounded command, releases it immediately afterwards, and prioritizes whichever lead is ready. Source implementation continues while another lead tests. No worker waits on an idle reserved build slot.
- The first lead to finish releases a slot for another lead's frontend, test or independent review child. Independent reviewers must not be the sole authors of the code they review. Maximum concurrency remains four including root.
- Preserve all architecture, S4 asset lifecycle/write, CI, runtime flag, ADR-005, NgRx and subscription frontend holds. No merge, deploy, external notification, real-data operation or runtime flag activation occurs here.

AutoJev tools are not exposed; no advisory verdict is claimed. Exact-head CI and designated CPQ Decision/owner gates remain separate. No story is marked Done merely because local code or unit tests pass. Frontend CI runs all Nx projects but has no typecheck target; PR-check has no frontend stage (DRG-527). Browser/chaos UAT is outside CI, and unavailable CPQ services do not constitute acceptance evidence.

## Outcomes

All three implementation leads finished their bounded scopes. When the catalog lead released its slot, the quote lead spawned `quote_comparison_frontend` for the actual screen/client and tests. When the defaults lead released its slot, the quote lead spawned `catalog_defaults_review`, which authored neither reviewed lane. Root reused the defaults lead for review fixes while frontend validation continued. At most four agents ran concurrently including root.

Independent review corrected two defaults issues: the PostgreSQL fixture needed an `&` after Testcontainers' existing query string, and newly selected default targets needed a non-deleted catalog lookup. Regression coverage also checks null selection-type refusal. The final defaults implementation preserves existing manual choices and performs every new-target lookup before managed writes. New 422 responses retain their status using Spring's nondeprecated enum name. The quote lead and frontend author independently reviewed each other's code; an old-route mutation transport error now reconciles the current scope by GET.

| Verification | Observed outcome |
| --- | --- |
| Initial assembled backend unit reactor | **1,097 passed**, zero failures/errors/skips: common263, security137, Jev decision66, product53, quote468, CE110. |
| Final defaults regression after review fixes | **514 passed**: common263, security137, CE114; includes four more regression cases. The latest affected-module results therefore cover **1,101 distinct backend unit cases**, rather than 1,101 tests in one command. |
| Catalog focused HTTP | Eight passed, including conditional wildcard/weak/strong/list and metadata/content changes. |
| Quote frontend unit | **168 passed** across 11 files; includes 13 comparison cases and one detail-entry regression. |
| Frontend lint / types | Lint passes with 16 existing warnings; library and focused new-spec type checks pass. Whole quote-spec checking retains four untouched fixture errors in document panel, quote-service and WebMCP tests; its failure log is retained. |
| Production Angular build | Passed, including a later scoped-style rebuild; no new component style-budget warning. |
| Built-SPA browser check | Five synthetic HTTP journeys passed, zero page errors; keyboard, delayed write, reload, refusal, retry, mixed currency and computed table spacing checked. No real backend/auth/database claim. |
| New Docker integration | Nine cases compiled (CE2, quote7); **unrun** because the engine is unavailable. The prior wave's five CRM cases also remain unrun. |
| Catalog latency | A real HTTP measurement harness exists; actual p95 under 100 ms remains **unmeasured**. |

Logs and synthetic screenshots live under ignored `.gitnexus/`, including `parallel-wave-20261106-backend-unit.log`, `drg427-final-unit.log`, `drg423-green-final.log`, `20261106-drg452-frontend-*`, and `quote-comparison-browser/report.json`. The verification browser script starts only a short-lived loopback static/mock fixture and headless browser and closes both; it starts no existing application service or container and forwards no request to real services. It does not mutate a real quote, primary preference, customer or tenant.

The old 36-file wave remains byte-for-byte intact. New work is local and uncommitted on this branch. Remote statuses, holds and existing workers were not changed. DRG-423's live latency, DRG-427/452 PostgreSQL guarantees, authentic quote margin, the four existing spec-type errors, designated scoring and exact-head CI remain explicit limits. No story is marked Done here.

## Final graph and preservation

The final forced local index rebuild took 69.3 seconds and indexed 30,851 nodes and 64,679 edges, bound to this checkout/source HEAD `7492fd09`. Scoped intent-to-add entries included all authored files in the diff without staging content. The audit covers **79 files**: the intact previous 36 and this wave's **43**. The owner's `.angular/` and `jev-worker/` remain outside the patch.

Both CLI `detect-changes --scope all --repo .` and the complete local `LocalBackend` result report **457 changed symbols, 33 affected flows, CRITICAL risk**. The full result retains all 457 symbols and 33 flows with no partial/truncated flag; the normal CLI display is abbreviated and was not used as a completeness check. Complete evidence: ignored `.gitnexus/parallel-wave-20261106-graph-full.json`. No GitNexus MCP or other checkout is involved.

The critical rating was reported and remains unresolved as a release-risk classification. Reviewed flows include inherited activity identity paths plus new explicit-default validation, primary-preference identity/group checks, bundle reads and domain refusals. Existing shared authentication implementations remain unchanged; existing constraint evaluation keeps its behavior, and explicit stored-default validation has a separate seam. Passing tests and static review do not waive this rating or replace the unrun PostgreSQL/rollout gates.

All prior-wave file hashes were rechecked with zero differences. Full binary patch, per-file hashes, changed-file sets and test/graph summary are preserved locally under `.gitnexus/parallel-wave-20261106-*`. The temporary intent-to-add entries are removed after capture, leaving source files uncommitted and unstaged for review. No commit, push, merge, tracker-Done transition or deployment occurred.
