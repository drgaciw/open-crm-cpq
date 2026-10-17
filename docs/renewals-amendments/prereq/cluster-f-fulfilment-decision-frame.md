# Cluster F — Fulfilment decision frame

**Status:** Accepted as documentation on 2026-10-17. See the [owner acceptance record](documentation-acceptance-2026-10-17.md) for scope; individual forks and implementation gates retain their stated statuses.

**Date:** 2026-10-17. **Reading base:** `main` at `0d71251`. **Project:** OPEN-CRM-CPQ. **Linear team:** DRG. **Pull request:** draft [#655](https://github.com/drgaciw/open-crm-cpq/pull/655), base `main`.

**Stories in this cluster:** [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals) (RA-44) and [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive) (RA-24). Both were set to **In Review** for this draft on 2026-10-17. Neither issue had comments when first read.

**This record authorizes no code, schema, migration, flag, topic, registry entry, gateway route, or deploy.** No feature flag is changed, including `opencrm.erp-sync.enabled`. Nothing is deployed.

Every default below has the same status: **Proposed, unaccepted, owner decision required.** A default is a proposal sitting beside its alternatives. It is not a decision.

No event is decided. No transport is decided. [DRG-466](https://linear.app/drgamtd-workspace/issue/DRG-466/bc-08-03-fulfillment-orchestrator-with-compensations) stays a dependency. Naming `FulfillmentDispatched`, `EntitlementProvisioned`, `LicenseRequired`, a Kafka command, a synchronous call, an outbox row, or the absence of any of those would be a decision. This frame does not make that decision.

No commitment state is settled. `PendingActive`, `Active`, `Suspended`, `InGrace`, `PendingTerminate`, `Terminated`, `Expired`, and `Superseded` are option names in fork S1. None of them is a landed state, and none of them is accepted here.

## Standing inputs

These are records already on `main`. This frame uses them. It does not reopen them, and it does not change their status. Using a record as an input does not settle a commitment state, an event, or a transport.

| Input | Status on `main` | What this frame takes from it |
|---|---|---|
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) | **Accepted** 2026-10-16, owner approval recorded in #623 | The charge formula. Fulfilment does not compute `periodAmount`, stub days, or the Drools full-term ratio. No fork below reopens proration. |
| [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) | **Accepted** 2026-10-16, owner approval recorded in #623. ADR-024 through ADR-027 stay **Proposed**. | Spine wins. The reconcile text places v1 order lines on on-prem lines and describes mid-term SaaS apply in CAM with no order and no fulfilment saga. D-R5 is input text for the operation forks. Its effect row lists candidate commitment names. This frame does not settle those names. |
| [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) | **Proposed** 2026-10-04 | A synchronous call is an alternative in fork C2. It is not decided. |
| [ADR-024](../../adr/ADR-024-order-orchestration-context.md), [ADR-025](../../adr/ADR-025-saga-process-manager.md), [ADR-026](../../adr/ADR-026-command-messages.md), [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) | **Proposed** 2026-10-04, each "Not accepted" | No `order-service`, `orders` schema, saga table, command topic, or point-of-no-return implementation. A Kafka command is an alternative in fork C2. It is not decided. |
| OQ5 option A | **ADOPTED** 2026-10-16, first slice only, in [the open-questions register](../renewals-amendments-open-questions-register.md). `specs/partial-apply.md` is not accepted. | One unit per target, atomic units, no automatic rollback, `PARTIALLY_APPLIED` when a later unit fails, `APPLY_BLOCKED` when none have committed, correcting amendment as the remedy. S6 is not authorized. |
| OQ6 | **ADOPTED** 2026-10-16 under delegation by CPQ Decision, relayed by Projects Manager, recorded in #642 (merged to `main`). See [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md). | After `EXECUTED`, no revision (`IMMUTABLE_EXECUTED`). At most one revision may be `EXECUTED`. A fulfilment outcome does not revise the document. OQ6 `SUPERSEDED` is a revision close status. It is not a commitment state. |
| OQ7 | **DEFAULT PROPOSED** for the slice: thin contract header only (S7), no synthetic history. See [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md). | This frame does not confirm OQ7. It adds no synthetic entitlement history and no retroactive fulfilment rows. |
| OQ18 | **Proposed** until the owner confirms it. Unconfirmed proposal: canonical digest library in `backend/libs/common`. | This frame does not place that library and does not start the library work. |
| S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) | Not started by this frame. #642 has merged. S3 still needs separate owner authorization. Digest-library work still waits on OQ18. | No digest-library task is opened here. |
| S6 | Not authorized (OQ5 record and the OQ6 record). | An event contract would be S6 work. No event is decided here. |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design | Not accepted. The note [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) is a design record, not an accepted design. | Fulfilment does not call pricing-service continuation and does not depend on that design. |
| [ADR-035](../adr-035-amendment-document-placement.md) | **Accepted** 2026-10-16 | Authorizes the first-slice schema and audit stories only (S1, S2). Ordering and fulfilment stay outside that acceptance. |
| DRG-466 | Backlog. Title: fulfilment orchestrator with compensations. | Dependency only. Its saga, compensations, and `FulfillmentDispatched` name are not decided here. |

