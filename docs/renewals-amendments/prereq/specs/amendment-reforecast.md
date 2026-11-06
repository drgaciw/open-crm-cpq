# Automatic CRM re-forecast on amendment save (DRG-600)

**Status: Proposed.** Design spec only. **Owner decision: ___ (drg, time).**

**Story:** [DRG-600](https://linear.app/drgamtd-workspace/issue/DRG-600/automatic-crm-re-forecast-on-amendment-save-net-new) (RA-66). Call decision 2026-10-16, audit item 18: saving an amendment triggers an automatic CRM re-forecast (net-new).

**Input source, unmerged.** Section 5 of `docs/renewals-amendments/crm-forecasting-requirements-maturity-review.md` at full commit `2275e65c1f3ad97740ead9e76510a9650b102b09` on branch `docs/crm-forecasting-maturity-review-2026-10-23`. That commit is not an ancestor of `main` `6ec091b1f9ca1262b87f8c890a1c52a2ec7cd861`. No pull request for the branch was found. The file is absent from that `main` SHA. The document is unmerged, and its source can change. This spec links the review and does not restate it:

<https://github.com/drgaciw/open-crm-cpq/blob/2275e65c1f3ad97740ead9e76510a9650b102b09/docs/renewals-amendments/crm-forecasting-requirements-maturity-review.md>

The definitions spec on the same commit, `docs/renewals-amendments/crm-forecast-definitions-spec.md`, is drafted there (review section 8). Both that file and the maturity review remain unmerged. Same pin, same warning: the source can change.

<https://github.com/drgaciw/open-crm-cpq/blob/2275e65c1f3ad97740ead9e76510a9650b102b09/docs/renewals-amendments/crm-forecast-definitions-spec.md>

**Main-tree DRG-588 frame (docs only):** [`../story-drg-588-crm-forecast-definitions-spec.md`](../story-drg-588-crm-forecast-definitions-spec.md) records Adopted FQ1–FQ3 and leaves FQ4–FQ10 as Status: Proposed. It does not design, replace, or gate this DRG-600 spec.

**Open item for drg.** Before this DRG-600 spec can be Done, the owner must accept the main-tree FQ citations (or the unmerged branch citation). This spec stays a draft.

## Expected values

The table is the contract. The sections after it restate the same values. Every row this spec adds is **Status: Proposed**. **Owner decision: ___ (drg, time).** The slot stays blank.

`forecast_delta_tcv` is `new_tcv − prior_tcv`, scale 2, mode HALF_UP (halfway away from zero). The figures are illustrative. They are not prices and they are not commercial terms.

| Fixture | prior_tcv | new_tcv | raw delta | forecast_delta_tcv |
|---|---|---|---|---|
| [upsell](fixtures/reforecast/upsell.json) | 1000.00 | 1008.325 | 8.325 | 8.33 |
| [downsell](fixtures/reforecast/downsell.json) | 2500.00 | 1800.125 | -699.875 | -699.88 |
| [cancel-line](fixtures/reforecast/cancel-line.json) | 960.005 | 0.00 | -960.005 | -960.01 |

| AC | Expected |
|---|---|
| AC1 | Trigger options are only (a) quote-service on draft save (pipeline), (b) CAM on EXECUTED (committed), (c) both. Each option has its consequences written out. No option is selected. Status: Proposed. Owner decision: ___ (drg, time). |
| AC2 | Input table from review section 5 at the pinned SHA. Primary unit is TCV, FQ2 quoted below with its time. ARR is Status: Proposed pending DRG-636. Renewal TCV cites the FQ10 quotation. DRG-636 is named as the term dependency. |
| AC3 | The carrier is a RESERVED event from the S6 catalog (#683): `AmendmentDrafted` for (a), `AmendmentExecuted` for (b), both for (c). Named. Publication is RESERVED. Absent from `EventContractRegistry` and `KafkaTopics` on the main SHA above. No registry, topic, or catalog edit. |
| AC4 | The three fixture rows above. Status on each file is Proposed. Owner slot blank. |
| AC5 | Idempotency, revision, audit, and failure options are listed and Status: Proposed. Owner slot blank. |
| AC-D1 | Docs-only draft pull request. CI recorded as blocked (DRG-629). |
| AC-D2 | A quotation below is cited only with the words and the time recorded at the pinned SHA. Every other choice stays Proposed with the owner slot blank. |

## Owner decisions at the pinned SHA

Quoted from the review at `2275e65c1f3ad97740ead9e76510a9650b102b09`, section 7.1. The recording commits are ancestors of that SHA. Author of both commits: drgaciw.

FQ1, FQ2, and FQ3 were recorded in `19ac0f6c4495aeeef4772b9ac4749eca7a7079e5` at 2026-10-23 21:39:53 +0100. The heading in the review states the time as 2026-10-23, about 21:39 Europe/Dublin. Word for word:

```text
### 7.1 Owner decisions (2026-10-23, about 21:39 Europe/Dublin)
- **FQ1. DECIDED:** sales leadership owns the forecast definition and is its consumer.
- **FQ2. DECIDED:** the forecast unit is TCV.
- **FQ3. DECIDED:** the source of truth for the renewal forecast is contract ARR in CAM.
```

FQ10 was recorded in `ae67553cd00a2c91227aca3539d1e2ce73042dcf` at 2026-10-25 15:35:33 +0000. The decision sentence states the time as 2026-10-25, 15:34 Europe/Dublin. Word for word:

```text
- **FQ10. DECIDED 2026-10-25, 15:34 Europe/Dublin:** renewal TCV = contract ARR in CAM x renewal term. Guards: partial years prorated by month; ramps are the sum of each period's ARR x that period's duration; if the renewal has no term yet, use the expiring contract's term and flag the row as assumed; store ARR next to TCV so ARR views keep working. Recommended by Jev router at about 0.8 confidence; decided by the owner.
```

The definitions spec on the same SHA restates these as D1, D2, D3, and D4. Those restatements are not the decision sentences. D2 adds "in the tenant base currency", which is not the FQ2 sentence. This spec cites the FQ2 sentence: the forecast unit is TCV. Currency of an amendment delta is **Status: Proposed**. **Owner decision: ___ (drg, time).** [ADR-032](../../../adr/ADR-032-multi-currency-pipeline-reporting.md) D-06 (Accepted) already sums `base_amount` for pipeline queries. This spec does not extend that rule.

SQ1, from the unmerged definitions spec at the same SHA, recorded in `c1687bfeefee8f85d2e9ea81128f47e7957392cc` at 2026-10-26 07:52:26 +0000. Word for word:

```text
- **D5 (SQ1, decided 2026-10-26, 07:50 Europe/Dublin, Jev router recommendation about 0.7 confidence):** deal `amount` is TCV as entered, labelled "Amount (TCV)" in this spec and the UI. A term field on deals goes to the backlog (see section 5); no ARR is derived from deals until then.
```

The backlog item is [DRG-636](https://linear.app/drgamtd-workspace/issue/DRG-636/deal-term-field-backlog-from-forecast-sq1) (RA-102), status Backlog. ARR for this re-forecast is **Status: Proposed** pending DRG-636. **Owner decision: ___ (drg, time).**

### FQ4 to FQ9 stay Proposed

Quoted from section 7.2 at the same SHA. None of these is decided. Each stays **Status: Proposed**. **Owner decision: ___ (drg, time).**

```text
- **FQ4.** Are forecast categories (commit, best case, pipeline, omitted) needed, and who may submit or override?
- **FQ5.** Should stage probabilities become tenant configuration, and may a rep override probability per deal?
- **FQ6.** Fiscal calendar and period definition; is quota in scope?
- **FQ7.** Snapshot cadence and accuracy metric, if any.
- **FQ8.** Are the AI agent rollups (F3, F6) requirements or illustrative prose?
- **FQ9.** Does the arr-bridge (F7) become the reporting model the forecast reads?
```

**FQ9 is an open dependency of this spec.** The question is whether [arr-bridge.md](arr-bridge.md) (DRG-575, on `main`, status Proposal) becomes the reporting model the forecast reads. This spec leaves that question open. It does not read the bridge as the reporting model. **Status: Proposed.** **Owner decision: ___ (drg, time).**

## AC1. Trigger

DRG-600 asks for the trigger point: on save of a draft, or on EXECUTED. The apply gate named in that issue is EXECUTED. The options are only the three below. None is selected.

The unmerged definitions spec drafts, under its F9 heading: "on amendment execution, recompute the F6 and F7 rows of the affected contract with D4. This spec only defines the target; DRG-600 designs the trigger." That sentence has no separate owner time for this trigger. This spec leaves the trigger **Status: Proposed**.

**(a) quote-service on draft save (pipeline).** The trigger is a draft save in quote-service. The forecast that moves is the pipeline forecast. A later withdraw or a later revision has already moved pipeline TCV, so AC5 has to replace or reverse that movement. The committed renewal forecast, whose source of truth is contract ARR in CAM (FQ3), stays unchanged under this option alone.

**(b) CAM on EXECUTED (committed).** The trigger is the amendment document reaching EXECUTED in CAM. The forecast that moves is the committed forecast. Draft saves leave both pipeline and committed figures unchanged. The pipeline view omits the amendment until execution. This option lines the re-forecast up with the EXECUTED apply gate named in DRG-600. It is still unselected.

**(c) both.** A draft save moves pipeline forecast only. EXECUTED moves committed forecast. The same amendment can sit in pipeline and then in committed. A rule that drops the pipeline movement when the committed movement is written is required, and that rule is unselected.

**Default:** no option is selected. **Status: Proposed.** **Owner decision: ___ (drg, time).**

## AC2. CRM forecast inputs

Primary unit, FQ2, 2026-10-23, about 21:39 Europe/Dublin: the forecast unit is TCV.

Renewal TCV, when the row being updated is a renewal forecast row, is the FQ10 sentence quoted above (2026-10-25, 15:34 Europe/Dublin). Using that sentence as the delta rule for a mid-term amendment is **Status: Proposed**. **Owner decision: ___ (drg, time).**

ARR is **Status: Proposed** pending DRG-636. SQ1 says a term field on deals is backlog and that ARR is not derived from deals until then. This spec does not derive ARR. Storing ARR beside an amendment TCV delta, which FQ10 requires for renewal rows ("store ARR next to TCV"), is **Status: Proposed** for amendment deltas. **Owner decision: ___ (drg, time).**

**DRG-636 term dependency.** DRG-636 is the deal term field from SQ1. Until that field exists, SQ1 blocks deriving ARR from deal Amount (TCV). Amendment re-forecast ARR waits on that dependency. DRG-636 does not change the SQ1 sentence that deal `amount` is TCV as entered.

The input names are section 5 of the unmerged review at the pinned SHA. The review marks the mapping from those inputs to forecast types as inference. This table names the inputs. A TCV calculation for a row is Proposed except where FQ2 or the FQ10 quotation already speaks.

| Input (review §5) | Changed by (review §5) | This spec |
|---|---|---|
| Line ARR (price, quantity) | Amendment add, update, remove (first slice action codes) | The fixture delta is a TCV delta. Turning line ARR into TCV for a non-renewal amendment is Status: Proposed, pending DRG-636 for term. Owner decision: ___ (drg, time). |
| Contract end date and term | Renewal, coterm (RA-20 / DRG-554), new-contract-preferred (RA-58 / DRG-592) | Term is an input to the FQ10 renewal sentence. Which term an amendment uses is Status: Proposed. Owner decision: ___ (drg, time). DRG-636 is the deal-term dependency named above. |
| Renewal date and auto-renew flag | Auto-renew opt-in (RA-62 / DRG-596) | Named. The TCV effect is Status: Proposed. Owner decision: ___ (drg, time). |
| Churn (non-renewal, remove line) | Renewal disposition, amendment remove | The cancel-line fixture is a negative TCV delta. Classifying that delta as churn belongs to the arr-bridge proposal and waits on FQ9. Status: Proposed. Owner decision: ___ (drg, time). |
| Conversion (maintenance to SaaS) | `conversions.md` | Named. No conversion figure is calculated here. Status: Proposed. Owner decision: ___ (drg, time). |
| Currency and FX rate at execution | Any priced document | Named. The currency of the delta is Status: Proposed. Owner decision: ___ (drg, time). |
| Precedence between old and new contract | `precedence_flag` (OQ27) | Named. Whether both contracts contribute TCV is Status: Proposed. Owner decision: ___ (drg, time). |
| Deal stage and probability | Quote acceptance (`QuoteAcceptedDealProcessor`) | Named. Weighted TCV waits on FQ5. Status: Proposed. Owner decision: ___ (drg, time). |

## AC3. Event source

S6 catalog, pull request [#683](https://github.com/drgaciw/open-crm-cpq/pull/683), merge `964322570ff98ee1c3a64ffb9ede770c4ca30ba2`, which is an ancestor of main `6ec091b1f9ca1262b87f8c890a1c52a2ec7cd861`. Catalog: [AmendmentLifecycleEventCatalog.java](../../../../backend/libs/common/src/main/java/com/opencrm/common/event/AmendmentLifecycleEventCatalog.java).

The catalog states that an entry there is RESERVED, that nothing in the catalog is published, that none of the types are in `EventContractRegistry`, and that there is no Kafka topic and no producer. On the main SHA above, `EventContractRegistry` and `KafkaTopics` contain neither `AmendmentDrafted` nor `AmendmentExecuted`.

| Trigger | Catalog constant | eventType | documentStatus | Publication |
|---|---|---|---|---|
| (a) draft save | `AMENDMENT_DRAFTED` | `AmendmentDrafted` | `DRAFT` | `RESERVED` |
| (b) EXECUTED | `AMENDMENT_EXECUTED` | `AmendmentExecuted` | `EXECUTED` | `RESERVED` |
| (c) both | both rows | both event types | `DRAFT` and `EXECUTED` | `RESERVED` |

The event is named. It is not registered. This spec adds no schema-registry entry, no `EventContractRegistry` entry, no `KafkaTopics` entry, and no topic. It does not edit the catalog or the DTO files. The current payloads carry document identity and status (`documentId`, `contractId`, `amendmentNumber`, `documentStatus`, `occurredAt`, `correlationId`, `eventId`, plus `provisionalNumber` on `AmendmentDrafted` and `executedRevisionNo` on `AmendmentExecuted`). They carry no TCV field.

DRG-600's issue text says the capability needs a new integration event from the amendment document. The event this spec names is the reserved catalog event in the table. No additional event type is named.

Which reserved event carries the re-forecast follows AC1. **Status: Proposed.** **Owner decision: ___ (drg, time).**

Putting TCV on that payload is **Status: Proposed**. **Owner decision: ___ (drg, time).**

## AC4. Fixtures

The files are the expected values. Illustrative TCV figures only.

- Upsell, line update. `1008.325 − 1000.00 = 8.325`. HALF_UP to 2 dp is `8.33`. [upsell.json](fixtures/reforecast/upsell.json).
- Downsell, line update. `1800.125 − 2500.00 = -699.875`. HALF_UP to 2 dp is `-699.88` (halfway away from zero). [downsell.json](fixtures/reforecast/downsell.json).
- Cancel line. `0.00 − 960.005 = -960.005`. HALF_UP to 2 dp is `-960.01`. [cancel-line.json](fixtures/reforecast/cancel-line.json).

Each file has `"status": "Proposed"` and `"owner_decision": "___ (drg, time)"`. **Status: Proposed.** **Owner decision: ___ (drg, time).**

## AC5. Idempotency, revision, audit, failure

No option below is selected. Each is **Status: Proposed**. **Owner decision: ___ (drg, time).**

### Idempotency on repeated saves

- **A.** Key `(documentId, revisionNo)`. A second save of the same revision with the same TCV digest writes no second delta. A changed digest replaces the snapshot for that revision. The stored delta is from the previous snapshot to the new snapshot.
- **B.** Key `(documentId, eventId)`. A replay of the same `eventId` writes nothing. A new save has a new `eventId` and applies once.
- **C.** No key. Every save adds the delta again.

### Revision handling

- **A.** A new revision supersedes the prior revision's forecast snapshot. The delta runs from the superseded snapshot to the new snapshot.
- **B.** Each revision stores its own delta from the original contract TCV. The forecast reads the latest revision only.
- **C.** Revisions leave the forecast unchanged until EXECUTED, and only the executed revision is applied. This overlaps trigger (b). It is unselected.

### Audit of forecast changes

- **A.** One audit record per applied change: document id, revision, prior TCV, new TCV, delta TCV, rounding mode, actor, time, correlation id. The record has no price fields.
- **B.** The existing document audit event is the only record. No forecast amounts are added to it.
- **C.** The document audit event and a forecast record are both written.

### Failure handling

- **A.** The amendment save commits. A forecast failure is recorded and retried. The forecast can lag the document.
- **B.** The save requires a forecast result. A forecast failure fails the save.
- **C.** The document save stands. The forecast row is marked stale, and the last successful TCV stays in place until a later success.

The safe option depends on the trigger in AC1. That dependency is **Status: Proposed**. **Owner decision: ___ (drg, time).**

## AC-D2. What is cited, and what stays open

Cited with drg's recorded words and a time, from the pinned SHA only: FQ1, FQ2, FQ3, FQ10, and SQ1 (D5), quotations above.

Left **Status: Proposed** with **Owner decision: ___ (drg, time)** blank: the trigger, the ARR framing, the DRG-636 wait, the currency of a delta, every input-row calculation, which reserved event carries TCV, payload contents, idempotency, revision, audit, failure, and FQ4 through FQ9. FQ9 is the named open dependency.
