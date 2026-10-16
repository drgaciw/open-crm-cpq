# Price-lock policy: stub

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only; no code changes. 2026-10-16. Stub.*

**Conventions.** **[S]** = sourced from a project file; **[I]** = inference or design proposal. File keys as in `mid-term-amendment-policy-spec.md` (*spec*, *spike*, *rca*, *sanity*, *transcript*). *schema* = `amendment-entity-field-schema.md`, whose field names are used here.

## 1. Purpose and intent types

The price-lock policy holds a line's price fixed for a bounded period, then hands over to a defined mode (Same, List, UpliftPct, UpliftAmt). The same mechanism expresses multi-year renewal escalators [S sanity G2, scenarios 1 and 4]. It is a policy on the amendment spine, not a data model: a lock is the `segment_schedule` on a document line with `policy_slot = PriceLock` [S spec 7; schema 2.5].

- **Intent types:** registers **no intent of its own** [I]. It runs inside `Renew` (lock or escalator within the renewal term) and `Amend` (promotional lock on a mid-term add, such as the illustrative AI Assist lock to 2027-03-31) [S sanity scenario 4]. A price-lock extension (moving a lock end date) is an `Amend` with action `Update` on the schedule only, matching the schema default; whether it should register its own intent is schema open question 4 [S schema 5, 7; I].
- **Backlog anchor:** RA-34 / DRG-568 (renewal escalators and time-bounded price locks). Escalator semantics in general belong to DRG-532 / DRG-533 and ramp amendments to DRG-534; RA-34 is scoped to renewal escalators and locks only [S sanity 6].
- **Ownership:** LifecycleChange, pricing and render stay in quote-service; CAM stores the schedule and alone writes rate periods [S transcript architecture review; rca 3].

## 2. Binding to the six extension points

| # | Extension point [S spec 7] | Price-lock binding | Rules inherited or tightened |
|---|---|---|---|
| 1 | Intent hook | **Leaves default.** Uses the host intent (Renew or Amend) [I] | Inherits **V1** from the host policy |
| 2 | Term hook | **Leaves default.** Never extends a term; under Amend the term hook still forbids it [S spec 7] | Tightens **V5** with PL2 |
| 3 | Pricing hook | **Overrides** the slot with a PriceLock typed policy: lock value, lock end, post-lock mode and value, rounding per step, contract price-protection clause at top precedence [S sanity G1, G2, 5] | Inherits **V8**, **V9**, **V14**, **A5**, **A8**. Tightens V9 with PL4 |
| 4 | Segment hook | **Overrides**: owns the ordered schedule `{from, to, mode, value}` per line [S spec 7; sanity 5] | Adds PL1, PL3. Inherits **V12** |
| 5 | Trigger hook | **Adds** at-quote and term-extension invocation; no scheduled job required [I] | Inherits A6, A7 |
| 6 | Validation hook | **Adds** PL1 to PL5; cannot remove V3, V8, V10, V12, V13 [S spec 7] | Inherits V2 to V4, V6, V7, V10 to V13, A1 to A8 |

**Policy rules [I]** (local numbering, not backlog IDs):
- **PL1** Segments on a line are contiguous, non-overlapping and exactly cover the line's `effective_from` to `effective_to`.
- **PL2** No lock segment ends after the contract end date unless the same change also carries a Renew term extension.
- **PL3** Rounding is declared per step (unit or extended, per segment or compounded) because it changes results: Y3 $49.01 vs $49.00 in the illustrative 7% escalator [S sanity scenario 1].
- **PL4** A post-lock List segment pins its pricebook version and price-as-of date at pricing, not at segment start [S spike 6.2; sanity G1].
- **PL5** Shortening or removing a lock that is in force is a V14 override: actor, reason code, prior schedule, approval.

## 3. Triggers

| Trigger | Basis |
|---|---|
| **At quote:** a seller or the renewal policy attaches a lock or escalator while pricing a Renew or Amend line | [S sanity scenarios 1, 4] |
| **At term extension:** a renewal re-prices locked lines; the policy decides whether a lock carries over, expires at term end or is re-granted | [S sanity G2; I on options] |
| **Lock extension:** seller or API extends an active lock end date via Amend | [I] |

Lock expiry needs no trigger [I]. CAM already applies future-dated lines as future rate periods, so each segment boundary is recorded at apply time [S spike 4 rule 4]. Maxio's Lock-in Date (freezing the renewal price before term end) is a related snapshot point, not this lock [S sanity scenario 1].

## 4. Required inputs

| From quote-service (pricing side) | From CAM (record side) |
|---|---|
| Lock value and mode per segment, evaluated by pricing-service with a trace [S sanity 4] | Asset ledger: current rate periods and `accepted_line_uuid` for the locked line [S spike 0] |
| Term extension, if any, from the host Renew change [S spike 0] | `commercial_revision` per target (V3) [S spike 0] |
| Uplift basis and post-lock uplift %, amount or List [S sanity G1, G2] | Contract header: contract end date and any price-protection clause reference [I; OQ3] |
| Price-as-of date and pinned pricebook version per List segment [S sanity G1] | Term dates per line, used by PL1 and PL2 [S spike 1 lines] |
| Precedence level that supplied the lock (clause, line, agreement, account) [S sanity G1] | Existing `segment_schedule` on the line from prior documents [S schema 5] |

## 5. Outputs and audit events

- Document lines carry the segment schedule; each segment links to the line's PolicyDecision [S spike 5; I on linkage].
- On apply, CAM writes one rate period per segment, comparable to RCA `AssetStatePeriod.SegmentIdentifier` [S rca 1]. Today renewal writes one flat ABSOLUTE period, so multi-period apply is new behaviour [S transcript 3.1].
- Spine events reused unchanged [S spike 6.6]. Policy payloads [I]: lock granted, lock extended, lock overridden (PL5), with the full prior and new schedule digest.
- ARR bridge separates lock effects (RA-41 / DRG-575) [S sanity scenario 4].
- Audit on every path, including rejected lock changes: append-only, hash-chained, in CAM, same transaction [S transcript decisions 4, 5].

## 6. Open questions and dependencies

Spike decisions [S spike; spec 8]. OQ2 and OQ4 were resolved on 2026-10-16 [S transcript, resolved decisions]; the rest stay open:
- **OQ1 Placement (RA-27 / DRG-561):** where the segment schedule is stored.
- **OQ2 Apply point, resolved:** apply after EXECUTED only, so a promotional lock binds at execution [I on consequence].
- **OQ3 Contract header timing (DRG-458):** price-protection clauses need a header or clause reference.
- **OQ4 Numbering, resolved:** gapless sequence at EXECUTED, so a lock-only extension takes a full number [I on consequence].
- **OQ5 Partial application (RA-31 / DRG-565):** a segment set must apply as one unit.
- **OQ6 Revision model:** schedule edits in negotiation create revisions.
- **OQ7 Backfill (RA-32 / DRG-566):** existing locks have no record and cannot be inferred from `LEGACY_AGGREGATE` periods.
- **OQ8 Retention (DRG-313):** lock evidence must outlive disputes.

Policy-specific [I]: does a lock survive a mid-term quantity increase, and at what price; can locks span renewal terms; expiry notice. Dependencies: RA-34 / DRG-568, RA-33 / DRG-567, DRG-532 / DRG-533, DRG-534, RA-30 / DRG-564.