`specs/lifecycle-intents.md`, `specs/conversions.md`, `specs/partial-apply.md`, and `specs/maintenance.md` are proposals. Their enums are not the fulfilment key, and they do not settle a commitment state.

## CAM ERP sync on `main` (read this pass)

The phrase "CAM ERP sync" in this note means the code and migrations in this table. Each row was read on this pass. None is marked UNVERIFIED.

| What was read | Path |
|---|---|
| `subscriptions.erp_sync_status VARCHAR(20) DEFAULT 'PENDING'` and `erp_subscription_id VARCHAR(255)`. This migration has no check constraint on `erp_sync_status`. | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_1__create_contract_tables.sql` lines 23–24 |
| Subscription `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'` with `CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'EXPIRED'))`. That check is the subscription row. It is not a commitment-state model, and it settles none of the S1 names. | Same file, lines 14 and 32–34 |
| `ALTER TABLE subscriptions ADD COLUMN erp_sync_claim_token VARCHAR(36)` and `ADD COLUMN erp_sync_claim_revision BIGINT`. | `backend/services/contract-asset-management/src/main/resources/db/migration/V1_6__subscription_rate_schedule.sql` lines 3–4 |
| A search of `backend/services/contract-asset-management/src/main/resources/db/migration` for `erp_sync` returned only `V1_1` and `V1_6`. | That directory, this pass |
| `ErpAdapter.syncToErp` sleeps and returns `"ERP-" + subscription.getSubscriptionNumber()`. | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/acl/ErpAdapter.java` lines 23–36 |
| `ErpSyncJob` documents the listener queueing `erp_sync_status = QUEUED`. On success it stores the ERP id, sets `SYNCED`, and calls `activate()` only when `status` is `PENDING`. The class is conditional on `opencrm.erp-sync.enabled` = `true`, match-if-missing `true`. | `.../scheduler/ErpSyncJob.java` lines 28–46 and 131–137 |
| Accepted deal types in `apply` are `NEW`, `AMENDMENT`, `EXPANSION`, `RENEWAL`. `NEW` builds the row with `erpSyncStatus(ErpSyncStatus.QUEUED)`. `mutate` sets `erpSyncStatus` to `QUEUED` again. A `RENEWAL` also sets `endDate` and `renewalDate` on the subscription. | `.../service/AcceptedQuoteSubscriptionService.java` lines 53, 104–109, 202–210 |
| String constants `PENDING`, `QUEUED`, `IN_PROGRESS`, `SYNCED`, `FAILED`. | `.../domain/ErpSyncStatus.java` lines 16–20 |
| `opencrm.erp-sync.enabled: ${ERP_SYNC_ENABLED:true}`. | `backend/services/contract-asset-management/src/main/resources/application.yml` lines 71–72 |

This frame does not change `opencrm.erp-sync.enabled` and does not add a migration. The path above is the current CAM ERP sync. It is not a fulfilment operation, not an event, and not a transport decision.

## Cluster note

DRG-578 and DRG-558 are one boundary. DRG-578 asks which fulfilment operation a change line selects. DRG-558 asks which commitment names might describe a line that is waiting on fulfilment. The Accepted reconcile is input text for that boundary. This frame does not scaffold a service.

Linear still lists [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577/reconcile-proposed-order-model-adr-024-to-adr-027-with-the-amendment) as `blockedBy` for both issues. DRG-577 is Done. Its reconcile record says specification of DRG-578 and DRG-558 is design work, not an authorization to scaffold `order-service`. This frame is that specification. Its status is Proposed, unaccepted, owner decision required.

### Fork C1 — What this frame ships

| Option | Content |
|---|---|
| A | This document only. No type, table, topic, or service. |
| B | A CAM-local fulfilment-request row written when an on-prem unit commits. |
| C | `order-service` and a `DecompositionRule` implementation now, including DRG-465. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. B and C need code. C also needs the owner to accept ADR-024 through ADR-027, which this frame does not do.

### Fork C2 — Transport

No transport is decided. DRG-466 stays a dependency for any later orchestrator. The rows below are options.

| Option | Content |
|---|---|
| A | This option names no transport. The CAM ERP sync table above remains a description of code on `main`. It is not selected as the fulfilment transport. |
| B | A new synchronous call, which would depend on Proposed ADR-017. |
| C | A Kafka command with a registered reply, which would depend on Proposed ADR-026, plus an outbox publication. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. Choosing A does not decide that a transport will never exist. Choosing B or C would decide a transport. This frame decides neither.

### Fork C3 — Failure after a unit has committed

| Option | Content |
|---|---|
| A | The executed document stays immutable (OQ6). Committed units stay committed (OQ5 option A). The remedy is a later correcting LifecycleChange. This option defines no exception queue. |
| B | Roll the committed unit back and compensate every fulfilment request. That shape is DRG-466's acceptance text. DRG-466 stays a dependency. This option does not decide its event or its transport. |
| C | Open a new revision of the executed document. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. B conflicts with OQ5 option A. C conflicts with OQ6 `IMMUTABLE_EXECUTED`. ADR-027's hold and exception queue stay inside that Proposed ADR.

### Fork C4 — Money

| Option | Content |
|---|---|
| A | Fulfilment consumes the unit the commercial apply already priced. The Accepted proration ADR remains the formula. |
| B | Recompute the stub, the full-term ratio, or uplift inside the fulfilment operation. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. B would reopen the Accepted proration ADR. This frame does not.

Shared consequence of C1–C4: while this frame is Proposed, unaccepted, owner decision required, it unblocks no implementation story. Owner confirmation of a default would still authorize no code, migration, flag, or deploy.

## DRG-578 — Action-aware fulfilment decomposition

**Issue:** [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals). **Status:** In Review, for draft #655. **Relation read:** `blockedBy` DRG-577 (Done). The issue body also names DRG-465, DRG-466, DRG-558, DRG-460, DRG-495, DRG-574, DRG-556, DRG-557, and DRG-577. Those links are not `relatedTo` rows on the issue. DRG-466 stays a dependency.

### Problem

No fulfilment package exists under `backend/services/`. After acceptance, the CAM ERP sync path cited above queues a simulated ERP call and, for a new subscription that is still `PENDING`, activates it when that call completes. A typed amendment, expansion, or renewal sets `erpSyncStatus` back to `QUEUED` on the same subscription (`AcceptedQuoteSubscriptionService.mutate`, lines 202–210, read). That path does not select an operation from the change-line action.

The gap the issue names: Add provisions; a quantity update modifies the entitlement or seat cap; Renew extends a SaaS end date and regenerates a term licence; Upgrade and Swap provision the new entitlement and revoke the old one inside a dual-entitlement window, create before terminate; Downgrade, Remove, and Terminate revoke or cap at the effective date; Suspend and Resume disable and enable. Revocation outside conversions (DRG-574) has no separate home. DRG-465 maps a commercial product to fulfilment items and does not key on the change-line action. DRG-558 names a fulfilment step and a status set and does not select the operation. No name in that status set is settled.

The issue's suggested next step is to extend `DecompositionRule`, to mark `REVOKE` and `REGENERATE` non-compensatable per ADR-027 D-02, and to feed results back to CAM. ADR-027 is Proposed, so that sentence is not a rule this frame can adopt. Fork F5 records a classification the Accepted reconcile's text allows a later specification to assume. The classification's status in this frame is Proposed, unaccepted, owner decision required.

### What is already settled on `main`

Commitment states are not in this table. Events and transports are not in this table.

| Fact | Path |
|---|---|
| No `order-service` and no fulfilment service. Services on `main`: `ai-service`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `crm-service`, `document-generation`, `gateway-service`, `pricebook-service`, `pricing-service`, `product-service`, `quote-service`. A search of `product-service` Java and SQL for `relationship` returned no matches on the earlier pass of this note. | `backend/services/` |
| CAM ERP sync columns, adapter, job, queue-on-accept, and the enabled property. | The read table in "CAM ERP sync on `main`" |
| The amendment append command carries no order, saga, or fulfilment identity. The audit publication carries no fulfilment payload. | `.../documents/events/AmendmentEventAppend.java` line 11; `.../documents/audit/AmendmentAuditPublication.java` line 11 |
| Reconcile text: v1 order lines for on-prem license models and for `ENTITLEMENT`; mid-term SaaS apply described with no order and no fulfilment saga. | [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) D-R2 |
| Reconcile text D-R5: a projection from ChangeLine codes to order-line actions and fulfilment operation names, for lines D-R2 sends to an order. Quote `DealType` is not the selector in that text. Create before terminate is spike section 4 item 3. | Same file, D-R5; [amendment-document-design-spike.md](../amendment-document-design-spike.md) section 4, item 3 |
| D-R5 names, as input text only: `Add` → `PROVISION`; `Update` → `MODIFY`; `Remove` and `Terminate` → `REVOKE`; `NoChange` → none; `Renew` → `REGENERATE` for a term licence, with SaaS renewal described as a CAM date change and no order; `Upgrade` and `Swap` → `PROVISION` then `REVOKE`; `Downgrade` → `MODIFY` and then "REVOKE or cap"; `ConvertMetric` → `REGENERATE`; `Suspend` → `SUSPEND`; `Resume` → `RESUME`. | Same file, D-R5 table |
| The effect row lists `EXTEND` among operations a DRG-578 specification may emit. No D-R5 row emits `EXTEND`. | Same file, "Effect on open stories" |
| Reconcile text says `REVOKE` and `REGENERATE` may be assumed non-compensatable, and that ADR-027 stays Proposed. ADR-027 D-02, Proposed, places a point of no return at dispatch of the first non-compensatable request. | Reconcile D-R5 last paragraph; [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) D-02 |
| Reconcile text: CAM writes the commercial result. It also says there is no v1 `ApplyAmendmentUnit` command. Any later command transport is fork C2 and is not decided. | Reconcile D-R4 |
| S2's additive event text is document id, amendment sequence, and digest, with no fulfilment payload. That sentence describes DRG-580's event scope. It does not decide a fulfilment event. | Reconcile D-R7 |
| OQ5 option A and OQ6 apply as in the standing inputs. Revoke submission timing is still an open question in the unaccepted partial-apply proposal. | Register; [partial-apply.md](specs/partial-apply.md) open question 4 |

### Forks

#### F1 — Who emits `EXTEND`

| Option | Content |
|---|---|
| A | `EXTEND` is a reserved operation name in the proposal. No v1 ChangeLine emits it under this option. A SaaS renewal's subscription `endDate` update is the `mutate` path already cited. A term-licence `Renew` would emit `REGENERATE`. |
| B | Drop `EXTEND` from the proposed operation set. |
| C | Emit `EXTEND` for SaaS renewals, and for term licences in place of `REGENERATE`. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. A keeps the name the reconcile effect row lists, and it follows the D-R5 wording that a SaaS renewal raises no order and a term licence regenerates. B would narrow that effect row. C departs from D-R2 and D-R5.

#### F2 — Quantity up and quantity down

| Option | Content |
|---|---|
| A | `Update` emits `MODIFY` in both directions. Quantity-down caps the entitlement or seat count at the effective date. It does not emit `REVOKE`. |
| B | Quantity-down emits `REVOKE` for the delta. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. The D-R5 `Update` row is `MODIFY`. B adds an operation that row does not name.

#### F3 — What the Downgrade disjunction means

The D-R5 row says Downgrade → `MODIFY` → "REVOKE or cap at the effective date." This fork interprets that disjunction. It does not edit the reconcile file.

| Option | Content |
|---|---|
| A | A quantity-only downgrade is F2's `MODIFY` (cap at the effective date). A downgrade that replaces the SKU uses the Upgrade/Swap shape, `PROVISION` then `REVOKE`, and waits until a catalog relationship says the pair is allowed (DRG-576). Until that relationship exists, a SKU-replacement downgrade has no emitter under this option. |
| B | Every Downgrade emits `REVOKE` of the whole entitlement. |
| C | Downgrade has no emitter until the owner picks, including quantity-only. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. Quantity-only work is already named on the `Update` row. SKU replacement is the create-before-terminate shape. A search of `product-service` Java and SQL for `relationship` returned no matches on the earlier pass.

#### F4 — Decomposition key

| Option | Content |
|---|---|
| A | For lines the reconcile text sends toward fulfilment: `(licenseModel, ChangeLine action_code)`, using the D-R5 codes. Product id and relationship type are optional qualifiers. They are unused until DRG-576 supplies relationship rows. |
| B | The key requires a relationship type now. |
| C | Quote `DealType` selects the operation. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. The issue asks for product or relationship type as part of the key. Those qualifiers stay optional so the proposal is not blocked on relationship rows. D-R5 says `DealType` is not the selector. The action codes in `specs/lifecycle-intents.md` (`Delete`, `Surrender`, `Convert`, and no `Upgrade` / `Downgrade` / `Swap` / `ConvertMetric`) are a proposal. They are not this key.

#### F5 — Compensatability

| Option | Content |
|---|---|
| A | A later specification may assume `REVOKE` and `REGENERATE` are non-compensatable, which the reconcile text permits. The explicit classification is in Accepted `adr-reconcile-adr-024-027.md` D-R5: "`REVOKE` and `REGENERATE` are non-compensatable" and "DRG-578 may assume that classification." ADR-027 D-02 defines the generic dispatch point of no return; it does not name those operations. Using the Accepted classification does not accept ADR-027. `PROVISION`, `MODIFY`, `SUSPEND`, `RESUME`, and reserved `EXTEND` are not given a class in this frame. |
| B | Every request has a compensation. That is DRG-466's acceptance text. DRG-466 stays a dependency. This option does not decide an event or a transport. |
| C | Accept ADR-027 as part of this frame. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. B conflicts with the reconcile sentence and with OQ5 option A's refusal of automatic rollback. C would accept a Proposed ADR, which this frame does not do.

#### F6 — Unmapped on-prem line

| Option | Content |
|---|---|
| A | No fulfilment request is dispatched for the unmapped line. When the missing mapping prevents its unit from committing, that unit is `BLOCKED`; the document is `APPLY_BLOCKED` if no unit committed, or `PARTIALLY_APPLIED` if an earlier unit committed (OQ5 option A). Already committed units, including SaaS units, remain applied. These are unit/document outcomes, not commitment states; no S1 commitment state is assigned. |
| B | The whole document is blocked, including committed SaaS units. |
| C | Dispatch `PROVISION` for an unmapped product. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. DRG-465 says an unmapped product blocks the order. A retains the failed unit and document outcomes while preserving committed units; it assigns no commitment state. A failure discovered only after that same unit committed must preserve the committed fact and use the later correcting-change path in C3, rather than rewrite the unit as never applied. B conflicts with OQ5 option A. C hides a missing rule.

#### F7 — Where evidence goes

| Option | Content |
|---|---|
| A | A future fulfilment component returns evidence. CAM would write commercial state. This option adds no command, no event, and no transport. DRG-466 stays a dependency. |
| B | The fulfilment component writes subscription status and commitment status. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. D-R1 and D-R4 describe CAM as the writer of applied commercial state. B would add a second writer while ADR-024 through ADR-027 are still Proposed. Neither option settles a commitment state.

#### F8 — Revocation outside conversions

| Option | Content |
|---|---|
| A | One `REVOKE` operation in the proposal. `Remove`, `Terminate`, and the old line of `Upgrade` or `Swap` use it. DRG-574's surrender, if that separate proposal is later accepted, would call the same operation after the dual-use window. This option does not add `Surrender` or `Surrendered`, and it does not settle either name. |
| B | Conversions get a second revocation mechanism, separate from `REVOKE`. |
| C | This frame specifies DRG-574's request payload. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. The issue says revocation outside conversions is uncovered. A gives it the operation name DRG-578 already uses, and leaves surrender on DRG-574. `specs/conversions.md` is Proposal only.

#### F9 — When `REVOKE` is submitted

| Option | Content |
|---|---|
| A | This option does not choose a time. `REVOKE` is a separate unit from `PROVISION`. Create-before-terminate still orders the units in the spike and the reconcile text. The unaccepted partial-apply proposal's open question 4 (record the revoke now with a future entitlement end, or delay submission until that date) stays open. |
| B | Dispatch `REVOKE` in the same step as `PROVISION`. |
| C | Always delay recording the revoke unit until the window ends. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. B can end the old entitlement before the dual-use window. C would close an open question this cluster does not own. The window length stays with DRG-341 and the ramp work. This frame does not set it. No transport for the dispatch is decided.

### Recommendation and default

The DRG-578 proposal is F1-A, F2-A, F3-A, F4-A, F5-A, F6-A, F7-A, F8-A, and F9-A, on top of cluster proposals C1-A through C4-A.

**Status of this combined default:** Proposed, unaccepted, owner decision required.

Operation names under that proposal, for on-prem lines only: `PROVISION`, `MODIFY`, `REVOKE`, `REGENERATE`, `SUSPEND`, `RESUME`, plus reserved `EXTEND` with no v1 emitter. Those names are a proposal. They are not a decided interface.

### Dependencies

| Dependency | Why it is named | What this frame does with it |
|---|---|---|
| DRG-577, Done | Linear `blockedBy`. Accepted reconcile is input text. | Used as input. Not reopened. |
| ADR-024 through ADR-027, Proposed | DRG-465 and DRG-466 wait on owner acceptance before any service. | Not accepted here. No transport is decided. |
| DRG-465, Backlog, parent DRG-290 | Product-to-item decomposition. The issue text names an `OrderDecomposed` event. | Not the action key. The event is not decided. Not started. |
| DRG-466, Backlog | Fulfilment orchestrator. Stays a dependency. | Compensation text is alternative B of C3 and of F5. No event and no transport from DRG-466 is decided. |
| DRG-460, Backlog | Provision entitlements. The issue text names `EntitlementProvisioned`. | That event is not decided. Not started. |
| DRG-495, Backlog | Licence determination. The issue text names `LicenseRequired`. | That event is not decided. Pause behaviour stays inside Proposed ADR-027. Not started. |
| DRG-576, Backlog | Catalog product relationship and lifecycle-eligibility model. | Optional qualifier only (F3, F4). |
| DRG-556, DRG-557, Backlog | Perpetual-plus-maintenance, and capacity meters. | A perpetual commercial renewal is outside this proposal. Maintenance renewal and true-up stay on those issues. |
| DRG-574, Backlog | Surrender and revocation after a dual-use window. | Caller of `REVOKE` only if its own proposal is later accepted (F8). `Surrendered` is not settled. |
| DRG-558, In Review | Commitment names while an operation is outstanding. | Options in the next section. No state is settled. |
| DRG-341 | Dual-entitlement window. | Window length is not set here (F9). |
| OQ18, S3 / DRG-581 | Digest library. | A future request may copy the digest the reconcile text already stores as replay data (D-R3). This frame does not build the library and does not start S3. |
| Accepted proration ADR | Charge formula. | Not reopened (C4). |
| DRG-609 design | Pricing continuation. | Not accepted, and not a dependency of this proposal. |

### What it unblocks

While this frame is Proposed, unaccepted, owner decision required, DRG-578 unblocks nothing.

Owner confirmation would still authorize no code, migration, flag, or deploy. It would not decide an event or a transport. It would not start DRG-465, DRG-466, DRG-460, DRG-495, DRG-574, DRG-556, DRG-557, S3, or S6. Any service, schema, or topic still waits on a separate acceptance of ADR-024 through ADR-027.

## DRG-558 — Entitlement fulfilment step and commitment state

**Issue:** [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive). **Status:** In Review, for draft #655. **Relation read:** `blockedBy` DRG-577 (Done). `relatedTo`: [DRG-429](https://linear.app/drgamtd-workspace/issue/DRG-429/bc-02-06-reconfigure-from-an-existing-hash) (Backlog), [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574/perpetual-licence-surrender-and-entitlement-revocation-in-conversions) (Backlog), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547/research-gap-intake-harvest-from-cpq-market-research) (Backlog). DRG-466 is a dependency of the cluster. It is not a `relatedTo` row on this issue.

### Problem

Applying a change updates the subscription through the CAM ERP sync path cited above. There is no licence regeneration step after renewal, upgrade, or metric conversion, and there is no commitment state model on the entitlement. The issue asks for a fulfilment-required signal and a commitment status set running from `PendingActive` to `Terminated`, plus `InGrace`, `Expired`, and `Superseded`. Market evidence in the issue (Revenera fulfilment operations, Thales regenerate, Snow upgrade quantity, TMF637 product status) is evidence. It is not the enum to copy, and it settles no state.

The suggested next step names a fulfilment-required event. No event is decided. Fork S2 keeps that choice as options. DRG-466 stays a dependency.

### What is already settled on `main`

No commitment state is in this table.

| Fact | Path |
|---|---|
| CAM ERP sync, including the subscription `status` check in `V1_1`. That check is not the commitment model. | The read table in "CAM ERP sync on `main`" |
| Java enum `SubscriptionStatus`: `PENDING`, `ACTIVE`, `SUSPENDED`, `CANCELLED`, `EXPIRED`. Same five values as the `V1_1` check. | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/SubscriptionStatus.java` lines 3–8 |
| No CAM production assignment of `SubscriptionStatus.EXPIRED` was found on the earlier pass. Quote expiry (`QuoteStatus.EXPIRED` in `QuoteService`) is a different status. | CAM `src/main/java` search on that pass |
| `ErpSyncJob` calls `activate()` only when subscription status is `PENDING`. `mutate` re-queues ERP sync on an existing subscription. | `ErpSyncJob.java` lines 135–137; `AcceptedQuoteSubscriptionService.java` lines 202–207. Both read. |
| The reconcile effect row lists eight candidate names and says a fulfilment-required signal would apply to on-prem commitment lines, firing when an on-prem unit has been applied. It also says a SaaS subscription unit in v1 does not fire it. That row is input text. It does not settle any name. | [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md), "Effect on open stories" |
| Reconcile text describes CAM as the writer of applied commercial state. | Same file, D-R1 and D-R4 |
| Research vocabulary for `CommitmentLine.status` uses the same eight names. That research file is not an accepted model. | [cpq-renewals-amendments-research.md](../cpq-renewals-amendments-research.md) section 7.2 |
| OQ6 `SUPERSEDED` is `status_at_close` on a revision closed before execution. It is not a commitment state. | [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) section 2 |
| OQ5 option A: a later unit's failure does not roll back an earlier committed unit. | Open-questions register, OQ5 row |
| `AmendmentEventAppend` carries no fulfilment identity. | `AmendmentEventAppend.java` line 11 |
| The first-slice requirements leave fulfilment out of the SaaS Add/Update/Remove slice. | [amendment-first-slice-requirements-and-acceptance.md](../amendment-first-slice-requirements-and-acceptance.md), slice definition |
| `Surrendered` appears in the conversions spec as a proposal for DRG-574. That spec is Proposal only. The name is not settled. | [conversions.md](specs/conversions.md) scope item 3 |

