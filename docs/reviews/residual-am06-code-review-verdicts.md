# Residual AM06: Code review lane verdicts

**Linear:** DRG-931 (residual DoD AM06, under DRG-652 W0-3)
**Rubric:** DoD v0.4 — AM06 (Code review verdict, w4)
**Scope:** Read-only static assessment against current `main` (`a877f90a`). All findings verified
against source; none executed against a database. Current holds in effect.
No GitHub thread resolution without drg's approval. No status changes on existing issues.

## Verdict summary

| PR | Head on main | Verdict | Open findings | Fixed findings |
| --- | --- | --- | --- | --- |
| #713 (DRG-654) | `aeb1993d` | FAIL — 3 open P1s (held behind flag) | 3 P1 + 1 nit | 1 (read auth → #723) |
| #719 (DRG-656) | `abafd089` | FAIL — 3 open P1 + 3 open P2 + 1 nit | 3 P1 + 3 P2 + 1 nit | 3 (auth → #723, tenant → #1077, rollback → #1070) |
| #741 (amend/terminate) | merge on main | FAIL — 2 open | 1 P1 + 1 P2 | 0 |
| #742 (renewal quotes) | merge on main | FAIL — 2 open | 1 P1 (mitigated) + 1 P2 | 0 |
| #740 (DRG-659) | `e53f90c1` | FAIL — 3 open P1 + 2 open P2 + 1 nit | 3 P1 + 2 P2 + 1 nit | 2 (test compile → #747, tenant → #1077) |
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

**Follow-ups under DRG-652:** Findings 1–4 are partly built under DRG-902 but not wired at
`a877f90a`. The hold (`asset-write-hold.md`) prevents production exposure.

### #719 — DRG-656: Queryable asset history and supersede chain

**Merged head:** `abafd089` (2026-11-01 15:15 UTC / 09:15 CST). Merged with 0 PR CI checks and 0
GitHub reviews. First CI run was post-merge `Main verify`.
**Reviewed by:** agy-01 (w1:p9), verified against `main` `a877f90a`.

| # | Finding | Severity | Status | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Non-atomic check-then-insert and `maxAppliedOrder() + 1` concurrency race | P1 | **STILL OPEN** | `JpaAssetHistoryStore.java:18-25`: `existsByEventId` followed by `maxAppliedOrder() + 1` and `save()`. Under `READ COMMITTED`, concurrent appends compute identical `applied_order`; duplicate event IDs race into `uq_asset_history_event`. `applied_order` is local arrival order, not a producer causal sequence. |
| 2 | `fold()` ignores `effectiveEnd` expiration and allows `AMENDED` to resurrect closed assets | P1 | **STILL OPEN** | `AssetHistoryProjection.java:311-372`: stores `end = row.getEffectiveEnd()` but never checks `asOf.isAfter(end)`, so expired assets stay `installed = true`. `case AMENDED` (lines 339-340) unconditionally sets `installed = true` even after `TERMINATED` or `SUPERSEDED`. |
| 3 | No production Kafka consumer; `dropProjection()` is public and unguarded | P1 | **STILL OPEN** | No Kafka listener populates `asset_history`; `AssetActivation` does not write to `AssetHistoryProjection`. `dropProjection()` (line 160) is `public @Transactional` with no auth guard and executes `deleteAllInBatch()` across all tenants. Mitigated only by holds. |
| 4 | Account-scope isolation (Q11) | P2 | **STILL OPEN** | `#723` added role auth, `#1077` added tenant scope, but tenant-wide access across all accounts remains open (`docs/runbooks/asset-read-access.md:5`). |
| 5 | Invalid `direction` query param returns HTTP 500 | P2 | **STILL OPEN** | `AssetHistoryProjection.java:402` throws `IllegalArgumentException`. `GlobalExceptionHandler` has no handler for it, so invalid `?direction=foo` returns HTTP 500. |
| 6 | Full-tenant `Asset` load into memory + unbounded `IN (:assetIds)` | P2 | **STILL OPEN** | `AssetHistoryProjection.java:174-183,243-259`: `visibleIds()` loads every non-deleted asset in the tenant into a `Set<String>` and passes it into `WHERE assetId IN :assetIds` (bind-parameter overflow risk at >32,767 assets). |
| 7 | Missing read auth and tenant isolation at merge time | P1 (at merge) | **FIXED by #723 + #1077** | `#723` (`47e34e5d`) added `InstalledBaseReadAccess.require()`. `#1077` (`bae5a413`) added `InstalledBaseReadScope.current()` tenant join. |
| 8 | `V1_14` shipped without rollback script | P2 (at merge) | **FIXED by #1070** | `#1070` (`95c3514e`) added `db/rollback/V1_14__drop_asset_history.sql`. Disposition tracked under DRG-924 / PR #1095. |
| 9 | `walk()` omits `link.getAssetId()` fallback for null `predecessorAssetId` | nit | **STILL OPEN** | `AssetHistoryProjection.java:294` reads `getPredecessorAssetId()` directly without the `getAssetId()` fallback used at line 187, silently skipping predecessor hops. |

