# DRG-340 — Credit draw-down for prepaid balances: residual Decision brief

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring of **requirements / ownership / delivery forks that remain after** Accepted ADR-023 D-09 (credit draw-down split out of the ramp sprint), the REQ-price-ramping out-of-scope declaration for prepaid balances, and the R3/R4 ramp residual Decision drafts that keep draw-down outside ramp Acceptance. This file does **not** reopen Adopted ADR-023 cells, does **not** reopen D-09's split, does **not** invent or accept a credit-draw-down REQ, does **not** adopt open **OQ-339-*** rows from the DRG-339 residual Decision brief draft [#878](https://github.com/drgaciw/open-crm-cpq/pull/878), does **not** adopt open **OQ-341-*** rows from the DRG-341 residual Decision brief draft [#880](https://github.com/drgaciw/open-crm-cpq/pull/880), does **not** adopt open **OQ2–OQ7** / Defaults slots from the DRG-534 Decision brief draft [#803](https://github.com/drgaciw/open-crm-cpq/pull/803), does **not** accept [`specs/conversions.md`](specs/conversions.md) or [`specs/ramp-amendments.md`](specs/ramp-amendments.md), does **not** authorize an implementation brief, authorize product code, open a Flyway migration, claim a CAM version in the reserved **V1_16–V1_21** band, register a `CreditDrawnDown` (or any credit) event contract, flip a flag, register a route, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed` under id **OQ-340-***. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `ramp` split-out / prepaid credit draw-down (BC-09-06). **Story:** [DRG-340](https://linear.app/drgamtd-workspace/issue/DRG-340/bc-09-06-credit-draw-down-for-prepaid-balances). Linear read this pass: **Backlog**.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Frame / authority (cite, do not reopen Adopted cells; do not adopt #878 / #880 / #803 OQs):**

