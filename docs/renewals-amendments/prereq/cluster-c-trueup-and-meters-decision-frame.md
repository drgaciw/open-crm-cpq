# Cluster C — true-up and meters: decision frame

**Status:** Proposed. Not an ADR. This file takes no number and is not copied into `docs/adr/`. Landing it on `main` records the frame. It does not accept any default in it. Acceptance is a later owner act that changes this status line.

**Date:** 2026-10-17. **Project:** OPEN-CRM-CPQ. **Cluster:** capacity meters and true-up (DRG-607 checklist row `capacity-meters`).

**Stories in this frame:**

| Ticket | RA | Title | Linear status read 2026-10-17 |
|---|---|---|---|
| [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569/capacity-true-up-policy-measurement-tolerance-pack-rounding-overage) | RA-35 | Capacity true-up policy: measurement, tolerance, pack rounding, overage rate and backdating | Backlog |
| [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570/meter-reading-provenance-dispute-and-frozen-true-up-snapshot) | RA-36 | Meter reading provenance, dispute and frozen true-up snapshot | Backlog |
| [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557/capacity-and-meter-readings-with-true-up-intent) | RA-23 | Capacity and meter readings with true-up intent | Backlog |

Comments on all three issues: none.

**Reading base:** `origin/main` at `0d71251d` (merge of #647). [#642](https://github.com/drgaciw/open-crm-cpq/pull/642) is already on that history (merge `3ee3ca0f`, OQ6 revision record). Citations below were read at that commit, or from Linear on 2026-10-17.

**Accepting this frame authorizes no code, migration, or deploy.** It authorizes no Flyway file, no Drools edit, no flag, no topic, no gateway route, and no story start.

## How to read a fork

Each fork has options, a recommendation, and a Proposed default. The recommendation is the option this frame would use. The Proposed default is the value that applies if the owner accepts the frame and leaves that fork unset. Until the owner accepts the frame, every default stays Proposed.

`specs/capacity-meters.md` is a separate proposal. It leaves these knobs unset and says no default is proposed as accepted. This frame does not edit that file and does not accept it. Where a Proposed default below names a knob that file left open, the owner is choosing the frame, not that spec.

## Holds this frame keeps

- [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) and [ADR-024](../../adr/ADR-024-order-orchestration-context.md) through [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) stay **Proposed** on this `main` (`docs/adr/README.md`). This frame does not treat them as accepted and does not place true-up fulfilment, licence regeneration, or a saga on them.
- Proration stays the Accepted record in [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) (Accepted 2026-10-16). This frame does not reopen the formula, the implementation class, or the rejected full-term ratio.
- S6 / [DRG-584](https://linear.app/drgamtd-workspace/issue/DRG-584) is not authorized. The OQ5 register row says S6 is not authorized. This frame adds no event, no schema-registry entry, and no `QuoteAcceptedEvent` field.
- S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) is not started here. #642 has merged. The OQ6 record on that merge says the ruling authorizes no story and that S3 needs separate owner authorization. Linear showed DRG-581 as Todo on 2026-10-17. Todo is not that authorization. OQ18 stays Proposed and is not confirmed here.
- [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design is not accepted. `story-drg-609-pricing-service-continuation-design.md` is a design proposal. This frame does not adopt its route, payload, or id scheme. Linear showed the issue In Review.
- On-prem self-report workflow stays parked (PL-1 through PL-5, RA-53 / DRG-587, `renewals-amendments-parking-lot.md`). This frame does not un-park it.
- `specs/partial-apply.md` is not accepted. OQ5 option A is adopted for the first slice only. True-up scenario tests stay deferred under DRG-565 (`partial-apply-rules-oq5-option-a.md`).

## Classification

The three titles are capacity-policy and meter-model decisions. Each description was read with its relations.

| Ticket | What the description asks | In this frame |
|---|---|---|
| DRG-569 | Define a typed `TrueUpPolicy`, defaults, and worked examples. Name a dry-run before presentation. | Included. The ask is a policy decision. |
| DRG-570 | Give a reading a source, attestation, status, and snapshot identity, and price only a frozen snapshot. | Included. The "add fields" sentence is the model decision. This file does not add the fields. |
| DRG-557 | Model a metric and `MeterReading` on the commitment line, and add a `TrueUp` intent with a threshold trigger. | Included. The ask is the model the other two hang on. |

No ticket in the trio was skipped. Descriptions do not ask this change to ship code.

Related issues were read and are not sections. [DRG-470](https://linear.app/drgamtd-workspace/issue/DRG-470) (`IngestUsage`) and [DRG-471](https://linear.app/drgamtd-workspace/issue/DRG-471) (`RateUsage`) are delivery stories. Their descriptions ask for an ingestion API and period rating. That work is outside this docs-only frame. [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547) is research intake. [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) is partial application; its true-up scenarios stay deferred. [DRG-607](https://linear.app/drgamtd-workspace/issue/DRG-607) is the spec umbrella and stays a checklist.

## Shared facts already on main

These apply to every section. Each section repeats only the facts that fork depends on.

| Fact | Where |
|---|---|
| Continuation operations are `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`. There is no `TRUE_UP` operation. | `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java` |
| A search of `backend/` `*.java` and `*.sql` for `MeterReading` and `TrueUp` found no type. | This pass, at `0d71251d` |
| Amendment and expansion proration is the day-stub in `QuoteContinuationPolicy.plan`: inclusive days in the partial billing period, scale 2 half-up, then full months. On a billing-period boundary, no day proration. The requested quantity is the replacement total. | Accepted proration record D-01, D-02; `QuoteContinuationPolicy.java` |
| The full-term ratio in `amendment-proration.drl` / `rule-007` is the rejected formula. Acceptance of the proration record authorizes DRG-542 to retire it. Acceptance did not itself delete the file. | Accepted proration record, "Rejected" and "Code to delete" |
| Scenario 2's `16 × 500 × 92/365 = 2,016.44` matches that rejected ratio only when the denominator is 365. It is an example figure, not a second formula. | `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md` Scenario 2; proration record, same coincidence |
| `TrueUp` is one of nine intent names on the LifecycleChange root. The root stays in quote-service. Pricing evaluation stays a call into pricing-service. The document pins results. Policies add no tables. Action date stays separate from effective date. Scope of that acceptance is S1 and S2. | ADR-035 D-03, D-05, D-06, status line. File: `docs/renewals-amendments/adr-035-amendment-document-placement.md` |
| Renewal field precedence, for renewal fields, is contract clause, then line, then agreement, then account, then the rules default. | Accepted uplift record D-04, `prereq/adr-renewal-uplift-basis-and-precedence.md` |
| Before `EXECUTED`, a superseding change opens a new revision under the same document number, freezes the old revision `SUPERSEDED`, clears pricing pins, and voids approvals. After `EXECUTED`, the remedy is a new LifecycleChange or correcting amendment. That ruling authorizes no code and does not start S3. | `prereq/oq6-revision-model-owner-decision.md`; register row OQ6 |
| Illustrative Scenario 2 (example numbers, not customer data): 64 cores committed, monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80, licence $2,500/core, maintenance $500/core/year, window 2026-01-01 through 2026-09-30. Peak excess 16 ($40,000 licence). Average 69.8 rounded to 70, excess 6 ($15,000). | Sanity check Scenario 2 |

The amendment spike's `delta_qty = max(0, peak − entitled)` (`docs/renewals-amendments/amendment-document-design-spike.md`, capacity true-up row) is a sketch. It is not a policy.

---

## DRG-569 — True-up policy

**Problem.** RA-35. A capacity true-up has no measurement semantics. On the Scenario 2 readings, peak bills 16 excess cores and average bills 6. The licence charge moves from $40,000 to $15,000 on that choice alone. With the example's forward maintenance, the totals are $42,016.44 and $15,756.16. The sanity check's about-2.8× spread ($44,433.11 versus $15,756.16) also includes the optional back-maintenance on the peak path. Missing knobs named on the issue: aggregation, window, tolerance, pack rounding, true-down, overage rate, backdating. The issue also lists a proration method. That method is not a fork here. DRG-557 is the meter and the intent. This ticket is the pricing semantics.

### Already settled on main

- The day-count formula for a partial billing period is the Accepted proration record D-01, implemented only in `QuoteContinuationPolicy.plan` (D-02). `None` and `NextCycle`, if DRG-550 adds them later, are switches around that method. The full-term ratio is not a `Daily` option (D-04 of that record).
- The perpetual licence lump in Scenario 2 (`16 × $2,500 = $40,000`) is not that stub. The stub prices a term charge for a replacement quantity inside a billing period. The $40,000 figure is excess cores times a contracted unit rate, with no day fraction.
- Continuation today has no true-up policy object. `QuoteContinuationPolicy` prices amendment, expansion, renewal, and extension. Renewal uplift percent defaults to zero when the caller omits it (`QuoteContinuationPolicy.java`, renewal branch). That zero is a renewal gap the Accepted uplift record replaces for renewals. It is not a true-up policy.
- ADR-035 D-05: a true-up policy binds by extension on the shared document. It does not add a policy table.
- ADR-035 D-03 places pricing evaluation as a call into pricing-service and has the document pin results. The DRG-609 note that would shape that call is not accepted.
- Maintenance percent-of-licence (the $500 figure) is DRG-571, Backlog. One-time arrears with their own service period are DRG-572, Backlog. Neither is decided here.
- `specs/capacity-meters.md` open question 4 still asks "which proration method". This frame leaves that question closed by the Accepted proration record and does not edit the spec.

### Forks

#### F1 — Aggregation

| Option | Meaning on the Scenario 2 readings |
|---|---|
| A. Peak | High-water 80. Excess 16. |
| B. P95 | No worked row on main. |
| C. Average | Mean 69.8. |
| D. EndOfPeriod | Last reading 80 on this particular window, which collides with peak. A window that falls after the peak would not. No separate fixture. |

**Recommendation:** A. **Proposed default:** Peak.

Peak bills the capacity the customer actually reached. Average on this window cuts the licence charge by $25,000. P95 and EndOfPeriod stay legal values a contract clause may set. They become acceptance fixtures only when the owner supplies the example. The spike sketch is evidence that Peak is the usual sketch, not a decision.

#### F2 — Tolerance

| Option | Meaning |
|---|---|
| A. `tolerancePct = 0` | Any aggregate above the commitment is excess. |
| B. A positive percent, set only by a clause | A 5% band on 64 cores ignores aggregates up to 67.2. The 80 peak still bills 16. A peak of 66 would bill 0. |

**Recommendation:** A, with B available as a clause. **Proposed default:** `0`.

Tolerance is an exceedance gate. It is not subtracted from billable excess after the gate opens. The 5% story in the sanity check is the illustration of B, not the product default.

#### F3 — Pack rounding

| Option | Meaning |
|---|---|
| A. `packSize` unset | Excess is the integer the aggregation produced. |
| B. Round excess up to `packSize` | The sanity-check variant takes 13 excess cores and a pack of 8 and bills 16. |

**Recommendation:** A, with B available as a clause. **Proposed default:** no pack rounding.

The base readings already produce 16 at peak. The pack row is a different variant. It is not applied on top of those 16.

#### F4 — True-down

| Option | Meaning |
|---|---|
| A. Never | Aggregate at or below commitment yields delta 0 and no credit. |
| B. AtRenewal | A shortfall could reduce the renewal quantity. Needs a renewal hook this frame does not define. |
| C. Allowed | A mid-term credit. |

**Recommendation:** A. **Proposed default:** Never.

B waits on the renewal policy. C would open a credit. Credits and termination fees are DRG-550. Mid-term customer-cancellation refunds stay parked (DRG-589). This frame does not start either.

#### F5 — Overage rate

| Option | Meaning |
|---|---|
| A. Contracted | The unit rate on the commitment line. Scenario 2 uses $2,500. |
| B. List | The price-book unit at a date. |
| C. Multiplier | Contracted times a clause factor. |

**Recommendation:** A, with B and C as clause overrides. **Proposed default:** Contracted.

The running continuation path retains the contracted unit rate. The proration record's note to DRG-550 calls that `RetainContracted`. List needs the price-book read in DRG-539 (In Review). This default does not start that read. A missing list, if a clause selects B, yields no price. It does not fall back to contracted in silence.

#### F6 — Backdating of arrears

This fork chooses whether an arrears charge exists, and the start of its service period. It does not choose a day-count formula.

| Option | Meaning |
|---|---|
| A. None | Forward only. No back-maintenance line. |
| B. FromFirstExceedance | Arrears from the first reading over the commitment. On the example, April (66) through September, 58 excess core-months. |
| C. FromWindowStart | Arrears from the window start, including months at or below commitment only if the owner later says those months count. This frame's reading of C is: the service period starts at the window start, and the quantity in each month is still `max(0, reading − commitment)` after tolerance. |

**Recommendation:** A. **Proposed default:** None.

B and C need a one-time charge line whose service period may start before the change effective date. That line is DRG-572. The example states back-maintenance as $2,416.67, which is `58 × (500 / 12)` rounded to cents at the end. Pre-rounding the core-month rate to $41.67 and then multiplying yields $2,416.86.

#### F7 — Rounding of an average, and of an arrears amount

Separate from proration scale.

| Option | Meaning |
|---|---|
| A. Whole metric unit, half-up, before excess. Arrears money is half-up at scale 2 on the extended amount. | 69.8 cores becomes 70. The 58 core-months become $2,416.67. |
| B. Keep the fractional aggregate. Pre-round a core-month rate, then multiply. | Excess from 69.8 would be 5.8 cores. Arrears would be $2,416.86 if the rate is $41.67. |

**Recommendation:** A for a core metric. **Proposed default:** half-up to a whole core before excess; extended arrears money half-up at scale 2. No pre-rounded core-month rate.

PVU, TB, or session metrics may be fractional. A clause may set the metric scale. The core default is the Scenario 2 row. Renewal unit scale 4 (Accepted uplift D-05) is the renewal unit. It is not this core count. Partial-period term money stays scale 2 half-up under the Accepted proration record.

#### F8 — Which rung sets a knob

| Option | Meaning |
|---|---|
| A. Same order as Accepted uplift D-04, applied to each true-up field: contract clause, then line, then agreement, then account, then the Proposed defaults in this frame | Unset fields fall through. The snapshot names the rung that supplied each field. |
| B. Account, then agreement, then line, as the sanity check's phrase "account > agreement > line" | The opposite specificity from D-04 if read as first-match. |
| C. One account policy, no line override | A line cannot pin peak versus average. |

**Recommendation:** A. **Proposed default:** A.

D-04 is Accepted for renewal fields. This frame does not amend D-04 and does not claim D-04 already covers true-up. It proposes the same order so two typed policies do not carry two ladders. `specs/capacity-meters.md` still says the order is an owner decision and sketches account, then agreement, then line. Option A is the recommendation in this frame. The spec is unchanged.

#### F9 — Dry-run before the customer sees the number

| Option | Meaning |
|---|---|
| A. A true-up cannot enter a customer-visible state until a dry-run has completed for that snapshot id, content hash, and policy version | The preview shows excess, rate source, maintenance trace, snapshot hash, and policy version. |
| B. Preview is optional | A presented quote may be the first run. |

**Recommendation:** A. **Proposed default:** A.

DealHub `isDryRun` is research evidence on the issue, not an API to copy. The dry-run is a quote-service preview that records the pins. It does not adopt the DRG-609 route. DRG-551 (Backlog) is the platform dry-run and revert story. This default does not start it. Re-running the same snapshot id, policy version, and content hash returns the same amounts.

#### F10 — Where the resolved policy is stored

| Option | Meaning |
|---|---|
| A. The change line in quote-service stores the policy version id and the field values, including the rung that won each field. The amendment document pins those ids. No true-up table. | Matches ADR-035 D-03 and D-05. |
| B. A `TrueUpPolicy` table in the contract-documents module | A policy-specific table. |

**Recommendation:** A. **Proposed default:** A.

Option B conflicts with Accepted D-05. A later ADR would be required to choose it. This frame does not write that ADR.

### Proration, stated so it is not a fork

The sanity-check field `prorationMethod { Daily | MonthDay | None }` is not offered. Any partial billing period uses Accepted proration D-01 in `QuoteContinuationPolicy.plan`. The forward maintenance example of $2,016.44 is the sanity check's 92/365 illustration. A true-up implementation, when one is later authorized, passes the maintenance line's billing period and the quantity that period should carry into that method. It does not store 92/365 as a method. The perpetual licence amount is excess times the F5 rate, with no day fraction.

Quantity sent into the day-stub is the replacement quantity that method already prices (proration record, the 70-seat example). A true-up line that displays delta `+16` still stores `endQty`. The term-charge call uses the quantity the maintenance policy selects. It does not pass `16` into the stub and treat it as the new committed quantity of 16. Maintenance policy selection is DRG-571.

### Recommendation and Proposed default (summary)

| Fork | Proposed default |
|---|---|
| F1 Aggregation | Peak |
| F2 Tolerance | 0 |
| F3 Pack | none |
| F4 True-down | Never |
| F5 Overage rate | Contracted |
| F6 Backdating | None |
| F7 Rounding | whole core, half-up; arrears extended amount scale 2 half-up |
| F8 Rung order | clause, line, agreement, account, then these defaults |
| F9 Dry-run | required before customer-visible |
| F10 Storage | pins on the change line; document pins ids; no policy table |
| Proration | not a fork; Accepted day-stub |

Worked check, if every default is later accepted, on the example readings: excess 16, licence $40,000, no pack, no tolerance effect, no true-down credit, no arrears line. Forward maintenance is whatever the Accepted day-stub returns for that maintenance line's billing period. It is not defined as $2,016.44 inside this frame.

### Owner decision needed

Accept this frame, or replace any row in the summary. Replacing F1 on the example moves the licence charge between $40,000 and $15,000. The sanity check's about-2.8× spread also turns F6 on. Accepting the frame still authorizes no code, migration, or deploy. DRG-569 stays open until the owner says the policy is the one to build.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-557 | The metric, the readings, and the intent this policy reads |
| DRG-570 | The frozen snapshot pricing is allowed to see |
| Accepted proration record | Day-stub for partial periods. Consumed. Not edited. |
| ADR-035 D-03, D-05, D-06 | Intent name, no policy table, action date separate from effective date |
| Accepted uplift D-04 | Source of the rung order proposed in F8. Not amended. |
| DRG-571 | Maintenance percent and whether the forward line uses delta or end quantity |
| DRG-572 | Required only if the owner replaces F6 with B or C |
| DRG-550 | Credit and termination fee. Out of F4. |
| DRG-539 | Required only if a clause selects List |
| DRG-564 | Audit row for the pinned policy. The note on `main` is Proposed. This frame does not accept it. |
| DRG-609 | Mechanism of the pricing call. Design not accepted. Not used. |
| DRG-565 | Mixed-line apply scenarios for a true-up plus an arrears line stay deferred |
| DRG-551 | Platform dry-run, if the owner later shares one implementation |
| DRG-471 | Related. Period rating of usage. Not this policy. Not started here. |

### What acceptance would unblock

A later implementation story can price a true-up from a named policy version and a frozen snapshot, using the defaults the owner left in place. Acceptance does not start that story. It does not start S3, S4, S5, or S6. It does not retire `rule-007` (that remains DRG-542). It does not implement DRG-571 or DRG-572.

---

## DRG-570 — Meter provenance, dispute, and frozen snapshot

**Problem.** RA-36. On-prem usage arrives as a licence-server log or a customer declaration. Both can be disputed or corrected. A true-up that cannot name the readings it priced will change when a reading is corrected. Scenario 2 needs the nine monthly readings frozen as a snapshot id plus a hash. DRG-470 covers idempotent ingestion. It does not cover attestation, dispute, or freeze. The issue's status set is Provisional, Attested, Disputed, Corrected.

### Already settled on main

- No `MeterReading` type exists in backend Java or SQL (shared facts).
- ADR-035 D-03: the document pins results. ADR-035 D-05: policies add no tables. A snapshot of readings is not an amendment-document table.
- ADR-008 (Accepted): one schema per service. A reading store and the document store do not join across schemas.
- OQ6, recorded by #642: a correction before `EXECUTED` is a new revision with pins cleared; after `EXECUTED` it is a new LifecycleChange or correcting amendment. The ruling authorizes no code and does not start S3.
- S2 hash chain (`amendment_document_events`, `AmendmentEventWriter`) records document lifecycle. It is the chain a document event can cite. It is not a meter store. This frame does not extend it. The writer requires an existing amendment document, so a quote that has no document yet has no chain row to append (`story-drg-564-policy-decision-audit-trail.md` describes that boundary; that note is Proposed).
- Parking lot PL-1 through PL-5 describe self-report entry, an attester, and a link from an attested report to a true-up. They are not active stories.
- `commercial_decision_audit` records the decision to open a renewal draft. It is not a meter snapshot. DRG-564's Proposed note says so. This frame does not widen that table.

### Forks

#### G1 — Reading status

| Option | Meaning |
|---|---|
| A. Provisional, Attested, Disputed, Corrected | The set named on the issue. Corrected is a new reading that supersedes. It does not mutate the prior row. |
| B. Attested and Disputed only | No provisional holding state and no explicit successor. |

**Recommendation:** A. **Proposed default:** A.

A corrected reading points at the reading it supersedes. The old row stays, with its original value, so the old hash still resolves.

#### G2 — Who may attest

| Option | Meaning |
|---|---|
| A. A named internal actor attests before a reading may enter a priceable snapshot. A customer declaration is a source, not an attestation. | Attester id, time, and method are stored. |
| B. A licence-server log is auto-attested. A declaration still needs A. | Monitoring connectivity is the attestation. |
| C. Any stored reading is priceable | No attestation gate. |

**Recommendation:** A. **Proposed default:** A.

The parking lot records that many on-prem customers will not allow usage-monitoring connectivity, so B cannot be the product default. PL-2 is the parked screen for the attester. This default does not un-park PL-1 through PL-5 and does not build the screen. The actor role (rep versus customer-ops) stays the parked choice inside PL-2. This frame requires a named actor and does not pick the role.

#### G3 — What pricing may read

| Option | Meaning |
|---|---|
| A. An Attested reading inside a Frozen snapshot | Provisional and Disputed readings are excluded. A snapshot that contains either is refused. The refusal names the reading id and status. |
| B. Provisional readings price with a warning | A presented number can move when the reading is later attested or disputed. |

**Recommendation:** A. **Proposed default:** A.

#### G4 — When the snapshot freezes

| Option | Meaning |
|---|---|
| A. When the dry-run completes | The hash is fixed before the customer sees the number. Presentation and execution cite it. |
| B. At presentation | A dry-run could still be recomputed. |
| C. At execution | The presented number is not yet bound to a hash. |

**Recommendation:** A. **Proposed default:** A.

This matches DRG-569 F9. The snapshot status is Open until that dry-run, then Frozen. `frozenAt` and `frozenBy` are part of the hash inputs.

#### G5 — Correction after freeze

| Option | Meaning |
|---|---|
| A. Follow the recorded OQ6 rule | Before `EXECUTED`, a new revision, old revision `SUPERSEDED`, pricing pins cleared, new snapshot id and hash. After `EXECUTED`, a new LifecycleChange or correcting amendment that cites a new snapshot. The old hash still resolves to the old readings. |
| B. Re-price the same quote in place | The presented number changes with no new revision. |

**Recommendation:** A. **Proposed default:** A.

B is the failure RA-36 names. Applying OQ6 here does not start S3 and does not decide the CAM command, the cleared-pin representation, or a supersession event. Those stay open in the OQ6 record. No S6 event is added.

#### G6 — Where the snapshot lives

| Option | Meaning |
|---|---|
| A. The LifecycleChange line stores snapshot id and content hash. The amendment document pins those two values. Reading rows live in the service that owns the readings, under ADR-008. They are not columns on the document line beyond the id and the hash. | Document placement stays ADR-035. |
| B. Reading rows and the snapshot table sit in the contract-documents module | Mixes meter evidence into the document aggregate. |

**Recommendation:** A. **Proposed default:** A.

The authoritative store for the reading rows (quote schema versus a later usage schema) is an implementation choice for the story that creates the table. This frame does not choose a Flyway version and does not create a schema. The priced artifact stores id and hash, not a live query of current readings.

#### G7 — Dispute versus renewal

| Option | Meaning |
|---|---|
| A. A disputed reading blocks pricing of every snapshot that includes it. It does not block an unrelated renewal of the agreement. | The true-up quote waits. A renewal that does not cite the snapshot proceeds under the renewal policy. |
| B. Any open dispute blocks renewal of the agreement | Couples meter dispute to the renewal gate. |

**Recommendation:** A. **Proposed default:** A.

B belongs with renewal policy and with parked PL-5. This frame does not add it.

### Recommendation and Proposed default (summary)

| Fork | Proposed default |
|---|---|
| G1 Status | Provisional, Attested, Disputed, Corrected |
| G2 Attestation | named internal actor required; declaration is a source |
| G3 Pricing input | Attested readings in a Frozen snapshot only |
| G4 Freeze | at dry-run completion |
| G5 Correction | OQ6 revision or correcting amendment; new snapshot |
| G6 Home | id and hash pinned on the change and on the document; no document table of readings |
| G7 Dispute scope | blocks snapshots that include the reading; does not block an unrelated renewal |

A priceable snapshot content hash covers the reading ids, values, units, window bounds, statuses, attester ids, and the policy version id from DRG-569. The algorithm family already used for document payload digests is SHA-256 as 64 lowercase hex (`AmendmentEventHasher`). This frame proposes that shape for the snapshot hash so two digests in one quote share one encoding. It does not call the hasher and does not add a payload.

### Owner decision needed

Accept the G1–G7 defaults, or replace them. The role of the attester stays parked in PL-2 even if G2 is accepted. Accepting this section authorizes no ingestion endpoint, no table, and no dispute UI.

### Dependencies

| Dependency | Why it is named |
|---|---|
| DRG-557 | The reading and the commitment line it hangs on |
| DRG-569 | The policy version included in the hash, and the dry-run that freezes the snapshot |
| OQ6 / #642 | Correction path. Already recorded. Does not start S3. |
| ADR-035 D-03, D-05 | Pins versus a new table |
| ADR-008 | Schema boundary for the reading store |
| ADR-018 | Idempotent consumers, when an event exists. This frame defines no event. |
| DRG-470 | Related ingestion story, Backlog. Not attestation. Not started. Description is an implementation story and is outside this frame. |
| DRG-564 | Proposed audit note. Not the snapshot. |
| PL-1..PL-5 | Parked self-report workflow. Not un-parked. |
| S6 / DRG-584 | Not authorized. No event contract in this section. |

### What acceptance would unblock

A later story can require every presented true-up to cite a snapshot id and a hash, and can refuse a quote whose hash does not match. Acceptance does not build ingestion, dispute screens, or the S3 API. It does not authorize S6 to emit a usage event.

---

## DRG-557 — Meter readings and true-up intent

**Problem.** RA-23. The product has no capacity metric and no meter reading, and the continuation operation enum has no true-up. The issue asks to model a metric (core, PVU, TB, session) and a `MeterReading` on the commitment line, and to add a `TrueUp` intent with a threshold initiation trigger. Research cited on the issue (Thales peak thresholds, Snow PVU/Oracle/SAP metrics, Tacton meters) is evidence. It is not the enum to copy. DRG-569 owns pricing semantics. DRG-570 owns provenance and freeze.

### Already settled on main

- `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION` (`QuoteContinuationRequest.java`). There is no metric field and no reading list on that request.
- ADR-035 D-03 already names `TrueUp` as one of nine intents on the LifecycleChange root, which stays in quote-service. That acceptance authorizes S1 and S2 only. It does not add the enum to `QuoteContinuationRequest` and does not define a meter.
- ADR-035 D-06: action date is not the effective date. Scenario 2's action date 2026-10-05, window end 2026-09-30, and contract effective 2026-10-01 are three dates. The continuation request has one `effectiveDate` and no action date (`QuoteContinuationRequest.java`).
- `specs/lifecycle-intents.md` is a proposal. It is not accepted. Its open question 1 still allows the owner to keep TrueUp as an Amend plus a policy slot. This frame's default follows the Accepted ADR-035 name list and does not accept that spec.
- Quantity on the running amendment path is the replacement total (proration record). Delta display is not implemented.
- Catalogue eligibility for which products may carry a metric is DRG-576, a different cluster. `specs/catalog-model.md` is a proposal. This frame does not accept it.
- Licence regeneration and fulfilment after apply are DRG-558, outside this frame. ADR-024 through ADR-027 stay Proposed, so fulfilment is not placed on an order saga.

### Forks

#### H1 — Where the metric is pinned

| Option | Meaning |
|---|---|
| A. On the commitment line: `Core`, `Pvu`, `Tb`, `Session`, or `Other` plus a code | The true-up reads the line. The product catalogue does not have to participate. |
| B. On the product only, copied onto the quote at draft time | The line can drift from the product with no stored pin. |
| C. The product lists allowed metrics and the line pins the one this commitment uses | Needs the catalogue model. |

**Recommendation:** A. **Proposed default:** A.

The issue asks for the metric on the commitment line. C waits on DRG-576. Until that cluster is accepted, the line pin is the commercial record. A later catalogue constraint may narrow `Other(code)`. It does not move the pin off the line.

#### H2 — True-up intent versus an amended seat line

| Option | Meaning |
|---|---|
| A. `TrueUp` stays its own intent name, as ADR-035 D-03 lists it. The true-up policy slot is selected by that intent. | Amend remains a different intent. |
| B. No new intent. An amendment line with a true-up policy slot. | Reopens the Accepted name list. |

**Recommendation:** A. **Proposed default:** A.

Option B needs an ADR that amends D-03. This frame does not write it. The name on the ADR is not an implemented enum. Adding `TRUE_UP` to `QuoteContinuationRequest.Operation` is a later code story.

#### H3 — What opens a true-up

| Option | Meaning |
|---|---|
| A. A true-up cycle opens the candidate. A threshold crossing is a warning only. | Scenario 2's trigger is the cycle. The March reading of 64, equal to commitment, can warn. It does not open a draft and it does not set the contract effective date. |
| B. A threshold crossing opens a draft true-up | A document can exist before the window closes. |
| C. Either the cycle or the threshold may open a draft | Two initiators, two candidates, unless a later rule dedupes them. |

**Recommendation:** A. **Proposed default:** A.

The issue asks for a threshold-based initiation trigger. This default keeps the threshold and makes it a warning, so the measurement window can finish before a price exists. The owner may replace A with B. B still cannot present a price before the DRG-569 dry-run, and it still cannot price from an unfrozen snapshot (DRG-570). First exceedance for arrears, if DRG-569 F6 is later set to FromFirstExceedance, is the April reading of 66, not the March reading of 64.

#### H4 — Quantity semantics on the true-up line

| Option | Meaning |
|---|---|
| A. Store `startQty`, `deltaQty`, and `endQty`. The line's semantics label defaults to Delta. Absolute is allowed when the contract says the new entitlement replaces the old number outright. | Example: start 64, delta +16, end 80. |
| B. Always Absolute | The line stores 80 and does not store the excess. |
| C. Always Delta | Overwrite contracts have no label. |

**Recommendation:** A. **Proposed default:** Delta, with both quantities stored. Absolute remains a line-level value.

The customer-visible capacity change is the excess. The entitlement after the change is `endQty`. The perpetual licence amount uses `deltaQty` times the contracted rate (DRG-569 F5). A term charge that goes through the Accepted day-stub uses the replacement quantity that method requires, taken from `endQty` unless DRG-571 says the maintenance line itself is a delta. Thales Add versus Overwrite, cited on the issue, is the research analogue of Delta versus Absolute. It is not an API.

#### H5 — What a reading contains before provenance

Provenance fields are DRG-570. This fork is the measurement body.

| Option | Meaning |
|---|---|
| A. A reading belongs to one commitment line and stores window start, window end, value, and unit | The unit matches the line metric. |
| B. Readings belong to an account-level meter, and the true-up joins them to a line at price time | The join key is a second model. |

**Recommendation:** A. **Proposed default:** A.

DRG-470 may later deliver rows into that shape. This frame does not define `IngestUsage`. A reading outside the policy window is dropped at aggregation time (DRG-569), not at capture time, so a late reading can still be stored and then excluded.

### Recommendation and Proposed default (summary)

| Fork | Proposed default |
|---|---|
| H1 Metric | pinned on the commitment line, extensible code |
| H2 Intent | `TrueUp`, as ADR-035 D-03 already names |
| H3 Initiation | cycle opens the candidate; threshold warns |
| H4 Quantities | Delta label; start, delta, and end all stored |
| H5 Reading body | per commitment line; window, value, unit |

### Owner decision needed

Accept H1–H5, or replace H3 if a threshold must open a draft. Replacing H2 means amending ADR-035, which is a separate ADR. Accepting H1 does not create the line column.

### Dependencies

| Dependency | Why it is named |
|---|---|
| ADR-035 D-03, D-06 | Intent name and separate dates. Consumed. Scope remains S1 and S2. |
| DRG-569 | Policy the intent selects. Pricing knobs stay there. |
| DRG-570 | Provenance, dispute, freeze. Not duplicated here. |
| DRG-552 / DRG-548 | Action codes and the wider intent enum. `specs/lifecycle-intents.md` is Proposed. Not accepted by this frame. |
| DRG-553 | Action date versus effective date, beyond the ADR-035 sentence |
| DRG-576 | Later constraint on which metrics a product allows |
| DRG-558 | Fulfilment after apply. Not placed on ADR-024..027. |
| DRG-565 | Related. True-up apply scenarios stay deferred. |
| DRG-547 | Research intake that spawned the gap. Tracking only. |
| S3 / DRG-581 | Not started. The intent name does not open the document API. |

### What acceptance would unblock

DRG-569 and DRG-570 have a line, a metric, a reading body, and an intent name to attach to. Acceptance does not add the enum, the table, or the threshold job. It does not authorize S3 to expose a true-up route. It does not move fulfilment onto a Proposed order model.

---

## Owner checklist

The owner can accept the frame as a whole or replace individual rows. Suggested order, because later rows read earlier ones:

1. DRG-557 H1–H5 (what is being measured, and which intent names it).
2. DRG-570 G1–G7 (which readings a price may use).
3. DRG-569 F1–F10 (how those readings become money), leaving proration on the Accepted record.

No row authorizes code, a migration, a flag, a topic, a gateway route, or a deploy. S6 stays unauthorized. S3 stays unstarted. The DRG-609 design stays unaccepted. ADR-017 and ADR-024 through ADR-027 stay Proposed.