### Forks

No state below is settled. Each default is Proposed, unaccepted, owner decision required.

#### S1 — Commitment state model (options)

The model from `PendingActive` through `Terminated` is this fork. Nothing in it is settled.

| Option | Content |
|---|---|
| A | Candidate names only, none of them settled: `PendingActive`, `Active`, `Suspended`, `InGrace`, `PendingTerminate`, `Terminated`, `Expired`, `Superseded`. The reconcile effect row lists these names. Listing them here does not accept them. |
| B | The eight names in A, plus the further unsettled TMF637 names `created`, `cancelled`, and `aborted`. |
| C | The eight names in A, plus the further unsettled name `Surrendered`. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. A is the shorter candidate list already written in the reconcile effect row. B adds names the issue calls illustrative. C adds a name that belongs to the unaccepted conversions proposal. Confirming A would still leave every name unaccepted until a later owner act, and this frame does not perform that act. No state is settled by recommending A.

#### S2 — Fulfilment-required signal

No event is decided. No transport is decided. DRG-466 stays a dependency.

| Option | Content |
|---|---|
| A | This option names no event and no transport. It describes a per-unit fact that would exist only if a later story stored one after an on-prem unit committed. |
| B | A document-level signal when the document reaches `APPLIED`. This option still names no transport. |
| C | Use the event name from DRG-460 (`EntitlementProvisioned`) or from DRG-466 (`FulfillmentDispatched`). Both stay dependencies. Selecting either name would be a decision. This frame does not select one. |

