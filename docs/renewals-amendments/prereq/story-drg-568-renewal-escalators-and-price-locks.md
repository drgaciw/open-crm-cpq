# DRG-568 — Multi-year renewal escalators and time-bounded price locks

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation first. Date: 2026-11-05.*

**Story:** [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568/multi-year-renewal-escalators-and-time-bounded-price-locks-in-renewal) (RA-34). Notion: [RA-34](https://app.notion.com/p/3fbf7cb4e4df81a7b50de01a26861164).

**Status:** Proposed. This file records reuse of landed escalator semantics and the generator, and encodes acceptance criteria 568-1..568-7 with fixtures. **It authorizes no production code path, no schema change, no Flyway migration, no flag flip, no route, and no deploy.** `opencrm.pricing.ramp.enabled` stays `false` in committed `quote-service` and `pricing-service` config. No `opencrm.jev.*.enabled` flag is turned on. **DRG-534 (ramp amendments) is out of scope for this PR and is not implemented here.**

**Unblocked:** [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532) Done; [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) Accepted (#669 / #674). Linear still listed historical blockers; this note treats both as met on `main` at the SHA below.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Spec + fixtures:** [specs/renewal-escalators-locks.md](specs/renewal-escalators-locks.md) and [specs/fixtures/renewal-escalators/](specs/fixtures/renewal-escalators/).

## Verification

| Mark | Meaning here |
|---|---|
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `verified by reading the issue` | Read from Linear DRG-568 / DRG-532 / DRG-533 in this pass. |
| `verified by GitNexus` | `npx gitnexus impact` on this checkout after `analyze --index-only`. |
| `unverified` | Not established in this pass. |

GitNexus (this checkout, after index):

| Symbol | Upstream risk | Notes |
|---|---|---|
| `RampEscalatorExpander` | LOW (`impactedCount` 3) | Direct: `AcceptedRampSnapshots`, `QuoteContinuationService` (imports). d=2: renewal eligibility consumer. |
| `QuoteContinuationService` | LOW | Direct: eligibility consumer; `QuoteService.setContinuationService`. |
| `QuoteContinuationPolicy` | UNKNOWN | Empty caller set from the graph. Text search shows callers in `QuoteContinuationService`, `QuoteContinuationController`, `QuoteService`, and tests. UNKNOWN is unresolved, not low. |

No production symbol is edited in this docs-first draft. A later flag-gated implementation must re-run impact before changing `QuoteContinuationPolicy.plan` or wiring the expander into renewal.

## Reuse (do not re-decide)

This story **reuses** landed pieces. It does not invent a second compounding rule, a second unit scale, or a second equal-block generator.

| Landed piece | What DRG-568 consumes | Evidence |
|---|---|---|
| DRG-533 ADR D-01..D-06 | Period 1 = uplift unit; compound-on-prior default; unit scale 4 / amount scale 2; explicit lock segments; skip second named-seat tier; marker ≠ lock | [DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md](DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md) Status: Accepted 2026-10-17. Blob `4922b07b4ce374b8ee9f88ecec593c5699b82fea`. |
| DRG-532 generator | `RampEscalatorExpander.expand` / `extend` for equal-block annual escalators; short final period when term ÷ step leaves a remainder; `compounding=false` percent-of-base path | `RampEscalatorExpander.java`. AC-532-1 already asserts 42.8000 / 45.7960 / **49.0017** for start 42.80, 7%, 36 months (`RampEscalatorExpanderTest`). `verified by reading code` |
| ADR-023 | Explicit periods stored; escalator inputs display-only after expansion; ramp flag default off | [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) |
| Uplift record D-01..D-05 | Single renewal unit (period 1); caps/floors | [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) |
| DRG-564 note | PolicyDecision audit grain (inputs + policy version per priced line/segment) | [story-drg-564-policy-decision-audit-trail.md](story-drg-564-policy-decision-audit-trail.md) Status: Proposed |

**Not reused for locks.** Unequal lock segments (5 months + 9 months) are an explicit `{from, to, mode, value}` list. Calling the equal-block expander with `everyMonths=12` on a 14-month term is the wrong path for scenario 4; the expander today emits a short final period, which is not the lock split. ADR D-04.

**Not in this PR.** DRG-534 elapsed-period amendment locking. [specs/ramp-amendments.md](specs/ramp-amendments.md) stays the amendment proposal. This file does not accept it and does not implement it.

## What the tree does today (renewal)

`verified by reading code` at the base SHA.

1. `QuoteContinuationPolicy.plan` for `RENEWAL` emits **one flat** period for the whole term. Unit = exit net × (1 + uplift%/100) at scale 2 via `money`. It does not call `RampEscalatorExpander`.
2. `QuoteContinuationService.draftRenewal` opens a draft with null uplift and null term → policy uses 0% and 12 months.
3. `opencrm.pricing.ramp.enabled` is `false` in both services' committed `application.yml`. `RampPricingProperties.enabled` defaults `false`.
4. With the flag off, schedule expand/attach returns 422 `RAMP_NOT_SUPPORTED` before pricing a ramp as flat.

Until a later, separately authorized, flag-gated implementation lands, production renewals stay one flat period. Encoding ACs 568-1..568-7 here is the contract those tests will assert; it is not a silent flip of continuation behavior.

## Acceptance criteria (Linear 568-1..568-7)

Full tables and fixture links: [specs/renewal-escalators-locks.md](specs/renewal-escalators-locks.md).

| ID | Summary | Fixture asserts |
|---|---|---|
| 568-1 | 36 months, 7%/yr, Y1 unit from uplift 42.8000, 200 seats | Units 42.8000 / 45.7960 / **49.0017**; TCV **330234.48**. Ticket $49.01 / $49.00 are ADR fork-2 alternatives, **not** the default. |
| 568-2 | 14-month renewal, lock through 2027-03-31 inclusive | 5 months @ 20.0000, then 9 months @ 21.4000. Boundary 2027-04-01 belongs to the new segment. No total required by the AC text; fixture also records line total 14630.00 at 50 seats for reconciliation. |
| 568-3 | 12-month renewal, no escalator | One flat period, unchanged from today. |
| 568-4 | Contiguity | No gaps, no overlaps; first starts at renewal start; last ends at renewal end. |
| 568-5 | Edges | Zero escalator → flat units; negative amount refused; leap/month-end no drift; no day stub at whole-month renewal boundary; escalator not stacked on uplift for period 1; segment totals reconcile. |
| 568-6 | Audit | PolicyDecision stores inputs + policy version per generated segment. |
| 568-7 | Flag | `opencrm.pricing.ramp.enabled` stays off by default. |

## Owner forks (Proposed — no silent adoption)

Every open fork stays **Proposed**. This file does not adopt a default as business law. Owner slots stay blank.

| # | Fork | Status | Notes |
|---|---|---|---|
| OF-568-1 | ADR body leftover "Proposed / stays Proposed" wording vs Status: Accepted; confirm scale-4 is final before money ships | **Proposed** | Linear AC 568-1 owner-fork note. No choice made here. Tests assert 49.0017 per ADR D-03 recommendation. |
| OF-568-2 | Unit scale 4 (TCV 330234.48) vs scale-2 illustration (TCV 330264.00) | **Proposed** | ADR fork 2. AC 568-1 asserts the scale-4 series; owner may still replace. |
| OF-568-3 | `compound=true` default vs RCA-style percent-of-period-1 | **Proposed** | ADR fork 1. Generator already supports `compounding=false`. |
| OF-568-4 | Post-lock percent applied once to locked unit | **Proposed** | ADR fork 3. Fixture 568-2 encodes the recommendation. |
| OF-568-5 | Unequal lengths as explicit segments only | **Proposed** | ADR fork 4. |
| OF-568-6 | Skip second named-seat tier on renewal nets | **Proposed** | ADR fork 5. |
| OF-568-7 | First slice: month-boundary locks only | **Proposed** | ADR fork 6. Mid-month lock end refused / unpriced, not day-prorated. |
| OF-568-8 | `ENABLE_12M_PRICE_PROTECTION` remains a marker, not a dated lock | **Proposed** | ADR fork 7 / uplift-automation AC-568-3 hold. |
| OF-568-9 | Whether a time-bounded lock is in force on the automation path | **Proposed** | Slot in [specs/renewal-automation.md](specs/renewal-automation.md). This story encodes the lock fixture; owner still confirms product enablement. |

## Flag hold

Committed config (must remain false in this PR and any follow-up that claims "flag-off only"):

- `backend/services/quote-service/src/main/resources/application.yml` → `opencrm.pricing.ramp.enabled: false`
- `backend/services/pricing-service/src/main/resources/application.yml` → `opencrm.pricing.ramp.enabled: false`
- `RampPricingProperties.enabled` field default `false`

Turning the flag on is a separate owner-authorized change. Flag off must never silently price a multi-period renewal as flat when a schedule was requested (ADR-023 / FR-09).

## Out of scope

- DRG-534 ramp amendments (elapsed lock, future regenerate).
- Enabling `opencrm.pricing.ramp.enabled`.
- Changing `QuoteContinuationPolicy` or `draftRenewal` behavior in this PR.
- Accepting ADR body numbering into `docs/adr/`.
- Reopening DRG-549 mode catalog or DRG-567 uplift basis.
- Subscriptions UI.

## Relation to renewal-automation.md

[specs/renewal-automation.md](specs/renewal-automation.md) § DRG-568 historically recorded the **pre-unblock** shape (one priced period; later units absent) while DRG-532 was still Backlog. That section is retained as the automation-path hold while the ramp flag is off. **AC identifiers 568-1..568-7 in this story and in [renewal-escalators-locks.md](specs/renewal-escalators-locks.md) are the Linear story ACs** and supersede the identically numbered interim rows in the automation spec for implementation planning. The automation file's interim rows are annotated as superseded; they are not deleted (audit trail).

## References

- Linear DRG-568, DRG-532, DRG-533, DRG-534, DRG-549.
- [DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md](DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md)
- `RampEscalatorExpander.java`, `RampEscalatorExpanderTest.java` (AC-532-1 / 49.0017)
- `QuoteContinuationPolicy.java`, `QuoteContinuationService.java`
- [price-lock-policy-stub.md](../price-lock-policy-stub.md) (stub; not accepted)
- Sanity check scenarios 1 and 4 in `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md`
