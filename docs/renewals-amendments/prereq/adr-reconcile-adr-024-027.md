# Reconcile ADR-024 to ADR-027 with the amendment spine

**Status:** Accepted 2026-10-16 (owner approval in chat, 4:26 PM CT)
**Acceptance scope:** This status-change record implements the owner approval recorded in PR #623. Pre-acceptance workflow references below do not override the Accepted status. Runtime implementation, migrations and flag rollout remain separate work; this record does not accept ADR-024 through ADR-027.
**Date:** 2026-10-16
**Story:** [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577/reconcile-proposed-order-model-adr-024-to-adr-027-with-the-amendment) (RA-43), project OPEN-CRM-CPQ, team DRG.
**Deciders:** Project owner (accepted 2026-10-16). The ruling in this file is CPQ Decision, recorded on DRG-577 on 2026-10-16, medium confidence 0.7.
**Relates to:** the amendment spine ([field schema](../amendment-entity-field-schema.md), [design spike](../amendment-document-design-spike.md), [mid-term policy spec](../mid-term-amendment-policy-spec.md), [transcript](../cpq-project-transcript.md)); [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals); [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive); [DRG-580](https://linear.app/drgamtd-workspace/issue/DRG-580/s2-hash-chained-append-only-amendment-event-writer-verifier-and-audit) (S2).
**Does not authorize:** code, schema, service, topic, registry entry, gateway route, feature flag, merge, or deploy. `opencrm.contract.subscription-source` is not introduced. ADR-024 to ADR-027 stay documentation until the owner accepts them.

---

## Context

ADR-024 to ADR-027 were proposed on 2026-10-04, before the spine-plus-policies design. There is no order or fulfilment service under `backend/services/`. Accepting those four ADRs as written would place a second apply path and a second numbering scheme beside the spine.

DRG-577 records four conflicts:

1. **Order source and key.** ADR-024 D-03 keys a contract-sourced order on `(CONTRACT, contract id, amendment number)` and listens for an undefined `ContractActivated`. The spine assigns a gapless `{contract_number}-A{nnn}` at EXECUTED (OQ4, resolved 2026-10-16).
2. **Apply trigger.** ADR-024 D-02 moves subscription activation to an `ActivateSubscription` command behind `subscription-source = quote-accepted | order`. OQ2, resolved 2026-10-16, applies changes only after EXECUTED, on a single `status`. Contract-asset-management (CAM) is the single writer of applied state. Landed code still applies on `QuoteAccepted`.
3. **Action vocabulary.** ADR-024 D-07 line actions (`ADD`, `MODIFY`, `RENEW`, `CANCEL`, `SUSPEND`, `RESUME`), selected by quote `DealType`, do not match ChangeLine action codes or the nine LifecycleChange intents.
4. **Stale premise.** ADR-024's context says CAM ignores `dealType`, so an amendment creates a second subscription. Landed `AcceptedQuoteSubscriptionService` applies typed `NEW`, `AMENDMENT`, `EXPANSION` and `RENEWAL` against `subscriptionChange.targetSubscriptionNumber`, with revision and digest checks. ADR-024 O-4 is answered for that event by the same snapshot.

The mid-term policy spec sections 3 and 8 still describe OQ2 as open. The field schema (section 7 and the resolved-decisions note) and the project transcript record OQ2 and OQ4 as resolved on 2026-10-16. This proposal follows that resolved record.

## Ruling

CPQ Decision, Option B, recorded on DRG-577:

- The amendment spine wins.
- The order model narrows to on-prem lines.
- Mid-term SaaS amendments apply with no order in v1.
- S2 (DRG-580) stays additive. Its event stays minimal: document id, amendment sequence, and digest.
- Rationale: the change is additive and reversible, S2 stays small, and on-prem fulfilment can wait.
- If the owner objects, the recorded fallback is Option C. The ruling names Option C and does not specify it. This draft does not invent Option C.
- Conditions from the ruling: ADR-024 to ADR-027 remain Proposed until the owner accepts them; the S2 event stays additive; Option B is flagged to the owner at the next ADR acceptance round. The ruling unblocks drafting of DRG-580 on that minimal event. It does not authorize an `order-service` implementation.

This proposal uses that ruling. It unblocks [DRG-578](https://linear.app/drgamtd-workspace/issue/DRG-578/action-aware-fulfilment-decomposition-for-amendments-and-renewals) and [DRG-558](https://linear.app/drgamtd-workspace/issue/DRG-558/entitlement-fulfilment-step-and-commitment-state-model-pendingactive): those stories can be specified against the on-prem boundary below. Specifying them is design work. It is not an authorization to scaffold order-service.

## Decision

### D-R1 — The spine is the commercial record

One AmendmentDocument per LifecycleChange is the legal and evidentiary record. CAM owns the document, its lines, revisions, application units, and the append-only event log. The CAM ledger is the single writer of applied rate periods and subscription state.

Apply runs after the document reaches `EXECUTED`. v1 has one `status` column. The parallel `apply_status` column is out of v1 (OQ2).

The human number `{contract_number}-A{nnn}` is assigned in the EXECUTED transaction as `last_executed_seq + 1`. It does not reset on renewal (OQ4). Drafts keep a provisional number. The idempotency identity of an executed amendment is the document id plus `amendment_seq`.

### D-R2 — The order model narrows to on-prem lines

v1 raises an order only for on-prem lines on an executed document. A mid-term SaaS amendment applies in CAM with no order and no fulfilment saga.

| Line | v1 path |
|---|---|
| `licenseModel = Subscription` and `apply_target_type = SUBSCRIPTION` | CAM applies the unit after EXECUTED. No order. |
| `licenseModel` in {`TermLicense`, `Perpetual`, `Maintenance`, `Capacity`, `Hardware`}, or `apply_target_type = ENTITLEMENT` | After EXECUTED, one order line. Fulfilment is DRG-578. Commitment state is DRG-558. |
| `licenseModel = Usage` | Follows `apply_target_type`: `ENTITLEMENT` is on-prem; `SUBSCRIPTION` is the SaaS path. |
| `apply_target_type = NONE` | Referenced only. No order line and no ledger write. |
| `apply_target_type = ONE_TIME` on a document that also has an on-prem unit (true-up charge, services beside a licence) | Order line on that same on-prem order. The charge is not a ledger rate period. |
| `ONE_TIME` on a SaaS-only document | No order in v1. Billing stays with the still-Proposed Quote-to-Revenue saga and is outside this proposal. |
| `action_code = NoChange` | No order line and no ledger mutation. |

A mixed document has one signature and one amendment number. SaaS units apply in CAM. On-prem units on the same document raise order lines. A parked on-prem line leaves the SaaS units applied. Partial application across units stays the open question OQ5; this proposal does not close it. Create-before-terminate (spine V12) still orders the units: the new entitlement or subscription exists before the old one is revoked.

ADR-024 D-03's standard-terms quote source for a new sale is unchanged by this amendment reconciliation. It remains inside Proposed ADR-024.

### D-R3 — On-prem order source and key

The source of an on-prem order is the executed AmendmentDocument, or the outbox event of that execution. `ContractActivated` is not the trigger. ADR-024 D-03's key `(CONTRACT, contract id, amendment number)` is retired for amendments.

| Level | Idempotency key |
|---|---|
| Order header | `(DOCUMENT, document id, amendment_seq)` |
| Application unit / order line | `(document id, unit_no)`, the spine V13 apply identity; store the content digest as replay data, no-op on a matching digest and reject/audit a different digest |

`orderId` stays a UUID, as ADR-024 D-06 states. Submitting the same executed document twice returns the same order. A second executed amendment on the same contract is a new document, a new sequence, and a new order.

### D-R4 — One apply trigger

CAM applies each ApplicationUnit after EXECUTED, inside the ledger transaction, idempotent on `(document id, unit_no)`, with the content digest stored and checked on replay. A matching digest is a no-op; a different digest is rejected and audited.

There is no v1 `ApplyAmendmentUnit` command from order-service to CAM. That command would be a second writer. Order-service, once the owner accepts it, sends fulfilment commands for on-prem lines and receives entitlement evidence back. CAM writes the commercial result.

`ActivateSubscription` and `opencrm.contract.subscription-source` are not the v1 path for mid-term SaaS. Both stay inside Proposed ADR-024. This proposal does not add the switch.

Landed behaviour continues until the spine is built: `AcceptedQuoteSubscriptionService` applies `QuoteAccepted` in the listener transaction. The spike's target, when the spine exists, is that `QuoteAccepted` moves the document to EXECUTED only when the signature policy is click-through, notice-only, or not required. Every other signature policy waits for EXECUTED before apply.

### D-R5 — Action vocabulary

LifecycleChange keeps nine intents: `Renew`, `Amend`, `Cancel`, `Restructure`, `Transfer`, `Suspend`, `Resume`, `TrueUp`, `Convert`. An intent selects the document and the allowed ChangeLine set. It is not an order-line action.

Quote `DealType` (`NEW`, `AMENDMENT`, `RENEWAL`, `EXPANSION`) remains the landed quote enum. It is not the selector for ChangeLine actions or for order-line actions.

Order-line actions stay the six names in ADR-024 D-07. They are a projection of ChangeLine codes, used only for lines that D-R2 sends to an order. Fulfilment operations are the set DRG-578 names. DRG-578 may use this table as its decomposition key together with product or relationship type and `licenseModel`.

| ChangeLine `action_code` | Order-line action (on-prem only) | Fulfilment operation |
|---|---|---|
| `Add` | `ADD` | `PROVISION` |
| `Update` | `MODIFY` | `MODIFY` (entitlement or seat cap, quantity up or down) |
| `Remove` | `CANCEL` | `REVOKE` |
| `NoChange` | none | none |
| `Renew` | `RENEW` | `REGENERATE` for a term licence. A SaaS renewal extends the commitment in CAM under the renewal policy and raises no order in v1. |
| `Upgrade` | `ADD` on the new line, then `CANCEL` on the old line | `PROVISION`, then `REVOKE`, inside the dual-entitlement window. V12: create before terminate. |
| `Downgrade` | `MODIFY` | `REVOKE` or cap at the effective date |
| `Swap` | `ADD` then `CANCEL` | `PROVISION` then `REVOKE` (V12) |
| `ConvertMetric` | `MODIFY` | `REGENERATE` |
| `Suspend` | `SUSPEND` | `SUSPEND` |
| `Resume` | `RESUME` | `RESUME` |
| `Terminate` | `CANCEL` | `REVOKE` |

Intent notes for the same table:

- `TrueUp` is on-prem: an `ENTITLEMENT` unit plus a `ONE_TIME` charge, so it raises an order.
- `Convert` and `Restructure` split per line. The new SaaS subscription unit applies in CAM. The on-prem terminate or revoke is an order line. Create before terminate.
- `Transfer` has no order action of its own. Its ChangeLines use the table.
- `Cancel` uses `Terminate` or `Remove` lines.

`REVOKE` and `REGENERATE` are non-compensatable, matching ADR-027 D-02 (a licence already issued cannot be undone by reversing the saga step). DRG-578 may assume that classification. ADR-027 itself stays Proposed.

### D-R6 — The stale premise is retired

ADR-024's context sentence that CAM ignores `dealType` and opens a second subscription for an amendment is retired.

`AcceptedQuoteSubscriptionService.apply` requires `dealType` in {`NEW`, `AMENDMENT`, `EXPANSION`, `RENEWAL`}. A non-`NEW` event must carry `subscriptionChange.targetSubscriptionNumber` and `observedRevision`. The service locks the target, rejects a stale `commercial_revision`, rejects a customer or currency mismatch, and rejects a replay whose commercial digest differs. `NEW` rejects a target. The subscription number for a new sale remains `SUB-<quote UUID>`.

The spine copies that idea onto `target_subscription_id`, `source_accepted_line_uuid`, and `observed_commercial_revision` (schema sections 2.5 and 2.6, rules V3 and V4). ADR-024 O-4 stays open only for a future order-path quote snapshot. It is closed for the landed event and for the spine.

ADR-024 O-5 (one subscription per order, or one per line) does not decide SaaS apply in v1, because that apply has no order. CAM mutates the target subscription. On-prem fulfilment is per ApplicationUnit, which matches ADR-025's per-line saga once an order exists.

### D-R7 — S2 stays additive

DRG-580 (S2) may be drafted. The event it publishes is additive on the amendment document and stays minimal:

- document id
- amendment sequence (`amendment_seq`, and the human `{contract_number}-A{nnn}` once assigned)
- content digest

The hash-chain fields DRG-580 already scopes stay in that story: per-document `seq_no`, `prev_hash`, `content_hash`, verifier, and outbox publication to the audit trail with `amendmentId` and `correlation_id`.

The S2 event carries no order id, saga id, command id, or fulfilment payload. A later correlation from an on-prem order back to the document is a separate additive change, and it waits until the owner accepts the order ADRs.

### D-R8 — What stays Proposed in ADR-024 to ADR-027

These four ADRs are not edited by this proposal and are not accepted by it. On owner acceptance, the following narrowing is the text to apply.

**ADR-024.** The service placement (port 8091, schema `orders`, aggregates, header and line states, holds) stays the proposed shape for on-prem fulfilment. D-02's `ActivateSubscription` path and the subscription-source switch are withdrawn for mid-term SaaS. D-03's amendment source becomes D-R3. D-07's six actions survive as the projection in D-R5, for on-prem lines, with ChangeLine as the selector.

**ADR-025.** The in-house process manager stays the proposed mechanism for on-prem fulfilment sagas, one saga per on-prem order line, started after EXECUTED. A mid-term SaaS amendment starts no fulfilment saga and no Quote-to-Revenue saga in v1. Correlation for an on-prem fulfilment saga is the document id, and the quote UUID when the document has a quote. Quote-to-Revenue remains Proposed and is not the amendment apply path.

**ADR-026.** Kafka commands with registered replies stay the proposed transport for on-prem fulfilment. The payload's business identity is `(document id, unit_no)`; it also carries the content digest as a value to validate on replay, not as an additional unique-key component. The Kafka record key may remain the order line id (ADR-026 D-04) once the line exists. No command in v1 applies commercial state in CAM.

**ADR-027.** The per-line point of no return applies to on-prem fulfilment lines and is crossed when the first non-compensatable request is dispatched (licence issued, shipment released, ERP posting). `REVOKE` and `REGENERATE` are non-compensatable. After that point, a further commercial change is a new AmendmentDocument; the on-prem line on that document is a new order. SaaS corrections in v1 are new amendments under spine rule A1, outside saga compensation. Holds (ADR-027 D-06) still pause an on-prem saga and do not compensate it.

## Effect on open stories

| Story | Effect of this proposal |
|---|---|
| DRG-578 | Unblocked for specification. DecompositionRule may be keyed on (product or relationship type, ChangeLine action, `licenseModel`) and may emit `PROVISION`, `MODIFY`, `EXTEND`, `REVOKE`, `REGENERATE`, `SUSPEND`, `RESUME` for on-prem lines, using D-R5. Results feed CAM as entitlement state periods. SaaS mid-term v1 stays on the ledger and the existing ERP sync. |
| DRG-558 | Unblocked for specification. A fulfilment-required signal and the commitment status set (`PendingActive`, `Active`, `Suspended`, `InGrace`, `PendingTerminate`, `Terminated`, `Expired`, `Superseded`) apply to on-prem commitment lines. The signal fires when an on-prem unit has been applied and fulfilment is required. A SaaS subscription unit in v1 does not fire it. |
| DRG-580 (S2) | Drafting stays unblocked on the additive event in D-R7. This proposal adds no field to that event. |
| DRG-290, DRG-289, DRG-464, DRG-467, DRG-505 | Still waiting on owner acceptance of ADR-024 to ADR-027. This proposal does not start them. |

## Options

| Option | Verdict |
|---|---|
| A. Accept ADR-024 to ADR-027 as written on 2026-10-04 | Rejected by the ruling. It would add a second apply path beside CAM and a second numbering scheme beside `{contract_number}-A{nnn}`. |
| **B. Spine wins. Order model narrows to on-prem lines. Mid-term SaaS applies with no order in v1. S2 stays additive. (chosen)** | Recorded CPQ Decision. Additive and reversible. Keeps DRG-580 to document id, amendment sequence, and digest. Leaves on-prem fulfilment specified and unimplemented. |
| C. Fallback if the owner objects | Named by the ruling. Contents not stated. This draft does not define Option C. |

## Consequences

- **Positive.** One writer applies commercial state. One numbering scheme identifies an executed amendment. On-prem fulfilment has a stable boundary for DRG-578 and DRG-558. S2 can be drafted without an order payload.
- **Negative.** A mixed SaaS and on-prem document has two downstream paths after one signature. Operators will see an applied SaaS unit while an on-prem line is still in fulfilment. OQ5 (partial application) remains open.
- **Unchanged until the owner accepts.** No `order-service`, no `orders` schema, no command topic, no saga table, no subscription-source flag. Landed `QuoteAccepted` apply behaviour stays as it is.
- **On acceptance.** Amend ADR-024 to ADR-027 in place, or supersede the conflicting clauses, using D-R1 to D-R8. That acceptance is a separate owner act. It is not this file.

## Flag to the owner

At the next ADR acceptance round, present Option B. ADR-024, ADR-025, ADR-026 and ADR-027 remain Proposed until that acceptance. If the owner objects, stop and take the unspecified Option C back for a decision. Do not treat this draft as acceptance.

## Evidence

- [ADR-024](../../adr/ADR-024-order-orchestration-context.md) D-02, D-03, D-07, O-4, O-5. Status Proposed, 2026-10-04.
- [ADR-025](../../adr/ADR-025-saga-process-manager.md) D-02, D-04. [ADR-026](../../adr/ADR-026-command-messages.md) D-03, D-04. [ADR-027](../../adr/ADR-027-compensation-and-point-of-no-return.md) D-02, D-07. All Proposed, 2026-10-04.
- [amendment-entity-field-schema.md](../amendment-entity-field-schema.md) sections 2.3, 2.5, 2.6, 2.7, 7, and the resolved-decisions note: OQ4 numbering, OQ2 single status, apply after EXECUTED, ChangeLine action codes, nine intents, V13 apply key.
- [mid-term-amendment-policy-spec.md](../mid-term-amendment-policy-spec.md) section 7: CAM is the single writer of applied state. Sections 3 and 8 still call OQ2 open; the schema and the transcript supersede that sentence for this proposal.
- [cpq-project-transcript.md](../cpq-project-transcript.md): OQ2 and OQ4 resolved 2026-10-16; spine plus policies.
- [amendment-document-design-spike.md](../amendment-document-design-spike.md) section 4: mixed lines, `ENTITLEMENT` and `ONE_TIME` units, create before terminate.
- [cpq-renewals-amendments-research.md](../cpq-renewals-amendments-research.md) section 7.2: nine intents, ChangeLine action codes, `licenseModel`, commitment status set.
- `backend/services/contract-asset-management/.../service/AcceptedQuoteSubscriptionService.java`: typed `NEW` / `AMENDMENT` / `EXPANSION` / `RENEWAL`, target subscription number, observed revision, digest equality.
- CPQ Decision comment on DRG-577, 2026-10-16: Option B, medium confidence 0.7.
