# Residual AM06: Code review lane verdicts

**Linear:** DRG-931 (residual DoD AM06, under DRG-652 W0-3)
**Rubric:** DoD v0.4 — AM06 (Code review verdict, w4)
**Scope:** Read-only review against current `main`. No GitHub thread resolution without drg's
approval. No status changes on existing issues.

## Verdict summary

| PR | Head on main | Verdict | Open findings | Fixed findings |
| --- | --- | --- | --- | --- |
| #713 (DRG-654) | `aeb1993d` | FAIL — 3 open P1s (held behind flag) | 3 P1 + 1 nit | 1 (read auth → #723) |
| #719 (DRG-656) | `abafd089` | FAIL — no prior review exists | Needs full review | 0 |
| #741 (amend/terminate) | merge on main | FAIL — 2 open | 1 P1 + 1 P2 | 0 |
| #742 (renewal quotes) | merge on main | FAIL — 2 open | 1 P1 (mitigated) + 1 P2 | 0 |
| #740 (renewals/churn) | merge on main | FAIL — no verdict exists | Needs full review | 0 |
| #958 (ADR-037) | `e34443d3` | FAIL — mitigated, provisional | Owner approval ≠ lane verdict | 0 |
| #1003 (DRG-761) | merge on main | FAIL — 1 open P2 | 1 P2 | 0 |

---

## Per-PR evidence

### #713 — DRG-654: Create asset when an order activates

**Merged head:** `aeb1993d` (2026-11-01 09:13:54 CDT)

| # | Finding | Severity | Status | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Asset created at quote acceptance, not at activation | P1 | **MITIGATED (held)** | Writer is now `@ConditionalOnProperty("opencrm.assets.legacy-acceptance-write-enabled", havingValue="true")` default off since #735. Design flaw documented in `docs/runbooks/asset-write-hold.md`. Not resolved — needs addressing when hold lifts. |
| 2 | Synthetic `legacy-aggregate` line ID written to `acceptedLineUuid` | P1 | **STILL OPEN** | `AcceptedQuoteSubscriptionService.java:146` still writes `"legacy-aggregate"`. Downstream paths (`EffectiveAssetActivation`, `RampScheduleService`) filter it out, but the row is still written. |
| 3 | No predecessor reconciliation on replacement | P1 | **STILL OPEN** | `AssetActivation.onActivated()` (lines 64-68) skips if an asset exists for the same subscription+contractLine, but performs no predecessor/replacement reconciliation. Held behind flag. |
| 4 | `productName` fallback as `productRef` | nit | **STILL PRESENT (held)** | `AssetActivation.java:76-78` falls back from `productExternalId` to `productName`. Held behind flag. |
| 5 | Coarse read auth on asset queries | P1 | **FIXED by #723** | `InstalledBaseReadScope` and `InstalledBaseReadAccess.require()` now gate all asset reads in `AssetQueryService`. Commit `47e34e5d`, merged 2026-11-01 09:50:43 CDT. |

**Follow-ups under DRG-652:** Findings 1–4 are covered by DRG-902 (durable effective-date asset lifecycle). The hold (`asset-write-hold.md`) prevents production exposure.

### #719 — DRG-656: Queryable asset history and supersede chain

**Merged head:** `abafd089` (2026-11-01 09:15 CDT)

No code review of any kind exists on this PR. A full lane review against the merged head is needed.
The PR adds `AssetHistory`, `AssetHistoryRepository`, the supersede chain query, and the history
projection. It was merged without a COMMENTED, APPROVED, or CHANGES_REQUESTED GitHub review.

**Follow-up:** Needs a dedicated review pass (new follow-up under DRG-652).

### #741 — Amend, terminate, and supersede assets inside document apply

**Merged head:** on main (squash merge)

| # | Finding | Severity | Status | Evidence |
| --- | --- | --- | --- | --- |
| 1 | `AmendmentWorkflow.validateLines` rejects SWAP/UPGRADE/TERMINATE | P1 | **STILL OPEN** | `AmendmentValidationRules.SLICE_ACTIONS` = `EnumSet.of(ADD, UPDATE, REMOVE)` (line 16). The validation at line 43 rejects any action not in this set. SWAP/UPGRADE/TERMINATE are defined in `ActionCode` and used by `DocumentApplyPeriods`/`DocumentApplyOrchestrator`, but the amendment command path blocks them before execution. |
| 2 | Replacement product's executed price not verified | P2 | **STILL OPEN** | The apply path processes SWAP/UPGRADE in `DocumentApplyPeriods.java:65` but the submit path blocks before execution reaches that code. No pricing verification for the replacement product. |