**Recommendation:** A. **Default:** A. **Alternatives:** B, C. **Status of this default:** Proposed, unaccepted, owner decision required. A matches the reconcile wording that a signal would follow an applied on-prem unit, and it matches OQ5 option A in that a committed unit does not wait for later units. Recommending A does not decide that no event will ever exist. B and C would name a signal shape. C would also name an event. None of those is decided.

#### S3 — Who would write a status, if a status is later confirmed

| Option | Content |
|---|---|
| A | If the owner later confirms a commitment status, CAM would write it. A future fulfilment component would supply evidence and would not write the status. No writer is added now. |
| B | The fulfilment orchestrator would write the status. DRG-466 stays the dependency for that orchestrator. This option does not decide DRG-466's event or transport. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. D-R1 describes a single writer of applied commercial state. B would be a second writer. Neither option settles a state.

#### S4 — SaaS subscription row in v1

| Option | Content |
|---|---|
| A | The SaaS subscription row keeps the `V1_1` status check and `ErpSyncStatus`, as read above. It does not enter the S1 candidate names. It does not raise an S2 signal under this option. |
| B | Map the five subscription status values onto the S1 candidate names. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. The subscription check is already on `main`. It is not a commitment state. Recommending A does not settle any S1 name.

#### S5 — `InGrace` and `Expired`, if they remain in the candidate list