| Artifact | Role |
|---|---|
| [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) (Accepted 2026-10-03) | **Adopted:** D-09 keeps usage-tier rating and credit draw-down **out of** the ramp sprint; ramp flag default **off** (D-06); ACV version switch separate (D-03) |
| [REQ-price-ramping.md](../../backlog/REQ-price-ramping.md) | Declares prepaid credit draw-down **not a ramp**; D-09 split; BC-09-06 needs **its own REQ**; out-of-scope for the ramp REQ |
| [forge/41-price-ramp-sprint.md](../../forge/41-price-ramp-sprint.md) § DRG-337 and DRG-340 | Explicitly **not edited** by the ramp sprint; Backlog / estimate none left as fetched |
| Wave-2 DRG-339 residual Decision brief [#878](https://github.com/drgaciw/open-crm-cpq/pull/878) | Cite-only R3 apply / Done / enablement forks (**OQ-339-***). This brief **does not score or close** those rows |
| Wave-2 DRG-341 residual Decision brief [#880](https://github.com/drgaciw/open-crm-cpq/pull/880) | Cite-only R4 hybrid / dual-entitlement forks (**OQ-341-***). Landed hybrid migration credit ≠ prepaid draw-down. This brief **does not score or close** those rows |
| Wave-2 DRG-534 Decision brief [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | Cite-only amendment forks (**OQ2–OQ7** + Defaults). Restates **ramp hold**. This brief **does not score or close** those rows |
| Notion BC-09-06 | Design backlog link on the Linear issue; story acceptance **not** individually reverified against code (Linear housekeeping 2026-10-09) |
| Capability names on the Linear story | `DrawDownCredit` · `GetCreditBalance` · `CreditDrawnDown` — vocabulary only until a REQ + Decision PASS |

**Related (cite; do not reopen or start product work):**

| Ticket / PR | Linear / GH | What this brief must not contradict / must not adopt |
|---|---|---|
| [DRG-339](https://linear.app/drgamtd-workspace/issue/DRG-339) / [#878](https://github.com/drgaciw/open-crm-cpq/pull/878) | In Progress / draft | Open **OQ-339-***. Cite only; do **not** adopt. R3 apply path is not prepaid draw-down |
| [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) / [#880](https://github.com/drgaciw/open-crm-cpq/pull/880) | In Progress / draft | Open **OQ-341-***. Cite only; do **not** adopt. Hybrid first-period migration credit is **not** a `CreditBalance` wallet |
| [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) | In Progress / draft | Open **OQ2–OQ7**. Cite only; do **not** adopt. Amendment elapsed lock is not draw-down |
| [DRG-337](https://linear.app/drgamtd-workspace/issue/DRG-337) | Backlog | Sibling D-09 split-out (usage tier rating). Needs its own REQ; do **not** fold into DRG-340 without owner words |
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) / conversion unused-prepaid | Related | Scenario unused-prepaid / trade-in credits are conversion pricing — not runtime prepaid wallet draw-down |
| [DRG-614](https://linear.app/drgamtd-workspace/issue/DRG-614) | Backlog | Owns non-prod enablement / Entra / chaos. Not a draw-down Authorization |
| Agent docs `agents/usage-based-pricing.md` | Target-state prose | Describes prepaid draw-down behaviour; **not** accepted product authority and **not** a substitute for a BC-09-06 REQ |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, credit wallet write, or deploy.** `opencrm.pricing.ramp.enabled` stays default off (**Ramp hold**). `opencrm.pricing.metrics.acv-version` stays committed `legacy` until a separate owner enablement act. No `opencrm.jev.*.enabled` flag is turned on. Dual-read / S4 enablement / asset-write / subscriptions-UI holds stay in force. An implementation brief that opens product work for DRG-340 (Java/TS edits, migrations, `CreditDrawnDown` registry entry, metering hooks, or enablement) is **blocked** until (1) a dedicated REQ exists and is accepted by owner words+time, and (2) CPQ Decision scores the open **OQ-340-*** forks below (or the owner explicitly defers a named fork with words and a time). **Product / schema / flag = NO-GO** while any blocking OQ-340-* row stays open. Closing #878 / #880 / #803 OQ rows is a separate owner act; this brief does not close them by citation.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (ADR-023)` / `Adopted (D-09)` | Already selected. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Residual Decision-PASS / REQ / ownership fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `UNKNOWN` | Graph could not answer; not a clean / low-risk check. |

## Problem (DRG-340 residual)

ADR-023 Accepted the ramp model with D-09 applied: prepaid credit draw-down is **not** inside R1–R4. REQ-price-ramping records that BC-09-06 needs **its own REQ** before estimate or start. The Linear story still carries the original acceptance criteria (non-negative `CreditBalance` without an explicit exception; emit `CreditDrawnDown`) and capability names (`DrawDownCredit` · `GetCreditBalance` · `CreditDrawnDown`), with **estimate: none**, status **Backlog**.

Without a residual Decision brief that separates **already Adopted split / holds** from **still-Proposed REQ / ownership / ledger / event / coupling forks**, a follow-up could treat agent prose or `MigrationCreditPolicy` as the prepaid wallet, register `CreditDrawnDown` under the ramp event family, fold draw-down into #878 / #880 Done claims, adopt #803 amendment recommendations by implication, open Flyway / CAM `V1_16`–`V1_21`, or flip the ramp flag "because credits are related."

Linear DRG-340 (`Linear read` 2026-11-06): **Backlog**; related to DRG-339 and DRG-341; parent epic DRG-291; Notion BC-09-06 + REQ-price-ramping attachments; no comments this pass. Housekeeping states acceptance has **not** been individually reverified against current code — Backlog does not prove absence of implementation, so this pass also searched the tree (`verified by reading code` below).

## Already Adopted (do not re-decide)

Restated from ADR-023 (`read`), REQ-price-ramping (`read`), forge/41 (`read`), and the orientation baselines in [#878](https://github.com/drgaciw/open-crm-cpq/pull/878) / [#880](https://github.com/drgaciw/open-crm-cpq/pull/880) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) (`read`). This Decision brief does **not** reopen these rows. Those drafts mark their OQ rows Proposed — those are **not** Adopted here.

| ID | Contract | Source |
|---|---|---|
| A1 | Credit draw-down is **split out** of ramp stories BC-03-09 / BC-09-04; ramp stays the shared core | ADR-023 D-09; REQ D-09 |
| A2 | Prepaid credit draw-down is **not a ramp**; it is related and may interact later, but has separate acceptance criteria | REQ-price-ramping "Not a ramp" table |
| A3 | BC-09-06 / DRG-340 **needs its own REQ** before it is estimated or started | Linear description; REQ split-out row |
| A4 | Ramp sprint docs **do not edit** DRG-340; live description / estimate none / Backlog left as fetched | forge/41 |
| A5 | **`opencrm.pricing.ramp.enabled` defaults false**; turning the flag on is outside Acceptance | ADR-023 D-06; **Ramp hold** |
| A6 | ACV version switch is **`opencrm.pricing.metrics.acv-version`**, not the ramp flag; committed default **`legacy`** | ADR-023 D-03 |
| A7 | Hybrid migration credit (`MigrationCreditPolicy`: 30% of book, once, first period of declining line) is **R4 hybrid**, not a prepaid `CreditBalance` wallet | forge/41 R4; [#880](https://github.com/drgaciw/open-crm-cpq/pull/880) orientation |
| A8 | Usage-tier rating (DRG-337) is a **sibling** D-09 split-out, not folded into DRG-340 by silence | ADR-023 D-09; Linear relations via shared subscription model only |
| A9 | Subscriptions / contract-asset UI stay **held**; billing schedules (BC-10) and revenue recognition (BC-11) stay out of the ramp REQ | REQ out of scope; ARCHITECTURE / AGENT.md holds |
| A10 | Docs / Decision briefs alone authorize **no** product code, event registry entry, flag flip, or deploy | AGENT.md / ADR-023 / #803 / #878 / #880 |

## What the tree does today (prepaid draw-down)

`verified by reading code` at the base SHA.

1. **No `CreditBalance` / `DrawDownCredit` / `GetCreditBalance` / `CreditDrawnDown` types** in production Java under `backend/` (search hit only the REQ/docs mention of the Notion AC text). Capability names on the Linear story are **not** implemented symbols.
2. **`EventContractRegistry`** publishes `RampApplied` and related ramp contracts; **no** `CreditDrawnDown` (or other credit-draw-down) contract is registered (`read` `EventContractRegistry.java`).
3. **`MigrationCreditPolicy`** — one-time hybrid migration amount adjustment on the declining ramp line; explicitly "recurring rates are never a credit allocation basis." This is **not** prepaid wallet draw-down (`read` `MigrationCreditPolicy.java`).
4. **Product-type labels** `SAAS_USAGE` / `SAAS_CREDITS` / `SAAS_USAGE_METERED` exist on enums / CHECK lists and appear in ramp **rejection** tests (usage/credits lines are not ramp-priced). Labels alone do **not** implement a credit ledger.
5. **Agent / skill prose** under `agents/usage-based-pricing.md` and `agents/skills/usage-based-pricing/` describes target-state prepaid draw-down behaviour. That prose is **not** an Accepted REQ and does **not** authorize schema or code.
6. **Ramp flag** — committed `opencrm.pricing.ramp.enabled: false` on pricing-service and quote-service remains the hold; unrelated to a future credit flag if one is ever designed.

### GitNexus (this checkout)

Docs-only change: **no production symbol is edited** by this brief. GitNexus MCP reported **no indexed repositories** this pass (`query` / `impact` unavailable until `gitnexus analyze`). Treat blast radius for any future credit symbols as **UNKNOWN** — not a clean check. A later implementation must run GitNexus `impact` before touching any new credit ledger, metering hook, `EventContractRegistry` entry, or CAM/quote/pricing schema. `UNKNOWN` / `partial` / `truncated` graph results are unresolved, not low risk.

## Owner-decision table (remaining REQ / ownership / delivery forks)

Every row is `Status: Proposed` under id **OQ-340-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No new Adopted cells.** Product / schema / flag stay **NO-GO** until blocking rows close (or the owner defers them with words and a time). Do **not** adopt open OQs from [#878](https://github.com/drgaciw/open-crm-cpq/pull/878) / [#880](https://github.com/drgaciw/open-crm-cpq/pull/880) / [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ-340-1 | Dedicated REQ before estimate / start | Keep Linear **Backlog**; write and accept a BC-09-06 REQ (separate from REQ-price-ramping) before any estimate, implementation brief, or status move to In Progress | Estimate from Linear AC alone; start coding without a REQ; fold into REQ-price-ramping | Yes — story start |
| OQ-340-2 | Linear status while residuals open | Leave **Backlog** unchanged by this docs PR; no In Progress / Done from Decision brief merge | Move In Progress when this PR opens; mark Done from docs alone | Yes — status hygiene |
| OQ-340-3 | Owning service for `CreditBalance` | Defer service ownership to the dedicated REQ (candidates: contract-asset-management vs pricing-service vs new billing-adjacent owner); **no** schema pick in this brief | Assign CAM / pricing / quote as owner from this file; invent a new microservice here | Yes — schema / ADR-008 |
| OQ-340-4 | Negative-balance exception policy | Keep AC text as **Proposed** until REQ: balance never goes negative without an **explicit, named** exception type; do not invent exception codes here | Allow silent overdraw; hard-fail always with no exception path; copy hybrid migration unused-credit semantics | Yes — AC-1 |
| OQ-340-5 | `CreditDrawnDown` event contract | **NO-GO** on registry / topic / payload until REQ + Decision PASS; do not piggyback on `RampApplied` | Register `CreditDrawnDown` now; overload `RampApplied`; publish without ADR-018 fitness | Yes — event / ADR-018 |
| OQ-340-6 | Coupling to usage tiers (DRG-337) | Keep sibling split; draw-down REQ may **cite** metering/rating but must not absorb BC-03-12 AC without owner words | Merge 337+340 into one story; implement tiers inside draw-down | Soft for docs; hard if product merges |
| OQ-340-7 | Distinction from hybrid / conversion credits | Keep A7: `MigrationCreditPolicy` and conversion unused-prepaid (DRG-573 / `specs/conversions.md`) are **not** `CreditBalance` draw-down; cite only | Treat hybrid first-period credit as wallet seed; reuse conversion Scenario 3 figures as ledger | Yes — commercial shape |
| OQ-340-8 | Coupling to #878 without adopting OQ-339-* | Cite [#878](https://github.com/drgaciw/open-crm-cpq/pull/878); R3 Done / apply residual scored separately; do **not** adopt OQ-339-* | Bundle 339+340 Done; require draw-down before R3 Done; implement draw-down in apply path | Yes — if product couples |
| OQ-340-9 | Coupling to #880 without adopting OQ-341-* | Cite [#880](https://github.com/drgaciw/open-crm-cpq/pull/880); hybrid pair / dual-entitlement residual scored separately; do **not** adopt OQ-341-* | Treat hybrid credit audit as prepaid ledger; enable dual-entitlement writes for credits | Yes — if product couples |
| OQ-340-10 | Coupling to #803 without adopting OQ2–OQ7 | Cite [#803](https://github.com/drgaciw/open-crm-cpq/pull/803); no amendment product from this story; do **not** adopt OQ2–OQ7 | Treat #803 Recommended rows as Adopted; hang draw-down off amendment elapsed-lock | Yes — amendment path |
| OQ-340-11 | Ramp flag / any credit enablement flag | **NO-GO.** Keep ramp flag false (A5). Do **not** invent or flip a credit-draw-down flag from this brief | Flip ramp true "for credits"; add `opencrm.*.credit-drawdown.enabled=true` in shared config now | Yes — flag / hold |
| OQ-340-12 | Schema / Flyway | **No** new migration from this Decision brief. Claim **no** CAM `V1_16`–`V1_21` (or any credit_balance table) | Ship credit_balance DDL now; reserve or consume V1_16–V1_21 here | Yes — any new migration |
| OQ-340-13 | Implementation brief / product edits | **NO-GO** on Java/TS product work until OQ-340-1 REQ acceptance **and** blocking OQ-340-* close (or owner defers) | Authorize ledger/metering/event PRs when this docs file merges | Yes — any code PR |
| OQ-340-14 | Agent prose as authority | Treat `agents/usage-based-pricing*` as **non-authoritative** target-state; REQ + Decision supersede | Promote agent skill to Accepted REQ; implement ERP purchase-history draw-down from agent text | Soft for docs; hard if coding from agents/ |
| OQ-340-15 | Billing / invoicing / revenue recognition | Keep BC-10 / BC-11 out of scope; draw-down REQ must not silently claim invoice application | Fold invoicing into BC-09-06; emit invoice lines from this story | Yes — if product expands |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-340-1 — Dedicated REQ before estimate / start

| Option | Notes |
|---|---|
| **A. Write/accept BC-09-06 REQ first; keep Backlog until then (Recommended, Proposed)** | Matches Linear "Needs: its own REQ" and REQ-price-ramping split-out row. |
| **B. Estimate from Linear AC alone** | Contradicts A3. **NO-GO** for start. |
| **C. Start coding / implementation brief without a REQ** | Violates hard product NO-GO. |

### OQ-340-2 — Linear status

| Option | Notes |
|---|---|
| **A. Leave Backlog unchanged (Recommended, Proposed)** | This docs PR must not mutate Linear status. |
| **B. Move In Progress when the Decision brief PR opens** | Premature without REQ. |
| **C. Mark Done from docs merge** | False Done. **NO-GO.** |

### OQ-340-3 — Owning service

| Option | Notes |
|---|---|
| **A. Defer ownership to the dedicated REQ (Recommended, Proposed)** | ADR-008 schema-per-service; wrong pick creates cross-DB reads. |
| **B. Assign CAM / pricing / quote from this brief** | Premature product topology. **NO-GO.** |
| **C. Spin a new microservice in this brief** | Out of Wave-2 docs lane. **NO-GO.** |

### OQ-340-4 — Negative-balance exception

| Option | Notes |
|---|---|
| **A. Keep "never negative without explicit exception" as Proposed AC; name exceptions in the REQ (Recommended, Proposed)** | Preserves Linear AC without inventing codes. |
| **B. Silent overdraw** | Contradicts story AC. **NO-GO.** |
| **C. Copy hybrid unused-credit / conversion day-count rules** | Different commercial object (A7). **NO-GO** as default. |

### OQ-340-5 — `CreditDrawnDown` event

| Option | Notes |
|---|---|
| **A. No registry/topic until REQ + Decision PASS (Recommended, Proposed)** | ADR-018 fitness; A10. **Product / schema NO-GO.** |
| **B. Register now under ramp topics** | Couples to R3 incorrectly. **NO-GO.** |
| **C. Publish without EventContractRegistry** | Violates ADR-018. **NO-GO.** |

### OQ-340-6 — Coupling to DRG-337

| Option | Notes |
|---|---|
| **A. Sibling split; cite only (Recommended, Proposed)** | D-09 / A8. |
| **B. Merge stories** | Needs owner words+time; not this brief. |
| **C. Implement tiers inside draw-down PRs** | Product scope creep. **NO-GO.** |

### OQ-340-7 — Hybrid / conversion credit distinction

| Option | Notes |
|---|---|
| **A. Keep MigrationCreditPolicy and conversion unused-prepaid distinct from wallet draw-down (Recommended, Proposed)** | A7; cite #880 / DRG-573 only. |
| **B. Seed CreditBalance from hybrid first-period credit** | Reopens R4 commercial shape. **NO-GO** here. |
| **C. Reuse Scenario 3 fixtures as ledger truth** | Proposal-only conversion figures. **NO-GO.** |

### OQ-340-8 — Coupling to #878 / OQ-339-*

| Option | Notes |
|---|---|
| **A. Cite #878; do not adopt OQ-339-* (Recommended, Proposed)** | Wave cite rule. |
| **B. Bundle Done for 339+340** | False coupling. **NO-GO.** |
| **C. Implement draw-down inside ramp apply** | Violates D-09 / A1. **NO-GO.** |

### OQ-340-9 — Coupling to #880 / OQ-341-*

| Option | Notes |
|---|---|
| **A. Cite #880; do not adopt OQ-341-* (Recommended, Proposed)** | Wave cite rule. |
| **B. Treat hybrid credit audit as prepaid ledger** | Conflates A7. **NO-GO.** |
| **C. Open dual-entitlement product writes for credits** | Hold / #880 OQ-341-3 territory. **NO-GO.** |

### OQ-340-10 — Coupling to #803 / OQ2–OQ7

| Option | Notes |
|---|---|
| **A. Cite #803; do not adopt OQ2–OQ7 (Recommended, Proposed)** | Wave cite rule. |
| **B. Treat #803 Recommended rows as Adopted** | Out of scope. **NO-GO.** |
| **C. Hang draw-down off amendment elapsed-lock** | Wrong story. **NO-GO.** |

### OQ-340-11 — Flags

| Option | Notes |
|---|---|
| **A. Keep ramp flag false; invent no credit flag here (Recommended, Proposed)** | A5 / Ramp hold. **Flag NO-GO.** |
| **B. Flip ramp true for credits** | Violates ADR-023 D-06. **NO-GO.** |
| **C. Commit a new credit-drawdown flag default true** | Enablement without REQ. **NO-GO.** |

### OQ-340-12 — Schema / Flyway

| Option | Notes |
|---|---|
| **A. No migration from this brief (Recommended, Proposed)** | Hard schema NO-GO. |
| **B. Ship credit_balance DDL now** | Product. **NO-GO.** |
| **C. Claim CAM V1_16–V1_21 for credits** | Band reserved / contested by other drafts. **NO-GO.** |

### OQ-340-13 — Implementation brief

| Option | Notes |
|---|---|
| **A. NO-GO on product PRs until REQ + blocking OQs close (Recommended, Proposed)** | Docs PR is not authorization. |
| **B. Authorize ledger/event PRs on merge of this file** | Violates hard product NO-GO. |
| **C. "Thin" PoC behind an undocumented flag** | Still product; still NO-GO. |

### OQ-340-14 — Agent prose

| Option | Notes |
|---|---|
| **A. Non-authoritative; REQ wins (Recommended, Proposed)** | Prevents coding from `agents/` alone. |
| **B. Promote agent skill to Accepted REQ** | Requires owner words+time as a REQ act, not this brief's silent adoption. |
| **C. Implement ERP purchase-history draw-down from agent text** | Product without REQ. **NO-GO.** |

### OQ-340-15 — Billing / BC-10 / BC-11

| Option | Notes |
|---|---|
| **A. Keep invoicing / rev-rec out of BC-09-06 unless REQ explicitly scopes them (Recommended, Proposed)** | REQ-price-ramping out of scope. |
| **B. Fold invoicing into this story now** | Scope explosion. **NO-GO.** |
| **C. Emit invoice lines from a docs-authorized PoC** | Product. **NO-GO.** |

---

## Implementation brief gate (explicit)

1. This Decision brief closes **nothing** by itself. Remaining forks are Proposed (`OQ-340-1` .. `OQ-340-15`).
2. CPQ Decision must score the OQ-340-* rows. The owner must fill `Owner decision: ___ (drg, time)` (or explicitly defer a named fork the same way).
3. A dedicated BC-09-06 REQ (OQ-340-1) is a **prerequisite** to estimate, In Progress, or any implementation brief.
4. Until blocking rows close (especially OQ-340-1, OQ-340-3, OQ-340-4, OQ-340-5, OQ-340-11, OQ-340-12, OQ-340-13), further product / schema / flag work is **NO-GO**.
5. Linear status for DRG-340 stays **Backlog** until the owner and Decision say otherwise. This brief does **not** mark In Progress or Done and does **not** change Linear status.

## What this Decision brief does not do

- Does not authorize an implementation brief or product code for DRG-340.
- Does not mark any OQ-340-* fork Adopted.
- Does not write or accept the missing BC-09-06 REQ (only recommends that it be written).
- Does not adopt or close [#878](https://github.com/drgaciw/open-crm-cpq/pull/878) OQ-339-*.
- Does not adopt or close [#880](https://github.com/drgaciw/open-crm-cpq/pull/880) OQ-341-*.
- Does not adopt or close [#803](https://github.com/drgaciw/open-crm-cpq/pull/803) OQ2–OQ7 / Defaults.
- Does not flip `opencrm.pricing.ramp.enabled` or `opencrm.pricing.metrics.acv-version`.
- Does not register `CreditDrawnDown` or any credit topic.
- Does not open or claim CAM Flyway `V1_16`–`V1_21`, or any credit_balance migration.
- Does not reopen ADR-023 Adopted cells or D-09.
- Does not change Linear status (remains **Backlog**).
- Does not close DRG-337, DRG-339, DRG-341, DRG-534, DRG-573, or DRG-614.

## Residual blockers (after this PR)

1. **Owner / CPQ Decision:** score and close OQ-340-* (especially OQ-340-1, OQ-340-3, OQ-340-4, OQ-340-5, OQ-340-11, OQ-340-12, OQ-340-13). Until then, product work is **NO-GO**.
2. **Missing BC-09-06 REQ:** still required before estimate / start.
3. **Ramp hold:** flag stays off; related R3/R4 residuals (#878 / #880) stay Proposed and unadopted.
4. **#803 sibling amendment residuals:** still Proposed; not adopted.
5. **GitNexus index:** not refreshed in this pass; any future code edit needs a live `impact` / `detect_changes` on the editing checkout.

## Sources read this pass

- Linear: DRG-340 (Backlog; AC; Needs own REQ; related DRG-339 / DRG-341; parent DRG-291); DRG-337 status (Backlog).
- GitHub: draft Decision briefs #878, #880, #803 (cite-only).
- ADR-023 (D-09 / D-06 / D-03); REQ-price-ramping (D-09, split-out, out of scope); forge/41 § DRG-337 and DRG-340.
- Code search: no `CreditBalance` / `DrawDownCredit` / `CreditDrawnDown` production types; `EventContractRegistry` has `RampApplied` only among ramp events; `MigrationCreditPolicy` hybrid one-time credit; `SAAS_CREDITS` / usage types as labels / ramp reject fixtures.
- AGENT.md / holds orientation (Jev / dual-read / asset-write / subscriptions UI unchanged; ramp flag treated as ADR-023 D-06 hold).

## Unverified in this pass

- Notion BC-09-06 page body beyond the Linear attachment URL (Notion connector not used this pass).
- Runtime execution of backend unit / integration stages on this checkout.
- AutoJev tools (not connected); no AutoJev verdict is claimed.
- Fresh GitNexus analyze / graph coverage beyond docs-only claim (MCP reported no indexed repositories until analyze).
- Whether the owner will accept a BC-09-06 REQ that differs from the Linear AC wording.

## Verification (this pass)

- [x] Docs-only file under `docs/renewals-amendments/prereq/`
- [x] Residual forks Proposed only under **OQ-340-***; A1–A10 restated, not reopened
- [x] Cite-only #878 / #880 / #803 — open OQs not adopted
- [x] Linear left **Backlog** (orchestrator / agent must not change status)
- [x] Product / schema / flag / event registry / Flyway V1_16–V1_21 = **NO-GO**
- [x] GitNexus unavailable / UNKNOWN — not treated as clean
- [x] Secret scan: no credentials in this file