**Hold-lift prerequisite:** `maxAppliedOrder() + 1` race (finding 1) and replay wiring (finding 3)
are grouped — both concern the append path that must be reliable before hold lift.
`dropProjection()` (finding 3) is a latent P2 behind holds. `fold()` / `effectiveEnd` / `AMENDED`
resurrection (finding 2) is coupled with #740's churn double-counting (see #740 notes).

**Follow-ups under DRG-652:** Findings 1–3 are partly built under DRG-902 but not wired at
`a877f90a`. Finding 2 distinguishes event-defined installed state (the projection's `fold()`)
from the unresolved drg lapse policy. Finding 4 is Q11 (account isolation). Findings 5–6 are
targeted hardening.

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

### #740 — DRG-659: Report upcoming renewals and asset churn

**Merged head:** `e53f90c1` (2026-11-01 18:50 UTC / 12:50 CST). Codex review had no findings; 0
Code review lane verdicts at merge time.
**Reviewed by:** agy-01 (w1:p9), verified against `main` `a877f90a`.

| # | Finding | Severity | Status | Evidence |
| --- | --- | --- | --- | --- |
| 1 | `upcomingRenewals()` queries `installedBase(window.end())`, tightly coupled to #719's `effectiveEnd` bug | P1 | **STILL OPEN** | `AssetReportService.java:56-61`: for any asset ending before `windowEnd`, it is only returned because `fold()` fails to exclude expired assets. Fixing `fold()` will break `upcomingRenewals()` for all assets ending before `windowEnd`. |
| 2 | `churn()` never marks asset closed or zeroes quantity on `TERMINATED` — double-counts and phantom downsizes | P1 | **STILL OPEN** | `AssetReportService.java:125-154`: `case TERMINATED` (lines 145-149) emits a churn event without resetting `quantity = 0` or recording a terminated guard. Duplicate `TERMINATED` double-counts; late `AMENDED` after `TERMINATED` emits phantom `DOWNSIZED`. Null `quantity` emits `quantityChurned = null`. Same-day `AMENDED` after `SUPERSEDED` still counts as downsize (strict `isBefore`, ignoring `appliedOrder`). |
| 3 | Reports depend exclusively on `asset_history`, which has no live production producer | P1 | **STILL OPEN** | `AssetReportService.java:57,76` reads solely from `AssetHistoryProjection`. All producers are held or disabled. Both reports return empty/incomplete data. Documented in `docs/runbooks/asset-read-access.md:20`. |
| 4 | `asOf` silently ignored when explicit `windowStart`/`windowEnd` supplied; no `withinDays` upper bound | P2 | **STILL OPEN** | `AssetReportService.java:208-233`: accepts `windowStart` + `windowEnd` + `asOf` and silently drops `asOf`. `withinDays` has no max-span guard. |
| 5 | Unbounded in-memory account history load and unpaginated report responses | P2 | **STILL OPEN** | `AssetReportService.java:57,76` → `accountRows()` → `visibleIds()` loads all account assets into memory. Reports return unpaginated lists. |
| 6 | Missing tenant scoping at merge time | P1 (at merge) | **FIXED by #1077** | `#740` called `store.findByAccountId()` directly. `#1077` switched to `history.accountRows()` with `InstalledBaseReadScope`. Residual Q11 account scope P2. |
| 7 | Broken test compilation at merge time (fixtures removed by #741) | P2 (at merge) | **FIXED by #747** | `#747` (`4615b35c`) restored compilation against refactored fixtures. |
| 8 | Unused `AssetHistoryStore store` field left in `AssetReportService` | nit | **STILL OPEN** | `AssetReportService.java:37,41,45`: `store` injected but never referenced after `#1077`. |

**Hold-lift prerequisite:** Terminal-state resurrection (#719 finding 2: `fold()` / `AMENDED`
resurrects closed assets) and churn double-counting (#740 finding 2: missing termination guard)
are coupled — both must be resolved before hold lift, as fixing `fold()` alone breaks
`upcomingRenewals()`.

**Follow-ups under DRG-652:** Findings 1–2 are hold-lift prerequisites (projection semantics +
report logic, partly built under DRG-902 but not wired at `a877f90a`). Finding 3 shared with #719
(no live producer). Findings 4–5 are targeted hardening.

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
| Non-atomic append + `maxAppliedOrder() + 1` race | #719 | Open P1; hold-lift prereq (grouped with replay wiring) |
| `fold()` ignores `effectiveEnd` / `AMENDED` resurrects closed | #719 | Open P1; hold-lift prereq (coupled with #740 churn) |
| No production consumer; `dropProjection()` unguarded | #719 | Open P1; latent behind holds, partly built under DRG-902 |
| Account-scope isolation (Q11) | #719 | Open P2; Q11 scope |
| Invalid `direction` → HTTP 500 | #719 | Open P2; hardening |
| Full-tenant `IN (:assetIds)` load | #719 | Open P2; hardening |
| Read auth + tenant isolation | #719 | **Fixed** by #723 + #1077 |
| `V1_14` rollback script | #719 | **Fixed** by #1070 (DRG-924 / #1095) |
| `walk()` missing `getAssetId()` fallback | #719 | Open nit |
| validateLines rejects SWAP/UPGRADE/TERMINATE | #741 | Open; amendment lifecycle prerequisite |
| Replacement product executed price | #741 | Open; amendment lifecycle prerequisite |
| RenewalPolicyClient shape mismatch | #742 | Mitigated (`@JsonIgnoreProperties`); `maxDecreasePct` data gap |
| Stale observed identities | #742 | Open; needs freshness verification |
| `upcomingRenewals()` coupled to `fold()` `effectiveEnd` bug | #740 | Open P1; hold-lift prereq (coupled with #719 finding 2) |
| `churn()` missing termination guard / double-counting | #740 | Open P1; hold-lift prereq |
| No live `asset_history` producer | #740 | Open P1; shared with #719 finding 3 |
| `asOf` silently ignored + unbounded `withinDays` | #740 | Open P2; hardening |
| Unbounded in-memory load + unpaginated responses | #740 | Open P2; hardening |
| Tenant scoping at merge time | #740 | **Fixed** by #1077 (residual Q11 P2) |
| Test compilation at merge time | #740 | **Fixed** by #747 |
| Unused `store` field | #740 | Open nit |
| Owner approval ≠ lane verdict | #958 | Provisional; docs-only, owner approved |
| Subscription list unfiltered | #1003 | Open; needs status filtering |

## References

- DoD v0.4: https://app.notion.com/p/408f7cb4e4df81b18b7adfee3d5b8e82
- DRG-652 W0-3 evidence: https://app.notion.com/p/422f7cb4e4df81e4b286d7e4cfa5ce0f
- Decision filing ruling: 7:03 PM CT Nov 23 2026
- Residual scores: `/workspace/reports/asset-mgmt/residual-v04-scores.md`
