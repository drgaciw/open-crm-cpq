# CPQ next wave readiness

The next implementation wave must preserve the new S4 asset-lifecycle hold and close the current ramp verification first. DRG-623 has a narrow audit-reason gap; DRG-627 needs a recovery contract before code; DRG-626 depends on tenant propagation and safe document routing. The CPQ wizard is ready for proposed decomposition, with one owner for shell integration. These are proposed briefs, not newly scheduled stories or accepted decisions.

## Source and checkout boundary

Linear is the delivery-status authority. The [Notion hub](https://app.notion.com/p/3e3f7cb4e4df8197abf7d804245c5df4) is a design and status mirror. This audit read the live issues and comments on 2026-11-01. The inspected worktree is `C:/Users/dgorn/cpq-wt/cpq-parallel-followups-20261101`.

Initial graph exploration used the local CLI index at base `4615b35c`, indexed 2026-11-01 19:32 UTC. It found the apply orchestrator, JDBC gateway, accepted-quote consumer and quote-detail UI. Concept searches for `DocumentApply` and `CostImport` returned no results; named contexts and confirming source searches supplied the evidence. The consumer name was ambiguous until disambiguated by its CAM path. Empty concept results are not proof of absence.

During the audit, `origin/main` advanced to `5ccfe79c`. Its #749 change introduces the S4 hold below; that new code was inspected with `git show origin/main:<path>`, not certified by the older graph. Any implementation requires the root's refreshed index, fresh upstream impact analysis and current-main migration checks. No production symbols were edited by this readiness lane.

## Current holds and sequencing

[Temporary S4 asset-lifecycle hold](../runbooks/s4-asset-lifecycle-hold.md), landed in #749 (`8f0a0cab`), refuses new attempts that could mutate installed assets with audited HTTP 503 `ASSET_LIFECYCLE_HOLD`. The document remains EXECUTED and pending units remain pending. Identical completed replay still works. `AssetLifecycleReadiness.isReady()` returns false and has no runtime override. A future code release must deliver and verify durable effective-date lifecycle, ordering/replay and reconciliation before returning true. Neither retry tooling nor an event consumer may bypass this policy.

The [asset write hold](../runbooks/asset-write-hold.md) also keeps acceptance-time asset creation off. Acceptance can create subscriptions and rate periods while creating no assets or asset events. Replay after release will not backfill deduplicated acceptances. Existing assets, events and pending outbox rows require operator inventory and verified reconciliation; no repair or backfill is authorized here.

The [delegation record](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82), updated 2026-11-01, records a 13:07 CT plan: first restore main green; then docs DRG-644/645/649/637 in parallel and DRG-651 → DRG-635 → DRG-699 sequentially. DRG-630/631 are queued behind that wave. Reconcile this recorded queue with the current ramp draft PR before launching another implementation wave. The recommendation below is a backlog-readiness order and does not silently replace that queue. The same record requires no merges and CPQ Decision gates.

Keep DRG-625 enablement, DRG-657/660 S7-class data work, subscription frontend, Activities, dual-read flip, NgRx, R2 price-book list-row deletion and other repository holds unchanged.

## Verified backlog assessment

| Story | Live status | Code evidence and disposition |
| --- | --- | --- |
| [DRG-623](https://linear.app/drgamtd-workspace/issue/DRG-623) | Backlog | Contract membership is already enforced by `JdbcDocumentApplyGateway.targetBelongsToContract`, including exact document contract and contract tenant. The broad UUID-only defect is partly repaired. `DocumentApplyOrchestrator.applyUnit` currently reports every failed membership check as `TARGET_CONTRACT_MISMATCH`; `TARGET_CONTRACT_UNSET` is absent. Retain the guard; scope the remaining change to the accepted null-contract distinction and its audit. |
| [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627) | Backlog | BLOCKED units return unchanged on replay; `DocumentApplyPartialTest` asserts no retry. `DocumentApplyAssetReplayTest` manually resets unit status, digest, attempt evidence and document status in a fixture before reapplying. That fixture is not operator tooling. A genuine gap remains, but a supported recovery transition must be specified first and must preserve #749. |
| [DRG-626](https://linear.app/drgamtd-workspace/issue/DRG-626) | Backlog | CAM `QuoteAcceptedEventConsumer.route` has no document apply path and throws if one is selected; `AmendmentRoutingFlag.decide` always returns LEGACY. Event wiring is absent. Tenant-event story [DRG-631](https://linear.app/drgamtd-workspace/issue/DRG-631) is itself blocked by real account-to-tenant source [DRG-630](https://linear.app/drgamtd-workspace/issue/DRG-630). No flag or hold release is implied. |
| [DRG-530](https://linear.app/drgamtd-workspace/issue/DRG-530) | Backlog | `CostImport`, `billingUnit`, `UNIT_MISMATCH` and `unitUnchecked` are absent from pricing-service after empty graph and confirming source searches. [DRG-526](https://linear.app/drgamtd-workspace/issue/DRG-526) remains In Progress; its comment locates unverified work on old margin branches, without a PR. Current `MetricsCalculator` still contains the estimated 40.0 placeholder. Do not build the follow-up as though ingestion had landed. |
| [DRG-685](https://linear.app/drgamtd-workspace/issue/DRG-685) | Backlog | The epic's criteria are draft and it remains parked until scheduled. Linear has zero child issues. The [Notion design](https://app.notion.com/p/40bf7cb4e4df81018f05f5b52d32eb3d) matches the guided UI intent. Proposed slices below preserve existing services and holds. |

DRG-623's [accepted owner decision](https://app.notion.com/p/408f7cb4e4df81f48e3ace381db17315) requires `subscriptions.contract_id` to equal the document contract. Missing contract means `APPLY_UNIT_BLOCKED` with `TARGET_CONTRACT_UNSET`; apply never infers a contract from an asset or another chain. [DRG-621](https://linear.app/drgamtd-workspace/issue/DRG-621), the preceding mid-loop audit/outcome fix, is Done. [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) remains tracking-only NO-GO: green integration, named environment/tenant, dry-run counts, restore point and explicit owner execution GO are required. A decision in principle is not execution permission.

## Proposed recovery delivery contracts

### DRG-623 remaining audit distinction

Prerequisites: current-main graph refresh and impact; preserve #749 readiness refusal and accepted option A. A test seam may exercise ready apply logic, but deployed readiness must remain false.

Acceptance for the residual brief:

- A target with no contract is blocked with exactly `APPLY_UNIT_BLOCKED` / `TARGET_CONTRACT_UNSET`, with no rates, ledger row or commercial revision mutation for that unit.
- A different contract or tenant stays refused; an exact matching contract remains the only admitted ownership relationship when readiness permits processing.
- Missing subscription and contract-less subscription stay distinguishable; no contract is inferred or populated.
- Replay produces no duplicate block audit; multi-unit failures preserve already committed siblings and the document outcome.
- Runtime readiness still refuses held new asset work before applying units. Cover both hold precedence and the isolated ready-policy unit/DB cases.

One CAM owner should change the gateway's membership result and orchestrator reason mapping together, with matching unit and PostgreSQL tests. Likely files: `documents/apply/{DocumentApplyGateway,JdbcDocumentApplyGateway,DocumentApplyOrchestrator,DocumentApplyPeriods}.java` as needed, and corresponding apply tests. No schema, event-tenant change, flag, backfill or frontend is in this brief. Exact files require impact analysis before assignment.

### DRG-627 recovery contract before implementation

First define which blocked reasons may be retried unchanged, which require rebase/new revision and approval, and which require a correcting amendment. The older [partial-apply frame](../renewals-amendments/prereq/specs/partial-apply.md) is explicitly Proposed and names rebase/correction; it is not authorization to reset signed evidence. The [role frame](../renewals-amendments/prereq/specs/apply-authorization.md) is also Proposed. Do not treat either as an accepted retry API contract.

Proposed acceptance after the recovery policy is accepted:

- A dry run identifies the exact document/unit, original failure, proposed observation/pin change and expected effects; it does not write.
- A permitted retry affects only the blocked target. APPLIED siblings and their ledger, event and asset-history identities remain unchanged.
- The retry preserves immutable prior attempt evidence and records actor, correlation, old/new observations, reason and outcome append-only. It never emulates the fixture's manual row resets.
- Repeated and concurrent requests are deterministic under the document/subscription lock; changed signed content is refused or follows the accepted revision/approval policy.
- Foreign tenant, refused role, invalid state and unsupported target requests have negative tests. Readiness hold remains authoritative, including dry-run explanations and no mutation on held commit.
- PostgreSQL failure injection proves rollback of one retry unit and reconciles before/after counts. Recovery tooling does not run against real data under this story.

A single recovery owner owns these same CAM apply files; DRG-623 and DRG-627 must not have parallel writers in them. A second agent may independently own new test fixtures and review after the API contract stabilizes. The later code scope must state whether the tool is an internal CLI or endpoint; rate limits and role decisions cannot be invented by implementation.

### DRG-626 event hook prerequisites

Dependency order is DRG-630 tenant source → DRG-631 accepted-event tenant contract → DRG-626 hook contract and implementation → separately authorized rollout. Before scheduling code, reconcile the current signature/EXECUTED transition: quote acceptance must not bypass required signature or approval. Keep the producer contract/registry owner separate from the CAM consumer owner, and serialize shared CAM changes with recovery work.

Proposed hook acceptance includes flag-off/legacy compatibility; authoritative tenant and document ownership; missing, malformed and cross-tenant event rejection; inbox idempotency; transaction/outbox atomicity; replay and partial-failure integration tests; and log/metric/alert evidence. Routing a held document must retain #749's audited hold, not fall through to legacy subscription creation or mark the inbox successfully handled while losing required work. Retry/parking semantics must be explicit. Adding a topic, enabling routing or releasing asset readiness is outside this brief.

### DRG-530 prerequisite recovery

First decide whether to salvage DRG-526's old unverified margin work through a reviewed rebase or implement a fresh bounded ingestion slice. Do not pull or merge those branches as part of this readiness work. Preserve Finance's documented per-unit contribution-cost model and row-isolated canonical export ingestion. Mock finance fixtures are test data, not live cost authority.

Only after DRG-526 is on the chosen base should DRG-530 carry catalog `unit_of_measure` through the existing pricing replica and reject mismatched v1 rows as `UNIT_MISMATCH`; replicated products must have `unitUnchecked = 0`. Assign the complete catalog-to-pricing/export path to one owner; do not split a published payload change between uncoordinated writers.

## Proposed wizard children

The labels W1–W7 are local proposal labels, not Linear IDs. Parent: DRG-685. All initial stages use Angular signals/HttpClient and existing gateway APIs. No new backend saga or compensation, full renewal/amendment continuation UI, subscription portal, direct approval-routing writes, new domain validation in the gateway or default flag changes.

| Proposed child | Testable acceptance | Depends on | File ownership |
| --- | --- | --- | --- |
| W1 Step state and resume model | Stages derive from authoritative existing quote/context state; blocked/stale/approval states prevent invalid advance; reload resumes by existing server identifiers; Vitest covers allowed and refused transitions. | Scheduled epic and an agreed capability/state matrix. | New wizard state model/tests only; owner A. |
| W2 Shell and entry points | Lead/Deal and Quotes CTA enter the same flow with validated existing context; keyboard navigation, loading/error/empty states and a single accessible progress indicator work. | W1 interface. | Owner A alone owns `app.routes.ts`, sidebar if needed, tsconfig path and `test:cpq`, plus entry links. |
| W3 Configure and bundle stage | Reuse catalog/configuration surfaces; preserve selected products; show canonical validation failures and link to their owning screen; no duplicate validation rules. | W1/W2 and confirmed existing config API contract. | Owner B owns new configure-stage adapter and tests; existing catalog edits require coordinated impact. |
| W4 Pricing and quote stage | Reuse pricing/quote authoring; submit remains blocked without priced valid lines and after commercial edits until repricing; concurrent version conflict offers reload; refresh retains quote/line identities. | W1/W2; W3 output contract. | Owner C owns new price/quote adapter and tests; one designated quote-service frontend owner for shared client changes. |
| W5 Approval and acceptance stage | Existing quote BFF submits/tracks decisions; pending/rejected/failure states are visible with owner-screen links; only permitted quote state enables accept; resulting artifacts appear on existing lists. | W4. | Owner B after W3, or a separate agent after a slot frees; approval adapter/tests only. |
| W6 Handoff and capability limits | Show accepted or pending artifacts and links supported by current APIs; unavailable downstream capabilities are explicit and never reported as fulfilled; no subscription UI or held kickoff action. | W5 and capability matrix. | Owner C after W4; handoff component/tests only. |
| W7 Journey acceptance | Browser happy path with mock data and CPQ services up produces the expected Quote/Approval artifacts; reload, stale price, denied action, version conflict and keyboard/error journeys pass. Mocked transport runs are labeled separately. | W2–W6. | Test owner; e2e fixtures/scripts only. |

Suggested scheduling: root + A shell/state + B configuration + C pricing after W1 contracts; then reuse slots for W5/W6 and independent W7. Keep one shell owner throughout. This fills four slots without concurrent writers in shared routes, quote clients or backend apply classes. The capability matrix must be accepted before W6: “as APIs allow” cannot lift repository holds.

## Review and delivery evidence

The [DoD v0.4 page](https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82) retains “Draft” in its title, but its body records owner approval on 2026-10-29 and says it supersedes v0.3 for new scoring. The [delegation record](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82) corroborates approval. The older [v0.3](https://app.notion.com/p/3fcf7cb4e4df8133860dcc8613c620eb) remains useful for traceability, not the latest score template. The CPQ threshold is provisionally 85; the final threshold remains an open owner point.

For a code PR, provide the exact `Scoring tuple: head=<sha> base=<sha> run=<id>`, actual base/chain to main, complete changed-file set, allowlist/forbidden list, an AC verification checklist, every changed test mapped to an AC and DoD criterion, failing-first SHA/log plus test diff, and one reason per trigger-false N/A. Exact-head CI is a MUST; local green tests cannot replace it. Applicable role/security, tenant-source, pricing, migration rollback, audit/outbox/registry/replay and flag-off checks need their stated evidence. No new tests may be silently disabled to pass.

CPQ Decision is the designated scorer, tied to the exact head/base/CI run; changes to those, ACs, allowlists or evidence invalidate the score. Local independent review is supporting evidence and does not manufacture a CPQ Decision PASS. The available connector tools exposed no callable CPQ Decision or Notion agent-run operation. Notion agent search requires an access-check tool that was not exposed, so no bot review was invoked. Linear's advertised `get_agent_context` returned tool-not-found. Neither limitation authorizes assuming a PASS.

The PR stays draft. Owner merge and other human gates remain separate: deployment, flags, S7/data writes, secrets, workflow changes and ADR/decision acceptance. A newer relay describes Hygiene merge delegation only after CPQ Decision PASS and green CI on the exact head; this readiness plan performs no merge or delegation action. Trackers must not say Done before the actual gates are satisfied.

This audit ran no tests, builds, browser, containers or services and made no external writes. It does not establish migration, replay, concurrency or browser correctness. Docker integration and browser/chaos are separate from unit CI. Current committed CI still specifies Node 22.22.3; the recorded approval to plan Node 24 is not evidence that CI has changed. Source summaries and old Proposed frames can lag code and need explicit reconciliation when writing implementation briefs.
