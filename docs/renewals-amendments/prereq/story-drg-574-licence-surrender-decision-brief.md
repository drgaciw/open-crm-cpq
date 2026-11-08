# DRG-574 — Perpetual licence surrender / entitlement revocation in conversions: residual Decision brief

*open-crm-cpq (drgaciw/open-crm-cpq). Documentation only. New file. Date: 2026-11-06.*

**Story:** [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574/perpetual-licence-surrender-and-entitlement-revocation-in-conversions) (RA-40).

**Status:** Proposed residual Decision brief. This file **restates Adopted Cluster B forks for DRG-574** (delegated 2026-10-17: TERMINATE-line disposition, `Surrendered` after confirmed revocation evidence, per-line `entitlementEndDate`, explicit dual-use, fulfilment REVOKE after entitlement end, commercial rights end at entitlement end while operational state waits for evidence) without reopening them, **cites** Cluster B, [`specs/conversions.md`](specs/conversions.md), fulfilment / entitlement siblings, and related Wave-2 drafts, and surfaces **remaining owner forks as Proposed OQ-574-\***. **It authorizes no production code path, no schema change, no Flyway migration (including CAM slots V1_16–V1_21 reserved by [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812)), no inventing of a `SURRENDER` action token, no inventing of landed `entitlementEndDate` / `convertedFromLineIds` / `Surrendered` columns as shipped product, no feature flag, no Kafka consumer registration, no S4 apply enablement, no dual-read / Jev / ramp flip, no asset-write hold bypass, and no deploy.** Product / schema / flag work that persists surrender metadata, schedules REVOKE, or writes commitment projections is **NO-GO** until the owner closes the residual OQ-574-\* forks below (or explicitly defers a named fork with words and a time).

