# DRG-577 options A and C

**Status:** Note only. Not an ADR. Nothing here accepts ADR-024, ADR-025, ADR-026, or ADR-027.
**Story:** [DRG-577](https://linear.app/drgamtd-workspace/issue/DRG-577/reconcile-proposed-order-model-adr-024-to-adr-027-with-the-amendment) (RA-43).
**Sits beside:** [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md).
**Source framing:** the DRG-577 decision-options document (2026-10-16). This note links that framing by listing the two options the reconcile record leaves unstated. It does not copy the reconcile record.
**Overlap:** [#623](https://github.com/drgaciw/open-crm-cpq/pull/623) merged the reconcile record and recorded Option B as Accepted. [#633](https://github.com/drgaciw/open-crm-cpq/pull/633) only retargeted leftover filename tokens. This note adds what those two pull requests do not cover.

## What is already recorded

Option B is recorded as Accepted in [adr-reconcile-adr-024-027.md](adr-reconcile-adr-024-027.md) ([#623](https://github.com/drgaciw/open-crm-cpq/pull/623), owner approval in chat, 4:26 PM CT on 2026-10-16). That ruling was a bot decision made under delegation (CPQ Decision, medium confidence 0.7), recorded on DRG-577 on 2026-10-16.

ADR-024 to ADR-027 themselves remain Proposed until the owner narrows them. This note does not narrow them.

The reconcile file's options table uses different labels. Its Option A is "accept ADR-024 to ADR-027 as written on 2026-10-04," which that file rejects. Its Option C is a fallback whose contents that file does not state. The A and C below are the decision-options framing.

## Option A — Amend ADR-024 to ADR-027 in place to follow the spine

Rewrite D-03, D-02, and D-07, and the context, of ADR-024, and make the consequential edits to ADR-025, ADR-026, and ADR-027.

- The contract source is the executed AmendmentDocument, keyed on (document id, amendment sequence).
- Add `ApplyAmendmentUnit` on contract-asset-management, keyed on (document id, unit number, digest).
- Add a ChangeLine-to-order-line action mapping table.
- Retire the stale premise that contract-asset-management ignores `dealType`.

One coherent order model, and the saga work in ADR-025 and ADR-027 stays. This is the largest edit. Order-service becomes mandatory on the amendment path unless it is carved out, and S2 and S3 can be pulled toward order concepts.

## Option C — Defer the order model; the spine is the only path

Mark ADR-024 to ADR-027 as deferred. They stay Proposed. Record that no order-service work is planned for the amendment slice, and revisit when on-prem fulfilment is scheduled. The spine keeps one interface rule open: the event is keyed on document id plus sequence.

Least work now, and S2 and S3 have no order-service dependency. The unresolved conflicts stay on the books. A second numbering scheme or a second apply path can be revived later. On-prem and true-up items stay unspecified.

The decision-options framing excludes accepting ADR-024 as written. DRG-577 rates that path High risk, because it would add a second apply path and a second numbering scheme beside the spine. The reconcile record already rejects that path, under its own Option A label.

## Boundary

D-R1 through D-R8 stay in the reconcile record. This note does not restate them, does not change that record's Accepted status, and does not assign an ADR number. [docs/adr/README.md](../../adr/README.md) already numbers ADR-024 to ADR-027, and those four stay Proposed.
