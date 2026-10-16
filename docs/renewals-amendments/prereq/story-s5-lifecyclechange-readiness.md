# S5 readiness: quote-service LifecycleChange (DRG-583)

*open-crm-cpq. Documentation only. Date: 2026-10-16. Story: RA-49 / [DRG-583](https://linear.app/drgamtd-workspace/issue/DRG-583/s5-quote-service-lifecyclechange-amend-with-pricing-service-decisions) (Backlog when read). This note records a boundary and a wait-list. It authorizes no code, schema, topic, gateway route, or flag.*

**Base branch is `main`.** Draft PR #619 targets `main` (`baseRefName` read from GitHub). This note lands on `main` when the owner merges it. The file is not on `main` at `dd0f1a8` while the PR stays draft. #613 merged to `docs/renewals-amendments-design-2026-10-16`. #614 merged to `cursor/crm-quote-dealtype-mapping-6410`. Neither of those merges landed on `main`.

**Verdict.** S5 is not ready to implement. [ADR-035](../adr-035-amendment-document-placement.md) does not authorize it. Pricing for Add, Update, and Remove lines with a PolicyDecision id, policy version, and trace id is absent on the reading in the OQ9 note, and [DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609/story-pricing-service-support-for-addupdateremove-lines-with) is the story that gap waits on.

Marks used below:

| Mark | Meaning in this note |
|---|---|
| Read | The cited file or Linear issue was opened for this note. |
| Unverified | Not opened, not executed, or only named by another document. A search is not a full read. |

Nothing in this note was executed. No Maven run, no Drools session, no HTTP call.

## 1. Authorization

**S5 is not authorized by ADR-035.** The acceptance paragraph authorizes the first-slice schema and audit stories only: S1 and S2 (RA-45 / RA-46, DRG-579 / DRG-580). It says later stories still need their own gates. The Decision text is unchanged. Read: ADR-035, "Acceptance 2026-10-16".

An ADR counts as accepted only after the owner accepts it and the status-change PR merges. That is DoD v0.2 item 6, read from the approved Notion page. ADR-035 on `main` is Accepted. Its status-change PR is #600, merged to `main` (merge commit `307f065`).

The same limit is recorded in:

- ADR-035 status line and the catalog row in `docs/adr/README.md` (ADR-035 authorizes S1 and S2; OQ3, OQ10, OQ12 confirmed).
- OQ1 in `renewals-amendments-open-questions-register.md`: accepted 2026-10-16; authorizes S1 and S2 only.
- Technical approach risk R1: S3 to S8 require their own readiness and authorization gates (`amendment-technical-approach-and-dependencies.md` §7).
- ADR-035 header: acceptance does not itself authorize code, schema migration, service, topic, or gateway route.

This file is not that later gate.

## 2. LifecycleChange boundary

Two documents on `main` define the boundary this story has to keep.

**ADR-035 D-03** (read). LifecycleChange stays in quote-service. The intent root named there is Renew, Amend, Cancel, Restructure, Transfer, Suspend, Resume, TrueUp, Convert. Pricing policy evaluation stays a call into pricing-service (trace / PolicyDecision). The document only pins results. Document render is requested from quote-service. D-03 does not move render ownership into CAM. The ADR leaves the render home (quote-service request versus document-generation type ownership) as an implementation-ticket question.

**Technical approach §1** (read). quote-service gains a minimal LifecycleChange for intent Amend, and the existing continuation endpoint becomes a facade. The legacy QuoteAccepted apply path stays alive behind a per-tenant flag until the document path is proven. Changes are expand-then-contract and additive. ADR-035 implementation authorization in that paragraph is S1 and S2 only.

**Render stays in quote-service, and it is out of this slice.** Technical approach §2 lists document-generation as not touched: no AMENDMENT render type; render out of slice; story column "none". S5 does not add a render type, a rendition API, or a document-generation consumer. D-03 still places the render *request* with quote-service when that work is authorized later.

**The legacy QuoteAccepted apply path stays behind a per-tenant flag, and that flag is not an S5 code change.**

| Written rule | Where | Story the docs assign |
|---|---|---|
| Legacy path stays alive behind a per-tenant flag | Technical approach §1 | Slice-wide strangler |
| `QuoteAcceptedEvent` gains `amendmentDocumentId` and per-line `applicationUnitNo`; CAM routes document-bearing events to the new path and legacy events to the existing path behind a per-tenant feature flag | FR-16 | S6 |
| Flag granularity: per tenant, default off, internal tenant first | OQ11, status DEFAULT PROPOSED | S6, before S6 |
| Flag off: a legacy QuoteAccepted AMENDMENT event behaves as today | AC-S4-9 | S4 |
| Flag on plus `amendmentDocumentId`: document path, and the legacy apply does not run. Flag off: legacy path | AC-S6-2 | S6 |
| Flag routing lives in `QuoteAcceptedEventConsumer` | Technical approach §2, CAM row | S1, S2, S3, S4, S6, S7 (the row lists those stories; it does not list S5) |

quote-service's row in the same table does list LifecycleChange, the `lifecycle-changes` API, the facade on `QuoteContinuationController`, pricing client calls, pinning, and QuoteAccepted emission with `amendmentDocumentId` under stories S5 and S6. FR-16 places the event fields and the CAM route on S6. Both assignments stay as written. This note does not choose a single owner.

AC-S5-5 is a different surface: the legacy continuation endpoint, with the flag on, creates a LifecycleChange and keeps the previous response shape. The requirements file does not name the flag key. The draft OpenAPI on `main` says the same thing and then says the proposal does not name or enable that switch (`docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml`, info description and `POST /quotes/continuations`, read). OQ11's per-tenant flag is the CAM apply flag. Whether AC-S5-5's "flag" is that same flag is **unverified**.

What the current apply path does, read on this tree:

- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java` claims the inbox and calls `acceptedQuotes.apply(event)`. The file has no tenant branch and no `amendmentDocumentId` branch. File identical to `origin/main` at `dd0f1a8` (empty diff against the pre-branch tree).
- `backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedEvent.java` fields read through `SubscriptionChangeSnapshot` include `targetSubscriptionNumber`, `observedRevision`, and `effectiveDate`. `LineItemSnapshot` includes `productExternalId`, `quantity`, and `sourceSubscriptionLineUuid`. No `amendmentDocumentId` and no `applicationUnitNo` in the portions read. File identical to `origin/main` at `dd0f1a8`.
- `AcceptedQuoteSubscriptionService.apply` was not read in full. A search shows it accepts operations `NEW`, `AMENDMENT`, `EXPANSION`, and `RENEWAL`. Apply internals beyond that allow-list are **unverified**.

The continuation gate that exists today is a different switch. `QuoteContinuationService.requireEnabled()` calls `RampPricingProperties.isEnabled()`. That type is `opencrm.pricing.ramp`, `enabled` default false (`backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampPricingProperties.java`, read). It is the ramp-pricing toggle. It is not described as a per-tenant QuoteAccepted flag.

## 3. What S5 can do before DRG-609, and what waits

[DRG-609](https://linear.app/drgamtd-workspace/issue/DRG-609/story-pricing-service-support-for-addupdateremove-lines-with) (read): story scope only; no code in that ticket. It records the OQ9 spike ([DRG-602](https://linear.app/drgamtd-workspace/issue/DRG-602/verify-pricing-service-continuation-pricing-capability-oq9), file now on `main`). pricing-service cannot price Add, Update, or Remove change lines, and execute returns no PolicyDecision id, policy version, or trace id. DRG-583 stays blocked until that gap is scoped and delivered. The issue note says a design note comes first, and no code until CPQ Lead sets sequence. Linear relation when read: DRG-609 blocks DRG-583. DRG-583 is also blocked by DRG-605 (OpenAPI draft) and DRG-602.

The OQ9 note on `main` (`docs/renewals-amendments/prereq/oq9-pricing-continuation-capability.md`, read) is a source reading. It marks runtime behavior unverified (`verified by running`: none). Its verdict, marked `verified by reading code` in that file: `POST /api/v1/pricing/execute` and `POST /api/v1/pricing/simulate` do not carry a ChangeLine action, quantity semantics, or a decision identity on the success body. This readiness note did not re-open those pricing types. The OQ9 file's own mark stands; re-confirmation here is **unverified**.

OQ9 §6 is a draft proposal, not an accepted ADR. It says S5 cannot meet AC-S5-2 by calling today's execute API, on that reading. It says that until the new pricing contract exists, storing a continuation by keeping the current RetainContracted calculation local still fails V8. Closing the gap is a separate implementation story. That story is DRG-609.

Register OQ9 on `main` still says **BLOCKS S5 until verified**, with a proposed fallback of RetainContracted plus an override flag (`renewals-amendments-open-questions-register.md`, read). Technical approach R2 says the same fallback: RetainContracted only, explicit unit price supplied by quote-service, flagged override. Spec V8 and FR-14 say no line is priced inside quote-service or CAM. The OQ9 note says the fallback's response types still have no PolicyDecision id, policy version, or trace id. This note does not treat R2 as permission to price inside quote-service, and it does not treat R2 as a way past DRG-609.

### Can be specified before DRG-609 lands

These items do not need the pricing-service success body. They are still not authorized (section 1). DRG-605 still blocks DRG-583 on the Linear relation that was read, so the shared HTTP shape is a draft, not an accepted contract.

| Item | Why it does not need DRG-609 | Bound |
|---|---|---|
| Store an Amend LifecycleChange with ChangeLines, action codes, quantity semantics, and observed revisions (AC-S5-1, FR-13 create) | No pricing call | Intent in slice is Amend. Action codes in slice are Add, Update, Remove. Quantity semantics in slice are Delta or Absolute (requirements slice definition; OQ12 confirmed). |
| Reject Swap, Upgrade, or Downgrade at submit with `ACTION_NOT_SUPPORTED_IN_SLICE` (AC-S5-3) | Local reject | OQ12 confirmed. Those codes wait for RA-42 / DRG-576. |
| Reject a target subscription that is not ACTIVE (AC-S5-6, V2) | Local check | V2 text: ACTIVE at the action date (`mid-term-amendment-policy-spec.md` §4, read). |
| Keep the continuation author response body as today's `QuoteDto` (AC-S5-5) | Shape constraint | Controller today: `POST /api/v1/quotes/continuations` returns `ApiResponse<QuoteDto>` (`QuoteContinuationController.java`, read). Draft OpenAPI puts a new id only in header `X-Lifecycle-Change-Id` when the facade behavior is active, and leaves RENEWAL, EXPANSION, and EXTENSION on today's authoring. Preview does not create a LifecycleChange (OpenAPI `POST /quotes/continuations/preview`, read). |
| Store action date and effective date as separate fields (FR-17, V6) | Storage | Backdating still needs a reason code and approval. Thresholds are OQ17, not closed. |
| Record override actor, reason code, overridden value, and an approval of type `CONTRACT_MODIFICATION` (AC-S5-4, FR-18) | Audit and approval type exist as names | `ApprovalType.CONTRACT_MODIFICATION` is present (`backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalType.java`, read). Whether quote-service already opens that approval request was not read. **Unverified.** The overridden amount must not be produced by pricing inside quote-service (V8). |

A workspace search for `class LifecycleChange` and for `lifecycle-changes` in `*.java`, `*.yml`, `*.yaml`, and `*.md` hit the renewals-amendments docs only. No Java type of that name was in the search output. Search, not a proof that every future name is absent.

### Must wait for DRG-609

| Item | Why it waits |
|---|---|
| AC-S5-2. RetainContracted + Daily proration line carries PolicyDecision id, policy id, policy version, trace id, and pins (V8, V9) | DRG-609 and the OQ9 verdict. Draft OpenAPI maps this to `POST /lifecycle-changes/{id}:price` (info `x-opencrm-acceptance` AC-S5-2, read). |
| FR-14. Every priced line carries those ids. No pricing in CAM or quote-service | Same gap. V8 is the rule text. |
| Technical approach §5 step 2. quote-service prices through pricing-service and stores PolicyDecisions | The call has nowhere to land on the current success body, on the OQ9 reading. |
| Using `QuoteContinuationPolicy` retained rates, or the R2 explicit unit price, as if they satisfied V8 | OQ9 §3 and §6. Today's continuation request is one operation (`AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`), one `sourceSubscriptionLineUuid`, and `@Positive` quantity (`QuoteContinuationRequest.java`, read). That is not Add / Update / Remove with Delta or Absolute. |
| Pinning "at pricing" (FR-15, V9) onto a priced line | The pin is stored when the line is priced. The formula is already written (interim non-null SHA-256 of product external id, pricebook version, and sorted option codes, until DRG-428). Storing it on a V8-complete line waits. Re-check at apply is S4 (AC-S4-7). |

Control-flow steps 3 through 7 in technical approach §5 (CAM document, submit, execute, apply, outbox) are not S5-local. Step 3 needs the S3 API. Steps 5 through 7 are CAM. See section 4.

## 4. Dependencies on S3 and S4

### S3 (RA-47 / DRG-581)

Read: requirements §2 S3 and S5 "Depends on"; technical approach §4 and §6; DRG-581; draft OpenAPI header.

S5's written dependency is the S3 contract, not a finished S3 implementation. Technical approach §6: S5 runs in parallel after S1 once the S3 contract is fixed (OpenAPI first). Critical path to S8 does not put S5 between S3 and S4.

What S5 takes from S3:

- Idempotent document create on `lifecycle_change_id` (FR-05, AC-S3-1). Technical approach §5 step 3: quote-service calls CAM to create the document, lines copied by value with digest.
- Shared validation FR-07 (V1..V14) at the named transitions. S3 submit is one of those transitions (AC-S3-2).
- Shared dates FR-17 / V6 (AC-S3-8 fails backdating without a reason code).
- Digest location OQ18, default `backend/libs/common`, status DEFAULT PROPOSED, blocks S3 and S5, needed before S3.
- Revision default OQ6, status DEFAULT PROPOSED, blocks S3.

`docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` is on `main` at `dd0f1a8`. The header that was read says proposal only: not an implementation, not a gateway route, not a feature flag, and it does not authorize merging, deploying, or enabling a runtime flag. `x-opencrm-status` is `proposal`. It names quote-service as owner of LifecycleChange and CAM contract-documents as owner of the amendment document. Paths read for this note: `/lifecycle-changes` and the continuation facade. The rest of the 2479-line file was not read. **Unverified** beyond the sections cited. DRG-605 was In Review and still listed as blocking DRG-583 and DRG-581 when the issue was read. Whether that relation changed after the file reached `main` was not re-read. **Unverified.**

### S4 (RA-48 / DRG-582)

Read: requirements §2 S4 and S5; technical approach §3 and §6; DRG-582.

S4 does not block S5 in the requirements "Depends on" line for S5, and DRG-582 was not in DRG-583's `blockedBy` list when read. S4's own dependencies are S1..S3 plus DRG-562, DRG-563, DRG-565, and the OQ5 default before start. OQ5 is still DEFAULT PROPOSED.

The seam:

- FR-15 is shared. S5 pins product external id, pricebook version, and configuration hash at pricing. S4 re-checks the pricebook pin at apply (AC-S4-7, V9). Technical approach §6: the slice does not block on DRG-428; the interim hash is mandatory and non-null; DRG-421 is the pricebook version pin.
- S4 owns document-driven apply after EXECUTED (FR-09..FR-12) and the new `document_apply_ledger` (technical approach §3, V1_9). S5 does not write rate periods. ADR-035 D-01: CAM remains the single writer of applied state.
- AC-S4-9 is the regression that the legacy path is unchanged when the flag is off. S5's facade work has to leave that consumer behavior in place (section 2).
- S5 and S4 meet at S6 (event contract and flag), then S8. DRG-583 blocks DRG-586 (S8) on the relation that was read.

## 5. Touch points

Stories in the last column are the technical approach §2 assignment unless a tighter id is named. "Read" means this note opened the file. Behavior beyond the lines cited is **unverified**.

| Path | What was read | S5 relation |
|---|---|---|
| `docs/renewals-amendments/adr-035-amendment-document-placement.md` | D-03 and the acceptance scope | Boundary. Does not authorize S5. |
| `docs/renewals-amendments/amendment-technical-approach-and-dependencies.md` | §§1, 2, 4, 5, 6, 7 | Facade, flag, sequencing, R1, R2. |
| `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` | Slice definition, FR-13..FR-18, AC-S3, AC-S4, AC-S5, §3 DoD | Acceptance and DoD. |
| `docs/renewals-amendments/mid-term-amendment-policy-spec.md` | §4 V1..V14, §8 OQ list, 2026-10-16 clarification on pricing modes | V2, V6, V8, V9, V14. |
| `docs/renewals-amendments/renewals-amendments-open-questions-register.md` | OQ table and the 2026-10-16 summary | OQ1, OQ9, OQ11, OQ12, OQ17, OQ18. |
| `docs/renewals-amendments/prereq/oq9-pricing-continuation-capability.md` | Full file on `main` | DRG-609 gap. Runtime **unverified** there. |
| `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` | Header, acceptance map, `POST /quotes/continuations` and preview | Draft contract. Not accepted. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationController.java` | Full file | Facade target. `POST /api/v1/quotes/continuations` and `/preview`. No `lifecycle-changes` mapping. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java` | Full file | One operation, one source line, one positive quantity. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java` | `plan` through the ACTIVE / revision / provenance checks | Rates come from the accepted schedule. Rest of `plan` **unverified**. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationService.java` | `requireEnabled` on this tree; `draftRenewal` via the `dd0f1a8` diff | `author` / `preview` still call `requireEnabled`. `draftRenewal` opens a RENEWAL draft and inserts `commercial_decision_audit` with policy id string `QuoteContinuationPolicy` and mode `RENEWAL_EXIT_RATE`. That path is renewal, outside Amend. The `author` method body was read on the parent of that diff; the diff hunks do not replace it. |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampPricingProperties.java` | Full file | Ramp toggle, default off. Not the per-tenant apply flag. |
| `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java` | Full file | Always applies after inbox claim. Flag routing is later (S4 / S6). |
| `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionService.java` | Search only: `apply` and the operation allow-list | Legacy apply. Internals **unverified**. |
| `backend/libs/common/src/main/java/com/opencrm/common/event/dto/QuoteAcceptedEvent.java` | Header fields and the snapshots cited in section 2 | S6 field additions are not in the file. |
| `backend/services/approval-routing/src/main/java/com/opencrm/approval/domain/ApprovalType.java` | Full enum | `CONTRACT_MODIFICATION` exists. Call path from quote-service **unverified**. |

Technical approach §2 also names pricing-service, product-service, configuration-engine, and pricebook-service as S5 touch points (read-only pins; DRG-421, DRG-428). Those modules were not opened for this note. **Unverified** here. The OQ9 note is the pricing reading.

Ordering and fulfilment stay out (technical approach §2). document-generation stays out of the slice (same table).

## 6. Open questions that still touch S5

| ID | Status as written on `main` | Effect on S5 |
|---|---|---|
| OQ1 | Accepted. Authorizes S1 and S2 only. | S5 has no ADR gate of its own yet. |
| OQ9 (register) | BLOCKS S5 until verified. Fallback text is RetainContracted with an override flag. | Superseded as a *finding* by the OQ9 file and by DRG-609, which say the capability is absent and S5 stays blocked until the pricing story lands. The register row itself was not edited by those later files. The row and the finding disagree on whether a fallback lets S5 start. This note follows DRG-609 plus V8: the fallback does not satisfy AC-S5-2. |
| OQ9 (spec §8) | "Backdating limits and approval thresholds (V6)." | Different question, same id. Spec clarification later uses "OQ9" again for pricing-service support (DRG-550). The register and the spec use one id for two questions. |
| OQ11 | DEFAULT PROPOSED. Per-tenant flag, default off, internal tenant first. Blocks S6. | Apply-path flag. Not specified as the AC-S5-5 switch. |
| OQ12 | Confirmed. Add, Update, Remove only. | AC-S5-3. |
| OQ17 | DEFAULT PROPOSED. Approval for overrides, backdating, or ARR delta above a Finance threshold. Needed before S5 ends. | AC-S5-4 can name `CONTRACT_MODIFICATION`. The non-override threshold is still a default. |
| OQ18 | DEFAULT PROPOSED. Digest utility in `backend/libs/common`. Before S3. | S5's digest on the document call (technical approach §5 step 3) uses that utility. Not accepted. |
| OQ5 | DEFAULT PROPOSED. Blocks S4, not S5. | S5 must not invent partial-apply behavior. |
| Render home | ADR-035 open questions. | Out of slice. Request stays in quote-service when a later ticket builds it. |
| Hash chain key management | ADR-035 acceptance: still open. | S2. Not S5. |
| DRG-421, DRG-428 | Technical approach §6. Pricebook version pinned directly. Interim configuration hash. Slice does not block on DRG-428. | Pin formula can be cited now. Attaching it to a V8 line waits on DRG-609. |
| DRG-552, DRG-553 | Named on DRG-583 as related. | Action codes and separate effective versus action dates. The requirements file already states the slice codes and V6. Those issues were not re-read. **Unverified.** |

Register summary dated 2026-10-16 says the remaining gate is OQ9 and that it only gates S5. That sentence predates the OQ9 file's verdict and DRG-609. It is not a clearance to start S5.

## 7. Definition of done (v0.2)

Approved rule, read 2026-10-16: [CPQ Definition of Done v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338) (owner drg, 4:30 PM CT). It replaces v0.1. Done means every item passes. Items 2 and 7 are owner gates. A story is Done only when the owner merges it to `main` and every item passes. The six additions are N/A, with a one-line reason, when the change does not touch events, rules, or commercial decisions.

This table scores draft PR #619, the readiness note. It does not score an S5 implementation. DRG-583 is not Done.

Requirements §3 on `main` still says: ready when dependencies are met, open-question defaults are accepted, and contract fixtures are drafted; done when acceptance criteria are automated, the migration is reviewed for expand-contract, docs are updated, the legacy path is unchanged with the flag off, and audit events are visible. That sentence is not v0.2. v0.2 is the checklist below.

### v0.1 items (unchanged in v0.2)

| # | Item | Result | Reason |
|---|---|---|---|
| 1 | Acceptance criteria checked on the Linear issue | N/A | This note does not implement or verify AC-S5-1 through AC-S5-6. |
| 2 | PR to drgaciw/open-crm-cpq, reviewed, CI green, owner merges | N/A | Owner gate. #619 links DRG-583 and is still a draft; the owner has not merged it. |
| 3 | Automated tests for Java or Angular behavior | N/A | This change is one markdown file and changes no Java or Angular behavior. |
| 4 | Edge-case tests for renewals, amendments, ramps, escalators, DealType, dates, and totals | N/A | This change adds no quote or pricing behavior to test. |
| 5 | Reversible schema or a documented rollback, and a compatibility check | N/A | This change adds no schema and no migration. |
| 6 | ADRs accepted only after owner acceptance and a merged status change; API or behavior changes noted | pass | No new ADR. ADR-035 counts as accepted because status-change PR #600 merged to `main`. This note is the boundary doc. |
| 7 | No flag enabled, nothing deployed, nothing sent externally | pass | Owner gate for later implementation. This change enables no flag, deploys nothing, and sends nothing external. |
| 8 | Linear links the PR, has no open blockers, and Notion is updated if sequencing changed | N/A | DRG-583 stays Backlog and is still blocked by DRG-609, DRG-605, and DRG-602. This note does not change the Notion hub. |
| 9 | Follow-ups have DRG issues; no TODO without a ticket | pass | This note adds no TODO. The pricing gap is already DRG-609. |

### v0.2 additions

| # | Item | Result | Reason |
|---|---|---|---|
| 1 | Open Host Service | N/A | This change adds or changes no integration surface. |
| 2 | Schema-registry events | N/A | This change adds or changes no event. |
| 3 | Outbox writes | N/A | This change writes no outbox row and publishes no event. |
| 4 | Commercial-decision audit | N/A | This change writes no commercial decision. |
| 5 | Event-replay test | N/A | This change produces no event, so it adds no replay test. |
| 6 | Base branch stated, and the work lands on `main` | pass | PR #619 base is `main`, so this note lands on `main` when the owner merges. #613 and #614 merged to other branches. |

## 8. Sources and limits

Read for this note: ADR-035; `docs/adr/README.md` catalog row; technical approach; requirements and acceptance; open-questions register; mid-term spec sections cited in section 5; OQ9 capability note; the OpenAPI sections cited in section 5; the Java files in section 5; Linear DRG-583, DRG-609, DRG-602, DRG-605, DRG-581, DRG-582; Notion [DoD v0.2](https://app.notion.com/p/3fbf7cb4e4df8156a5bfe5c6c61d4338); GitHub PR metadata for #600, #613, #614, and #619.

Not read, so **unverified**: pricing-service Java (the OQ9 note is the reading); product-service, configuration-engine, and pricebook-service; the remainder of `DRAFT-openapi-amendments.yaml`; `AcceptedQuoteSubscriptionService` beyond the operation allow-list; approval-routing call sites; DRG-552 and DRG-553 bodies; whether Linear blocking relations changed after `dd0f1a8`.

No code is included here. No flag is turned on. No merge is requested.