| Option | Content |
|---|---|
| A | If S1-A remains the proposal, `InGrace` and `Expired` are names only. This option sets no grace length, no expiry job, and no assignment rule. Neither name is settled. |
| B | Define a grace length and a job that would move a line to `Expired`. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. Duration belongs with the renewal-policy work. This cluster does not invent a number of days. `SubscriptionStatus.EXPIRED` on the subscription enum is not this name, and it is not settled as a commitment state.

#### S6 — The word `Superseded` and OQ6

| Option | Content |
|---|---|
| A | If a commitment name `Superseded` is later confirmed, it would mean the entitlement was replaced. OQ6 `SUPERSEDED` remains `status_at_close` on a pre-execution revision. They would be different facts. This option does not settle the commitment name. |
| B | Store a commitment replacement in the revision close status. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. OQ6 is already adopted for document revisions. Reusing that column for an entitlement would mix a document fact with a delivery fact. A fulfilment outcome still does not revise an executed document (cluster fork C3). No commitment state is settled.

#### S7 — When `PendingActive` and `Active` would start, if those names are later confirmed

| Option | Content |
|---|---|
| A | If those two names are later confirmed, `PendingActive` would start when the on-prem unit has committed and fulfilment evidence is not yet recorded, and `Active` would start when CAM records that evidence. This option does not confirm the names. |
| B | `Active` would start at commercial apply, before evidence. |