**Follow-ups under DRG-652:** These are prerequisites for the amendment lifecycle beyond the current slice.

### #742 — Open renewal quotes from active assets

**Merged head:** on main (squash merge)

| # | Finding | Severity | Status | Evidence |
| --- | --- | --- | --- | --- |
| 1 | CAM policy response shape vs `RenewalPolicyClient` | P1 | **MITIGATED** | `RenewalPolicyView` (quote-service) has `@JsonIgnoreProperties(ignoreUnknown = true)`, so extra fields in `RenewalPolicyRecordDto` (`notBelowExitNet`, `retentionMarker`, `status`) are safely ignored. `maxDecreasePct` on the client view is absent from CAM's DTO, so it always deserializes as null — a data gap, not a runtime crash. The validation in `HttpRenewalPolicyClient` (lines 51-56) checks only fields present in both shapes. |
| 2 | Stale observed identities accepted | P2 | **STILL OPEN** | No evidence of a fix addressing stale observed identities in the renewal path. The client accepts whatever CAM returns without freshness or version checks. |

**Follow-ups under DRG-652:** Finding 1 needs a `maxDecreasePct` data gap fix. Finding 2 needs identity freshness verification.

### #740 — Report upcoming renewals and asset churn

**Merged head:** on main (squash merge)

The codex review found no issues, but no Code review lane verdict was recorded. A full lane review
against the merged head is needed.

**Follow-up:** Needs a dedicated review pass (new follow-up under DRG-652).

### #958 — ADR-037 asset installed base (Proposed)

**Merged head:** `e34443d3`

`drgamtd0` APPROVED head `e34443d3`, but per Decision ruling 4 that is not the Code review lane
verdict. The PR is a docs-only ADR. The owner approval is a human gate (G07), not a substitution for
the lane verdict.

**Verdict:** FAIL, mitigated, provisional. The content is docs-only and the owner approval exists,
but the rubric requires the lane verdict as a separate artefact.

### #1003 — DRG-761: Read-only contracts and assets screens (accepted UAT quotes)

**Merged head:** on main (squash merge)

| # | Finding | Severity | Status | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Subscription list unfiltered — purged/cancelled subscriptions visible | P2 | **STILL OPEN** | `SubscriptionController.list()` delegates to `subscriptionService` which returns all non-soft-deleted subscriptions regardless of status. There is no PURGED status in `SubscriptionStatus` (only PENDING/ACTIVE/SUSPENDED/CANCELLED/EXPIRED). The `/contracts` list includes cancelled and expired subscriptions. The codex review (post-merge) flagged this. |

**Follow-up under DRG-652:** Needs status filtering or a dedicated view endpoint.

---

## Finding disposition map

| Finding | PR | Disposition |
| --- | --- | --- |
| Acceptance-time vs activation-time writer | #713 | Held behind flag; DRG-902 scope |
| Synthetic `legacy-aggregate` line ID | #713 | Open; DRG-902 scope |
| No predecessor reconciliation | #713 | Open; DRG-902 scope |
| productName fallback | #713 | Open (nit); DRG-902 scope |
| Coarse read auth | #713 | **Fixed** by #723 (`47e34e5d`) |
| No review exists | #719 | Open; needs dedicated review |
| validateLines rejects SWAP/UPGRADE/TERMINATE | #741 | Open; amendment lifecycle prerequisite |
| Replacement product executed price | #741 | Open; amendment lifecycle prerequisite |
| RenewalPolicyClient shape mismatch | #742 | Mitigated (`@JsonIgnoreProperties`); `maxDecreasePct` data gap |
| Stale observed identities | #742 | Open; needs freshness verification |
| No lane verdict | #740 | Open; needs dedicated review |
| Owner approval ≠ lane verdict | #958 | Provisional; docs-only, owner approved |
| Subscription list unfiltered | #1003 | Open; needs status filtering |

## References

- DoD v0.4: https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82
- DRG-652 W0-3 evidence: https://app.notion.com/p/422f7cb4e4df81e4b286d7e4cfa5ce0f
- Decision filing ruling: 7:03 PM CT Nov 23 2026
- Residual scores: `/workspace/reports/asset-mgmt/residual-v04-scores.md`
