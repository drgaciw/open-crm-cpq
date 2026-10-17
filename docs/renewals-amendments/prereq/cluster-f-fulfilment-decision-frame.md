# Cluster F — Fulfilment decision frame

**Status: Proposed.** This file is a decision frame. It is not an ADR, and it is not accepted. The owner has not confirmed any fork below.

**Date:** 2026-10-17. **Reading base:** `main` at `0d71251`. **Project:** OPEN-CRM-CPQ. **Linear team:** DRG.

**Stories in this cluster:** [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals) (RA-44) and [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive) (RA-24). Both were Backlog on 2026-10-17. Neither issue had comments.

**This record authorizes no code, schema, migration, flag, topic, registry entry, gateway route, or deploy.** Accepting this frame would still authorize no code, migration, or deploy. That later act would be a separate owner decision.

Both issue descriptions include a suggested implementation step (a `DecompositionRule` type, and a fulfilment-required event). This frame records the decisions those steps would need. It does not perform the steps. Neither ticket is skipped: the titles are design gaps, and the descriptions do not ask this change to land code.

## Standing inputs

These are records already on `main`. This frame uses them. It does not reopen them, and it does not change their status.

| Input | Status on `main` | What this frame takes from it |
|---|---|---|
| [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) | **Accepted** 2026-10-16, owner approval recorded in #623 | The charge formula. Fulfilment does not compute `periodAmount`, stub days, or the Drools full-term ratio. No fork below reopens proration. |
| [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) | **Accepted** 2026-10-16, owner approval recorded in #623. ADR-024 through ADR-027 stay **Proposed**. | Spine wins. v1 fulfilment is an on-prem boundary. Mid-term SaaS applies in CAM with no order and no fulfilment saga. D-R5 is the ChangeLine projection this frame uses. |
| [ADR-017](../../adr/ADR-017-synchronous-inter-service-communication.md) | **Proposed** 2026-10-04 | No new synchronous inter-service call is chosen here. |
| [ADR-024](../../adr/ADR-024-order-orchestration-context.md), [ADR-025](../../adr/ADR-025-saga-process-manager.md), [ADR-026](../../adr/ADR-026-command-messages.md), [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) | **Proposed** 2026-10-04, each "Not accepted" | No `order-service`, `orders` schema, saga table, command topic, or point-of-no-return implementation. |
| OQ5 option A | **ADOPTED** 2026-10-16, first slice only, in [the open-questions register](../renewals-amendments-open-questions-register.md). `specs/partial-apply.md` is not accepted. | One unit per target, atomic units, no automatic rollback, `PARTIALLY_APPLIED` when a later unit fails, `APPLY_BLOCKED` when none have committed, correcting amendment as the remedy. S6 is not authorized. |
| OQ6 | **ADOPTED** 2026-10-16 under delegation by CPQ Decision, relayed by Projects Manager, recorded in #642 (merged to `main`). See [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md). | After `EXECUTED`, no revision (`IMMUTABLE_EXECUTED`). At most one revision may be `EXECUTED`. A fulfilment outcome does not revise the document. |
| OQ7 | **DEFAULT PROPOSED** for the slice: thin contract header only (S7), no synthetic history. See [oq7-header-backfill-owner-decision.md](oq7-header-backfill-owner-decision.md). | This frame does not confirm OQ7. It adds no synthetic entitlement history and no retroactive fulfilment rows. |
| OQ18 | **Proposed** until the owner confirms it. Unconfirmed proposal: canonical digest library in `backend/libs/common`. | This frame does not place that library and does not start the library work. |
| S3 / [DRG-581](https://linear.app/drgamtd-workspace/issue/DRG-581) | Not started by this frame. #642 has merged. S3 still needs separate owner authorization. Digest-library work still waits on OQ18. | No digest-library task is opened here. |
| S6 | Not authorized (OQ5 record and the OQ6 record). | No fulfilment event contract, topic, or outbox publication. |
| [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609) design | Not accepted. The note [story-drg-609-pricing-service-continuation-design.md](story-drg-609-pricing-service-continuation-design.md) is a design record, not an accepted design. | Fulfilment does not call pricing-service continuation and does not depend on that design. |
| [ADR-035](../adr-035-amendment-document-placement.md) | **Accepted** 2026-10-16 | Authorizes the first-slice schema and audit stories only (S1, S2). Ordering and fulfilment stay outside that acceptance. |

`specs/lifecycle-intents.md`, `specs/conversions.md`, `specs/partial-apply.md`, and `specs/maintenance.md` are proposals. Their enums are not the fulfilment key.

## Cluster note

DRG-578 and DRG-558 are one boundary. DRG-578 asks which fulfilment operation a change line selects. DRG-558 asks when a commitment line is waiting on that operation, and which status names describe the wait. The Accepted reconcile already places both stories on on-prem lines and leaves SaaS mid-term v1 on the CAM ledger plus the existing ERP sync. This frame specifies the remaining forks. It does not scaffold a service.

Linear still lists [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577/reconcile-proposed-order-model-adr-024-to-adr-027-with-the-amendment) as `blockedBy` for both issues. DRG-577 is Done. Its reconcile record says specification of DRG-578 and DRG-558 is unblocked, and that specification is design work, not an authorization to scaffold `order-service`. This frame is that specification, and it stays Proposed.

### Fork C1 — What this frame ships

| Option | Content |
|---|---|
| A | This document only. No type, table, topic, or service. |
| B | A CAM-local fulfilment-request row written when an on-prem unit commits. |
| C | `order-service` and a `DecompositionRule` implementation now, including DRG-465. |

**Recommendation:** A. **Default:** A. B and C need code. C also needs the owner to accept ADR-024 through ADR-027, which this frame does not do.

### Fork C2 — Transport

| Option | Content |
|---|---|
| A | No transport is chosen. The landed in-process `ErpAdapter` remains the SaaS ERP simulation. |
| B | A new synchronous call, under ADR-017. |
| C | A Kafka command with a registered reply, under ADR-026, plus an outbox event. |

**Recommendation:** A. **Default:** A. ADR-017 and ADR-026 are Proposed. S6 is not authorized, so C's event is out of scope. The Accepted reconcile D-R4 already says no v1 command applies commercial state in CAM.

### Fork C3 — Failure after a unit has committed

| Option | Content |
|---|---|
| A | The executed document stays immutable (OQ6). Committed units stay committed (OQ5 option A). The remedy is a later correcting LifecycleChange. This frame defines no exception queue. |
| B | Roll the committed unit back and compensate every fulfilment request (DRG-466's acceptance criteria). |
| C | Open a new revision of the executed document. |

**Recommendation:** A. **Default:** A. B contradicts OQ5 option A. C contradicts OQ6 `IMMUTABLE_EXECUTED`. ADR-027's hold and exception queue stay inside that Proposed ADR.

### Fork C4 — Money

| Option | Content |
|---|---|
| A | Fulfilment consumes the unit the commercial apply already priced. The Accepted proration ADR remains the formula. |
| B | Recompute the stub, the full-term ratio, or uplift inside the fulfilment operation. |

**Recommendation:** A. **Default:** A. B would reopen the Accepted proration ADR. This frame does not.

Shared consequence of C1–C4: while this frame is Proposed, it unblocks no implementation story. Owner confirmation of the defaults would still authorize no code, migration, or deploy.

## DRG-578 — Action-aware fulfilment decomposition

**Issue:** [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals). **Status read:** Backlog. **Relation read:** `blockedBy` DRG-577 (Done). The issue body also names DRG-465, DRG-466, DRG-558, DRG-460, DRG-495, DRG-574, DRG-556, DRG-557, and DRG-577. Those links are not `relatedTo` rows on the issue.

### Problem

No fulfilment code exists. After acceptance, CAM queues a simulated ERP sync and, for a new subscription that is still `PENDING`, activates it when that sync completes. A typed amendment, expansion, or renewal re-queues ERP sync on the same subscription. That path does not select an operation from the change-line action.

The gap the issue names: Add provisions; a quantity update modifies the entitlement or seat cap; Renew extends a SaaS end date and regenerates a term licence; Upgrade and Swap provision the new entitlement and revoke the old one inside a dual-entitlement window, create before terminate; Downgrade, Remove, and Terminate revoke or cap at the effective date; Suspend and Resume disable and enable. Revocation outside conversions (DRG-574) has no separate home. DRG-465 maps a commercial product to fulfilment items and does not key on the change-line action. DRG-558 names a fulfilment step and a status set and does not select the operation.

The issue's suggested next step is to extend `DecompositionRule` and to mark `REVOKE` and `REGENERATE` non-compensatable per ADR-027 D-02. ADR-027 is Proposed, so that sentence is not a rule this frame can adopt as accepted. Fork F5 records the classification the Accepted reconcile already allows DRG-578 to assume.

### What is already settled on `main`

| Fact | Path |
|---|---|
| No `order-service` and no fulfilment service. Services on `main`: `ai-service`, `approval-routing`, `configuration-engine`, `contract-asset-management`, `crm-service`, `document-generation`, `gateway-service`, `pricebook-service`, `pricing-service`, `product-service`, `quote-service`. A search of `product-service` Java and SQL for `relationship` returned no matches on this reading. | `backend/services/` |
| `ErpAdapter.syncToErp` sleeps and returns `ERP-` plus the subscription number. | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/acl/ErpAdapter.java` lines 23–36 |
| `ErpSyncJob` claims `QUEUED`, calls the adapter outside the transaction, stores the ERP id, sets `SYNCED`, and calls `activate()` only when status is `PENDING`. The job is on unless `opencrm.erp-sync.enabled` is `false`. This frame does not change that flag. | `.../scheduler/ErpSyncJob.java` lines 28–46 and 131–137 |
| Accepted deal types are `NEW`, `AMENDMENT`, `EXPANSION`, `RENEWAL`. `NEW` inserts `PENDING` and `erpSyncStatus = QUEUED`. `mutate` re-queues ERP sync. A `RENEWAL` also sets `endDate` and `renewalDate` on the target. An already `ACTIVE` target is not passed back through `PENDING`. | `.../service/AcceptedQuoteSubscriptionService.java` lines 53, 104–109, 128–139, 202–210 |
| The amendment append command carries no order, saga, or fulfilment identity. The audit publication carries no fulfilment payload. | `.../documents/events/AmendmentEventAppend.java` line 11; `.../documents/audit/AmendmentAuditPublication.java` line 11 |
| v1 raises an order only for on-prem lines. A mid-term SaaS amendment applies in CAM with no order and no fulfilment saga. `licenseModel = Subscription` with `apply_target_type = SUBSCRIPTION` is the SaaS path. Term licence, perpetual, maintenance, capacity, hardware, and `ENTITLEMENT` are the on-prem path. | [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) D-R2 |
| ChangeLine action codes project to order-line actions and fulfilment operations only for lines D-R2 sends to an order. Quote `DealType` is not the selector. Create before terminate still orders units. | Same file, D-R5, and [amendment-document-design-spike.md](../amendment-document-design-spike.md) section 4, item 3 |
| The D-R5 projection this frame uses as input: `Add` → `PROVISION`; `Update` → `MODIFY`; `Remove` and `Terminate` → `REVOKE`; `NoChange` → none; `Renew` → `REGENERATE` for a term licence, and a SaaS renewal extends the commitment in CAM and raises no order; `Upgrade` and `Swap` → `PROVISION` then `REVOKE`; `Downgrade` → `MODIFY` and then "REVOKE or cap"; `ConvertMetric` → `REGENERATE`; `Suspend` → `SUSPEND`; `Resume` → `RESUME`. | Same file, D-R5 table |
| The effect row also lists `EXTEND` among operations DRG-578 may emit. No D-R5 row emits `EXTEND`. | Same file, "Effect on open stories" |
| `REVOKE` and `REGENERATE` may be assumed non-compensatable. ADR-027 stays Proposed. ADR-027 D-02, Proposed, places the point of no return at dispatch of the first non-compensatable request. | Reconcile D-R5 last paragraph; [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) D-02 |
| CAM writes the commercial result. A future order component, after the owner accepts it, sends fulfilment commands for on-prem lines and receives evidence back. There is no v1 `ApplyAmendmentUnit` command. | Reconcile D-R4 |
| S2's event stays document id, amendment sequence, and digest. It carries no fulfilment payload. | Reconcile D-R7 |
| OQ5 option A and OQ6 apply as in the standing inputs. Revoke submission timing is still an open question in the unaccepted partial-apply proposal. | Register; [partial-apply.md](specs/partial-apply.md) open question 4 |

### Forks

#### F1 — Who emits `EXTEND`

| Option | Content |
|---|---|
| A | `EXTEND` stays a reserved operation name. No v1 ChangeLine emits it. A SaaS renewal extends dates in CAM under the landed `mutate` path and raises no fulfilment request. A term-licence `Renew` emits `REGENERATE`. |
| B | Drop `EXTEND` from the operation set. |
| C | Emit `EXTEND` for SaaS renewals, and for term licences in place of `REGENERATE`. |

**Recommendation:** A. **Default:** A. A keeps the name the Accepted effect row already lists, and it follows D-R5: SaaS renewal raises no order, and a term licence regenerates. B would narrow that effect row. C contradicts D-R2 and D-R5.

#### F2 — Quantity up and quantity down

| Option | Content |
|---|---|
| A | `Update` emits `MODIFY` in both directions. Quantity-down caps the entitlement or seat count at the effective date. It does not emit `REVOKE`. |
| B | Quantity-down emits `REVOKE` for the delta. |

**Recommendation:** A. **Default:** A. The Accepted `Update` row is `MODIFY`. B adds an operation that row does not name.

#### F3 — What the Downgrade disjunction means

The Accepted D-R5 row says Downgrade → `MODIFY` → "REVOKE or cap at the effective date." This fork interprets that disjunction. It does not edit the reconcile file.

| Option | Content |
|---|---|
| A | A quantity-only downgrade is F2's `MODIFY` (cap at the effective date). A downgrade that replaces the SKU uses the Upgrade/Swap shape, `PROVISION` then `REVOKE`, and waits until a catalog relationship says the pair is allowed (DRG-576). Until that relationship exists, a SKU-replacement downgrade has no emitter. |
| B | Every Downgrade emits `REVOKE` of the whole entitlement. |
| C | Downgrade has no emitter until the owner picks, including quantity-only. |

**Recommendation:** A. **Default:** A. Quantity-only work is already covered by the `Update` row. SKU replacement is the create-before-terminate shape, and product relationships are not on `main`.

#### F4 — Decomposition key

| Option | Content |
|---|---|
| A | For lines D-R2 sends toward fulfilment: `(licenseModel, ChangeLine action_code)`, using the D-R5 codes. Product id and relationship type are optional qualifiers. They are unused until DRG-576 supplies relationship rows. |
| B | The key requires a relationship type now. |
| C | Quote `DealType` selects the operation. |

**Recommendation:** A. **Default:** A. The issue asks for product or relationship type as part of the key. Those qualifiers stay optional so v1 is not blocked on a catalog that has no relationship rows. C contradicts D-R5, which says `DealType` is not the selector. The action codes in `specs/lifecycle-intents.md` (`Delete`, `Surrender`, `Convert`, and no `Upgrade` / `Downgrade` / `Swap` / `ConvertMetric`) are a proposal. They are not this key.

#### F5 — Compensatability

| Option | Content |
|---|---|
| A | Assume `REVOKE` and `REGENERATE` are non-compensatable, which the Accepted reconcile permits DRG-578 to assume. The source text is Proposed ADR-027 D-02. Assuming the class does not accept ADR-027. `PROVISION`, `MODIFY`, `SUSPEND`, `RESUME`, and reserved `EXTEND` are not given a class in this frame. |
| B | Every request has a compensation, matching DRG-466's acceptance criteria. |
| C | Accept ADR-027 as part of this frame. |

**Recommendation:** A. **Default:** A. B contradicts the reconcile sentence and OQ5 option A's refusal of automatic rollback. C would accept a Proposed ADR, which this frame does not do.

#### F6 — Unmapped on-prem line

| Option | Content |
|---|---|
| A | No fulfilment request is dispatched. Units that have already committed stay committed (OQ5 option A). SaaS units on the same document are unchanged. The on-prem commitment, once the DRG-558 vocabulary is in force, stays `PendingActive`. |
| B | The whole document is blocked, including committed SaaS units. |
| C | Dispatch `PROVISION` for an unmapped product. |

**Recommendation:** A. **Default:** A. DRG-465 says an unmapped product blocks the order. A narrows that block so it does not undo a committed unit. B contradicts OQ5 option A. C hides a missing rule.

#### F7 — Where evidence goes

| Option | Content |
|---|---|
| A | A future fulfilment component returns evidence. CAM writes entitlement state periods. This frame adds no command and no writer. |
| B | The fulfilment component writes subscription status and commitment status. |

**Recommendation:** A. **Default:** A. D-R1 and D-R4 keep CAM as the single writer of applied state. B would add a second writer while ADR-024 through ADR-027 are still Proposed.

#### F8 — Revocation outside conversions

| Option | Content |
|---|---|
| A | One `REVOKE` operation. `Remove`, `Terminate`, and the old line of `Upgrade` or `Swap` use it. When the owner accepts a conversion proposal, DRG-574's surrender is a caller of that same operation after the dual-use window. This frame does not add `Surrender` or `Surrendered`. |
| B | Conversions get a second revocation mechanism, separate from `REVOKE`. |
| C | This frame specifies DRG-574's request payload. |

**Recommendation:** A. **Default:** A. The issue says revocation outside conversions is uncovered. A gives it the operation DRG-578 already names, and leaves surrender status on DRG-574. `specs/conversions.md` is Proposal only.

#### F9 — When `REVOKE` is submitted

| Option | Content |
|---|---|
| A | This frame does not choose. `REVOKE` is a separate unit from `PROVISION`. Create-before-terminate still orders the units. The unaccepted partial-apply proposal's open question 4 (record the revoke now with a future entitlement end, or delay submission until that date) stays open. |
| B | Dispatch `REVOKE` in the same step as `PROVISION`. |
| C | Always delay recording the revoke unit until the window ends. |

**Recommendation:** A. **Default:** A. B can end the old entitlement before the dual-use window. C closes an open question this cluster does not own. The window length stays with DRG-341 and the ramp work. This frame does not set it.

### Recommendation and default

The DRG-578 default is F1-A, F2-A, F3-A, F4-A, F5-A, F6-A, F7-A, F8-A, and F9-A, on top of cluster defaults C1-A through C4-A.

Operation set under that default, for on-prem lines only: `PROVISION`, `MODIFY`, `REVOKE`, `REGENERATE`, `SUSPEND`, `RESUME`, plus reserved `EXTEND` with no v1 emitter. SaaS mid-term v1 emits none of them.

### Owner decision needed

Confirm or replace each of F1–F9. Until that confirmation, the defaults are a proposal. Confirming them authorizes no `DecompositionRule`, no request table, and no order-service.

### Dependencies

| Dependency | Why it is named | What this frame does with it |
|---|---|---|
| DRG-577, Done | Linear `blockedBy`. Accepted reconcile is the boundary. | Used as input. Not reopened. |
| ADR-024 through ADR-027, Proposed | DRG-465, DRG-466, and any orchestrator wait on owner acceptance. | Not accepted here. |
| DRG-465, Backlog, parent DRG-290 | Product-to-item decomposition, versioned rules, `OrderDecomposed`. | Not the action key. Not started. |
| DRG-466, Backlog | Saga, a compensation per request, `FulfillmentDispatched`. | Its acceptance criteria are not the default (forks C3 and F5). Not started. |
| DRG-460, Backlog | `ProvisionEntitlement` and an `EntitlementProvisioned` event. | Event stays out (S6). Not started. |
| DRG-495, Backlog | `LicenseRequired` pauses a fulfilment saga. | Pause behaviour stays inside Proposed ADR-027. Not started. |
| DRG-576, Backlog | Catalog product relationship and lifecycle-eligibility model. | Optional qualifier only (F3, F4). |
| DRG-556, DRG-557, Backlog | Perpetual-plus-maintenance, and capacity meters. | A perpetual line is not commercially renewed here. Maintenance renewal and true-up stay on those issues. |
| DRG-574, Backlog | Surrender and revocation after a dual-use window. | Caller of `REVOKE` only after its own proposal is accepted (F8). |
| DRG-558 | Status of the line while the operation is outstanding. | Specified in the next section. Still Proposed. |
| DRG-341 | Dual-entitlement window. | Window length is not set here (F9). |
| OQ18, S3 / DRG-581 | Digest library. | A future request may copy the digest the Accepted reconcile already stores as replay data (D-R3). This frame does not build the library and does not start S3. |
| Accepted proration ADR | Charge formula. | Not reopened (C4). |
| DRG-609 design | Pricing continuation. | Not accepted, and not a dependency of this default. |

### What it unblocks

While this frame is Proposed, DRG-578 unblocks nothing.

Owner confirmation of the defaults would allow a later implementation story to key on-prem decomposition on `(licenseModel, action_code)` and to emit the operation set above. That later story still waits on owner acceptance of ADR-024 through ADR-027 before any service, schema, or topic. Confirmation does not unblock DRG-465, DRG-466, DRG-460, DRG-495, DRG-574, DRG-556, DRG-557, S3, or S6.

## DRG-558 — Entitlement fulfilment step and commitment state

**Issue:** [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive). **Status read:** Backlog. **Relation read:** `blockedBy` DRG-577 (Done). `relatedTo`: [DRG-429](https://linear.app/drgamtd-workspace/issue/DRG-429/bc-02-06-reconfigure-from-an-existing-hash) (Backlog), [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574/perpetual-licence-surrender-and-entitlement-revocation-in-conversions) (Backlog), [DRG-547](https://linear.app/drgamtd-workspace/issue/DRG-547/research-gap-intake-harvest-from-cpq-market-research) (Backlog).

### Problem

Applying a change updates the subscription. There is no licence regeneration step after renewal, upgrade, or metric conversion, and there is no commitment state model on the entitlement. The issue asks for a fulfilment-required signal and a commitment status set: `PendingActive`, `Active`, `Suspended`, `InGrace`, `PendingTerminate`, `Terminated`, `Expired`, `Superseded`. Market evidence in the issue (Revenera fulfilment operations, Thales regenerate, Snow upgrade quantity, TMF637 product status) is evidence. It is not the enum to copy.

The suggested next step emits a fulfilment-required event. S6 is not authorized, so that event is not the default. Fork S2 records the fact without a topic.

### What is already settled on `main`

| Fact | Path |
|---|---|
| Subscription status on the SaaS record is `PENDING`, `ACTIVE`, `SUSPENDED`, `CANCELLED`, `EXPIRED`. | `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/SubscriptionStatus.java` lines 3–8 |
| No CAM production assignment of `SubscriptionStatus.EXPIRED` was found on this reading. Quote expiry (`QuoteStatus.EXPIRED` in `QuoteService`) is a different status. The enum value is not a commitment lifecycle. | CAM `src/main/java` search for `SubscriptionStatus.EXPIRED` and `setStatus(SubscriptionStatus` |
| ERP sync status is `PENDING`, `QUEUED`, `IN_PROGRESS`, `SYNCED`, `FAILED`. It is not a commitment status. | `.../domain/ErpSyncStatus.java`; `ErpSyncJob` as cited under DRG-578 |
| Activation of a `PENDING` subscription happens inside `ErpSyncJob` after a simulated ERP id is stored. An `ACTIVE` renewal stays `ACTIVE` and is re-queued. | `ErpSyncJob.java` lines 135–137; `AcceptedQuoteSubscriptionService.java` lines 202–207 |
| The Accepted reconcile's effect on DRG-558: the fulfilment-required signal and the eight status names apply to on-prem commitment lines. The signal fires when an on-prem unit has been applied and fulfilment is required. A SaaS subscription unit in v1 does not fire it. | [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md), "Effect on open stories" |
| CAM is the single writer of applied commercial state. | Same file, D-R1 and D-R4 |
| Research vocabulary for `CommitmentLine.status` is the same eight names. `Superseded` there means replaced by a renewal or a new contract. That research file is not an accepted model. | [cpq-renewals-amendments-research.md](../cpq-renewals-amendments-research.md) section 7.2 |
| OQ6 `SUPERSEDED` is `status_at_close` on a revision that was superseded before execution. | [oq6-revision-model-owner-decision.md](oq6-revision-model-owner-decision.md) section 2 |
| OQ5 option A: a later unit's failure does not roll back an earlier committed unit. | Open-questions register, OQ5 row |
| `AmendmentEventAppend` carries no fulfilment identity. | `AmendmentEventAppend.java` line 11 |
| The first-slice requirements leave fulfilment out of the SaaS Add/Update/Remove slice. | [amendment-first-slice-requirements-and-acceptance.md](../amendment-first-slice-requirements-and-acceptance.md), slice definition |
| `Surrendered` is proposed on the conversions spec for DRG-574. That spec is Proposal only. | [conversions.md](specs/conversions.md) scope item 3 |

### Forks

#### S1 — Status set

| Option | Content |
|---|---|
| A | The eight names the Accepted reconcile already assigns to on-prem commitment lines: `PendingActive`, `Active`, `Suspended`, `InGrace`, `PendingTerminate`, `Terminated`, `Expired`, `Superseded`. |
| B | Copy TMF637 as well: `created`, `cancelled`, `aborted`, beside the eight. |
| C | Add `Surrendered` now. |

**Recommendation:** A. **Default:** A. The reconcile effect row already names the eight. B copies an illustrative external list the issue itself calls illustrative. C belongs to the unaccepted conversions proposal and to DRG-574. `Surrendered` can be added later without renaming the eight.

#### S2 — What the fulfilment-required signal is

| Option | Content |
|---|---|
| A | A per-unit fact, recorded when that on-prem unit has committed and fulfilment is still required. No event, no outbox row, no topic, no schema-registry entry. |
| B | One document-level event when the document reaches `APPLIED`. |
| C | Emit `EntitlementProvisioned` (DRG-460) or `FulfillmentDispatched` (DRG-466) now. |

**Recommendation:** A. **Default:** A. A matches the reconcile effect row (the signal fires when an on-prem unit has been applied) and OQ5 option A (a committed unit does not wait for later units). B delays the fact until full apply and would be an event. C is an event contract. S6 is not authorized, and ADR-026 is Proposed.

#### S3 — Who writes the status

| Option | Content |
|---|---|
| A | CAM writes the commercial commitment status. A future fulfilment component supplies evidence and does not write the status. |
| B | The fulfilment orchestrator writes the status. |

**Recommendation:** A. **Default:** A. D-R1 keeps a single writer. B is a second writer, and the orchestrator is DRG-466, which this frame does not start.

#### S4 — SaaS records in v1

| Option | Content |
|---|---|
| A | A SaaS subscription unit keeps `SubscriptionStatus` and `ErpSyncStatus`. It does not enter the eight-name set, and it does not raise the S2 fact. |
| B | Map SaaS `PENDING` / `ACTIVE` / `SUSPENDED` / `CANCELLED` / `EXPIRED` onto the eight names now. |

**Recommendation:** A. **Default:** A. The reconcile effect row says a SaaS subscription unit in v1 does not fire the signal. The landed ERP path stays the SaaS activation path.

#### S5 — `InGrace` and `Expired`

| Option | Content |
|---|---|
| A | The names are in the set. This frame sets no grace length, no expiry job, and no assignment rule. |
| B | Define the grace length and the job that moves a line to `Expired`. |

**Recommendation:** A. **Default:** A. Duration belongs with the renewal-policy work. This cluster will not invent a number of days. `SubscriptionStatus.EXPIRED` existing on the SaaS enum is not this rule.

#### S6 — `Superseded` and OQ6

The word `SUPERSEDED` is already used.

| Option | Content |
|---|---|
| A | Commitment `Superseded` means the entitlement was replaced (upgrade, swap, or a successor line). OQ6 `SUPERSEDED` remains `status_at_close` on a pre-execution revision. They are different facts. |
| B | Store both in the revision close status. |

**Recommendation:** A. **Default:** A. OQ6 is already adopted for document revisions. Reusing that column for an entitlement would mix a document fact with a delivery fact. A fulfilment outcome still does not revise an executed document (cluster fork C3).

#### S7 — When `PendingActive` and `Active` start

| Option | Content |
|---|---|
| A | `PendingActive` starts when the on-prem unit has committed and fulfilment evidence is not yet recorded. `Active` starts when CAM records that evidence. |
| B | `Active` starts at commercial apply, before evidence. |

**Recommendation:** A. **Default:** A. B removes the step this issue exists to name. Evidence flowing back to CAM is DRG-578 fork F7.

### Recommendation and default

The DRG-558 default is S1-A, S2-A, S3-A, S4-A, S5-A, S6-A, and S7-A, on top of cluster defaults C1-A through C4-A.

Under that default the eight names are a vocabulary for on-prem commitment lines. Nothing on `main` stores them yet. This frame does not add a column, a check constraint, or a writer.

### Owner decision needed

Confirm or replace each of S1–S7. Confirming the defaults authorizes no status column, no event, and no fulfilment job.

### Dependencies

| Dependency | Why it is named | What this frame does with it |
|---|---|---|
| DRG-577, Done | Linear `blockedBy`. The eight names and the on-prem signal boundary come from the Accepted reconcile. | Used as input. |
| DRG-578 | Selects the operation whose evidence moves `PendingActive` to `Active`. | Specified above. Still Proposed. |
| DRG-574 and `specs/conversions.md` | `Surrendered` and revocation after the dual-use window. | Not in the default set (S1). |
| DRG-429, Backlog | `ReconfigureFrom(hash)` starts a configuration session from a frozen item. | Related on the issue. It does not set commitment status and does not select a fulfilment operation. Its acceptance criteria are not adopted. |
| DRG-547, Backlog | Research-gap intake that harvested this issue. | Source context. Not a design. |
| DRG-460, DRG-466 | Events and an orchestrator. | Not the S2 signal. Not started. |
| OQ5 option A, OQ6 | Partial apply, and no revision after `EXECUTED`. | S2 and cluster C3. |
| OQ7 | Thin header backfill, no synthetic history. | No backfill of commitment periods for existing subscriptions. OQ7 is not confirmed here. |
| S6, ADR-026, ADR-017 | Event and call transports. | Not chosen (C2, S2). |
| Accepted proration ADR, DRG-609 | Money and continuation design. | The status set does not reprice a line. DRG-609 is not accepted and is not required for S1–S7. |
| S3 / DRG-581, OQ18 | Digest library. | Not required to name these statuses. Not started here. |

### What it unblocks

While this frame is Proposed, DRG-558 unblocks nothing.

Owner confirmation of the defaults would allow a later story to use the eight names for on-prem commitment lines and to treat fulfilment-required as a per-unit fact with no event. That later story still writes no event and still waits on owner acceptance of ADR-024 through ADR-027 before any orchestrator. Confirmation does not unblock DRG-429, DRG-460, DRG-466, DRG-574, DRG-547, the conversions spec, S3, or S6.

## What remains outside this frame

- Accepting ADR-017 or ADR-024 through ADR-027.
- Editing the Accepted proration ADR, the Accepted reconcile record, or ADR-035.
- Implementing DRG-465, DRG-466, DRG-460, DRG-495, DRG-556, DRG-557, DRG-574, or DRG-576.
- Starting S3 / DRG-581, confirming OQ18, or authorizing S6.
- Treating the DRG-609 design as accepted.
- Confirming OQ7, or synthesizing fulfilment history during header backfill.
- Choosing revoke submission timing (partial-apply open question 4) or a grace duration.
- Any flag change, including `opencrm.erp-sync.enabled`. The landed ERP sync job stays as it is.
