# DRG-585 — S7 Contract header backfill: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585/s7-contract-header-backfill-for-existing-subscriptions-no-synthetic) (RA-51 / first-slice **S7**).

**Status:** Proposed residual Decision brief for CPQ Decision scoring. This file **cites** the merged S7 companion [#634](https://github.com/drgaciw/open-crm-cpq/pull/634) / [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) / `V1_8__contract_header_backfill.sql`, the OQ7 owner note [#624](https://github.com/drgaciw/open-crm-cpq/pull/624) / [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md), and the S3–S8 authorization gate [#621](https://github.com/drgaciw/open-crm-cpq/pull/621) / [story-s3-s4-authorization-gate.md](story-s3-s4-authorization-gate.md), **without treating residual delivery forks as Adopted**, and **does not reopen** Adopted **OQ7** (header-only first slice; no synthetic history; one thin contract per existing subscription). It inventories **remaining owner forks as `OQ-585-*`**. **It authorizes no production code path, no schema change, no Flyway migration (including inventing a CAM version that collides with reserved draft slots V1_16–V1_21 on [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no feature flag, no dual-read flip, no Jev enablement, no ramp enable, no S3 / S4 / S6 product start, no S7 `contract_header_backfill_count` / `_apply` / `_compensate` run or hold bypass, no asset-write hold bypass, no environment dry-run against real tenants, and no deploy.** Product / schema / flag work that runs the header apply, widens V1_8, wires AC-S7-3 into production acceptance without a scored tenant contract, or folds DRG-566 history into the header job is **NO-GO** until the owner closes the residual OQ-585-\* forks below (or explicitly defers a named fork with words and a time). Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen closed cells)

| Artifact | Relation to this brief |
|---|---|
| [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) issue body (RA-51 / S7) | Scope: V1_8 / job creates one contract per existing subscription, sets `subscriptions.contract_id`, idempotent; no AmendmentDocument rows; no outbox events; NEW path creates header at creation; full amendment-history backfill stays [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566). AC-S7-1..4; M1–M8 owner-gated manual steps. Hygiene: V1_8 installs functions only; data backfill is **not** applied; merging #634 does **not** close the ticket. **Cite; inventory residuals as OQs.** |
| [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md) / `V1_8__contract_header_backfill.sql` / [#634](https://github.com/drgaciw/open-crm-cpq/pull/634) | Merged companion + functions. Count writes nothing; apply / compensate are **manual owner steps**; no bot runs them. Fill rules, `created_by` markers, and compensate refusal are recorded. **This residual brief does not call, wrap, fold history into, or bypass that hold.** |
| Register **OQ7** in [renewals-amendments-open-questions-register.md](../renewals-amendments-open-questions-register.md) | **ADOPTED, header-only first slice** (2026-10-17): one thin contract per existing subscription; no regrouping or synthetic history. History backfill stays DRG-566. Phased manifest selection is in the delegated record. **Cite; do not reopen.** |
| [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md) / [#624](https://github.com/drgaciw/open-crm-cpq/pull/624) | Owner-decision note that asked for scope + grouping confirmation. **Does not authorize** V1_8 apply or mark DRG-585 Done. Cite; do not reopen OQ7. |
| [story-s3-s4-authorization-gate.md](story-s3-s4-authorization-gate.md) / [#621](https://github.com/drgaciw/open-crm-cpq/pull/621) | S7 row: CAM V1_8; S7 stays blocked until the owner authorizes DRG-585. Gate authorizes **no** implementation. Cite only. |
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § Migration and access | Thin-contract / headers-only; unfiltered V1_8 is **not** a phase selector; S7 count/apply/compensate stay owner steps. Cite; do not reopen. |
| [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md) | F-598-4 / F-598-5 / F-598-6: history outside the phase; null `contract_id` stays on current acceptance path; S7 functions stay in the companion. Cite; do not reopen. |
| Related Wave-2 draft **[#859](https://github.com/drgaciw/open-crm-cpq/pull/859)** (DRG-566 residual Decision brief) | **Cite only.** Inventories **OQ-566-\***. This DRG-585 brief **does not adopt** any OQ-566-\* cell and does not authorize history synthesis, history dry-run, or folding history into `contract_header_backfill_apply`. |
| Related stories (cite only) | [DRG-619](https://linear.app/drgamtd-workspace/issue/DRG-619) NEW-path header writer; [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566) history; [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598) phases; [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) later regroup; [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) / [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) S7-class asset tracks **NO-GO**; [DRG-579](https://linear.app/drgamtd-workspace/issue/DRG-579) S1 Done. |
| CAM Flyway draft slots | [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) reserve **V1_16–V1_21**. Any schema fork from this story stays **Proposed / NO-GO**; this brief invents **no** migration number and does **not** edit landed V1_8 in place. |

## Verification marks

| Mark | Meaning here |
|---|---|
| `verified by reading the issue` | Read from Linear in this pass. |
| `verified by reading docs` | Read from Adopted / Proposed docs cited in Alignment. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed unless a row says so. |
| `unverified` | Not established in this pass. |

GitNexus: this checkout had **no** `.gitnexus/run.cjs` / no usable indexed graph for this pass. Graph impact for this docs-only change is **UNKNOWN**, not an all-clear. Text search / file reads confirm the schema tokens and migration inventory below.

## Adopted OQ7 / S7 companion — restated (do not re-open)

`verified by reading docs` (register OQ7; [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md); `V1_8__contract_header_backfill.sql` header comment; delegated § Migration and access).

| ID | Contract | Source |
|---|---|---|
| **H1** | **One thin contract per existing subscription.** No regrouping in the S7 header job. | OQ7 Adopted; V1_8 companion |
| **H2** | **No synthetic amendment history** and **no retroactive events** in the S7 header job. | OQ7; V1_8; AC-S7-2 |
| **H3** | A later merge (tracked on DRG-554) is the place to regroup. The S7 backfill does not regroup. | OQ7 / V1_8 wording |
| **H4** | History synthesis (ORIGINAL / BACKFILLED documents) stays **DRG-566**, outside the header-only slice. | Register OQ7; issue body |
| **H5** | `contract_header_backfill_count` / `_apply` / `_compensate` remain the S7 surface. Apply is **owner-only**. This brief does **not** authorize calling them, folding history into them, or bypassing that hold. | S7 companion; Cluster G F-598-6 |
| **H6** | Unfiltered V1_8 `contract_header_backfill_apply(p_tenant_id)` is **not** a DRG-598 phase selector. | Delegated § Migration and access |

**Cite; do not reopen.** This residual brief does not re-litigate H1–H6.

## What #634 left open (still residual)

`verified by reading the issue` / `verified by reading docs` / Linear hygiene 2026-10-17.

After [#634](https://github.com/drgaciw/open-crm-cpq/pull/634) merged, DRG-585 remains **In Review** because:

1. V1_8 installs **functions only**. The data backfill is **not** applied anywhere.
2. **AC-S7-1** as written is unmet: no owner GO / M1–M8 sequence has run; eligibility skips soft-deleted rows, inverted dates, and non-UUID `customer_id`.
3. **AC-S7-3** is unmet in production: `ContractHeaderBackfill.ensureHeaderAtCreation` / `contract_header_at_creation` has **no** production caller; `AcceptedQuoteSubscriptionService` NEW path does not set `contract_id` (open item / [DRG-619](https://linear.app/drgamtd-workspace/issue/DRG-619)).
4. Merging #634 explicitly **does not** close DRG-585 (M8).

This brief inventories those residuals as **Proposed OQ-585-\*** — it does not run apply, invent product rows, authorize DRG-619 wiring, adopt OQ-566-\*, or bypass asset-write holds.

## What the tree does today (orientation only)

`verified by reading code` / `verified by reading docs` at the base SHA.

1. **S1 header shape.** `contracts` and nullable `subscriptions.contract_id` exist (`V1_7`). OQ3 thin-header contract is confirmed.
2. **S7 functions on main.** `V1_8__contract_header_backfill.sql` defines `contract_header_backfill_count`, `contract_header_backfill_apply`, `contract_header_backfill_compensate`, and `contract_header_at_creation`. Flyway installs them and does **not** execute the data backfill.
3. **Java surface.** `ContractHeaderBackfill` wraps count / apply / compensate / `ensureHeaderAtCreation`. Tenant is required; the service invents none. `AcceptedQuoteSubscriptionService` does **not** call `ensureHeaderAtCreation` (`verified by reading code` — no match in that class).
4. **Eligibility (companion).** Eligible rows: `is_deleted = false`, `contract_id` null, `end_date >= start_date`, `customer_id` a canonical UUID. Soft-deleted / non-UUID / inverted dates are skipped. Non-UUID ids are not hashed. `created_by = V1_8_HEADER_BACKFILL` on backfill; `S7_HEADER_AT_CREATION` on creation-time headers.
5. **CAM migrations present through V1_15** (including V1_8 header-backfill functions, V1_12 apply ledger, V1_13/V1_14 assets, V1_15 replay evidence). **No** CAM `V1_16`–`V1_21` files in this tree; those numbers stay reserved on draft PRs #805–#812.
6. **Asset-write hold** remains active ([asset-write-hold.md](../../runbooks/asset-write-hold.md)). Acceptance creates subscriptions / rate periods without assets while the hold is on. DRG-657 / DRG-660 stay S7-class **NO-GO** tracks.

## Explicit hold section (S7 apply + asset-write) — NO-GO

This brief **keeps** the following holds. Closing or merging this documentation PR does **not** lift them.

| Hold | What stays forbidden | Cite |
|---|---|---|
| **S7 apply / compensate** | No bot or agent runs `contract_header_backfill_count`, `_apply`, or `_compensate` against any real environment. No owner GO is implied by this file. M1–M8 remain unmet until the owner records them in words. | S7 companion; issue M1–M8; H5 |
| **S7 hold bypass** | No folding DRG-566 history, DRG-598 phase writers, or asset lineage into `contract_header_backfill_apply`. No “run S7 to close DRG-585 from docs alone.” | H2/H4/H5; #859 cite-only |
| **Asset-write hold** | No enabling `opencrm.assets.legacy-acceptance-write-enabled` (or successor) from this story. No installed-base / lineage backfill. | [asset-write-hold.md](../../runbooks/asset-write-hold.md); DRG-657; DRG-660 |
| **CAM V1_16–V1_21** | Reserved. This brief invents no migration and does not edit landed V1_7 / V1_8 in place. | #805–#812 |
| **Flags / dual-read / Jev / ramp / S3–S6** | No feature-flag flip; no dual-read; no `opencrm.jev.*`; no ramp enable; no S3/S4/S6 product start from this brief. | AGENT.md; OQ11 DEFAULT PROPOSED |

## Remaining owner forks (Proposed OQ-585-\*)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Do not use these rows to reopen H1–H6 or Adopted OQ7.

| ID | Question | Source residual | Recommended (Proposed) | Reject / avoid | Blocks |
|---|---|---|---|---|---|
| **OQ-585-1** | When may a real-env S7 apply run? | Issue M1–M8; companion owner sequence | **Only** after named env/tenant, backup+restore plan, green `ContractHeaderBackfillIntegrationTest` evidence on `main`, dry-run counts attached, **explicit written owner GO** (words, time, env, tenant), before/after reconcile, second-apply = 0, compensate rehearsal. **No bot apply.** | Treat merge of #634 / this brief / a PR approval as GO; unattended apply | S7 companion; M1–M8 |
| **OQ-585-2** | How do non-UUID `customer_id` rows get a header (or stay skipped)? | AC-S7-1 gap; companion eligibility; S1 varchar→uuid note | Keep **skip** until a separate scored mapping story. **No** hash; **no** fabricated uuid. | Hash free-text; cast invalid varchar; widen V1_8 in place | S1 companion; V1_8 eligibility |
| **OQ-585-3** | What does the tenant argument mean on count/apply? | Issue hygiene: tenant labels rather than filters | Require an **authoritative tenant** on every call; first call still claims every eligible row under that label until a **later** selector/wrapper (DRG-598) exists. **Do not** invent a default tenant. | Fabricate tenant; claim unfiltered apply is a “phase” | H6; ContractHeaderBackfill; delegated record |
| **OQ-585-4** | How is AC-S7-3 (header at creation) closed? | Companion open item; DRG-619 | Track on **DRG-619** (tenant on accepted event / writer). This brief **does not** wire `AcceptedQuoteSubscriptionService`. | Wire NEW path without a scored tenant contract; treat IT-only coverage as production Done | DRG-619; companion AC-S7-3 |
| **OQ-585-5** | Does AC-S7-1 “N subscriptions” mean every stored row or the eligible set? | Companion eligibility; issue hygiene | **N = eligible set** (not soft-deleted; valid dates; canonical UUID customer). Document skipped classes in the owner dry-run report (M3). | Redefine AC-S7-1 to force headers onto ineligible rows without a scored change | Companion fill rules |
| **OQ-585-6** | How does S7 relate to DRG-598 phases? | Delegated F-598 / H6 | Phasing is **selection/policy only**. Unfiltered V1_8 apply is **not** a manifest. A separately delivered selector/wrapper is required before any phased run. This brief authorizes **neither**. | Call tenant-wide apply and call it a phase | DRG-598; delegated § Migration |
| **OQ-585-7** | How does S7 relate to DRG-566 history (#859)? | OQ7 H4; #859 | History stays on DRG-566. **Cite** [#859](https://github.com/drgaciw/open-crm-cpq/pull/859) **without adopting OQ-566-\***. **Do not** fold history into header apply. | Adopt OQ-566-\* here; synthesize ORIGINAL/BACKFILLED inside V1_8 | #859; H2/H4 |
| **OQ-585-8** | Any further CAM schema / migration for S7 residuals? | Reserved slots; landed V1_8 | **No file in this brief.** Later additive only after forks close; **never** consume reserved **V1_16–V1_21**; never edit landed V1_8 in place. | Use V1_16–V1_21; edit V1_8 in place; Flyway-at-deploy data write | #805–#812 |
| **OQ-585-9** | Relation to asset-write / DRG-657 / DRG-660? | Asset hold; S7-class NO-GO tracks | Keep **asset-write hold**. DRG-657 / DRG-660 remain separate S7-class **NO-GO** tracks. Header apply does **not** create assets or lift the hold. | Enable legacy acceptance asset write; merge asset lineage into S7 apply | asset-write-hold; DRG-657; DRG-660 |
| **OQ-585-IMPL** | Implementation / apply / flag / asset authorization | — | Separate owner act **after** OQ-585-1..9 (or a deferred subset) close. **No** S7 hold bypass; **no** asset-write hold bypass; **no** reserved Flyway slots; **no** flag / dual-read / Jev / ramp; **no** environment dry-run or apply from this brief; **no** marking Linear Done. | Piggy-back V1_16–V1_21; run apply now; wire AC-S7-3 without tenant contract; enable asset-write | Owner release; AGENT.md holds; M8 |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-585-1 — Owner execution GO (M1–M8)

| Option | What the owner would be choosing |
|---|---|
| **A. Full M1–M8 gate before any real apply (Recommended, Proposed)** | Named env/tenant; backup+restore; green IT evidence; dry-run counts; written GO; reconcile; idempotent second apply; compensate rehearsal. No bot. |
| **B. Merge / PR approval as GO** | Collapses M8 (“merging #634 does not close DRG-585”) and bypasses M4. |
| **C. Unattended / CI apply** | Violates companion “no bot runs it.” |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-2 — Non-UUID customer_id

| Option | What the owner would be choosing |
|---|---|
| **A. Keep skip; separate mapping story later (Recommended, Proposed)** | Preserves V1_8 eligibility; avoids inventing account ids. |
| **B. Hash free-text into uuid** | Invents `customer_account_id`; contradicts companion “not hashed.” |
| **C. Force-cast / widen V1_8 in place** | Edits landed migration; reserved-slot risk if a new file is invented carelessly. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-3 — Tenant argument semantics

| Option | What the owner would be choosing |
|---|---|
| **A. Authoritative tenant required; unfiltered claim until a later selector (Recommended, Proposed)** | Matches `ContractHeaderBackfill` / SQL today; defers subsetting to DRG-598 wrapper. |
| **B. Fabricate or default a tenant** | Forbidden by companion and Java `TENANT_REQUIRED`. |
| **C. Treat today’s apply as a phased manifest** | Contradicts H6 / delegated record. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-4 — AC-S7-3 / DRG-619

| Option | What the owner would be choosing |
|---|---|
| **A. Track wiring on DRG-619; no production call from this brief (Recommended, Proposed)** | Keeps tenant contract explicit; IT coverage ≠ production Done. |
| **B. Wire `AcceptedQuoteSubscriptionService` now without tenant on the event** | Invents tenant at acceptance time. |
| **C. Mark AC-S7-3 Done from integration tests alone** | Leaves NEW path without `contract_id` in production. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-5 — Eligible set vs “N subscriptions”

| Option | What the owner would be choosing |
|---|---|
| **A. N = eligible set; report skips in M3 (Recommended, Proposed)** | Aligns AC-S7-1 with companion predicates. |
| **B. Expand eligibility without a scored change** | Silent product change to V1_8 behavior. |
| **C. Require headers for soft-deleted / inverted / non-UUID rows** | Needs a new scored contract + likely schema/mapping. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-6 — DRG-598 phasing

| Option | What the owner would be choosing |
|---|---|
| **A. Policy/selector separate; unfiltered apply is not a phase (Recommended, Proposed)** | Preserves H6; authorizes no wrapper and no run. |
| **B. One tenant-wide apply equals “phase 1”** | Collapses manifest + selector requirements. |
| **C. Change V1_8 SQL to take a manifest list in place** | Edits landed V1_8. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-7 — DRG-566 / #859 boundary

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #859; do not adopt OQ-566-\*; keep history out of S7 (Recommended, Proposed)** | Preserves H2/H4; avoids dual ownership of history forks. |
| **B. Adopt selected OQ-566-\* cells here** | Out of lane; risks contradictory Adopted cells. |
| **C. Fold history into `contract_header_backfill_apply`** | Violates OQ7 and the companion. **NO-GO** without explicit owner supersede of OQ7. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-8 — Future migration / reserved slots

| Option | What the owner would be choosing |
|---|---|
| **A. No file here; later additive only; never V1_16–V1_21; never edit V1_8 in place (Recommended, Proposed)** | Hard NO-GO on reserved slots and in-place edits. |
| **B. Consume a reserved V1_16–V1_21 slot** | Violates #805–#812 reservation. |
| **C. Edit V1_8 in place / write data from Flyway at deploy** | Removes owner gate; rewrites landed contract. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-9 — Asset-write / DRG-657 / DRG-660

| Option | What the owner would be choosing |
|---|---|
| **A. Keep asset-write hold; leave 657/660 as separate NO-GO tracks (Recommended, Proposed)** | Header job creates no assets; hold stays. |
| **B. Enable legacy acceptance asset write with header apply** | Bypasses asset-write hold. **NO-GO.** |
| **C. Treat DRG-660 lineage backfill as part of S7 apply** | Merges tracking-only NO-GO into header job. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-585-IMPL — Implementation / apply gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate owner act after residual OQx close; no S7 bypass; no asset-write; no reserved slots; no flags; Linear stays In Review until M8 reality (Recommended, Proposed)** | Hard product / schema / flag / apply **NO-GO** until then. |
| **B. Use CAM V1_16–V1_21 now** | Violates #805–#812. |
| **C. Run S7 / wire AC-S7-3 blindly / enable asset-write / flip flags now** | Explicitly out of scope — **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

## Explicit product / schema / flag NO-GO

Until OQ-585-1..9 (or an owner-deferred subset) and OQ-585-IMPL close with words and a time:

1. **No** Java/TypeScript product change that runs header backfill apply/compensate, or that wires AC-S7-3 into production acceptance without a scored tenant contract (DRG-619 remains the tracking issue).
2. **No** Flyway / schema change. **CAM V1_16–V1_21 reserved (#805–#812).** No edit in place of landed V1_7 / V1_8. No Flyway-at-deploy data write.
3. **No** inventing tenant ids, customer account uuids, or eligibility expansions to force AC-S7-1 onto skipped rows.
4. **No** feature-flag flip for S6 / OQ11, dual-read, Jev (`opencrm.jev.*`), ramp, or asset legacy acceptance write.
5. **No** running S7 `contract_header_backfill_count` / `_apply` / `_compensate` against real data, and **no S7 hold bypass**. History must not be folded into the header apply.
6. **No** asset-write hold bypass, installed-base migration, or dual-write from this story ([DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657) / [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660) remain S7-class NO-GO).
7. **No** environment dry-run, tenant count against real data, or header apply from this brief alone.
8. **No** starting S3 / DRG-581, S4 apply enablement, or S6 topic registration from this brief.
9. **No** adopting open OQs from [#859](https://github.com/drgaciw/open-crm-cpq/pull/859) (OQ-566-\*) by side effect.
10. **No** marking Linear DRG-585 Done from this brief alone (M8).

## Out of scope (explicit)

- Reopening Adopted OQ7 (H1–H6) or the S7 companion’s owner-only apply rule.
- Reopening OQ5 Option A, OQ6 revision rules, or delegated F-598-\* cells.
- Adopting or scoring OQ-566-\* (cite #859 only).
- Implementing DRG-619 tenant-on-event / NEW-path writer.
- Delivering a DRG-598 manifest selector/wrapper.
- Merge authority for this file into `main` beyond draft PR review.

## What accepting this brief would unblock

Documentation clarity only: implementation / execution stories may cite **OQ-585-1..9** and **OQ-585-IMPL** as the residual owner menu without re-litigating Adopted OQ7 or treating #634 as Done. Accepting / merging this file **does not** authorize a header apply, does not wire AC-S7-3, does not bypass asset-write holds, does not consume reserved Flyway slots, and does not mark DRG-585 Done.

## Unverified (carried forward)

- Whether any environment has called `contract_header_backfill_apply`.
- Row counts of eligible vs skipped subscriptions (no query run this pass).
- Whether quote-service free-text customer ids dominate the installed base.
- CI run-id / sha pairing cited on the issue for #634 (`unverified` per issue hygiene).
- DRG-554’s later effect on `subscriptions.contract_id` after regroup.

## References

- Linear: [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585/s7-contract-header-backfill-for-existing-subscriptions-no-synthetic), [DRG-566](https://linear.app/drgamtd-workspace/issue/DRG-566), [DRG-619](https://linear.app/drgamtd-workspace/issue/DRG-619), [DRG-598](https://linear.app/drgamtd-workspace/issue/DRG-598), [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554), [DRG-657](https://linear.app/drgamtd-workspace/issue/DRG-657), [DRG-660](https://linear.app/drgamtd-workspace/issue/DRG-660), [DRG-579](https://linear.app/drgamtd-workspace/issue/DRG-579).
- GitHub (landed S7 / OQ7 / gate): [#634](https://github.com/drgaciw/open-crm-cpq/pull/634), [#624](https://github.com/drgaciw/open-crm-cpq/pull/624), [#621](https://github.com/drgaciw/open-crm-cpq/pull/621).
- GitHub (cite-only Wave-2 draft): [#859](https://github.com/drgaciw/open-crm-cpq/pull/859) (DRG-566 residual; **do not adopt OQ-566-\***).
- Docs: [s7-v1-8-header-backfill.md](../s7-v1-8-header-backfill.md); [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md); [story-s3-s4-authorization-gate.md](story-s3-s4-authorization-gate.md); register OQ7; [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md); [cluster-g-visibility-queues-migration-decision-frame.md](cluster-g-visibility-queues-migration-decision-frame.md); [asset-write-hold.md](../../runbooks/asset-write-hold.md).
- Schema (read-only): `V1_7__contract_documents.sql`, `V1_8__contract_header_backfill.sql` (inventory through V1_15).
- Code (read-only): `ContractHeaderBackfill.java`; `AcceptedQuoteSubscriptionService.java` (no header-at-creation call).
- Holds: [AGENT.md](../../../AGENT.md); dual-read / Jev / ramp / S4; **S7 apply owner-only**; **asset-write**; CAM Flyway V1_16–V1_21 reservation (#805–#812).