**Recommendation:** A. **Default:** A. **Alternatives:** B. **Status of this default:** Proposed, unaccepted, owner decision required. B would remove the waiting step the issue asks to name. Evidence returning to CAM is DRG-578 fork F7, which is also Proposed, unaccepted, owner decision required. No state is settled.

### Recommendation and default

The DRG-558 proposal is S1-A, S2-A, S3-A, S4-A, S5-A, S6-A, and S7-A, on top of cluster proposals C1-A through C4-A.

**Status of this combined default:** Proposed, unaccepted, owner decision required.

Under that proposal the eight names are unsettled candidates for a later on-prem commitment vocabulary. Nothing on `main` stores them. This frame does not add a column, a check constraint, or a writer, and it settles no state.

### Dependencies

| Dependency | Why it is named | What this frame does with it |
|---|---|---|
| DRG-577, Done | Linear `blockedBy`. The effect row lists candidate names. | Input text. No state is settled from it. |
| DRG-578, In Review | Would select an operation whose later evidence might move a candidate name. | Specified above. Proposed, unaccepted, owner decision required. |
| DRG-574 and `specs/conversions.md` | Propose `Surrendered`. | Alternative C of S1. The name is not settled. |
| DRG-429, Backlog | `ReconfigureFrom(hash)` starts a configuration session from a frozen item. | Related on the issue. It does not set a commitment status and does not select a fulfilment operation. |
| DRG-547, Backlog | Research-gap intake that harvested this issue. | Source context. Not a design. |
| DRG-466, Backlog | Orchestrator dependency for the cluster. | Stays a dependency. No event and no transport is decided. |
| DRG-460, Backlog | Names `EntitlementProvisioned`. | Alternative inside S2-C. Not decided. |
| OQ5 option A, OQ6 | Partial apply, and no revision after `EXECUTED`. | S2 and cluster C3. OQ6 `SUPERSEDED` is not a commitment state. |
| OQ7 | Thin header backfill, no synthetic history. | No backfill of commitment periods. OQ7 is not confirmed here. |
| S6, ADR-026, ADR-017 | Where an event or a call could later live. | Forks C2 and S2. No event and no transport is decided. |
| Accepted proration ADR, DRG-609 | Money and continuation design. | The candidate names do not reprice a line. DRG-609 is not accepted and is not required for S1–S7. |
| S3 / DRG-581, OQ18 | Digest library. | Not required to list candidate names. Not started here. |

