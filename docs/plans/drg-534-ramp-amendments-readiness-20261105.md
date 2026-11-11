# DRG-534 ramp-amendment reconciliation

2026-11-05. Scout only; no implementation or decision adoption. Inspected checkout: `C:/Users/dgorn/open-crm-cpq`, HEAD `7492fd099e71b55ecc20ab783367fa546c227b5e`.

## Existing ownership and source conflicts

[DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) is already **In Progress**, assigned to drg, with [Decision-brief PR #803](https://github.com/drgaciw/open-crm-cpq/pull/803). Its latest fetched update is `2026-11-06T00:07:14Z` (November 5 CT). Preserve that worker's ownership; do not launch another implementation or duplicate brief. This pass read Linear metadata, not the PR diff or CI.

The [Notion mirror](https://app.notion.com/p/3fbf7cb4e4df81f08315f3eb1bb8e415), last edited `2026-10-17T14:28:22Z`, is unverified and still says Not started/No PR. Its body explicitly makes Linear the status authority. Its DoD v0.3 link also predates the [v0.4 record](https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82) cited by the local next-wave plan. This document performs no tracker write.

## Baseline and precise remaining gap

Accepted [ADR-023](../adr/ADR-023-price-ramp-schedule-model.md) defines explicit stored steps, anniversary boundaries, scale-4 units/scale-2 amounts, per-period quantity tiers and exit-rate renewal semantics. Its appended implementation record distinguishes landed ramp work from the historical initial proposal. [The dated-extension correction](ramp-extension-dated-term-review-20261101.md) documents durable standalone quote duration/date/schedule updates and specifically excludes continuation-term extension. Those capabilities do not establish safe mid-term amendment of a subscription's existing schedule.

The existing [ramp-amendments proposal](../renewals-amendments/prereq/specs/ramp-amendments.md) is **Proposal only**, aligned under DRG-639 on 2026-10-27. It already supplies the requested fixtures, candidate contract, ACs and owner forks. The [DRG-533 ADR](../renewals-amendments/prereq/DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) has Accepted status but explicitly does not accept that specification; its recommendation/fork wording still requires reconciliation. Neither file authorizes production code by itself.

DRG-534's requested behavior is: preserve elapsed periods, edit future periods, generate a new fingerprint on change, supersede the old approval binding, rerun per-period governance, and carry the amended schedule through additive v1 event fields with replay coverage. Current source-level implementation completeness is **not certified by this scout**. The root repaired and refreshed the local index at `7492fd09`; this ownership-reconciliation lane did not perform a code-completeness graph audit because PR #803 already owns the decision brief. No empty or stale result is treated as proof of absence.

## Review packet for the existing owner

These are verification targets for PR #803's readiness review, not newly adopted business rules:

| Target | Required evidence / unresolved boundary |
| --- | --- |
| Elapsed-row preservation | Reuse the existing 36-month fixture: start 2027-01-01, units 40/45/50, as-of 2028-06-15. Reject a year-1 edit and compare every fixture column except the two declared audit timestamps. Preserve accepted-line and prior commercial evidence. |
| Future edit | Proposed year-3 replacement 50 to 55 leaves elapsed rows unchanged; expected exit ARR 33,000 at 50 seats is a candidate fixture assertion, not a new owner choice. |
| Governance | Changed fingerprint invalidates the old binding and recomputes each period's outcome. Confirm whether a policy-compliant change needs a human approval or a fresh automatically approved binding. Do not redefine the existing worst-period policy. |
| Current period and clock | Resolve who supplies authoritative as-of/action/effective dates; decide whether the current period is immutable or split. The current proposal marks its immutable-current AC pending. |
| Shape and quantity | Resolve price versus quantity edits, end deletion and associated term/credit effects, and appending/extension. Do not turn the Salesforce reference's first-or-last deletion into an adopted rule: the project proposal permits end deletion only and keeps term effects unresolved. |
| Event and replay | Establish the actual accepted snapshot/EXECUTED apply path, additive-v1 compatibility in both directions, replay identity, ownership checks, transaction/outbox atomicity and no duplicate approval/audit/application. An authoring test alone cannot prove this. |

## Dependency order and parallel ownership

1. **Existing PR #803 owner:** reconcile the proposed specification with the recorded owner words/times and DRG-639/533; publish remaining forks and the exact testable contract. No duplicate spec writer.
2. **Independent reviewer, read-only:** compare that packet with the current checkout's graph and code; run fresh CLI query/context and upstream impact before proposing a production allowlist. Record callers/processes, HIGH/CRITICAL warnings and unresolved UNKNOWN results. Bind the exact checkout and index commit.
3. **After the contract is accepted and scheduled:** one quote owner may own authoring/fingerprint/governance changes; one CAM owner owns stored-period/application/replay changes; one shared-event owner owns the additive payload and compatibility tests. Stabilize their interfaces before fan-out, serialize shared files, and reuse agent slots for independent review. These are proposed future lanes, not launches.

No production file allowlist is approved here. The existing fixture/spec is the starting review packet; index evidence must establish the final symbol/file ownership before code work.

## Hard gates and adjacent work

- Keep the ramp flag off and ACV mode `legacy`; do not enable Jev, approvals dual-read changes or subscription frontend.
- Preserve [S4 asset-lifecycle refusal](../runbooks/s4-asset-lifecycle-hold.md) and [acceptance-time asset write hold](../runbooks/asset-write-hold.md). This scout does not release readiness, route events around it, repair installed assets, backfill or operate on customer data.
- Real period-cost/margin authority requires DRG-526; the placeholder margin 40.0 cannot support governance. Stored-step amendment does not authorize regeneration from escalator percentages or renewal-policy expansion.
- Applicable DoD v0.4, exact-head CI, designated CPQ Decision scoring and owner merge remain separate gates. Docker integration/replay and browser/chaos are not implied by unit CI; none ran here.
- Adjacent inspected stories are also occupied: DRG-568 docs/fixtures PR #807; DRG-675 test fix PR #804; DRG-644 cluster-line docs PR #800; DRG-699 wiring inventory PR #802; DRG-623 audit distinction PR #827. DRG-460's activation Decision brief [#809](https://github.com/drgaciw/open-crm-cpq/pull/809) explicitly covers DRG-469 too, despite 469 having no own attachment. Do not duplicate these lanes merely because some statuses remain Backlog.

Validation for this artifact is source/status reconciliation only. No code, runtime state, tracker status or approval was changed.