**Scope boundary:** This brief is **perpetual licence surrender + entitlement revocation in conversion flows** only. Conversion **pricing** residuals stay on [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) ([#848](https://github.com/drgaciw/open-crm-cpq/pull/848)) — **cite only**. Perpetual + maintenance **linkage** stays on [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) ([#849](https://github.com/drgaciw/open-crm-cpq/pull/849)) — **cite only**. Restructure intent / apply units stay on [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) (Cluster B; no Wave-2 residual brief required to be present) — **cite only**. Do **not** adopt open OQs from those Wave-2 drafts.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Linear status when read:** In Review (not Done). This brief leaves that status unchanged.

## Alignment (cite; do not reopen Adopted DRG-574 dispositions)

| Artifact | Relation to this brief |
|---|---|
| [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md) § One-time charges and conversion + § Fulfilment + Cluster B DRG-574 index rows | **Adopted baseline.** Surrender is a **business disposition** on an existing **`TERMINATE`** line action plus entitlement-end metadata — **not** a new action token contrary to Cluster E. `Surrendered` is a terminal **commitment projection** only after **confirmed** licence-revocation evidence. Commercial rights end at `entitlementEndDate`; operational PendingTerminate / failed revocation never extends those rights. Scheduled **REVOKE** uses `notBefore=entitlementEndDate` and a confirmed replacement dependency. Cite; **do not reopen**. |
| [cluster-b-onprem-to-saas-and-maintenance-decision-frame.md](cluster-b-onprem-to-saas-and-maintenance-decision-frame.md) § DRG-574 ([#652](https://github.com/drgaciw/open-crm-cpq/pull/652), merged) | Historical options / rationale for the six DRG-574 forks; dispositions already indexed (with the action / status / window replacements above). Do not re-litigate Adopted cells. Illustrative Scenario 3 dual-use through 2026-12-31 is **not** a silent default length |
| [`specs/conversions.md`](specs/conversions.md) | **Proposal only; not accepted.** Still names action code `Surrender`, status `Surrendered`, `entitlementEndDate`, `convertedFromLineIds`, and Scenario 3 ACs 9–10. Authorizes no code. **Adopted disposition supersedes** the proposal’s `Surrender` **action token** (use `TERMINATE` + disposition metadata). This brief does **not** accept the file wholesale |
| [`specs/lifecycle-intents.md`](specs/lifecycle-intents.md) | **Proposal** lists `Surrender` in vocabulary. Unaccepted. Do not treat as wire enum |
| [cluster-f-fulfilment-decision-frame.md](cluster-f-fulfilment-decision-frame.md) + [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) / [#817](https://github.com/drgaciw/open-crm-cpq/pull/817) | Fulfilment / commitment-state home for REVOKE consumption and `Surrendered` projection. Cite Wave-2 entitlement residual; **do not adopt** its open OQs |
| [../cpq-pricing-policy-sanity-check.md](../cpq-pricing-policy-sanity-check.md) Scenario 3 / G8 | Illustrative dual-use window and surrender narrative; example dates only |

**In-flight alignment (cite-only — do not adopt their open OQs):**

| Ticket / PR | What this brief must not contradict / adopt |
|---|---|
| [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) | Restructure intent / `priorAgreementId` / three named apply units. Cite Cluster B; **do not invent** OQ-559-\* closures or start multi-subscription apply |
| [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) / [#848](https://github.com/drgaciw/open-crm-cpq/pull/848) | Conversion pricing residuals (**OQ-573-\***). Cite only; do not score SpendParity / credit-stack forks here |
| [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) / [#849](https://github.com/drgaciw/open-crm-cpq/pull/849) | Linkage residuals (**OQ-556-\***). Cite only; the perpetual line being surrendered is the one linkage names |
| [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558) / [#817](https://github.com/drgaciw/open-crm-cpq/pull/817) | Entitlement / commitment-state residual. Cite; do not adopt open OQs or invent licence-server adapters here |
| [DRG-341](https://linear.app/drgamtd-workspace/issue/DRG-341) | Landed hybrid dual-entitlement **window** (1–12 months) is **not** the big-bang surrender dual-use rule |
| [DRG-563](https://linear.app/drgamtd-workspace/issue/DRG-563) / [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) | Apply ledger / partial-apply. Create-before-terminate ordering is named; S4 stays unauthorized |
| Dual-read / NgRx / Jev / ramp / S4 / asset-write | `AGENT.md` holds — untouched |

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. `opencrm.pricing.ramp.enabled` stays default off. An implementation brief for DRG-574 (or any product change that adds a `SURRENDER` action token, persists `entitlementEndDate` / `convertedFromLineIds`, schedules REVOKE, or writes `Surrendered`) is **blocked** until CPQ Decision scores the open forks below (or the owner explicitly defers a named fork with words and a time).

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (delegated)` | Already selected in the 2026-10-17 delegated record. Restated for orientation; **not** re-opened. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read in the tree at the base SHA. Not executed. |
| `verified by GitNexus` | Impact/query after `npx gitnexus@latest analyze --index-only` on this checkout. Index process discovery reported truncation (`truncated: true`, entry-point candidates dropped / walks cut by budget) — **not a clean completeness claim**. |

## Problem (RA-40)

A perpetual licence (`isPermanent` / `ONPREM_PERPETUAL`) does **not** end by the ordinary subscription senses of Terminated or Expired. A conversion or trade-in requires the customer to **surrender** that licence, usually after a **dual-use window**, and then requires **keys revoked** through fulfilment.

Illustrative Scenario 3 (sanity check; **example dates only**): maintenance ends 2026-06-30, SaaS starts 2026-07-01, on-prem dual use runs to 2026-12-31, then perpetual cores are surrendered and keys revoked. The change carries an action date and several trigger dates; the perpetual line needs its **own entitlement end**, separate from the maintenance commercial end.

What the meta-model historically lacked (ticket / Cluster B / conversions proposal): a settled surrender **disposition** encoding, a terminal **Surrendered** projection gated on evidence, a per-line `entitlementEndDate`, and a REVOKE request after the dual-use window. Linear’s suggested next step still names action code `Surrender` — that **token** suggestion is **superseded** by the delegated disposition (TERMINATE + metadata). Status name `Surrendered` remains the Adopted projection name once evidence confirms revocation.

What already exists on `main` (`read` / `verified by reading code`):

- CAM `ActionCode` includes `TERMINATE` and does **not** include `SURRENDER` (`ActionCode.java`).
- Catalog / pricing product types include `ONPREM_PERPETUAL`. That enum value is not a commitment status and not a surrender disposition.
- Landed hybrid dual-entitlement window: `MigrationWindowProperties` defaults `opencrm.pricing.ramp.migration-window-months` to 12 (reject outside 1–12); `HybridMigrationPolicy` requires the declared window to equal contiguous positive-quantity overlap. That window is **not** a surrender date on a perpetual line for a big-bang conversion.
- Tree search for production `Surrender`, `Surrendered`, `entitlementEndDate`, `convertedFromLineIds` under `backend/` Java/SQL returns **no matches** (same observation as Cluster B; re-checked this pass).
- Cluster B DRG-574 forks are **Adopted** (with the replacements in the delegated index). `specs/conversions.md` and lifecycle-intents remain **proposals**.

What is still missing for product:

1. Residual **delivery** forks the delegated baseline did **not** close (how TERMINATE encodes surrender vs ordinary terminate; where `entitlementEndDate` / disposition metadata live; `convertedFromLineIds` snapshot home; dual-use validation vs hybrid window; REVOKE schedule coupling to DRG-558; first-slice exclusion; Flyway outside reserved CAM band).
2. Explicit **NO-GO** on inventing a `SURRENDER` action token, inventing schema in reserved CAM **V1_16–V1_21**, or treating conversions.md ACs 9–10 as Adopted oracles.
3. Cite-only boundaries with **DRG-559 / 573 / 556 Wave-2 drafts** so a later story does not silently land Restructure apply, conversion credits, or linkage columns under this ticket.

Without that split, a later PR could re-litigate Adopted Cluster B cells, add a `SURRENDER` enum contrary to Cluster E, treat `specs/conversions.md` as authorized schema, claim CAM **V1_16–V1_21**, call a licence server from quote-service, or claim this brief authorized S4 / asset-write / dual-read / Jev / ramp.

Linear DRG-574 (`Linear read` 2026-11-06): Medium; **In Review**; related DRG-558, DRG-559, DRG-341. Attachments: Cluster B draft [#652](https://github.com/drgaciw/open-crm-cpq/pull/652), Notion RA-40 / sanity check. Wave-2 refill comment records docs-only residual Decision brief scope with product/schema/flag **NO-GO**.

## Market research — cited as forks only

Issue research links are orientation only. They are **not** selected options and do not override the delegated record.

| Vendor pattern | Observed options (research) | How this brief treats them |
|---|---|---|
| Thales Sentinel lifecycle / revoke | Licence revoke after commercial change | Orientation for fulfilment REVOKE after dual-use — not a Thales field map |
| Thales EMS update modes fail for perpetual models | Permanent entitlements need distinct end handling | Orientation for separate entitlement end — not EMS schema |
| Revenera permanent lines / fulfilment ops | Permanent lines not “expired” like term; fulfilment ops revoke | Orientation for disposition vs Terminated/Expired — not a FlexNet wire format |

## Already Adopted (do not re-decide)

Restated from the delegated record § One-time charges and conversion, § Fulfilment, and Cluster B DRG-574 index (`read`). This Decision brief does **not** reopen these rows.

| ID | Contract | Source |
|---|---|---|
| A1 | **Surrender is a business disposition** encoded by existing **`TERMINATE`** line action **plus entitlement-end metadata**. **No new `SURRENDER` action token** contrary to Cluster E | Delegated § One-time charges; Cluster B “Action on the perpetual line” replacement |
| A2 | **`Surrendered` is a terminal commitment projection** written by CAM **only after confirmed** licence-revocation **adapter evidence**. Request intent is separate from confirmed state | Delegated § Fulfilment; Cluster B “Commitment status” replacement |
| A3 | **`entitlementEndDate` is its own per-line field** on the perpetual line, separate from the maintenance commercial end. Action date, contract effective, service activation, and customer acceptance stay change-level dates | Cluster B “Entitlement end” baseline; delegated “Adopt baseline default” |
| A4 | **Dual-use length requires an explicit `entitlementEndDate`**; a missing date is refused under this option. Scenario 3’s six months and the hybrid 1–12 month cap are **not** silent fill-ins for every conversion | Cluster B “Dual-use length” baseline |
| A5 | **Revocation is a fulfilment request recorded after commercial unit commit**, scheduled with **`notBefore = entitlementEndDate`** and a **confirmed replacement dependency**. Dispatch only when both hold. Do **not** call a licence server from quote-service or pricing-service. S6 event bus for this request is **not** authorized here | Cluster B “When revocation is requested” + delegated § Fulfilment |
| A6 | **Commercial rights end at `entitlementEndDate`**. Operational PendingTerminate / failed revocation **never extends** those rights. Status becomes **`Surrendered` only when revocation evidence confirms**; SaaS start leaves the perpetual line commercially entitled until entitlement end | Cluster B “Status during the window” replacement + § Fulfilment |
| A7 | One **Restructure** + successor agreement with **`priorAgreementId`** remains the commercial shape that **carries** the surrender line (DRG-559 disposition). First-slice **conversion remains excluded** until a named execution authorization after verified pricing/audit contracts | Delegated § One-time charges |
| A8 | Create-before-terminate ordering of commercial units; operational REVOKE is an **additional** fulfilment unit with evidence. `REVOKE` / `REGENERATE` remain non-compensatable per Accepted reconcile classification (does not accept ADR-027 wholesale) | Delegated § Fulfilment; Cluster F / reconcile |
| A9 | Holds: no product code / Flyway / dual-read / Jev / ramp / S4 / asset-write from this brief; CAM **V1_16–V1_21** reserved (#805–#812); S3/S6 remain separately authorized | AGENT.md; task holds |

## What the tree does today (surrender / revocation)

`verified by reading code` at the base SHA.

1. `ActionCode` stores `TERMINATE` among other tokens; first-slice apply behavior comments name Add/Update/Remove as primary; **no `SURRENDER` enum value**.
2. No production Java/SQL fields named `entitlementEndDate`, `convertedFromLineIds`, `Surrendered`, or surrender-disposition metadata under `backend/`.
3. Hybrid dual-use is `MigrationWindowProperties` + `HybridMigrationPolicy` overlap equality — not a perpetual-line surrender date.
4. CAM Flyway tip on this `main` is through `V1_15__document_apply_replay_evidence.sql`. **V1_16–V1_21 remain reserved** for other stories (#805–#812); this brief must not consume them.
5. [`specs/conversions.md`](specs/conversions.md) AC-9/AC-10 and Cluster B § DRG-574 remain documentation. Proposal text that still says action code `Surrender` is **historical relative to A1**.

### GitNexus (this checkout)

Repository: **open-crm-cpq** (`/agent/repos/open-crm-cpq`). Worktree: same. Index: `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`, built this pass with `npx gitnexus@latest analyze --index-only`. Process discovery reported **`truncated: true`** (entry-point candidates dropped / walks cut by budget) — treat graph completeness as **incomplete**, not clean.

| Symbol | Upstream risk | Notes |
|---|---|---|
| `ActionCode` | **UNKNOWN** (0 callers resolved) | Enum; absence of edges is **not** “unused.” Text search shows apply / asset paths switch on `TERMINATE`. Docs-only this pass — **do not edit** |
| `DocumentApplyOrchestrator` | **LOW** (2 direct) | Apply path that would eventually schedule fulfilment units. **Do not edit** in this brief |
| `HybridMigrationPolicy` | **HIGH** (3 direct; execute / simulate / simulateBulk) | **WARN:** any future code change is HIGH risk; this docs brief must not touch it. Dual-use for surrender must not silently reuse hybrid window rules without an owner fork |
| `MigrationWindowProperties` | **LOW** (1 direct) | Hybrid window config only |
| Concept query (surrender / entitlement revocation) | Docs + off-topic AI `PolicyExecutor.revokeAndRollback` | No landed `Surrender` / `entitlementEndDate` symbol — consistent with “not landed.” Text search remains required |

**Docs-only change impact:** this branch adds one markdown file under `docs/renewals-amendments/prereq/`. No production symbol is edited. A later implementation must re-run GitNexus `impact` before changing `ActionCode`, apply orchestrator, asset terminate paths, hybrid migration window code, or any new entitlement / REVOKE store. Pre-commit `detect_changes` on this docs-only diff is reported in the PR / Linear comment; `partial` / `truncated` / `UNKNOWN` is **not** a clean check.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-574-\***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No new Adopted cells. Rows refine **delivery** against A1–A9; they do **not** reopen those contracts. **Do not adopt** open OQs from DRG-559 / DRG-573 [#848] / DRG-556 [#849] / DRG-558 [#817] by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| **OQ-574-1** | **How `TERMINATE` encodes surrender disposition** (delivery of A1) | Keep wire **`ActionCode.TERMINATE`**. Add **disposition metadata** (proposal name: `terminationDisposition` / `surrenderIntent`) required when the line is a perpetual / `isPermanent` conversion surrender; ordinary maintenance terminate omits it. **No `SURRENDER` enum** | Add `SURRENDER` to `ActionCode` (contradicts A1 / Cluster E); reuse `REMOVE` or `CANCEL` without disposition; infer surrender only from presence of `entitlementEndDate` with no explicit disposition | Yes — any create/submit that claims surrender |
| **OQ-574-2** | **Persistence home for `entitlementEndDate` + disposition** (A3) | Snapshot on the **priced commercial / change line** owned with the commercial document (proposal: CommitmentLine / amendment line projection). **No migration in this brief**; additive shape only after forks close and **never** in reserved V1_16–V1_21 | Change-header-only date (collapses four per-line dates); pricing-service-only field; invent parallel stores in quote-service and CAM | Yes — dual-use / entitlement-end ACs |
| **OQ-574-3** | **`convertedFromLineIds` home** | Store on the **arriving SaaS / target line** as a snapshot list of source perpetual (and related) line ids at price/accept time; empty when not a conversion | Store only on fulfilment REVOKE payload; omit entirely; mutate source lines to point forward only | Soft for docs; hard for conversion lineage AC |
| **OQ-574-4** | **Dual-use validation vs hybrid window** (A4 vs DRG-341) | Affirm A4: **explicit** `entitlementEndDate` required for surrender; refuse missing. Do **not** auto-fill from Scenario 3 six months or from `MigrationWindowProperties` 1–12. Hybrid overlap equality stays on hybrid pairs only | Silent six months; apply 1–12 hybrid cap to every big-bang conversion; forbid dual use on big-bang | Yes — Scenario 3 dual-use AC |
| **OQ-574-5** | **When commercial rights end vs operational `Surrendered`** (A6) | At `entitlementEndDate`, commercial rights end even if REVOKE is PendingTerminate / blocked. Projection becomes **`Surrendered` only on confirmed revoke evidence**. Do not invent an intermediate commercial “pending-end” that extends rights | Move perpetual to pending-end when SaaS starts (extends or confuses commercial rights); set `Surrendered` at entitlement end without evidence (contradicts A2) | Yes — dual-use + state ACs |
| **OQ-574-6** | **REVOKE schedule / fulfilment coupling** (A5 / A8) | CAM records durable REVOKE request **after** successful commercial unit apply, with `notBefore=entitlementEndDate` + replacement dependency. Worker / adapter / evidence owned with **DRG-558 / Cluster F**. Cite [#817](https://github.com/drgaciw/open-crm-cpq/pull/817); **do not adopt** open 558 OQs; **no** licence-server call from quote/pricing | Request revoke at pricing time; revoke inside CPQ without adapter evidence; dispatch REVOKE in the same step as SaaS PROVISION without notBefore | Yes — revocation AC / fulfilment |
| **OQ-574-7** | **Maintenance terminate vs perpetual surrender in one Restructure** | Same change may carry maintenance **`TERMINATE` (commercial end)** and perpetual **`TERMINATE` + surrender disposition + `entitlementEndDate`**. Dates may differ. Cite DRG-559; do not invent apply-ledger product here | Force one shared end date; use `SURRENDER` token on perpetual only; terminate perpetual without disposition | Soft until Restructure execution; hard for Scenario 3 line plan |
| **OQ-574-8** | **Boundary with DRG-573 / 556 / 559 Wave-2 residuals** | **Cite only** [#848](https://github.com/drgaciw/open-crm-cpq/pull/848), [#849](https://github.com/drgaciw/open-crm-cpq/pull/849), Cluster B DRG-559. Closing OQ-574-\* does **not** close OQ-573-\* / OQ-556-\* or authorize conversion price / linkage columns / Restructure apply | Land SpendParity or `relatedPerpetualLineId` under DRG-574; start multi-subscription apply here | Yes — if sibling product ships under 574 |
| **OQ-574-9** | **Accept `specs/conversions.md` surrender sections?** | Keep **Proposal**. Score delivery via OQ-574-\*. Treat proposal `Surrender` **action** wording as superseded by A1; keep `Surrendered` / `entitlementEndDate` / `convertedFromLineIds` as proposal vocabulary until implementation brief | Accept conversions.md AC-9/AC-10 wholesale as ADR; treat Scenario 3 dates as Adopted defaults | Yes — if treated as schema mandate |
| **OQ-574-10** | **First-slice exclusion / execution lift** (A7) | Exclusion stands until owner authorizes a **named conversion execution slice** after pricing/audit/fulfilment evidence contracts are verified. Closing this brief alone does **not** enable surrender apply or REVOKE dispatch | Enable on OQ close alone; widen continuation TERMINATE to imply surrender without Restructure | Yes — any conversion execute / revoke AC |
| **OQ-574-11** | **Flyway / schema numbering** | Would-be only; **next free after reserved CAM V1_16–V1_21**. Number settled when an authorized migration PR exists. **Never** claim V1_16–V1_21 here | Reserve / consume V1_16–V1_21 in this brief; ship schema before OQs close | Yes — any migration |
| **OQ-574-IMPL** | **Implementation / migration / flag authorization** | Separate implementation story **after** OQ-574-1..11 (or a deferred subset) close. Additive migrations only if needed; **never** consume reserved **V1_16–V1_21**; **do not** enable S4 apply / asset-write / dual-read / Jev / ramp; **do not** edit `HybridMigrationPolicy` without a fresh HIGH-risk impact | Piggy-back migrations in V1_16–V1_21; ship SURRENDER enum + REVOKE before forks close | Owner release; S3/S4/S6 gates; AGENT.md holds; #805–#812 reservation |
| **OQ-574-MKT** | Vendor research (Thales / Revenera) | Cite as research only; no vendor parity commitment | Encode vendor revoke APIs as CPQ public wire | Soft |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail (residuals only)

### OQ-574-1 — TERMINATE + disposition encoding

| Option | What the owner would be choosing |
|---|---|
| **A. TERMINATE + explicit surrender disposition metadata (Recommended, Proposed)** | Affirms A1 / Cluster E. Distinguishes maintenance commercial end from perpetual surrender without a new action token. |
| **B. Add `SURRENDER` to `ActionCode`** | Contradicts A1. **NO-GO** unless owner supersedes the delegated record with words + time. |
| **C. Infer from `entitlementEndDate` alone** | Ambiguous for ordinary terminates that might carry stray dates. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-2 — Entitlement-end persistence home

| Option | What the owner would be choosing |
|---|---|
| **A. Per-line snapshot on commercial change line; migration later outside reserved slots (Recommended, Proposed)** | Affirms A3 without inventing schema now. |
| **B. Header-only date** | Collapses Scenario 3’s per-line dates; conflicts with A3. |
| **C. Dual stores without a single snapshot** | Digest / pin risk across quote-service and CAM. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-3 — `convertedFromLineIds`

| Option | What the owner would be choosing |
|---|---|
| **A. Snapshot on arriving SaaS line (Recommended, Proposed)** | Matches conversions.md proposal vocabulary without accepting the file; supports REVOKE payload lineage. |
| **B. Fulfilment-only** | Loses commercial audit of conversion lineage at price time. |
| **C. Omit** | Breaks Scenario 3 AC lineage / revocation request naming. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-4 — Dual-use vs hybrid window

| Option | What the owner would be choosing |
|---|---|
| **A. Explicit date required; no silent hybrid/Scenario fill (Recommended, Proposed)** | Affirms A4. **WARN:** `HybridMigrationPolicy` is GitNexus **HIGH** risk — do not overload it for surrender dual-use. |
| **B. Silent six months** | Invents a commercial length from an illustration. **NO-GO** as default. |
| **C. Apply 1–12 hybrid cap to all conversions** | Conflates hybrid pair constructor with big-bang surrender. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-5 — Rights end vs `Surrendered` projection

| Option | What the owner would be choosing |
|---|---|
| **A. Rights end at entitlementEndDate; `Surrendered` after evidence (Recommended, Proposed)** | Affirms A2 + A6. |
| **B. Pending-end when SaaS starts** | Historical alternative; risks extending or confusing commercial rights. |
| **C. `Surrendered` at entitlement end without evidence** | Contradicts A2. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-6 — REVOKE schedule

| Option | What the owner would be choosing |
|---|---|
| **A. Post-commit CAM request; notBefore + replacement; DRG-558 consumes (Recommended, Proposed)** | Affirms A5/A8. Cite #817 only. |
| **B. At pricing time** | Mixes fulfilment with pricing; no durable apply unit. |
| **C. Same step as PROVISION without notBefore** | Dual-use window collapses. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-7 — Two TERMINATE lines in one Restructure

| Option | What the owner would be choosing |
|---|---|
| **A. Maintenance terminate + perpetual terminate-with-surrender disposition; dates may differ (Recommended, Proposed)** | Matches Scenario 3 line plan under A1/A7 without inventing apply product. |
| **B. One shared end date** | Conflicts with A3 / Scenario 3. |
| **C. `SURRENDER` token on perpetual** | Reopens A1. **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-8 — Cluster B siblings

| Option | What the owner would be choosing |
|---|---|
| **A. Cite-only Wave-2 drafts; no adopted sibling OQs (Recommended, Proposed)** | [#848](https://github.com/drgaciw/open-crm-cpq/pull/848), [#849](https://github.com/drgaciw/open-crm-cpq/pull/849), Cluster B DRG-559. |
| **B. Land pricing / linkage / apply under DRG-574** | **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-9 — conversions.md acceptance

| Option | What the owner would be choosing |
|---|---|
| **A. Keep Proposal; deliver via scored OQs; action-token wording superseded by A1 (Recommended, Proposed)** | Avoids treating illustrative ACs as Adopted schema. |
| **B. Accept wholesale as ADR** | Pulls open sibling scopes and Scenario 3 numbers. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-10 — First-slice exclusion

| Option | What the owner would be choosing |
|---|---|
| **A. Exclusion until named execution slice + verified contracts (Recommended, Proposed)** | Restates A7/A10. Closing this Decision brief is necessary but **not sufficient**. |
| **B. Enable on OQ close alone** | Skips pricing/audit/fulfilment delivery gates. |
| **C. Continuation TERMINATE implies surrender** | Skips Restructure / disposition. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-11 — Flyway numbering

| Option | What the owner would be choosing |
|---|---|
| **A. Next free after V1_16–V1_21; number at migration PR (Recommended, Proposed)** | Affirms A9. Docs claim no reserved slot. |
| **B. Claim V1_16–V1_21** | Conflicts with #805–#812. **NO-GO.** |
| **C. Ship schema before OQs close** | Product/schema **NO-GO** while blocking OQs open. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-574-IMPL — Implementation gate

| Option | What the owner would be choosing |
|---|---|
| **A. Separate impl after residual OQx close; no reserved slots; no flags; no HIGH-risk hybrid edits without fresh impact (Recommended, Proposed)** | Hard **NO-GO** on product/schema/flag until then. |
| **B. Use CAM V1_16–V1_21 now** | Violates #805–#812 reservation. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Implementation brief gate (explicit) — NO-GO

Until CPQ Decision scores OQ-574-1..OQ-574-11 and OQ-574-IMPL (owner fills `Owner decision: ___ (drg, time)` or explicitly defers a named fork the same way):

1. **NO-GO:** product code (Java / TypeScript) that adds a `SURRENDER` action token, persists `entitlementEndDate` / surrender disposition / `convertedFromLineIds`, schedules REVOKE, or writes `Surrendered` projections.
2. **NO-GO:** Flyway / schema for those fields or any edit that consumes reserved **CAM V1_16–V1_21** (#805–#812).
3. **NO-GO:** feature flags (including any `opencrm.jev.*.enabled`), outbox / relay / ramp enablement, dual-read flip, S4 apply enablement, asset-write hold bypass.
4. **NO-GO:** new or changed gateway routes / filters for surrender or entitlement revocation.
5. **NO-GO:** calling a licence server from quote-service or pricing-service; inventing a second revocation mechanism beside fulfilment REVOKE.
6. **NO-GO:** landing DRG-573 conversion pricing, DRG-556 linkage columns, or DRG-559 apply-ledger product under this ticket; **do not adopt** open OQ-573-\* / OQ-556-\* / DRG-558 OQs.
7. **NO-GO:** treating this file, Cluster B, or `specs/conversions.md` as authorization to ship RA-40 product behavior.
8. Even after forks close: S3/S4/S6 remain separately authorized; dual-read / NgRx / ramp / S4 / Jev / asset-write holds stay as in `AGENT.md`; Adopted A1–A9 stay closed; first-slice conversion stays excluded until a **named** execution authorization; `HybridMigrationPolicy` edits need a fresh GitNexus impact (**HIGH** upstream risk this pass).

Linear status for DRG-574 stays **In Review** until the owner merges and DoD says otherwise. This brief does **not** mark Done.

## Holds this brief keeps

| Hold | What stays true |
|---|---|
| Product code | **Forbidden** in the PR that carries this file |
| Schema / Flyway | No surrender / entitlement-end columns here; **V1_16–V1_21 reserved** |
| Flag / deploy | No flag flip; no deploy |
| Gateway | No surrender / revoke route or filter |
| Jev / ramp / dual-read / NgRx / S4 / asset-write | Untouched |
| `SURRENDER` action token | **Not** introduced; A1 stands |
| DRG-573 / 556 / 559 / 558 Wave-2 OQs | Cite only — not adopted |
| `specs/conversions.md` / `lifecycle-intents.md` | Stay proposals (A1 supersedes proposal action-token wording) |

## Out of scope

- Reopening Adopted Cluster B DRG-574 dispositions or the delegated fulfilment REVOKE contracts.
- Editing the delegated decision record, accepting `specs/conversions.md`, or accepting `lifecycle-intents.md` as an ADR.
- Implementing Scenario 3 AC-9/AC-10 or calling a licence server.
- Authorizing S3, S4, S6, billing receiver, asset writes, or conversion pricing.
- Adopting open OQs from DRG-573 [#848], DRG-556 [#849], DRG-558 [#817], or inventing OQ-559-\* closures.
- Consuming CAM Flyway V1_16–V1_21.
- Editing `HybridMigrationPolicy` / `MigrationWindowProperties` / `ActionCode` / apply orchestrator.

## References

- Linear: DRG-574, DRG-559, DRG-573, DRG-556, DRG-558, DRG-341, DRG-563, DRG-565, DRG-607.
- GitHub: Cluster B frame [#652](https://github.com/drgaciw/open-crm-cpq/pull/652); Wave-2 cite-only [#848](https://github.com/drgaciw/open-crm-cpq/pull/848), [#849](https://github.com/drgaciw/open-crm-cpq/pull/849), [#817](https://github.com/drgaciw/open-crm-cpq/pull/817); CAM reservation drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812).
- Docs: `cluster-b-onprem-to-saas-and-maintenance-decision-frame.md`, `delegated-renewals-amendments-decisions-2026-10-17.md`, `specs/conversions.md`, `specs/lifecycle-intents.md`, `cluster-f-fulfilment-decision-frame.md`, `../cpq-pricing-policy-sanity-check.md`.
- Code (read-only evidence): `ActionCode.java` (`TERMINATE` present; no `SURRENDER`); `MigrationWindowProperties` / `HybridMigrationPolicy` (hybrid window only); CAM migration directory through `V1_15` (no entitlement-end / Surrendered columns).
- GitNexus: index built this pass; process discovery **truncated**; `ActionCode` upstream **UNKNOWN**; `HybridMigrationPolicy` upstream **HIGH**; docs-only diff must still run `detect_changes` before commit — not a clean check if `partial` / `truncated` / `UNKNOWN`.