### What it unblocks

While this frame is Proposed, unaccepted, owner decision required, DRG-558 unblocks nothing.

Owner confirmation would still authorize no code, migration, flag, or deploy, would settle no commitment state by itself, and would decide no event and no transport. It would not start DRG-429, DRG-460, DRG-466, DRG-574, DRG-547, the conversions spec, S3, or S6.

## Owner-decision table

Every row is Proposed, unaccepted, owner decision required. The owner confirms or replaces the default. Alternatives sit in the same row. No row decides an event, a transport, or a commitment state. DRG-466 remains a dependency on the rows that name it.

| Fork | Default | Alternatives | Status |
|---|---|---|---|
| C1 What this frame ships | A. This document only | B CAM-local request row. C `order-service` and `DecompositionRule` now | Proposed, unaccepted, owner decision required |
| C2 Transport | A. Name no transport | B Synchronous call (ADR-017, Proposed). C Kafka command (ADR-026, Proposed) plus an outbox publication. DRG-466 stays a dependency | Proposed, unaccepted, owner decision required |
| C3 Failure after commit | A. No revision, no automatic rollback, later correcting LifecycleChange | B Compensate every request (DRG-466 text; event and transport not decided). C Revise the executed document | Proposed, unaccepted, owner decision required |
| C4 Money | A. Use the Accepted proration ADR | B Recompute proration or uplift in fulfilment | Proposed, unaccepted, owner decision required |
| F1 `EXTEND` | A. Reserved name, no v1 emitter; term licence `REGENERATE` | B Drop the name. C Emit `EXTEND` for SaaS and for term licences | Proposed, unaccepted, owner decision required |
| F2 Quantity up and down | A. Both directions `MODIFY` | B Quantity-down emits `REVOKE` | Proposed, unaccepted, owner decision required |
| F3 Downgrade | A. Quantity-only caps; SKU replacement waits on DRG-576 | B Whole-entitlement `REVOKE`. C No Downgrade emitter at all | Proposed, unaccepted, owner decision required |
| F4 Decomposition key | A. `(licenseModel, action_code)`; relationship type optional | B Relationship type required now. C `DealType` selects the operation | Proposed, unaccepted, owner decision required |
| F5 Compensatability | A. Assume `REVOKE` and `REGENERATE` non-compensatable without accepting ADR-027 | B Every request compensated (DRG-466; event and transport not decided). C Accept ADR-027 here | Proposed, unaccepted, owner decision required |
| F6 Unmapped on-prem line | A. No dispatch; uncommitted failed unit `BLOCKED`; document `APPLY_BLOCKED` or `PARTIALLY_APPLIED` under OQ5; committed units preserved; no commitment state assigned | B Block the whole document. C Dispatch `PROVISION` anyway | Proposed, unaccepted, owner decision required |
| F7 Evidence | A. CAM would write commercial state; no command added | B Fulfilment component writes status | Proposed, unaccepted, owner decision required |
| F8 Revocation outside conversions | A. One `REVOKE`; DRG-574 would call it only after its own acceptance | B A second revocation mechanism. C Specify the DRG-574 payload here | Proposed, unaccepted, owner decision required |
| F9 When `REVOKE` is submitted | A. Leave partial-apply question 4 open | B Same step as `PROVISION`. C Delay recording until the window ends | Proposed, unaccepted, owner decision required |
| S1 Commitment states | A. Eight candidate names, none settled | B Add unsettled TMF names `created`, `cancelled`, `aborted`. C Add unsettled `Surrendered` | Proposed, unaccepted, owner decision required |
| S2 Fulfilment-required signal | A. Name no event and no transport | B Document-level signal, still no transport named. C DRG-460 or DRG-466 event name. DRG-466 stays a dependency | Proposed, unaccepted, owner decision required |
| S3 Writer, if a status is later confirmed | A. CAM would write it | B Orchestrator would write it (DRG-466 stays a dependency) | Proposed, unaccepted, owner decision required |
| S4 SaaS subscription row | A. Keep the read `V1_1` status check and ERP sync status | B Map those values onto S1 names | Proposed, unaccepted, owner decision required |
| S5 `InGrace` and `Expired` | A. Names only, if they stay in S1; no duration | B Define a grace length and an expiry job | Proposed, unaccepted, owner decision required |
| S6 `Superseded` versus OQ6 | A. Different facts, and the commitment name is not settled | B Store the replacement in the revision close status | Proposed, unaccepted, owner decision required |
| S7 `PendingActive` and `Active` | A. Waiting name until evidence, and only if those names are later confirmed | B `Active` at commercial apply, before evidence | Proposed, unaccepted, owner decision required |

## What remains outside this frame

- Accepting ADR-017 or ADR-024 through ADR-027.
- Deciding any event or any transport. DRG-466 stays a dependency.
- Settling any commitment state.
- Editing the Accepted proration ADR, the Accepted reconcile record, or ADR-035.
- Implementing DRG-465, DRG-466, DRG-460, DRG-495, DRG-556, DRG-557, DRG-574, or DRG-576.
- Starting S3 / DRG-581, confirming OQ18, or authorizing S6.
- Treating the DRG-609 design as accepted.
- Confirming OQ7, or synthesizing fulfilment history during header backfill.
- Choosing revoke submission timing (partial-apply open question 4) or a grace duration.
- Any flag change, including `opencrm.erp-sync.enabled` (`application.yml` lines 71–72, read). Any deploy.
