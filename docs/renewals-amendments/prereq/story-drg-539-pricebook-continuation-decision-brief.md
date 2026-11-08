# DRG-539 — Product, pricebook and configuration in continuation: residual Decision brief

**Status:** Proposal only. Decision brief for CPQ Decision scoring of **delivery forks that remain after** the merged Proposed price-book read note for List bases. This file does **not** accept [`story-drg-539-pricebook-read-for-list-bases.md`](story-drg-539-pricebook-read-for-list-bases.md) (#645), does **not** reopen [`adr-renewal-uplift-basis-and-precedence.md`](adr-renewal-uplift-basis-and-precedence.md) (Accepted 2026-10-16 / DRG-604 / [#623](https://github.com/drgaciw/open-crm-cpq/pull/623)), does **not** reopen [ADR-008](../../adr/ADR-008-postgres-schema-per-service.md), does **not** reopen [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) (Accepted 2026-10-17), does **not** reopen the [delegated decision record](delegated-renewals-amendments-decisions-2026-10-17.md) cells that leave older 539 proposals unaccepted wholesale, authorize an implementation brief, authorize product code, open a Flyway migration, flip a flag, register a gateway route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / continuation catalog edges. **Story:** [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539/product-pricebook-and-configuration-services-not-in-continuation-flow). Linear read this pass: **In Review**. Notion mirror linked from the issue (Linear is source of status).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Existing design note (cite, do not accept from this PR):** [`story-drg-539-pricebook-read-for-list-bases.md`](story-drg-539-pricebook-read-for-list-bases.md) — Proposed; merged [#645](https://github.com/drgaciw/open-crm-cpq/pull/645). Covers the price-book read for `CurrentList`, `ListLessContractedDiscount`, and `notAboveList`. Its recommendation (Option A + A1) and owner-decision list remain **unaccepted**. Product substitution and `ReconfigureFrom` are explicitly out of that note.

**In-flight alignment (cite without adopting open OQs / OF-*):**

| Ticket / artifact | What this brief must not contradict / must not adopt |
|---|---|
| [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567) residual Decision brief (draft) | OQ-567-* stay **Proposed**. This brief restates that List bases wait on an **Accepted** 539 read + delivered as-of resolve; it does **not** score or adopt OQ-567-*. |
| [DRG-576](https://linear.app/drgamtd-workspace/issue/DRG-576) frame + catalog-eligibility Decision brief (draft) | SUCCESSOR / relationship vocabulary and OQ-* stay **Proposed** where those drafts leave them open. This brief does **not** adopt 576-F* or residual OQ1–OQ12. |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) / [`drg-609-pricing-design-acceptance-record.md`](drg-609-pricing-design-acceptance-record.md) | Design **Accepted** 2026-10-17; acceptance authorizes **no** code. Continuation evaluate sketch and conversion price remain outside this brief's Adopted set. Do **not** treat 609 acceptance as choosing book-selection rules. |
| [`oq9-pricing-continuation-capability.md`](oq9-pricing-continuation-capability.md) | Verification / capability gap; do **not** close OQ9 here. |
| [DRG-429](https://linear.app/drgamtd-workspace/issue/DRG-429) | Reconfigure-from-hash; Backlog. Not a product-to-product type. |
| [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549) / [DRG-541](https://linear.app/drgamtd-workspace/issue/DRG-541) / [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564) | Mode catalog, rules wiring, and PolicyDecision store remain their stories' Proposed residuals. |

**This note authorizes no product code, schema, Flyway, Drools edit, feature flag, topic, gateway route, or deploy.** Product / schema / flag = **NO-GO**. No `opencrm.jev.*.enabled` flag is turned on. `opencrm.pricing.ramp.enabled` stays default off. An implementation brief that adds `resolve-as-of`, wires List bases, consults product-service for SUCCESSOR, or calls configuration-engine from continuation is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (uplift ADR)` / `Adopted (ADR-008)` / `Adopted (ADR-017)` / `Adopted (delegated)` / `Adopted (609 acceptance)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual delivery fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact / detect-changes on this checkout after index. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-539 residual)

Continuation today prices from the accepted rate schedule only. Product-service, pricebook-service, and configuration-engine do **not** participate on the renewal / continuation path. The Accepted uplift record names a price-book read for List bases as DRG-539 work and does not design it. [#645](https://github.com/drgaciw/open-crm-cpq/pull/645) shipped a **Proposed** design note for that read (Option A + successor books A1). Merging the note is not acceptance. Product substitution (successor SKUs) and `ReconfigureFrom` remain undesigned in that note and in code.

Without a residual Decision brief that separates **already Adopted** contracts from **still-Proposed** forks, a follow-up PR could treat #645 as Done for RA-level catalog consultation, invent a cross-schema JDBC read of `price_books`, silently accept Option A without scoring owner forks, claim CAM Flyway `V1_16`–`V1_21`, or adopt DRG-567 / DRG-576 open OQs by implication.

Linear DRG-539 (`Linear read` 2026-11-06): Medium; **In Review**; related to DRG-567; attachment #645 (merged docs). Hygiene: Proposed note only; Done needs DoD v0.2 + CPQ Decision validation.

## Already Adopted (do not re-decide)

Restated from Accepted records (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | List bases need a current-list unit at the price-as-of date; missing list / book version → `UNRESOLVED_BASIS`; the uplift ADR **names** the read as DRG-539 and does not design it | **Adopted (uplift ADR)** D-01 / D-03 / consequences |
| A2 | Default basis remains `ContractedNet`; List bases exist only when a rung sets them | **Adopted (uplift ADR)** D-01 |
| A3 | Service integration is **HTTP or Kafka**; cross-schema table reads forbidden; pricing-service is sole Flyway owner of the shared `pricing` schema (including `price_books`); pricebook-service stays Flyway-disabled | **Adopted (ADR-008)** |
| A4 | ADR-017 call-graph allow-list E1–E6; E4 is pricing → pricebook `POST /internal/v1/price-books/resolve-batch`; **new edges need a D-01 amendment**; D-03 timeouts (5s/30s default; E4 first); D-04 retries only for harmless reads including price-book resolution | **Adopted (ADR-017)** |
| A5 | Older DRG-539 / 609 / related proposals are **not accepted wholesale** by the 2026-10-17 delegation; narrow delegated contracts supersede only where they explicitly decide | **Adopted (delegated)** opening |
| A6 | DRG-609 pricing **design** is Accepted (continuation evaluate capability); acceptance authorizes **no** code, migration, flag, or deploy | **Adopted (609 acceptance)** |
| A7 | Documentation acceptance of the 2026-10-17 batch covers Cluster / story frames listed there; it does **not** accept the DRG-539 price-book read note | **Adopted** [`documentation-acceptance-2026-10-17.md`](documentation-acceptance-2026-10-17.md) scope |
| A8 | Holds: docs PRs alone authorize no product code; ramp flag off; no Jev enablement; dual-read / Activities holds untouched | AGENT.md / Cluster holds |

## What the tree does today (continuation × catalog)

`verified by reading code` at the base SHA.

1. **`QuoteContinuationPolicy`** renewal branch multiplies exit net by caller percent (null → 0) at scale 2. No pricebook, product, or configuration client call.
2. **`QuoteContinuationService.author` / `draftRenewal`** write exit-derived amounts into quote line money columns; system drafts stay on the zero-uplift / 12-month boundary. No catalog consultation.
3. **`RenewalUpliftResolver`** (#640) prices `ContractedNet` + `UPLIFT_PCT` / `SAME` as preview. Non-contracted bases → `UNRESOLVED_BASIS`. No price-book HTTP call.
4. **pricebook-service** exposes `POST /internal/v1/price-books/resolve-batch` (region + product external ids; effective as **today**; no as-of date; surrogate id). pricing-service `PriceBookResolutionClient` / `RestPriceBookResolutionClient` used on **execute**, not on continuation. Client builder still has no connect/read timeouts on this edge (ADR-017 Rollout item 1 not yet landed as code).
5. **product-service** has no `product_relationship` / SUCCESSOR table (migrations tip `V1_8`). No continuation client consults catalog relationships.
6. **configuration-engine** evaluate path exists for quote configuration validate; continuation does not call `ReconfigureFrom` / hash reconfigure (DRG-429 Backlog).
7. Migration tips on this `main` (`read`): quote Flyway tip `V1_16__ramp_escalator_compounding.sql`; pricing tip `V9__…`; CAM tip `V1_15__document_apply_replay_evidence.sql`; product tip `V1_8`; pricebook-service has **no** Flyway directory. **This brief claims no CAM Flyway `V1_16`–`V1_21` and reserves those integers for other owners.**

### Docs-only change impact

This branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must run GitNexus `impact` before changing `PriceBookInternalResolutionService`, `RestPriceBookResolutionClient`, `RenewalUpliftResolver`, `QuoteContinuationPolicy`, product relationship readers, or CAM migrations.

## Owner-decision table (remaining delivery forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table. **Do not** treat a recommendation as authorization. Rows refine the #645 note's owner list and the undesigned product/configuration remainder; they do **not** reopen A1–A8.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-539-1 | Accept vs keep Proposed the #645 price-book read note | Keep the note **Proposed** until the owner fills `Owner decision` on OQ-539-2..OQ-539-11 (or explicitly accepts the note with words and a time). Merging **this** brief does **not** accept #645 | Accept #645 wholesale from this PR; reject #645 and redesign | Yes — any List-basis code |
| OQ-539-2 | Reader for current list | Affirm #645 Option **A**: pricing-service calls pricebook-service; quote sends product, currency, price-as-of date, region when present; pricing writes list + `price_books.uuid` onto the snapshot | Option B (quote resolves and sends amounts); Option C (pricing JPA on `price_books`); Option D (execute waterfall) | Yes — reader / ADR-008 |
| OQ-539-3 | Successor books (A1) vs dated entry table (A2) | Affirm **A1**: non-overlapping intervals per region / default; pin = `price_books.uuid` | A2 dated price rows on one book; keep one book forever and invent lists | Yes — schema / write rules |
| OQ-539-4 | Null `effective_date` books | `UNRESOLVED_BASIS` (not a list-basis book) | Treat null as already started (today's `isEffective`) | Yes — selection predicate |
| OQ-539-5 | Still-mutable future book when as-of falls in its interval | Allow pin; snapshot stores resolved uuid + list; fresh run re-resolves (D-03) | Refuse until `PRICE_BOOK_PUBLISHED_IMMUTABLE` would apply | Soft for August quotes of Nov books; hard for pin semantics |
| OQ-539-6 | Interval inclusivity + currency + region miss | Inclusive ends (match `isEffective`); book **and** entry currency must match subscription currency; optional region never falls through to default; 0 or >1 match → `UNRESOLVED_BASIS` | Exclusive ends; soft currency coerce; default fallthrough | Yes — List ACs |
| OQ-539-7 | Miss / timeout / outage behavior | Always `UNRESOLVED_BASIS` and **no** guessed price (`PricingProduct.listPrice`, override, zero, cache forbidden) | Map to execute's `PRICEBOOK_UNAVAILABLE` as a priced path; invent zero | Yes — fail-closed |
| OQ-539-8 | New edge vs extend E4 | Treat `resolve-as-of` as a **new** ADR-017 D-01 edge (path sketch only until scored); keep `resolve-batch` unchanged for execute | Overload `resolve-batch` with optional as-of; dual semantics on one path | Yes — allow-list / client |
| OQ-539-9 | Latency / retry class for the renewal read | Apply ADR-017 D-03 defaults (5s/30s) and D-04 retry class for price-book resolution **once the edge is amended**; first failure class still yields `UNRESOLVED_BASIS` after retries exhaust | No retries; custom timeouts without ADR amendment | Soft for preview; hard for production List |
| OQ-539-10 | Prior-list pin storage | Affirm #645: prior list is an **acceptance pin**, not a live re-read; **no** CAM column from this brief; until a later story stores it, `ListLessContractedDiscount` stays `UNRESOLVED_BASIS` on existing rates. **Do not** claim CAM `V1_16`–`V1_21` | Add `list_unit_price` / `price_book_uuid` under a reserved CAM version in this story; invert `discount_rate` | Yes — ListLessContractedDiscount |
| OQ-539-11 | Product substitution (SUCCESSOR) on continuation | **Cite** DRG-576 vocabulary / residual Decision brief; do **not** adopt open 576 OQs. Continuation does not substitute SKUs until a scored SUCCESSOR read exists; same-SKU renew needs no row (576-F3 baseline disposition as design default only) | Invent ad-hoc successor strings in quote-service; fold SUCCESSOR into UPGRADE here | Yes — substitution ACs |
| OQ-539-12 | Configuration / `ReconfigureFrom` | Leave hash reconfigure on **DRG-429**; continuation does not call configuration-engine for reconfigure in this story. Do not design a config session from this brief | Bring ReconfigureFrom into DRG-539 first slice; force config evaluate on every renewal | Yes if config required; Soft if deferred |
| OQ-539-13 | Sequencing with DRG-567 List delivery | Keep resolver List bases at `UNRESOLVED_BASIS` until #645 is **Accepted** **and** a delivered as-of read exists; do **not** adopt OQ-567-* | Implement List inside 567 from caller pins; accept #645 from the 567 brief | Yes — List / notAboveList |
| OQ-539-14 | Flyway / CAM version claim | **No** migration from this Decision brief. Tips observational only. **CAM `V1_16`–`V1_21` reserved** (not claimed here). pricing would-be uniqueness / A2 table = next free pricing migration at build time when a later story asks | Reserve CAM `V1_16`–`V1_21` for prior-list pins in this PR; open pricebook Flyway | Soft (docs); hard if a PR invents numbers |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-539-1 — Accept the #645 note?

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposed until OQ-539-2..11 scored (Recommended, Proposed)** | Matches the note's own gate and A5 / A7. |
| **B. Accept #645 wholesale from this PR** | Out of scope; this file does not accept other Proposed notes. |
| **C. Reject #645 and redesign** | Throws away the List-basis design without a replacement. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-2 — Reader

| Option | What the owner would be choosing |
|---|---|
| **A. pricing-service → pricebook-service (Recommended, Proposed)** | #645 Option A. Honors A3. |
| **B. quote-service resolves** | #645 Option B; quote has no price-book client today. |
| **C. pricing JPA on `price_books`** | Rejects A3 / ADR-008. |
| **D. Execute waterfall** | #645 Option D; invents lists the uplift ADR forbids. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-3 — Successor books vs dated entries

| Option | What the owner would be choosing |
|---|---|
| **A. A1 successor books (Recommended, Proposed)** | Scenario 1 ($50 → $52) without a new price table. |
| **B. A2 dated entry table** | Keeps one book per region; new table. |
| **C. Freeze one book forever** | Cannot express as-of list change. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-4 — Null `effective_date`

| Option | What the owner would be choosing |
|---|---|
| **A. UNRESOLVED_BASIS (Recommended, Proposed)** | #645 owner item 3. Pins stay meaningful. |
| **B. Treat as already started** | Matches today's execute `isEffective`; mutable null books. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-5 — Mutable future book

| Option | What the owner would be choosing |
|---|---|
| **A. Allow pin; snapshot keeps resolved list (Recommended, Proposed)** | #645 owner item 4. |
| **B. Refuse until immutable** | Withholds future list on earlier action dates. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-6 — Inclusivity, currency, region

| Option | What the owner would be choosing |
|---|---|
| **A. Inclusive + strict currency + no default fallthrough (Recommended, Proposed)** | #645 owner items 5–6. |
| **B. Soften any of those** | Diverges from `isEffective` / invites wrong-book lists. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-7 — Fail closed

| Option | What the owner would be choosing |
|---|---|
| **A. UNRESOLVED_BASIS, never guess (Recommended, Proposed)** | #645 recommendation + owner item 7. |
| **B. Reuse execute unavailable codes as a price** | Turns outage into a commercial unit. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-8 — ADR-017 edge shape

| Option | What the owner would be choosing |
|---|---|
| **A. New D-01 edge; resolve-batch unchanged (Recommended, Proposed)** | Matches A4; path name remains a sketch until scored. |
| **B. Overload resolve-batch** | Couples execute and renewal contracts. |

**Recommended (Proposed):** A. Confirming the sketch authorizes **no** endpoint. `Owner decision: ___ (drg, time)`.

### OQ-539-9 — Latency / retry

| Option | What the owner would be choosing |
|---|---|
| **A. ADR-017 D-03/D-04 once edge amended; then UNRESOLVED_BASIS (Recommended, Proposed)** | Supersedes the design note's "ADR-017 Proposed" wording without editing that file; honors A4. |
| **B. No retries** | Faster fail; more transient UNRESOLVED. |
| **C. Custom limits without amendment** | Conflicts with A4 governance. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-10 — Prior list

| Option | What the owner would be choosing |
|---|---|
| **A. Acceptance pin later; no CAM claim now (Recommended, Proposed)** | #645 owner item 8. Protects CAM `V1_16`–`V1_21` reservation. |
| **B. Claim a CAM version in this story** | Forbidden by this lane's hard rule. |
| **C. Invert discount_rate** | #645 Option E; rejected under the note's recommendation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-11 — Product substitution

| Option | What the owner would be choosing |
|---|---|
| **A. Defer to DRG-576 scored read; no ad-hoc substitution here (Recommended, Proposed)** | Keeps SUCCESSOR vocabulary one place; cites drafts without adopting open OQs. |
| **B. Ad-hoc successor in quote-service** | Second mapping store. |
| **C. Fold into UPGRADE inside DRG-539** | Conflicts with 576 type set. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-12 — ReconfigureFrom / configuration

| Option | What the owner would be choosing |
|---|---|
| **A. Leave on DRG-429 (Recommended, Proposed)** | Matches #645 out of scope and Linear Backlog status. |
| **B. First-slice config evaluate on every renewal** | New continuation → configuration-engine edge without a design. |
| **C. Design ReconfigureFrom inside DRG-539 now** | Expands this residual beyond List + sequencing. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-13 — Sequencing with DRG-567

| Option | What the owner would be choosing |
|---|---|
| **A. List stays UNRESOLVED until Accepted note + delivered read (Recommended, Proposed)** | Matches uplift consequences and OQ-567-3 recommended default without adopting that brief. |
| **B. Caller pins only inside 567** | Prices List without the book service. |
| **C. Accept #645 from the 567 PR** | Wrong story owns acceptance. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-539-14 — Flyway

| Option | What the owner would be choosing |
|---|---|
| **A. No migration; CAM V1_16–V1_21 reserved / unclaimed (Recommended, Proposed)** | Docs NO-GO. |
| **B. Reserve those CAM versions here for prior-list pins** | Forbidden; numbering belongs to a migration PR after forks close. |
| **C. Enable pricebook-service Flyway** | Conflicts with A3. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit NO-GO)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-539-1` .. `OQ-539-14`).
2. CPQ Decision must score the OQ-539-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. Until those forks are scored/closed, **NO-GO** on:
   - product / pricebook / configuration / quote / pricing Java or TypeScript for continuation catalog edges;
   - Flyway under pricing, CAM, product, or quote for this story;
   - new gateway routes or feature flags;
   - treating #645 merge or this PR merge as acceptance of the price-book read.
4. Even after forks close: A1–A8 hold; ramp flag stays off; no Jev enablement; DRG-567 / DRG-576 / DRG-564 open OQs stay Proposed unless their own Decision briefs close them; DRG-609 acceptance still authorizes no code by itself.
5. Linear status for DRG-539 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Out of scope

- Editing `QuoteContinuationPolicy`, `RenewalUpliftResolver`, `PriceBookInternalResolutionService`, product relationship schema, configuration-engine clients, or any Flyway file.
- Accepting [`story-drg-539-pricebook-read-for-list-bases.md`](story-drg-539-pricebook-read-for-list-bases.md) from this PR.
- Adopting OQ-567-*, DRG-576 residual OQs, OF-568-*, or OQ-564-*.
- Closing OQ9 or implementing `POST /api/v1/pricing/continuation/evaluate`.
- Claiming CAM Flyway versions `V1_16`–`V1_21`.
- Enabling `opencrm.pricing.ramp.enabled` or any `opencrm.jev.*.enabled` flag.
- Reopening Accepted uplift D-01..D-07, ADR-008, ADR-017 D-01..D-08, or delegated wholesale-exclusion of older 539 proposals.

## Evidence index

| Item | Where it was read |
|---|---|
| DRG-539, DRG-567, DRG-576, DRG-609, DRG-429 | Linear, this pass |
| Proposed price-book read note | `story-drg-539-pricebook-read-for-list-bases.md`; #645 merged |
| Accepted uplift ADR | `adr-renewal-uplift-basis-and-precedence.md`; #623; DRG-604 |
| ADR-008 / ADR-017 | `docs/adr/`; ADR-017 Accepted 2026-10-17 (#672) |
| Delegated / documentation acceptance | `delegated-renewals-amendments-decisions-2026-10-17.md`; `documentation-acceptance-2026-10-17.md` |
| DRG-609 acceptance | `drg-609-pricing-design-acceptance-record.md` |
| DRG-576 frame (successor / Reconfigure cite) | `story-drg-576-product-relationship-and-eligibility-decision-frame.md` |
| Continuation still schedule-only | `QuoteContinuationPolicy.java`; `QuoteContinuationService` |
| List bases unresolved without book | `RenewalUpliftResolver.java` |
| Resolve-batch / no as-of | `InternalPriceBookController`; `PriceBookInternalResolutionService`; `RestPriceBookResolutionClient` |
| Migration tips | quote `V1_16`; pricing `V9`; CAM `V1_15`; product `V1_8`; no pricebook Flyway (no `V1_16`–`V1_21` claimed) |

## Unverified in this pass

- Any runtime HTTP JSON or live book selection.
- Whether ADR-017 Rollout item 1 (E4 timeouts) has a partial code PR not yet on this `main`.
- Next free pricing / CAM migration integers in every environment at build time.
- Notion DoD `verification.state` for the linked page.
- Full text of in-flight DRG-567 / DRG-576 Decision brief draft PRs beyond citations required to avoid adopting their open OQs.
