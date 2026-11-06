# DRG-567 — Renewal uplift basis, caps, price-as-of, precedence: residual Decision brief

**Status:** Proposal only. Decision brief for CPQ Decision scoring of **delivery forks that remain after** the Accepted uplift record and the merged ContractedNet first slice. This file does **not** reopen or amend [`adr-renewal-uplift-basis-and-precedence.md`](adr-renewal-uplift-basis-and-precedence.md) (Accepted 2026-10-16 / DRG-604 / [#623](https://github.com/drgaciw/open-crm-cpq/pull/623)), does **not** reopen the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) § Pricing and ARR or § Renewal automation cells, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a gateway route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / renewal uplift. **Story:** [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy-precedence) (RA-33). Linear read this pass: **In Review**. Notion: [RA-33](https://app.notion.com/p/3fbf7cb4e4df81dbafe9c3cc82c56acd) (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**First slice (cite, do not re-implement here):** [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) merged `RenewalUpliftResolver` + internal preview `POST /internal/v1/pricing/renewals/resolve`. Partial delivery only: `ContractedNet` + `UPLIFT_PCT` / `SAME`; List bases return `UNRESOLVED_BASIS`; other modes throw `RenewalUpliftModeOutOfSliceException`; no persistence; live `draftRenewal` / `QuoteContinuationPolicy` path unchanged.

**In-flight alignment (cite without adopting open OQs / OF-*):**

| Ticket / PR | What this brief must not contradict / must not adopt |
|---|---|
| [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568) draft [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) | Escalators / locks ACs + OF-568-1..9 stay **Proposed**. This brief cites D-06 start-price handoff only. Does **not** adopt OF-* money, compounding, or lock defaults. `opencrm.pricing.ramp.enabled` stays **false**. |
| [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) draft [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) | Continuation PolicyDecision audit Decision brief; OQ-564-* stay **Proposed**. This brief restates delegated preview ≠ confirmed; does **not** score or adopt OQ-564-*. |
| [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539) [`story-drg-539-pricebook-read-for-list-bases.md`](story-drg-539-pricebook-read-for-list-bases.md) | Proposed price-book read; List bases stay unresolved until that note is accepted. |
| [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549) | Mode catalog beyond `SAME` / `UPLIFT_PCT`. |
| [DRG-541](https://linear.app/drgamtd-workspace/issue/DRG-541) | Rules rung `dealType` / churn wiring and fraction→percent-points conversion. |
| [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) / escalator ADR | Per-step compounding and rounding; D-06 already defers expansion. |

**This note authorizes no product code, schema, Flyway, Drools edit, feature flag, topic, gateway route, or deploy.** Product / schema / flag = **NO-GO**. `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. An implementation brief that wires the live renewal path or widens modes/bases is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` / `Adopted (uplift ADR)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact after `npx gitnexus@latest analyze --index-only` on this checkout. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (RA-33 residual)

The Accepted uplift record fixed basis default `ContractedNet`, caps/floors, `EffectiveDate` price-as-of, sparse five-rung precedence, scale-4 unit rounding, and the D-07 snapshot field list. [#640](https://github.com/drgaciw/open-crm-cpq/pull/640) delivered a pure pricing-service resolver for that mathematics on `ContractedNet` + `UpliftPct` / `Same`, plus an internal preview endpoint. Live renewals still price inside `QuoteContinuationPolicy` at caller percent (system drafts: **0%**) and scale **2**. List bases, remaining modes, rules-rung execution, durable confirmation, and multi-year expansion remain outside this story's closed cells.

Without a residual Decision brief that separates **already Adopted** contracts from **still-Proposed delivery forks**, a follow-up PR could treat #640 as Done for RA-33, silently wire `draftRenewal` to preview output as commercial price, invent a gateway path that contradicts the delegated resolve contract, or adopt #807 / #822 open forks by implication.

Linear DRG-567 (`Linear read` 2026-11-06): High; **In Review**; blocked by DRG-604 (Done); attachments include #640 (merged) and #623 (Accepted ADRs). Hygiene: partial delivery; Done needs DoD v0.2 + CPQ Decision validation.

## Already Adopted (do not re-decide)

Restated from the Accepted uplift ADR (`read`) and the delegated record (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | Default basis **`ContractedNet`** (exit-period net). `CurrentList` and `ListLessContractedDiscount` exist only when a rung sets them | Uplift ADR D-01 |
| A2 | Cap/floor after basis formula; `maxIncreasePct` from exit net; optional `notAboveList`; default `notBelowExitNet=true`; directional scale-4 bound normalize; `PRICE_PROTECTION_CONFLICT` when floor > cap | Uplift ADR D-02 |
| A3 | Default price-as-of **`EffectiveDate`**; per-line choice among ActionDate / EffectiveDate / LineStart; pin book version on the decision | Uplift ADR D-03 |
| A4 | Sparse overlay precedence: **clause → line → agreement → account → rules**; unset fields fall through; missing percent is `UNRESOLVED_UPLIFT` (not silent zero) | Uplift ADR D-04 |
| A5 | Single renewal unit half-up **scale 4**; extended amount scale 2; escalator per-step rounding is **not** this decision | Uplift ADR D-05 |
| A6 | Multi-year escalator starts from this first-period unit; **no expansion** until the escalator procedure is accepted; DRG-568 consumes start price + per-step cap check | Uplift ADR D-06 |
| A7 | D-07 snapshot field list; DRG-564 owns the audit **store**; this story lists fields the store must eventually carry | Uplift ADR D-07 |
| A8 | pricing-service is the **sole merger** of five unmerged rungs; quote-service does not collapse a clause away before the cap runs | Uplift ADR Decision + Delegated § Pricing and ARR |
| A9 | Select **`SAME` + `UPLIFT_PCT`** for the renewal pricing slice; LIST and multi-year expansion remain excluded until separate delivered contracts exist | Delegated § Pricing and ARR |
| A10 | Versioned read-only/no-persistence resolve contract; same inputs → same semantic `data`; pure **preview ≠ confirmed** commercial price; distinct confirmation command before commercial use | Delegated § Pricing and ARR |
| A11 | Keep the **single-line zero-uplift 12-month** auto-generated draft boundary; widening it needs delivered pricing contracts | Delegated § Renewal automation |
| A12 | Holds: ramp flag off; no Jev enablement; docs PRs alone authorize no product code | AGENT.md / Cluster holds |

## What the tree does today (uplift delivery)

`verified by reading code` at the base SHA.

1. **`RenewalUpliftResolver`** (`pricing-service`) merges five rungs, defaults basis `CONTRACTED_NET`, price-as-of `EFFECTIVE_DATE`, `notBelowExitNet=true`. Prices `UPLIFT_PCT` and `SAME` on `ContractedNet`. Non-contracted bases → `UNRESOLVED_BASIS`. Other modes → `RenewalUpliftModeOutOfSliceException` (names DRG-549). No DB read, no rules port, no persist, no event.
2. **`RenewalResolveController`** exposes `POST /internal/v1/pricing/renewals/resolve` with `ROLE_INTERNAL_INVOKE`. Response is preview (`RenewalResolveResponse.previewOf`). Not on the API gateway path the delegated prose named as `/api/v1/pricing/renewals/resolve`.
3. **`QuoteContinuationPolicy.plan`** (renewal branch) still multiplies exit net by caller `upliftPercent` (null → 0) at **scale 2** via `money`. Does not call the resolver.
4. **`QuoteContinuationService.draftRenewal`** opens the system draft with null uplift / null term → **0% / 12 months**. Does not call pricing-service. Writes `commercial_decision_audit` for new drafts only (`upliftPercent` `"0"`).
5. Seeded `renewal-uplift.drl` still stores fractions (`0.08` / `0.03`); continuation does not send `dealType` / churn (DRG-541).
6. Migration tips on this `main` (`read`): quote Flyway tip `V1_16__ramp_escalator_compounding.sql`; pricing tip `V9__…`; CAM tip `V1_15__document_apply_replay_evidence.sql`. **This brief claims no CAM Flyway `V1_16`–`V1_21` and reserves no migration numbers.**

### GitNexus (this checkout)

Indexed this pass with `npx gitnexus@latest analyze --index-only` (`verified by GitNexus`). Docs-only: **no production symbol is edited** by this brief.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `RenewalUpliftResolver` | LOW (`impactedCount` 1) | Direct: `RenewalResolveService`. Text search: tests + resolve service only. |
| `RenewalResolveController` | LOW (`impactedCount` 1) | Direct: `GlobalExceptionHandler` import. |
| `QuoteContinuationPolicy` | **UNKNOWN** | Empty caller set from the graph. Text search shows callers in `QuoteContinuationService`, controllers, and tests. **UNKNOWN is unresolved, not low.** |

A later implementation that edits `QuoteContinuationPolicy.plan` or wires `draftRenewal` to the resolver **must** re-run impact and treat UNKNOWN as unresolved until text-search / graph evidence closes it. Process enumeration for this index was truncated (`truncated: true` on flows); that is **not** a clean check for any future code change.

## Owner-decision table (remaining delivery forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table. **Do not** treat a recommendation as authorization.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-567-1 | Live path vs preview-only | Keep `draftRenewal` / `QuoteContinuationPolicy` on the A11 zero-uplift boundary until a **separately authorized** wiring story after confirmation (A10) exists; do not treat preview resolve as commercial price | Wire `draftRenewal` to call `/internal/.../resolve` now; replace local multiply with resolver unit without confirmation | Yes — commercial renewals |
| OQ-567-2 | Resolve URL surface | Keep landed **`/internal/v1/pricing/renewals/resolve`** as the first-slice contract; treat delegated `/api/v1/...` wording as the **public/gateway shape to reconcile later**, not as a silent rename in this story | Immediately expose `/api/v1/...` on the gateway; dual-path both forever | Soft for preview; hard before external consumers |
| OQ-567-3 | List bases (`CurrentList`, `ListLessContractedDiscount`, `notAboveList` without list) | Keep `UNRESOLVED_BASIS` until [`story-drg-539-pricebook-read-for-list-bases.md`](story-drg-539-pricebook-read-for-list-bases.md) is **Accepted** and a delivered read exists | Implement List bases from caller-supplied pins only; accept the 539 note from this PR | Yes — List / notAboveList ACs |
| OQ-567-4 | Mode catalog beyond `SAME` / `UPLIFT_PCT` | Leave `LIST`, `UPLIFT_AMT`, `OVERRIDE`, `RENEGOTIATE` to **DRG-549**; keep `RenewalUpliftModeOutOfSliceException` | Expand modes inside DRG-567 without 549 Decision | Yes — RA-15 modes |
| OQ-567-5 | Rules rung fraction + `dealType` / churn | Leave conversion and wiring to **DRG-541**; rules remain lowest rung when present | Delete the DRL; treat fraction as percent points without conversion; invent a second rules merge | Soft for ContractedNet preview; hard for RULES-sourced percent |
| OQ-567-6 | Unit scale cutover (scale-2 continuation vs scale-4 resolver) | New priced renewals that use the Accepted path store **scale-4** units (A5); do not rewrite historical scale-2 continuation rows | Force scale-2 on the resolver to match today's continuation; silently reprice open drafts | Yes — when live path switches |
| OQ-567-7 | Confirmation before commercial use | Restate A10: preview resolve never inserts; commercial use waits on DRG-564 / DRG-612 confirmation. **Cite** [#822](https://github.com/drgaciw/open-crm-cpq/pull/822) OQ-564-*; **do not adopt** them here | Allow resolve to insert; treat preview envelope as commercial evidence | Yes — mid-term V8 / A4 |
| OQ-567-8 | Escalator / lock handoff | Period-1 unit from this decision remains the start price for DRG-568. **Cite** [#807](https://github.com/drgaciw/open-crm-cpq/pull/807) OF-568-*; **do not adopt** them. Flat first period while ramp flag off | Expand Y2/Y3 inside DRG-567; adopt OF-568 money defaults | Soft while flag off; hard for multi-period priced renewals |
| OQ-567-9 | CAM renewal policy bodies vs thin header | Restate delegated store of versioned renewal policy bodies in **CAM policy records**; quote passes unmerged rungs — delivery of writers/readers is a separate story | Enrich quote header with merged policy; skip CAM records | Soft for resolver math; hard for production policy sourcing |
| OQ-567-10 | What counts as “delivered pricing contracts” to widen A11 | Require at least: live path uses pricing-service merge for `SAME`/`UPLIFT_PCT` + confirmation id retained on the draft line + DoD evidence — **before** widening multi-line / non-zero system drafts | Widen `draftRenewal` as soon as #640 exists; widen when List bases land | Yes — automation widening |
| OQ-567-11 | Flyway / schema for this story | **No** migration from this Decision brief. Would-be tip integers (quote next after `V1_16`, pricing next after `V9`, CAM next after `V1_15`) are observations only — **not reserved**. This brief does **not** claim or open CAM Flyway `V1_16`–`V1_21` | Reserve CAM `V1_16`–`V1_21` here; open a pricing PolicyDecision table under DRG-567 | Soft (docs); hard if a PR invents numbers |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-567-1 — Live path vs preview-only

| Option | What the owner would be choosing |
|---|---|
| **A. Keep A11 boundary until authorized wiring + confirmation (Recommended, Proposed)** | Matches delegated automation hold and A10. #640 stays a pure/preview slice. |
| **B. Wire `draftRenewal` to internal resolve now** | System drafts would leave silent zero, but preview ≠ confirmed still forbids presenting that as commercial without confirmation. |
| **C. Replace local multiply without confirmation** | Publishes resolver output as the draft price with no decision id — contradicts A10. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-2 — Resolve URL surface

| Option | What the owner would be choosing |
|---|---|
| **A. Keep `/internal/v1/...` for first slice; reconcile gateway later (Recommended, Proposed)** | Matches landed controller; avoids a silent public surface. |
| **B. Gateway `/api/v1/...` immediately** | Matches delegated prose literally; needs gateway + auth design not in this brief. |
| **C. Dual paths forever** | Two contracts for one merge. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-3 — List bases

| Option | What the owner would be choosing |
|---|---|
| **A. Wait on Accepted DRG-539 read (Recommended, Proposed)** | Matches uplift ADR consequences and the 539 note's own gate. |
| **B. Caller-supplied pins only** | Prices List without a book service; drifts from ADR-008 HTTP boundary intent. |
| **C. Accept the 539 note from this PR** | Out of scope; this file does not accept other Proposed notes. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-4 — Mode catalog

| Option | What the owner would be choosing |
|---|---|
| **A. DRG-549 owns remaining modes (Recommended, Proposed)** | Matches #640 exception text and A9. |
| **B. Expand inside DRG-567** | Splits RA-15 from its Decision frame. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-5 — Rules rung

| Option | What the owner would be choosing |
|---|---|
| **A. DRG-541 owns wiring + fraction conversion (Recommended, Proposed)** | Matches uplift ADR consequences. |
| **B. Delete DRL** | Removes the lowest rung named in A4. |
| **C. Pass fraction as percent points** | Prices 0.08% instead of 8%. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-6 — Scale cutover

| Option | What the owner would be choosing |
|---|---|
| **A. Scale-4 for Accepted-path renewals; no rewrite of scale-2 history (Recommended, Proposed)** | Matches A5. |
| **B. Force resolver to scale-2** | Reopens D-05. |
| **C. Silently reprice open drafts** | Commercial integrity risk. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-7 — Confirmation (cite #822, do not adopt)

| Option | What the owner would be choosing |
|---|---|
| **A. Preview never inserts; commercial use waits on 564/612 (Recommended, Proposed)** | Restates A10. Leaves OQ-564-* to [#822](https://github.com/drgaciw/open-crm-cpq/pull/822). |
| **B. Resolve inserts** | Rejects A10 / A4. |
| **C. Adopt OQ-564-* defaults here** | Out of scope; would adopt #822 policy. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-8 — Escalator handoff (cite #807, do not adopt)

| Option | What the owner would be choosing |
|---|---|
| **A. Period-1 only; OF-568-* stay Proposed (Recommended, Proposed)** | Restates A6. Ramp flag stays off. |
| **B. Expand Y2/Y3 in DRG-567** | Reopens D-06. |
| **C. Adopt OF-568-* here** | Out of scope; would adopt #807 policy. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-9 — Policy body home

| Option | What the owner would be choosing |
|---|---|
| **A. CAM policy records as delegated; separate delivery story (Recommended, Proposed)** | Restates delegated § Pricing and ARR. |
| **B. Quote-header enrichment** | Rejected by delegated store choice. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-10 — “Delivered pricing contracts” gate for A11

| Option | What the owner would be choosing |
|---|---|
| **A. Live merge + confirmation id + DoD evidence before widening drafts (Recommended, Proposed)** | Protects A11. |
| **B. #640 alone is enough** | Contradicts A10 / A11. |
| **C. Wait only for List bases** | Over-blocks `ContractedNet` commercial path; under-blocks audit. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-567-11 — Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No migration from this brief; tips observational only (Recommended, Proposed)** | Docs NO-GO. Does not claim CAM `V1_16`–`V1_21`. |
| **B. Reserve CAM `V1_16`–`V1_21` here** | Forbidden by this lane's hard rule; numbering belongs to a migration PR. |
| **C. Open pricing PolicyDecision table under DRG-567** | Belongs to DRG-564 / DRG-612 sequencing, not this residual brief. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-567-1` .. `OQ-567-11`).
2. CPQ Decision must score the OQ-567-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **do not** write or accept an implementation brief that authorizes Java/TS product work, Flyway, Drools edits, gateway routes, or flag flips for residual DRG-567 delivery.
4. Even after forks close: A1–A12 hold; ramp flag stays off; no Jev enablement; #807 OF-568-* and #822 OQ-564-* stay Proposed unless their own Decision briefs close them; List bases still need Accepted DRG-539.
5. Linear status for DRG-567 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Out of scope

- Editing `RenewalUpliftResolver`, `QuoteContinuationPolicy`, `draftRenewal`, Drools, gateway routes, or any Flyway file.
- Accepting the DRG-539 note, DRG-549 frame, DRG-541 wiring, #807 OF-568-*, or #822 OQ-564-*.
- Reopening Accepted uplift D-01..D-07 or delegated A8–A11 cells.
- Enabling `opencrm.pricing.ramp.enabled` or any `opencrm.jev.*.enabled` flag.
- Claiming CAM Flyway versions `V1_16`–`V1_21`.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-567, DRG-604, relations | Linear, this pass |
| Accepted uplift ADR | `adr-renewal-uplift-basis-and-precedence.md`; #623; DRG-604 Done |
| Delegated Pricing / ARR / automation | `delegated-renewals-amendments-decisions-2026-10-17.md` |
| First slice | #640; `RenewalUpliftResolver.java`; `RenewalResolveController.java` |
| Live path still local | `QuoteContinuationPolicy.java`; `QuoteContinuationService.draftRenewal` |
| DRG-539 Proposed note | `story-drg-539-pricebook-read-for-list-bases.md` |
| #807 escalators (cite only) | Draft PR #807; OF-568-* Proposed |
| #822 audit (cite only) | Draft PR #822; OQ-564-* Proposed |
| Migration tips | quote `V1_16`; pricing `V9`; CAM `V1_15` (no `V1_16`–`V1_21` claimed) |
| GitNexus | impact `RenewalUpliftResolver` LOW; `RenewalResolveController` LOW; `QuoteContinuationPolicy` **UNKNOWN**; process enum truncated |

## Unverified in this pass

- Any runtime HTTP call to `/internal/v1/pricing/renewals/resolve` against a running stack.
- Whether a future gateway mapping will keep the `/internal` prefix or publish `/api/v1/...`.
- AutoJev tools (not connected in this environment); no AutoJev verdict is claimed.
- Full process coverage: GitNexus process enumeration reported truncation — not a clean check for future code edits.
