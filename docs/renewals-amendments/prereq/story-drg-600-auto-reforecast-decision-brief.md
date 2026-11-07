# DRG-600 — Automatic CRM re-forecast on amendment save: Decision brief (owner forks)

**Status:** Proposal only. Additive Wave-2 residual Decision brief for CPQ Decision scoring. This file does **not** accept [`specs/amendment-reforecast.md`](specs/amendment-reforecast.md) as an enablement contract, does **not** adopt open **FQ4–FQ10** from the DRG-588 CRM forecast definitions frame ([#823](https://github.com/drgaciw/open-crm-cpq/pull/823)), does **not** adopt open **OQ-575-*** from the DRG-575 ARR bridge residual Decision brief ([#852](https://github.com/drgaciw/open-crm-cpq/pull/852)), does **not** treat merged design-spec PR [#758](https://github.com/drgaciw/open-crm-cpq/pull/758) or a prior Linear **Done** as product enablement, authorize an implementation brief, authorize product code, open a Flyway / CRM schema migration, flip a flag, register a Kafka topic or `EventContractRegistry` entry, publish a RESERVED amendment lifecycle event, enable ramp / Jev / dual-read / S4, bypass the asset-write hold, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `renewals-amendments` / CRM re-forecast (RA-66). **Story:** [DRG-600](https://linear.app/drgamtd-workspace/issue/DRG-600/automatic-crm-re-forecast-on-amendment-save-net-new) (RA-66). Linear read this pass: restored / left **In Review** (was incorrectly **Done** after the design-spec draft; design ≠ enablement).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**Spec / frame authority (cite, do not reopen as enablement):**

| Artifact | Role |
|---|---|
| [`specs/amendment-reforecast.md`](specs/amendment-reforecast.md) ([#758](https://github.com/drgaciw/open-crm-cpq/pull/758), merged) | **Proposed** design spec + illustrative TCV fixtures. Lists AC1–AC5 options. **No option selected.** Authorizes **no** producer, consumer, flag, or CRM write |
| [`specs/fixtures/reforecast/*.json`](specs/fixtures/reforecast/) | Illustrative TCV deltas only (`status: Proposed`). Not commercial terms |
| [story-drg-588-crm-forecast-definitions-spec.md](story-drg-588-crm-forecast-definitions-spec.md) ([#823](https://github.com/drgaciw/open-crm-cpq/pull/823), draft) | Forecast *definitions* frame. **Adopted FQ1–FQ3** (2026-10-23). **FQ4–FQ10 remain Proposed.** Cite only; **do not adopt** open FQs here |
| [story-drg-575-arr-bridge-decision-brief.md](story-drg-575-arr-bridge-decision-brief.md) ([#852](https://github.com/drgaciw/open-crm-cpq/pull/852), draft) | ARR bridge residual. Open **OQ-575-***. Cite only; **do not adopt**. Bridge storage does not push CRM |
| [`specs/arr-bridge.md`](specs/arr-bridge.md) | Proposal only. FQ9 asks whether forecast reads it — still Proposed on #823 |
| [AmendmentLifecycleEventCatalog.java](../../../backend/libs/common/src/main/java/com/opencrm/common/event/AmendmentLifecycleEventCatalog.java) (#683 / S6) | `AmendmentDrafted` / `AmendmentExecuted` are **RESERVED**. Not published |
| [renewals-amendments-parking-lot.md](../renewals-amendments-parking-lot.md) PL-6 · [OQ22](../renewals-amendments-open-questions-register.md) | Historical “review does not gate design drafting” language. This Decision brief **supersedes that language for product enablement**: automatic CRM re-forecast stays **NO-GO** until #823 FQ4–FQ10 are owner-settled **and** the OQ-600-* forks below are scored (or explicitly deferred with words + time) |
| [ADR-032](../../adr/ADR-032-multi-currency-pipeline-reporting.md) | Accepted pipeline `base_amount` summing. Does **not** by itself pick amendment-delta currency |

**Related Wave-2 drafts (cite only; do not adopt open OQs / FQs):**

| Ticket / draft | Linear / GH | What this brief must not adopt |
|---|---|---|
| [DRG-588](https://linear.app/drgamtd-workspace/issue/DRG-588) / [#823](https://github.com/drgaciw/open-crm-cpq/pull/823) | CRM forecast definitions | Open **FQ4–FQ10**. Definitions ≠ automatic re-forecast. Enablement of DRG-600 waits on those forks closing (or owner deferral) |
| [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) / [#852](https://github.com/drgaciw/open-crm-cpq/pull/852) | ARR bridge residual | Open **OQ-575-***. Do not wire bridge → CRM from this brief; do not score OQ-575-* here |
| [DRG-636](https://linear.app/drgamtd-workspace/issue/DRG-636) | Deal term field backlog (SQ1) | ARR-from-deal Amount remains blocked. Cite; do not invent term |
| [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) | S3 amendment document API | Document state machine is separate; this brief does not authorize S3 or event publication |
| CAM drafts [#805](https://github.com/drgaciw/open-crm-cpq/pull/805)–[#812](https://github.com/drgaciw/open-crm-cpq/pull/812) | Flyway **V1_16–V1_21 reserved** | This brief writes **no** migration and claims **no** CAM / CRM version in that band |

### Explicit enablement block (NO-GO)

**Automatic CRM re-forecast on amendment save is NO-GO / blocked** until:

1. Owner settles **FQ4–FQ10** on a landed DRG-588 definitions artifact ([#823](https://github.com/drgaciw/open-crm-cpq/pull/823)) — or records an explicit deferral with words and a time for each named FQ that remains open; **and**
2. Owner scores the residual **OQ-600-*** forks below (or explicitly defers named forks with words and a time); **and**
3. A separately authorized **implementation brief** exists after (1)–(2). Closing this Decision brief alone does **not** enable producers, CRM writers, flags, topics, or registry entries.

**Product / schema / flag = NO-GO** while any blocking OQ-600-* row stays open or FQ4–FQ10 remain unsettled without owner deferral. **Do not invent** a forecast-on-amendment runtime, CRM forecast table, or `opencrm.*.reforecast` (or similar) flag as landed.

**This note authorizes no code, schema migration, job, data rewrite, feature flag, topic, gateway route, registry edit, event publication, or deploy.** No `opencrm.jev.*.enabled` flag is turned on. Dual-read / NgRx / Activities / S4 apply / asset-write holds in `AGENT.md` stay in force.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file or Linear / GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Cited (proposed spec)` | Restated from `amendment-reforecast.md` / fixtures for orientation. Still Proposed; **not** Adopted by this file. |
| `Cited (#823 Adopted)` | FQ1–FQ3 only, as restated on the #823 frame. Not re-decided here. |
| `Status: Proposed` | Residual fork only. Slot stays `Owner decision: ___ (drg, time)`. |
| `verified by reading code` | Read / searched in the tree at the base SHA. Not executed. |
| `GitNexus` | This checkout has **no** `.gitnexus/run.cjs` / no indexed repo. Graph impact is **UNKNOWN** (unresolved), not a clean check. |

## Problem (RA-66)

Call decision 2026-10-16 (audit item 18): saving an amendment should eventually trigger an **automatic CRM re-forecast** (net-new). Scope to design included trigger (draft save vs EXECUTED), CRM inputs (ARR delta / bridge), idempotency, revision handling, and audit. Outside first slice S1–S8.

What landed on `main` via [#758](https://github.com/drgaciw/open-crm-cpq/pull/758) is a **Proposed** design spec with unselected AC1–AC5 options and illustrative fixtures. Linear was briefly marked **Done** after that draft — incorrect for enablement. Hygiene this pass restores / leaves **In Review**.

Forecast *definitions* remain incomplete on [#823](https://github.com/drgaciw/open-crm-cpq/pull/823): FQ1–FQ3 Adopted; **FQ4–FQ10 Proposed** (categories, probabilities, fiscal/quota, snapshots, AI rollups, arr-bridge as reporting model, ARR→TCV conversion). ARR bridge delivery residuals remain open on [#852](https://github.com/drgaciw/open-crm-cpq/pull/852) (**OQ-575-***). Without an owner-scored Decision brief that (a) cites those drafts without adopting their open forks and (b) keeps automatic re-forecast **explicitly blocked**, a later PR could publish RESERVED events, register topics, write CRM forecast rows, or flip a flag against unsettled definitions.

## Already settled / cited (do not re-decide as enablement)

| ID | Contract | Source |
|---|---|---|
| A1 | Capability intent exists (RA-66 / call 2026-10-16): automatic CRM re-forecast on amendment save is a named net-new feature | Issue; `amendment-reforecast.md` |
| A2 | Design spec on `main` is **Proposed** only. AC1–AC5 list options; **none selected**. Fixtures are illustrative TCV | `amendment-reforecast.md`; fixture JSON `status: Proposed` |
| A3 | **FQ1 Adopted:** sales leadership owns and consumes the forecast definition | #823 / 2026-10-23 |
| A4 | **FQ2 Adopted:** forecast unit is TCV | #823 / 2026-10-23 |
| A5 | **FQ3 Adopted:** renewal forecast source of truth is contract ARR in CAM | #823 / 2026-10-23 |
| A6 | **FQ4–FQ10 remain Proposed** on the #823 definitions frame (including FQ9 bridge-as-model and FQ10 ARR→TCV). This brief does **not** Adopt them | #823 (`read`) |
| A7 | Carrier names from S6 catalog: `AmendmentDrafted` (`AMENDMENT_DRAFTED`, DRAFT) and `AmendmentExecuted` (`AMENDMENT_EXECUTED`, EXECUTED). Publication is **RESERVED** | Catalog; `amendment-reforecast.md` AC3 |
| A8 | On base SHA: neither event type is a published `EventContractRegistry` / `KafkaTopics` producer contract for re-forecast (audit topic `AmendmentAuditRecorded` is unrelated). Payloads carry document identity/status, **no TCV field** | `verified by reading code` |
| A9 | ARR bridge storage / delivery does **not** push CRM. #852 keeps DRG-600 out of scope for bridge writes | #852 OQ-575-6 cite only |
| A10 | Docs / design PRs alone authorize **no** product, schema, flag, topic, registry, or deploy. CAM **V1_16–V1_21** reserved by sibling CAM drafts | AGENT.md; #805–#812 |

## What the tree does today (forecast / events)

`verified by reading code` / docs at the base SHA.

1. **Design only.** `docs/renewals-amendments/prereq/specs/amendment-reforecast.md` and three `fixtures/reforecast/*.json` files exist. All Proposed.
2. **RESERVED events.** `AmendmentLifecycleEventCatalog` marks `AmendmentDrafted` / `AmendmentExecuted` reserved. No re-forecast producer is authorized by this brief.
3. **No re-forecast flag / job.** No product path in this brief enables automatic CRM forecast mutation from amendment save or EXECUTED.
4. **Definitions unfinished.** #823 leaves FQ4–FQ10 Proposed. #852 leaves OQ-575-* Proposed. Neither is adopted here.
5. **CAM tip.** CAM migrations through `V1_15` are present. This brief claims **no** CAM or CRM migration and **never** V1_16–V1_21.

### GitNexus (this checkout)

No indexed repository (no `.gitnexus/run.cjs`). Graph impact for any future CRM forecast writer, Kafka producer, `EventContractRegistry` edit, or gateway filter is **UNKNOWN** — unresolved, not low. A later implementation must run impact before editing those symbols. Docs-only change: one new markdown file under `docs/renewals-amendments/prereq/`.

## Owner-decision table (remaining forks)

Every row is `Status: Proposed` under id **OQ-600-***. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. **No Adopted cells in this table.** Rows refine **delivery / enablement** against A1–A10; they do **not** reopen Adopted FQ1–FQ3. **Do not adopt** open FQs from #823 or open OQs from #852 by scoring a row here. Do **not** accept `amendment-reforecast.md` as enablement by scoring a row here.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks auto re-forecast enablement? |
|---|---|---|---|---|
| OQ-600-1 | **Definitions gate (#823 FQ4–FQ10)** | Keep automatic re-forecast **NO-GO** until FQ4–FQ10 are owner-settled on a landed definitions artifact (or each remaining open FQ is explicitly deferred with words + time). Cite #823; **do not adopt** FQ answers here | Enable runtime while FQ4–FQ10 stay Proposed; treat PL-6 / OQ22 “does not gate design” as product authorization | **Yes** — hard enablement gate |
| OQ-600-2 | Trigger (AC1) | Leave **unselected** until OQ-600-1 closes. Options remain only (a) quote-service draft save → pipeline, (b) CAM EXECUTED → committed, (c) both — as listed in the design spec | Select (a)/(b)/(c) from this brief; invent a fourth trigger | **Yes** — any live producer |
| OQ-600-3 | ARR bridge coupling (#852 / FQ9) | Cite #823 FQ9 and #852 only. **Do not adopt OQ-575-***. Do not treat bridge storage as CRM push. Forecast may later *consume* settled definitions + settled bridge delivery — not from this brief | Wire bridge components into CRM now; score OQ-575-* here; invent bridge schema as landed | **Yes** if CRM reads bridge money |
| OQ-600-4 | Event carrier / publication | Keep `AmendmentDrafted` / `AmendmentExecuted` **RESERVED**. No registry, topic, schema-registry, or catalog publication edit from docs / this brief | Publish reserved types now; mint a third event type without owner words | **Yes** — any published carrier |
| OQ-600-5 | TCV (and ARR) on payload | Leave payload enrichment **Proposed**. Current reserved DTOs carry identity/status only. Do not add TCV/ARR fields without owner words + implementation brief after OQ-600-1 | Ship TCV on DTO from this brief; derive ARR from deal Amount ahead of DRG-636 | **Yes** — payload / CRM write ACs |
| OQ-600-6 | Idempotency | Leave AC5 idempotency options **unselected** (key by revision digest vs eventId vs none) | Pick a key and ship writers now | Soft for docs; **Yes** for writer AC |
| OQ-600-7 | Revision handling | Leave AC5 revision options **unselected** (supersede vs per-revision vs EXECUTED-only) | Apply every revision to CRM while trigger unset | Soft for docs; **Yes** for writer AC |
| OQ-600-8 | Forecast-change audit | Leave AC5 audit options **unselected**. Do not invent a forecast-only audit table here; do not overload `AmendmentAuditRecorded` with amounts without owner words | Silent CRM writes with no audit; store prices on forecast audit | Soft for docs; **Yes** for audit AC |
| OQ-600-9 | Failure handling | Leave AC5 failure options **unselected** (retry/lag vs fail-save vs mark-stale). Safe choice depends on OQ-600-2 | Fail amendment save for forecast errors before trigger is chosen | Soft until trigger; **Yes** for save-path AC |
| OQ-600-10 | ARR / DRG-636 framing | Keep amendment re-forecast ARR **Proposed** pending DRG-636 / SQ1. Do not derive ARR from deal Amount (TCV) here | Invent deal term; treat FQ10 unmerged wording as Adopted enablement | Soft for TCV-only fixtures; **Yes** for ARR views |
| OQ-600-11 | Currency of amendment delta | Leave **Proposed**. Cite ADR-032 for pipeline `base_amount` only; do not extend it to amendment deltas here | Silent FX; invent tenant-base rule beyond FQ2 | Soft for single-currency fixtures; **Yes** for FX AC |
| OQ-600-12 | Registry / topic / flag freeze | **NO-GO** on `EventContractRegistry`, `KafkaTopics`, feature flags, CRM schema, gateway routes, and jobs until blocking OQ-600-* (and OQ-600-1) close or are deferred with words+time | “Spec merged ⇒ enable”; dark-launch flag from this brief; claim V1_16–V1_21 | **Yes** — release gate |
| OQ-600-13 | Design-spec acceptance vs enablement | Keep `amendment-reforecast.md` as **Proposed** design. Merged #758 ≠ accepted enablement contract. Linear Done ≠ product authorization | Treat #758 / prior Done as GO; accept fixtures as commercial terms | **Yes** — process gate |
| OQ-600-IMPL | Implementation brief authorization | **Blocked** until OQ-600-1 and other blocking OQ-600-* close (or owner defers with words+time), **and** a named implementation brief is authorized separately | Authorize implementation from this file or from #758 alone | **Yes** |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ-600-1 — Definitions gate (#823 FQ4–FQ10)

| Option | What the owner would be choosing |
|---|---|
| **A. NO-GO until FQ4–FQ10 settled or explicitly deferred (Recommended, Proposed)** | Aligns enablement with unfinished definitions on #823. Design drafting may continue; **runtime stays blocked**. Does **not** adopt FQ answers. |
| **B. Enable while FQ4–FQ10 stay Proposed** | Builds CRM writers against unsettled categories / probability / fiscal / bridge-model / ARR→TCV. **NO-GO.** |
| **C. Re-read PL-6 / OQ22 as product authorization** | Those texts addressed *design drafting* vs the review. They do **not** authorize automatic re-forecast. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-2 — Trigger (AC1)

| Option | What the owner would be choosing |
|---|---|
| **A. Leave unselected until OQ-600-1 (Recommended, Proposed)** | Restates design-spec AC1. No producer until definitions gate + owner pick. |
| **B. (a) draft save → pipeline only** | Requires idempotency/revision rules for withdraw and mid-flight revision. Still blocked by OQ-600-1. |
| **C. (b) EXECUTED → committed only** | Aligns with apply gate language on the issue. Still blocked by OQ-600-1. |
| **D. (c) both** | Needs a rule that drops pipeline when committed is written. Unselected. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-3 — ARR bridge coupling

| Option | What the owner would be choosing |
|---|---|
| **A. Cite #823 FQ9 + #852; do not adopt OQ-575-*; no CRM push (Recommended, Proposed)** | Affirms A9. Bridge residuals stay on DRG-575. |
| **B. Wire bridge into CRM forecast now** | Adopts open FQ9 / OQ-575-*. **NO-GO.** |
| **C. Score OQ-575-* from this brief** | Out of scope. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-4 — Event carrier / publication

| Option | What the owner would be choosing |
|---|---|
| **A. Keep RESERVED; no publication from this brief (Recommended, Proposed)** | Affirms A7/A8. Carrier choice still follows a future trigger decision. |
| **B. Publish AmendmentDrafted / AmendmentExecuted now** | Registry/topic/product work. **NO-GO** while OQ-600-1 open. |
| **C. Invent a third event type here** | Extra surface without owner words. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-5 — TCV / ARR on payload

| Option | What the owner would be choosing |
|---|---|
| **A. Leave enrichment Proposed; identity/status only today (Recommended, Proposed)** | Affirms A8. Fixtures stay illustrative. |
| **B. Add TCV fields in this story** | Schema/DTO change. Needs implementation brief after OQ-600-1. |
| **C. Derive ARR from deal Amount** | Conflicts with SQ1 / DRG-636 wait. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-6 — Idempotency

| Option | What the owner would be choosing |
|---|---|
| **A. Leave AC5 options unselected (Recommended, Proposed)** | Keys (revision digest / eventId / none) wait on trigger + definitions. |
| **B. Pick a key and ship writers** | Product. **NO-GO** while OQ-600-1 open. |
| **C. No key (every save adds)** | Double-count risk. Unselected. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-7 — Revision handling

| Option | What the owner would be choosing |
|---|---|
| **A. Leave AC5 options unselected (Recommended, Proposed)** | Supersede / per-revision / EXECUTED-only wait on trigger. |
| **B. Apply every revision to CRM now** | Product without trigger. **NO-GO.** |
| **C. Freeze forecast until EXECUTED without selecting AC1 (b)** | Hidden trigger choice. Score via OQ-600-2 instead. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-8 — Forecast-change audit

| Option | What the owner would be choosing |
|---|---|
| **A. Leave AC5 audit options unselected (Recommended, Proposed)** | Dedicated forecast audit vs document audit vs both — owner pick later. No amounts on audit from this brief. |
| **B. Silent CRM writes** | No evidence trail. **NO-GO** for commercial confirm. |
| **C. Put price fields on forecast audit** | Spec forbids price fields on the illustrative audit option A. **NO-GO** without supersession. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-9 — Failure handling

| Option | What the owner would be choosing |
|---|---|
| **A. Leave AC5 failure options unselected (Recommended, Proposed)** | Retry/lag vs fail-save vs mark-stale depends on OQ-600-2. |
| **B. Fail amendment save for forecast errors before trigger chosen** | Couples document write to an unset integration. **NO-GO.** |
| **C. Always best-effort with no stale marker** | Unobservable lag. Unselected. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-10 — ARR / DRG-636

| Option | What the owner would be choosing |
|---|---|
| **A. ARR stays Proposed pending DRG-636 / SQ1 (Recommended, Proposed)** | TCV fixtures may remain illustrative; ARR views wait. |
| **B. Invent deal term in this brief** | Out of scope. **NO-GO.** |
| **C. Treat unmerged FQ10 wording as enablement** | #823 keeps FQ10 Proposed on the landed frame. Cite only. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-11 — Currency of amendment delta

| Option | What the owner would be choosing |
|---|---|
| **A. Leave Proposed; cite ADR-032 pipeline only (Recommended, Proposed)** | Does not extend Accepted D-06 to amendment deltas. |
| **B. Silent FX at CRM write** | Invents FX policy. |
| **C. Mandate tenant-base currency beyond FQ2 wording** | Over-reads FQ2. Score only with owner words. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-12 — Registry / topic / flag freeze

| Option | What the owner would be choosing |
|---|---|
| **A. Hard freeze until blocking OQs + OQ-600-1 close (Recommended, Proposed)** | Affirms A10. No dark-launch from docs. |
| **B. Dark-launch feature flag from this brief** | Product/flag **NO-GO.** |
| **C. Claim CAM/CRM Flyway in V1_16–V1_21** | Conflicts with #805–#812. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-13 — Design-spec acceptance vs enablement

| Option | What the owner would be choosing |
|---|---|
| **A. #758 remains Proposed design; Done ≠ GO (Recommended, Proposed)** | Linear hygiene: **In Review** until enablement gates close. |
| **B. Merged design spec = accepted enablement** | Conflates docs with runtime. **NO-GO.** |
| **C. Illustrative fixtures = commercial terms** | Fixtures say otherwise. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ-600-IMPL — Implementation brief authorization

| Option | What the owner would be choosing |
|---|---|
| **A. Blocked until OQ-600-1 + blocking OQ-600-* close / defer, then a named implementation brief (Recommended, Proposed)** | Closing this Decision brief is necessary but **not sufficient**. |
| **B. Authorize implementation from this file alone** | Skips definitions gate. **NO-GO.** |
| **C. Authorize from #758 alone** | Design ≠ enablement. **NO-GO.** |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Summary

| Item | Verdict |
|---|---|
| Automatic CRM re-forecast enablement | **NO-GO / blocked** |
| Product / schema / flag | **NO-GO** |
| Cite #823 FQ4–FQ10 | Yes — **do not adopt** |
| Cite #852 OQ-575-* | Yes — **do not adopt** |
| Residual forks | **OQ-600-1 … OQ-600-13** + **OQ-600-IMPL** — all `Status: Proposed` |
| Linear | Leave / restore **In Review** (not Done) |
| This PR | Docs-only **draft**; do not merge as enablement |

## Security / holds

Docs only. No secrets, SQL, auth change, feature flag, workflow, pom, registry, topic, or schema edit. Security-sensitive future paths (CRM forecast writes, event publication) stay flagged for review when an implementation brief is authorized — not now.
