# DRG-537 UI specification (draft)

**Status: this specification cannot be built until the OpenAPI contract is accepted.**

[DRG-537](https://linear.app/drgamtd-workspace/issue/DRG-537/no-ui-for-renewals-and-amendments) asks for screens to start, price, and review a renewal or an amendment. The only HTTP shape those screens could call is [`DRAFT-openapi-amendments.yaml`](DRAFT-openapi-amendments.yaml). That file is a **proposal**. Its `info.version` is `0.1.0-draft` and its `x-opencrm-status` is `proposal`. The description says it is not an implementation, a gateway route, or a feature flag, and it does not authorize merging, deploying, or enabling any runtime flag. [DRG-605](https://linear.app/drgamtd-workspace/issue/DRG-605/draft-openapi-contract-contractsidamendments-and-lifecycle-changes) owns that draft and blocks DRG-537. The S3 amendment API (DRG-581) is not built in this repository and is not authorized by this document.

The screens, states, empty and error states, and API calls below are a reading of that proposal and of the amendment documents on `main`. They are here so a reviewer can see the intended surfaces. They are not a build order, a route change, or a client.

Date of this reading: 2026-10-16. No Angular or Java code was changed. No service was started. No endpoint was called.

## 1. Build gate

Work on the screens stops until all of the following are true:

1. The owner accepts `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` and its `x-opencrm-status` is no longer `proposal`.
2. S3 (`POST /api/v1/contracts/{contractId}/amendments` and the amendment state machine) is implemented under that accepted contract. A text search of `backend/` for `lifecycle-changes`, `AmendmentDocument`, and `/amendments` returned no matches. That search was not a running test. **Unverified by execution.**
3. S5 can return the pricing pins AC-S5-2 names. [`oq9-pricing-continuation-capability.md`](oq9-pricing-continuation-capability.md) §6 says S5 cannot meet AC-S5-2 by calling today's pricing execute API, and that lines priced only by the local continuation calculation fail V8. The price screen in §5 depends on that gap being closed by a later, accepted pricing contract.

Until then, the Angular app stays as read in §3. Deal-type labels are not a continuation flow.

## 2. What was read, and what was not

| Source | What this spec uses | Verification |
|---|---|---|
| `docs/renewals-amendments/prereq/DRAFT-openapi-amendments.yaml` | Paths, statuses, error codes, request fields, slice notes | Read in this pass |
| `docs/renewals-amendments/amendment-first-slice-requirements-and-acceptance.md` | Slice cut, FR-13, FR-17, FR-18, AC-S3 and AC-S5 | Read in this pass |
| `docs/renewals-amendments/mid-term-amendment-policy-spec.md` §3, §4, addendum, clarification | Document transitions and V1–V14 | Read those sections |
| `docs/renewals-amendments/prereq/oq9-pricing-continuation-capability.md` §6 | Pricing gap that blocks the price screen | Read §6 |
| `docs/renewals-amendments/renewals-amendments-dev-readiness.md` §3 | Readiness row that still says the OpenAPI is to be written in S3 | Read §1–§4 |
| `docs/renewals-amendments/crm-quote-dealtype-mapping.md` | `AMENDMENT` is reached by continuation, not by CRM mapping | Read the opening |
| Linear DRG-537, DRG-605 | Problem statement and the blocking contract story | Read via Linear |
| Angular files named in §3 | Routes, quote list, quote detail actions, quote create body, CSRF interceptor | Read those files |
| `QuoteContinuationController.java`, `QuoteContinuationRequest.java` | Existing continuation operations | Read those files |

**Unverified, and not used as screen requirements:**

- Shared UI class bodies (`PageHeaderComponent`, `EmptyStateComponent`, `DataTableComponent`, `StatusBadgeComponent`). The quote screens import them. Their input contracts were not opened.
- Gateway route tables, session cookie name at runtime, and ADR-004. The draft states browser calls use the session cookie and the `X-XSRF-TOKEN` header. The interceptor in §3 was read; the gateway was not called.
- Whether a contract always has an `ORIGINAL` document. The list operation says it includes the original and every amendment. No fixture was read.
- DRG-595 role matrix. The policy clarification says visibility is contract owner plus read-only sales ops and finance, and that this is not yet in the policy spec. The OpenAPI lists DRG-595 role enforcement as out of this slice. This UI spec defines no role-specific screens.
- A contract collection. The draft defines `GET /contracts/{contractId}` only. Where a seller obtains `contractId` is unspecified.
- Any renewal lifecycle screen. Create accepts intent `AMEND` only. See §8.

Where the first-slice requirements file and the OpenAPI draft disagree, this spec follows the draft for HTTP behavior and records the disagreement in §9. Neither text is an accepted contract.

## 3. Angular surfaces that exist today

Read from `origin/main` at the time of writing. These are the only quote and navigation surfaces this spec relies on.

`frontend/apps/crm/src/app/app.routes.ts` mounts `ShellComponent` and lazy-loads these child paths: `dashboard`, `organizations`, `contacts`, `leads`, `deals`, `catalog`, `quotes`, `approvals`, `price-books`, `pricing-customers`, `rule-sets`, `product-bundles`, `pricing/simulator`. There is no `contracts`, `amendments`, or `lifecycle-changes` path.

`frontend/apps/crm/src/app/layout/sidebar.component.ts` links the same paths under CRM and CPQ groups. Quotes is `routerLink="/quotes"`. There is no amendments or contracts item.

`frontend/libs/cpq/quotes/src/lib/quotes.routes.ts`:

| Path | Component |
|---|---|
| `''` (URL `/quotes`) | `QuotesListComponent` |
| `'new'` (URL `/quotes/new`) | `QuotesCreateComponent` |
| `':id'` (URL `/quotes/:id`) | `QuotesDetailComponent` |

`frontend/libs/shared/models/src/lib/quote.model.ts` defines `QuoteDealType` as `NEW`, `AMENDMENT`, `RENEWAL`, `EXPANSION`, with labels New, Amendment, Renewal, Expansion. The comment says this is distinct from CRM `DealType`.

`QuotesListComponent` (`quotes-list.component.ts`) shows a page titled Quotes, a New Quote button, a search box, a status select, and a deal-type select. The deal-type options are the four `QuoteDealType` values. Columns are quote number, name, customer, status (badge), deal type, and total. Empty copy is "No quotes yet" or "No matching quotes". Errors render in a banner with `role="alert"`. `QuoteService.list` sends `status` and `dealType` as query parameters to `GET /api/v1/quotes`. A search of `frontend/` for `continuation` returned no matches. The portion of `quote.service.ts` that was read (`list`, `getById`, `create`, `submit`, `approve`, `reject`, `send`, `accept`, `expire`, `cancel`, `cloneQuote`, `calculate`, `listLineItems`) has no continuation method and no lifecycle-change method. **Methods below line 200 of that file were not read.**

`QuotesCreateComponent` posts `QuoteCreateRequest` with `name` and `customerExternalId`, plus optional `currency`, `dealExternalId`, `region`, and `durationInMonths`. The request builder does not set `dealType`. Success navigates to `/quotes/:id`.

`QuotesDetailComponent` shows `quote.dealType` as text. Header actions that were read: Back to quotes, Calculate, Submit, Send, Accept, and a More menu with Approve, Reject, Expire, Cancel quote, and Clone. Those actions call `QuoteService` quote commands. They are quote-status commands (`DRAFT`, `SENT`, and so on). They are not `POST /lifecycle-changes/{id}:price` and they are not `POST /amendments/{id}:accept`.

`ApprovalsInboxComponent` subtitle, as read: "Pending quote approvals via quote-service BFF". It uses `ApprovalService`. It was not read far enough to list every button. It is not an amendment `:record-approval` screen.

`csrf-bootstrap.interceptor.ts` sets header `X-XSRF-TOKEN` on non-safe methods whose URL path starts with `/api/`, unless the request already has that header or an `Authorization: Bearer` value. It loads a missing token from `GET /api/v1/csrf`. That matches the draft's browser lane (session cookie plus CSRF header; bearer calls send `Authorization` and no CSRF token). **Whether the cookie name at the gateway is `SESSION` was not checked at runtime.**

`QuoteContinuationController` is mapped to `/api/v1/quotes/continuations`. `POST` returns `ApiResponse<QuoteDto>` with message "Continuation draft created; calculate before submission". `POST /preview` returns `ApiResponse<QuoteContinuationPolicy.Plan>`. `QuoteContinuationRequest.Operation` is `AMENDMENT`, `EXPANSION`, `RENEWAL`, `EXTENSION`. Required fields on the Java type: `operation`, `targetSubscriptionNumber`, `sourceSubscriptionLineUuid`, `observedRevision`, `customerExternalId`, `currency`, `quantity`. Optional: `effectiveDate`, `termMonths`, `upliftPercent`, `name`. The draft's `/quotes/continuations` schema lists the same fields. No Angular service calls this controller.

`crm-quote-dealtype-mapping.md` says `AMENDMENT` is not a CRM mapping target and is reached only by typed continuation (`QuoteContinuationRequest.Operation.AMENDMENT`). Filtering the quotes list to deal type `AMENDMENT` or `RENEWAL` shows quotes that already carry that label. It does not start a lifecycle change.

## 4. Envelope, money, and idempotency

Every new draft path returns the platform `ApiResponse` envelope: `success`, `timestamp`, and on success a `data` payload. List payloads are an array in `data` plus `pagination` (`page`, `size`, `totalElements`, `totalPages`, `hasNext`, `hasPrevious`). `QuoteService.list` reads a Spring page (`content`, `number`, `size`, `totalElements`, `totalPages`). A later amendment client must not reuse that parser for `/lifecycle-changes` or `/contracts/{contractId}/amendments`.

Money fields are decimal strings (`numeric(19,4)`). The screen shows the string the API returned. Quote totals use a currency column type; that column type was not verified against a decimal string.

`Idempotency-Key` is required on every new mutation in the draft. The same key and the same body replay the original result with header `Idempotent-Replayed: true`. The same key and a different body is `409 IDEMPOTENCY_CONFLICT`. The draft says the existing continuation endpoints keep their current contract, so this spec does not add `Idempotency-Key` to `POST /quotes/continuations` or `POST /quotes/continuations/preview`.

Quote ids on `QuotesDetailComponent` are numbers (`QuoteService.getById(id: number)`). Lifecycle-change ids and amendment ids in the draft are UUIDs. `/quotes/:id` cannot host an amendment document.

Browser calls go to the gateway prefix `/api/v1`. This spec does not name a direct service port.

## 5. Screens

Proposed URLs below are **names for this document**. They are not routes in `app.routes.ts`. Adding them is part of the build that §1 forbids.

The seller flow the draft can describe is amend-in-place for one contract: create an `AMEND` lifecycle change, price it, submit it, open one amendment document, submit that document, and for `CLICK_THROUGH` accept it. Renewal is §8. Apply, void, rebase, rendition, and attachments are later slices and have no seller screen here.

### 5.1 Contract header and amendment list

**Purpose.** Show the thin contract header and the documents already stored for one contract. This is the only list the draft defines for amendments.

**Proposed URL.** `/contracts/:contractId`. Not in the app. The sidebar has no Contracts item. The draft has no `GET /contracts` collection, so this screen cannot offer a contract picker. If the URL has no id, the screen shows the empty state below and does not call the API.

**Loading.** Same shape as quote detail: a polite status region while the two reads are in flight.

**Loaded.** Header fields from `ContractHeader`: `contractNumber`, `customerAccountId`, `termModel` (`TERMED`, `EVERGREEN`, `PERPETUAL`), `startDate`, `endDate` (null when `EVERGREEN`), `status` (`ACTIVE`, `EXPIRED`, `TERMINATED`), `lastExecutedSeq`, `governingAgreementRef`, `priorContractId`. Then a table of documents. List items omit lines. Columns: `provisionalNumber`, `amendmentNumber` (blank until `EXECUTED`), `documentKind`, `status`, `signaturePolicy`, `actionDate`, `contractEffectiveDate`. The list operation says the page includes the original and every amendment, ordered by provisional creation time, with executed documents also sorted by `amendmentSeq`.

**Row click.** Opens §5.4 for that `amendmentId`.

**Primary action.** "Start amendment" opens §5.2 with this `contractId`. The action is present for a loaded header. The draft does not say a non-`ACTIVE` contract header is rejected by itself. V2 rejects a target subscription that is not `ACTIVE` when the lifecycle change is created. The screen shows the contract status and still lets the seller open §5.2. **Whether `EXPIRED` or `TERMINATED` can still have an `ACTIVE` subscription was not read.**

**Empty.** `200` with `data: []` and `totalElements: 0`: title "No amendment documents", message "This contract has no original and no amendments in the document list." No start-amendment row is invented from an empty list. The header still renders when `GET /contracts/{contractId}` succeeded.

**Errors.**

| HTTP | `errorCode` | Screen |
|---|---|---|
| 401 | `UNAUTHORIZED` | Banner: message and code. No table. |
| 404 | `NOT_FOUND` | Banner: "Resource not found [NOT_FOUND]". No start action. |

**API calls.**

| When | Call |
|---|---|
| Enter with a contract id | `GET /api/v1/contracts/{contractId}` |
| Header loaded | `GET /api/v1/contracts/{contractId}/amendments?page=0&size=20` |
| Status or kind filter | Same list with `status` (`AmendmentStatus`) and/or `documentKind` |

### 5.2 Start an amendment (lifecycle change)

**Purpose.** Seller command that stores intent `AMEND`, the lines, quantity semantics, and observed revisions (AC-S5-1). It does not price and it does not open the amendment document.

**Proposed URL.** `/contracts/:contractId/amendments/new`.

**Fields the seller enters**, from `CreateLifecycleChangeRequest`:

| Field | Rule from the draft |
|---|---|
| `intent` | Fixed `AMEND`. The create schema enum is `[AMEND]`. Any other intent is `422 INTENT_NOT_SUPPORTED_IN_SLICE`. The screen does not offer `RENEW`. |
| `initiationTrigger` | `MANUAL` for this screen. `API` is the other create value. Lead-time, term-end, threshold, true-up, and end-of-life triggers are described as renewal or later triggers. |
| `actionDate` | Required date. |
| `contractEffectiveDate` | Required date. If it is before `actionDate`, `backdatingReasonCode` is required or submit of the document fails V6 (AC-S3-8). The screen asks for the reason code before create when the dates are in that order. |
| `contractId` | The route id. Not a free-text field. |
| `currencyCode` | Required three-letter code. |
| `outputMode` | Sent as `QUOTE`. `DIRECT_ORDER` is on the schema. Ordering is out of the first slice in the requirements file. This screen does not offer it. See §9. |
| `dryRun` | Left false. The draft says a dry run is not persisted and has no amendment document. This screen does not send `dryRun: true`. |
| Lines | At least one. |

**Line fields** (`ChangeLineWrite`):

| Field | Rule |
|---|---|
| `actionCode` | Seller choices are `ADD`, `UPDATE`, `REMOVE`. `SWAP`, `UPGRADE`, and `DOWNGRADE` may be stored and fail later at lifecycle submit with `ACTION_NOT_SUPPORTED_IN_SLICE` (AC-S5-3). This screen does not offer them. Any other action code is rejected at create. |
| `quantitySemantics` | `DELTA` or `ABSOLUTE`. |
| `startQty`, `deltaQty`, `endQty` | Decimal strings. V7 requires `start_qty + delta_qty = end_qty` and `end_qty >= 0`. The screen blocks submit when that identity fails, and still shows a server `VALIDATION_REJECTED` / `V7` if the server disagrees. |
| `effectiveFrom` | Required. `effectiveTo` is required for a termed contract. The amendment policy co-terms `effectiveTo` to the contract end (V5). The screen shows the contract `endDate` from §5.1 and sends it as `effectiveTo` when the seller does not change it. **A co-term policy other than the existing end date is a separate story (DRG-554) and is not a control here.** |
| `targetSubscriptionId` | Required for `UPDATE` and `REMOVE`. Optional for `ADD` of a new subscription on the contract. |
| `sourceAcceptedLineUuid` | Required for `UPDATE` and `REMOVE`. V4 requires an explicit, unique source line that exists, is not retired, and has `ACCEPTED_PRICED` provenance. |
| `observedCommercialRevision` | Required when `targetSubscriptionId` is set (V3). |
| `productExternalId` | Required on `ADD` before price. On `UPDATE` and `REMOVE` the server pins the source product if omitted. |
| `businessCategory` | Omitted. The draft says the server sets `UPSELL`, `DOWNSELL`, or `CANCELLATION` from the action and the delta, and that this default is a proposal. |
| `chargeType` | Default `RECURRING`. |

**There is no subscription picker in the Angular app that was read.** The seller must supply `targetSubscriptionId` and `sourceAcceptedLineUuid`. **Where those values are copied from is unspecified. Unverified.**

**Success.** `201` with `Location` and a `LifecycleChange` in `DRAFT`. The screen moves to §5.3. A second submit with the same `Idempotency-Key` and the same body is `200` with `Idempotent-Replayed: true` and the same change. The screen treats that as the same navigation, and shows a short note that the change already existed.

**Empty.** Zero lines: the primary button stays disabled. Copy: "Add at least one line." No request is sent.

**Errors.** Banner `role="alert"`, text `{message} [{errorCode}]`, matching `httpErrorMessage` in `quote.service.ts`. For `422 VALIDATION_REJECTED`, also list each `violations[]` item as `{ruleId}: {message}` and, when `field` is set, attach it to that input.

| HTTP | `errorCode` | What the seller sees |
|---|---|---|
| 401 | `UNAUTHORIZED` | Banner. Form values stay. |
| 404 | `NOT_FOUND` | Banner. The draft names 404 on create. **Which missing id produces it was not illustrated.** |
| 409 | `IDEMPOTENCY_CONFLICT` | Banner. The form stays so the seller can start a new key only by leaving and re-entering. The screen does not silently mint a second key for a changed body. |
| 422 | `INTENT_NOT_SUPPORTED_IN_SLICE` | Banner. Intent is fixed, so this is a defect display, not a picker. |
| 422 | `ACTION_NOT_SUPPORTED_IN_SLICE` | Banner on the line. Status is unchanged because no resource is stored for a rejected create of an unsupported action. |
| 422 | `VALIDATION_REJECTED` rule `V2` | Banner. AC-S5-6: a target subscription that is not `ACTIVE` stores no resource. |
| 422 | `VALIDATION_REJECTED` other rule ids | Banner plus field markers. State unchanged when a resource already exists; on this screen no resource was stored. |

**API calls.**

| When | Call |
|---|---|
| Open | `GET /api/v1/contracts/{contractId}` if §5.1 was not already loaded |
| Create | `POST /api/v1/lifecycle-changes` with `Idempotency-Key` |

`POST /api/v1/quotes/continuations` is not this screen. The draft says that path, when a later facade is active, can also create a lifecycle change and return its id only in `X-Lifecycle-Change-Id`, while the body stays `QuoteDto`. The proposal does not name or enable that switch. This screen calls `POST /lifecycle-changes` directly. It does not call the continuation facade.

`POST /api/v1/quotes/continuations/preview` does not create a lifecycle change and does not open an amendment. This screen does not call it.

### 5.3 Price and submit the lifecycle change

**Purpose.** Price lines through pricing-service and submit the commercial change. Quote-service stores the decision. It does not calculate the price (AC-S5-2, V8).

**Proposed URL.** `/lifecycle-changes/:lifecycleChangeId`.

**Loading.** Spinner while `GET` is in flight. Error with no body: banner, Retry, and a link back to the contract list when `contractId` is known.

**Loaded header.** `id`, `intent`, `status`, `revision`, `version`, `actionDate`, `contractEffectiveDate`, `backdatingReasonCode`, `currencyCode`, `outputMode`, `contentDigest` (nullable on an unpriced draft), `amendmentId` (null until the document exists), `createdAt`, `createdBy`.

**Lines.** For each `ChangeLine`: `lineNo`, `actionCode`, `quantitySemantics`, quantities, `effectiveFrom`, `effectiveTo`, `targetSubscriptionId`, `sourceAcceptedLineUuid`, `observedCommercialRevision`, `businessCategory`, and the pricing pins when present: `amendMode`, `prorationMethod`, `priceAsOf`, `policyDecisionId`, `policyId`, `policyVersion`, `pricingTraceId`, `productExternalId`, `pricebookVersion`, `configurationHash`, `unitPrice`, `netAmount`, `approvalRequestId`. Pins are blank before a successful price. `configurationHash` is the interim non-null SHA-256 pin until DRG-428 replaces it (V9). The screen does not let the seller type the hash, the policy ids, or the actor.

**Price.** Enabled while the description allows re-pricing the current revision: status is still an unpinned draft decision, which the draft names as becoming `PRICED` when every priced line has a decision. Re-pricing after `IN_APPROVAL` requires a new revision; the Price button is disabled in `IN_APPROVAL` and the copy points at Revise. **The exact set of statuses that accept `:price` besides that sentence was not enumerated. Unverified beyond the sentences read.**

The first-slice acceptance mode is `RETAIN_CONTRACTED` with proration `DAILY` and `priceAsOf` `EFFECTIVE_DATE`. Omitted lines use those defaults and policy slot `AMENDMENT`. The screen sends that mode. `LIST` and `OVERRIDE`, and proration `MONTH_DAY`, `NONE`, and `NEXT_CYCLE`, are accepted on the request so the policy slot can be recorded. The draft says whether pricing-service can evaluate them is a prerequisite check, not a second pricing implementation. This screen shows `LIST` and `OVERRIDE` as selectable only after the pricing contract in §1 exists. Until then the control stays on `RETAIN_CONTRACTED` / `DAILY`. An override, when the contract allows it, sends `value` and `reasonCode` only. The actor is the authenticated principal (AC-S5-4, V14). The response `approvalRequestId` is shown as text. The screen does not decide the approval.

**Revise.** `POST .../revisions` is allowed from `DRAFT`, `PRICED`, `IN_APPROVAL`, `APPROVED`, and `PRESENTED`. The previous revision is frozen `SUPERSEDED`. Success returns status `DRAFT` and clears pricing pins on the new revision. The screen then looks like a new unpriced draft. A new revision of an already executed document is `409 IMMUTABLE_EXECUTED`. The correction path is a new lifecycle change, which returns the seller to §5.2.

**Submit commercial change.** Enabled only in `PRICED`. Success moves status to `IN_APPROVAL`. Repeating submit for the same revision and `contentDigest` is `200` with `Idempotent-Replayed: true`. The screen shows the status it received and does not treat a replay as a second submission.

**Withdraw.** Enabled before `ORDERED` or `APPLIED`. Success is `WITHDRAWN`. A second withdraw is a no-op. Optional `reasonCode`. This does not assign an amendment sequence.

**Open document.** Enabled when the seller chooses to open the CAM document. The draft does not name a lifecycle-status guard on `POST /contracts/{contractId}/amendments`. Amendment `:submit` requires the current revision to be priced and `contentDigest` to be present. The button is enabled when status is `PRICED` or `IN_APPROVAL` and a digest is present. **Enabling it in other statuses is unverified.**

**Empty.** `GET .../pricing-decisions` before price: "No pricing decisions for this revision." The line table still shows the commercial lines. A lifecycle change with no lines is outside the create schema (`minItems: 1`). If a `GET` returns no lines, the screen shows "No lines on this revision" and disables Price and Submit.

**Errors.** Same banner as §5.2. `422` leaves status unchanged.

| HTTP | `errorCode` | When |
|---|---|---|
| 404 | `NOT_FOUND` | Unknown id |
| 409 | `VERSION_CONFLICT` | `version` on price, submit, revise, or withdraw is stale. The screen reloads `GET` and asks the seller to repeat. |
| 409 | `IDEMPOTENCY_CONFLICT` | Same key, different body |
| 409 | `IMMUTABLE_EXECUTED` | Revise of an executed document |
| 422 | `ACTION_NOT_SUPPORTED_IN_SLICE` | Submit sees `SWAP`, `UPGRADE`, or `DOWNGRADE` (AC-S5-3) |
| 422 | `VALIDATION_REJECTED` `V2` | Target is not `ACTIVE` |
| 422 | `VALIDATION_REJECTED` `V8` | A priced line is missing `policyDecisionId`, `policyId`, `policyVersion`, or `pricingTraceId` |
| 422 | `VALIDATION_REJECTED` `V9` | Product, pricebook, or configuration pin is absent |

**API calls.**

| When | Call |
|---|---|
| Open and Retry | `GET /api/v1/lifecycle-changes/{lifecycleChangeId}` |
| Pricing pins panel | `GET /api/v1/lifecycle-changes/{lifecycleChangeId}/pricing-decisions` |
| Revision list | `GET /api/v1/lifecycle-changes/{lifecycleChangeId}/revisions` |
| Price | `POST /api/v1/lifecycle-changes/{lifecycleChangeId}:price` with `Idempotency-Key` and `version` |
| Revise | `POST /api/v1/lifecycle-changes/{lifecycleChangeId}/revisions` with `Idempotency-Key`, `version`, and lines |
| Submit | `POST /api/v1/lifecycle-changes/{lifecycleChangeId}:submit` with `Idempotency-Key` and `version` |
| Withdraw | `POST /api/v1/lifecycle-changes/{lifecycleChangeId}:withdraw` with `Idempotency-Key`, `version`, optional `reasonCode` |
| Open document | `POST /api/v1/contracts/{contractId}/amendments` with `Idempotency-Key`, `lifecycleChangeId`, `lifecycleChangeRevision`, `signaturePolicy` |

**Signature policy on open-document.** Choices that can reach `EXECUTED` in this slice: `NOT_REQUIRED`, `NOTICE_ONLY`, `CLICK_THROUGH`. `WET` and `E_SIGN` can be stored and cannot be executed here. The screen does not offer `WET` or `E_SIGN`. Default shown: `CLICK_THROUGH`, because that is the policy that stops for a seller accept. `NOT_REQUIRED` and `NOTICE_ONLY` auto-execute inside `:record-approval`, which is not a button on this screen. **The draft does not name a UI default. The `CLICK_THROUGH` default above is a proposal in this file, not a contract field.**

`coTermDate` defaults to the contract end date when omitted.

`201` creates a `DRAFT` document with `provisionalNumber` set and `amendmentSeq` null. `200` with `Idempotent-Replayed: true` is the same document. The same `lifecycleChangeId` with a different `lifecycleChangeRevision` or a different content digest is `409 IDEMPOTENCY_CONFLICT`. A further edit is a new revision, not a second create. Every target subscription must belong to this contract (V1) or the response is `422 VALIDATION_REJECTED`.

### 5.4 Amendment document review

**Purpose.** Legal and evidentiary wrapper. Totals are the digest-bound copies. The screen does not recompute them. CAM is the only writer of applied state, and apply is S4, so this screen does not apply the document.

**Proposed URL.** `/amendments/:amendmentId`.

**Loaded.** From `AmendmentDocument`: `provisionalNumber` as the title until `EXECUTED`, then `amendmentNumber` (`{contractNumber}-A{seq}`, sequence zero-padded to 3). Also `status`, `documentKind`, `signaturePolicy`, `currentRevisionNo`, `amendmentSeq` (hidden until non-null), `actionDate`, `contractEffectiveDate`, `backdatingReasonCode`, `coTermDate`, `currencyCode`, `contentDigest`, `tcvDelta`, `arrDelta`, `oneTimeTotal`, `creditTotal`, `approvalRef`, `version`, `evidenceClass`, `executedAt`. `retentionUntil` is not computed in this slice (OQ8); the screen shows it only when the payload includes it.

Lines of the current revision, from the `GET` by id (also available at `GET .../lines`). Application units, ordered by `dependencyOrder`, so the seller can see the units S4 will apply. The draft says one unit per target subscription in the first slice, and dependency order creates before terminates. Events, ordered by `seqNo`. Clients cannot post events. Revisions, in `revisionNo` order. At most one revision has `statusAtClose` `EXECUTED`.

**Actions by document status.** First-slice commands use `DRAFT`, `IN_REVIEW`, `APPROVED`, `EXECUTED`, `REJECTED`, and `WITHDRAWN`. Other enum values are reserved and have no button.

| Status | Enabled seller actions | Disabled, with the reason in the control title |
|---|---|---|
| `DRAFT` | Submit document, Withdraw | Accept (needs `APPROVED` and `CLICK_THROUGH`) |
| `IN_REVIEW` | Withdraw | Submit (from `DRAFT` only). Accept. |
| `APPROVED` and `signaturePolicy` `CLICK_THROUGH` | Accept, Withdraw | Submit |
| `APPROVED` and `NOTICE_ONLY` or `NOT_REQUIRED` | Withdraw | Accept. Those policies auto-execute in `:record-approval`, not in `:accept`. If the document is still `APPROVED`, the screen says execution is waiting on approval routing. |
| `APPROVED` and `WET` or `E_SIGN` | Withdraw | Accept and any send action. Copy states that execution returns `SIGNATURE_NOT_SUPPORTED_IN_SLICE` and leaves status unchanged (AC-S3-4). |
| `EXECUTED` | None | Further mutation returns `409 IMMUTABLE_EXECUTED` and an audit event is written (AC-S3-5). Number and `executedAt` stay visible. |
| `REJECTED`, `WITHDRAWN` | None | Terminal. Withdraw of a terminal state that was never executed is `409 ILLEGAL_TRANSITION`. |
| `OUT_FOR_SIGNATURE`, `APPLIED`, `PARTIALLY_APPLIED`, `APPLY_BLOCKED`, `EXPIRED`, `SUPERSEDED`, `VOID` | None | Reserved. The draft's withdraw list includes `OUT_FOR_SIGNATURE`, but the first slice does not send for signature, so this screen has no path that lands there. |

**Submit document.** From `DRAFT` only, and only when the current revision is priced and `contentDigest` is present. Runs V1 through V14. Success is `IN_REVIEW` and the body includes `contentDigest` (AC-S3-2). The same revision and digest again is `200` with `Idempotent-Replayed: true`. A digest mismatch is `409`. The screen sends `version` and the `contentDigest` it last read.

**Accept.** From `APPROVED` with `CLICK_THROUGH` only. One transaction sets `EXECUTED`, `executedAt`, `executedRevisionNo`, and `amendmentNumber`. A second accept of the same executed revision is a no-op. The screen shows the number from the response.

**Withdraw.** From `DRAFT`, `IN_REVIEW`, `APPROVED`, or `OUT_FOR_SIGNATURE`. Status becomes `WITHDRAWN`. `amendmentSeq` stays null and `lastExecutedSeq` on the contract is unchanged (AC-S3-7). A second withdraw is a no-op.

**Not on this screen.**

- `:send-for-signature`. In this slice it returns `422 SIGNATURE_NOT_SUPPORTED_IN_SLICE` for every policy, including `CLICK_THROUGH`, `NOTICE_ONLY`, and `NOT_REQUIRED`, and leaves status unchanged.
- `:execute`. Marked `x-opencrm-internal: true`. It is the system counterpart of auto-execute on `:record-approval`.
- `:record-approval`. Called when approval-routing decides `CONTRACT_MODIFICATION`, or when the change is auto-approved under the threshold. The threshold amount is not defined here (S5 OQ17). `APPROVED` stores `approvalRef` and moves `IN_REVIEW` to `APPROVED`, then auto-executes when the policy is `NOT_REQUIRED` or `NOTICE_ONLY`. `CLICK_THROUGH` stops at `APPROVED`. `REJECTED` is terminal. The existing `ApprovalsInboxComponent` is a quote-approval inbox. This spec does not add an amendment button to it.
- `:apply`, `:void`, `:rebase`, `GET .../rendition`, `GET .../attachments`. Tagged `later`.

**Empty.**

| Read | Empty copy |
|---|---|
| Lines | "No lines on this revision." Submit stays disabled. |
| Application units | "No application units." Review still works. Apply is not offered. |
| Events | "No events." |
| Revisions | A document always has a current revision in the header (`currentRevisionNo`). An empty revision list is an error banner, not a normal empty state. **The draft does not describe an empty revision list. Unverified.** |

**Errors.**

| HTTP | `errorCode` | Screen |
|---|---|---|
| 401 | `UNAUTHORIZED` | Banner, Retry |
| 404 | `NOT_FOUND` | Banner, link back to quotes. No contract id is on the URL. The seller uses the document only after a successful `GET`. |
| 409 | `VERSION_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `IMMUTABLE_EXECUTED`, `ILLEGAL_TRANSITION` | Banner. On `VERSION_CONFLICT`, reload then ask for a repeat. On `IMMUTABLE_EXECUTED`, remove the action buttons. |
| 422 | `VALIDATION_REJECTED` | Status stays `DRAFT`. List `violations[].ruleId`. V6 copy from the draft example: effective date is before the action date and `backdatingReasonCode` is absent. The screen returns the seller to the dates and the reason code. It does not change status locally. |
| 422 | `SIGNATURE_NOT_SUPPORTED_IN_SLICE` | Status unchanged. Banner uses the server `message`. |

**API calls.**

| When | Call |
|---|---|
| Open and Retry | `GET /api/v1/amendments/{amendmentId}` |
| Lines for a chosen revision | `GET /api/v1/amendments/{amendmentId}/lines?revisionNo={n}` (omit `revisionNo` for the current revision) |
| Units | `GET /api/v1/amendments/{amendmentId}/application-units` |
| Events | `GET /api/v1/amendments/{amendmentId}/events?page=0&size=20` |
| Revisions | `GET /api/v1/amendments/{amendmentId}/revisions` |
| Submit | `POST /api/v1/amendments/{amendmentId}:submit` |
| Accept | `POST /api/v1/amendments/{amendmentId}:accept` |
| Withdraw | `POST /api/v1/amendments/{amendmentId}:withdraw` |

All three posts send `Idempotency-Key` and `version`.

### 5.5 Lifecycle change queue

**Purpose.** The draft's list is "the review list for the start and review flow."

**Proposed URL.** None in the app. A queue could filter `GET /api/v1/lifecycle-changes` by `contractId`, `status`, and `intent`. Ordered by `createdAt` descending. Filters are exact matches.

**Empty.** `data: []`: "No lifecycle changes."

**Error.** `401` banner. The draft does not list `404` on this collection.

**API.** `GET /api/v1/lifecycle-changes?page=0&size=20`, plus optional `contractId`, `status`, `intent`.

This queue is not the quotes list. `QuotesListComponent` stays the list of quotes. Deal type `AMENDMENT` on a quote is not a lifecycle-change row.

DRG-597 (separate renewals and amendments work queues) is related to DRG-537 and is not specified here.

## 6. States

Two machines. The draft says quote-service is not the legal record. `APPROVED` and later on the lifecycle change follow the amendment document.

### 6.1 Lifecycle change (seller commands in this slice)

| Status | How it is reached | Seller next step |
|---|---|---|
| `DRAFT` | `POST /lifecycle-changes`, or a new revision | Edit lines, then Price |
| `PRICED` | `:price` when every priced line has a decision | Submit, or open the amendment document |
| `IN_APPROVAL` | `:submit` from `PRICED` | Wait. Price requires a new revision. |
| `WITHDRAWN` | `:withdraw` before `ORDERED` or `APPLIED` | None |

The enum also contains `APPROVED`, `PRESENTED`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `SUPERSEDED`, `ORDERED`, `APPLIED`, `REVERTED`. Revise is allowed from `APPROVED` and `PRESENTED`. This spec does not add seller buttons that target the other values. **Which command sets `PRESENTED`, `ACCEPTED`, `ORDERED`, `APPLIED`, or `REVERTED` on the lifecycle change was not defined in the operations that were read.**

### 6.2 Amendment document (first-slice commands)

Policy spec §3 is the state table. Its OQ2 note in that section is superseded by the 2026-10-16 addendum: apply only after `EXECUTED`, one `status` column, no `applyStatus`. The OpenAPI repeats that.

| From | To | Command in the draft |
|---|---|---|
| (none) | `DRAFT` | `POST /contracts/{contractId}/amendments`. `provisionalNumber` is set. `amendmentSeq` is null. The policy table says "amendment number reserved"; the draft's final number is assigned only at `EXECUTED`. The screen shows the provisional number, not a final number, in `DRAFT`. |
| `DRAFT` | `IN_REVIEW` | `:submit` when priced and the digest is present. V1–V14 pass. |
| `IN_REVIEW` | `APPROVED` | `:record-approval` decision `APPROVED`. Not a seller button. |
| `IN_REVIEW` | `REJECTED` | `:record-approval` decision `REJECTED`. Terminal. |
| `APPROVED` | `EXECUTED` | Auto-execute in that same approval transaction when policy is `NOT_REQUIRED` or `NOTICE_ONLY`. `:accept` when policy is `CLICK_THROUGH`. |
| `DRAFT`, `IN_REVIEW`, `APPROVED` | `WITHDRAWN` | `:withdraw`. Terminal. No sequence consumed. |

`OUT_FOR_SIGNATURE` → `EXECUTED`, expiry, void, and `APPLIED` / `PARTIALLY_APPLIED` / `APPLY_BLOCKED` are in the policy table and in the enum. They are later slices. S4 apply is the ledger owner's command after `EXECUTED`. This UI does not show an Apply button.

A failing rule on submit appends a `VALIDATION_REJECTED` event whose payload names the rule id, and leaves status unchanged. The events panel reloads after a `422`.

## 7. Shared empty, loading, and error behavior

Match the quote screens that were read, without copying their quote commands:

- Loading with no body yet: a status region and a spinner, as `QuotesDetailComponent` does.
- Error with no body: banner `role="alert"`, message, Retry, and a way back. Quote detail's back target is `/quotes`. Amendment screens use that same target because there is no contracts nav item today.
- Error while a table already has rows: keep the rows and show the banner, as the quotes list does when `tableRows().length > 0`.
- Empty collection: `EmptyStateComponent` is what the quotes list and the approvals inbox import. Title and message are the copies in §5. **The component's inputs were not read; only the call shape in `QuotesListComponent` (`icon`, `title`, `message`, `actionLabel`) was read.**
- `401` and `404` replace the body.
- `409` and `422` keep the form or the document on screen and do not change the status badge until a later `GET` says it changed.
- Success toasts are not specified. The quote detail actions that were read do not show a toast in the template region that was read. Status comes from the returned resource.

Rule ids the banner can show, from the policy spec, when the draft names them on these screens:

| Rule | Seller-visible meaning (policy wording) |
|---|---|
| V1 | Intent is Amend and every target subscription belongs to this contract |
| V2 | Each target subscription is `ACTIVE` at the action date |
| V3 | `observedRevision` equals the current commercial revision |
| V4 | Update and Remove name a unique source accepted line that exists, is not retired, and has `ACCEPTED_PRICED` provenance |
| V5 | Effective date is inside the contract term; amended lines co-term to the existing end date |
| V6 | Action date and effective date are both stored; backdating requires a reason code and approval |
| V7 | Quantity semantics are declared and `start + delta = end`, with `end >= 0` |
| V8 | Every priced line carries policy decision id, policy id, policy version, and pricing-service trace id |
| V9 | Product external id, pricebook version, and configuration hash are pinned |
| V14 | A manual override records actor, reason code, and value, and routes to approval |

V10, V11, V12, and V13 are apply-time or schema rules in the policy list. The draft says spine rules V3, V10, V12, and V13 apply when S4 runs. This UI does not run apply, so those failures have no screen of their own here. If a document submit returns one of those rule ids, the banner still prints `ruleId` and `message`.

## 8. Renewals

DRG-537 names renewals and amendments together. The draft does not.

- `POST /lifecycle-changes` accepts intent `AMEND` only. `RENEW` is in the `LifecycleIntent` enum and is rejected on create with `INTENT_NOT_SUPPORTED_IN_SLICE`.
- The OpenAPI description lists renewal intent, price-lock segments, and DRG-592 new-contract versus amend-in-place as out of this slice.
- `renewals-amendments-dev-readiness.md` says renewals are not ready.
- `QuoteContinuationController` already accepts `operation: RENEWAL` and returns a quote draft. The draft says `RENEWAL`, `EXPANSION`, and `EXTENSION` keep today's authoring behavior until those intents are in a later slice. No Angular caller exists.
- The quotes list can filter `dealType=RENEWAL`. That filter lists quotes. It does not start a renewal.

A renewal start, price, or review screen is not specified, because this contract cannot authorize it. Building one would require an accepted contract that accepts intent `RENEW`. That contract is not this file.

## 9. Disagreements that keep the spec unbuilt

These are readings, not resolutions.

1. **Click-through versus auto-execute.** First-slice FR-08 says `NOT_REQUIRED`, `NOTICE_ONLY`, and `CLICK_THROUGH` auto-execute after `APPROVED`. The OpenAPI says `NOT_REQUIRED` and `NOTICE_ONLY` auto-execute inside `:record-approval`, and `CLICK_THROUGH` stops at `APPROVED` until `:accept`. The screens in §5 follow the OpenAPI. FR-08 is not applied as a screen rule.
2. **Provisional number versus "number reserved".** Policy §3 says the transition into `DRAFT` reserves an amendment number. The draft, OQ4 as recorded in the OpenAPI description, and AC-S3-7 say `provisionalNumber` at `DRAFT`, `amendmentSeq` only at `EXECUTED`, and a withdrawn draft consumes no sequence. The screen shows the provisional number until `EXECUTED`.
3. **`DIRECT_ORDER`.** The create schema allows `outputMode` `DIRECT_ORDER`. The first-slice requirements list ordering as out of slice. §5.2 sends `QUOTE` only.
4. **Readiness text.** `renewals-amendments-dev-readiness.md` says API names exist at resource level and the OpenAPI is to be written in S3. The YAML now exists and is still `proposal`.
5. **Pricing.** The price screen's pins are AC-S5-2. OQ9 §6 says today's pricing execute API does not return them. The screen cannot be built on the current pricing API.
6. **Legal-review gate.** The policy clarification says only changes to pricing, term, or seats are legal-review-free, and that adding or removing a product line is outside that set unless product treats it as seats. That gate is DRG-594 and is not a field on the draft create body. This spec adds no legal-review control.
7. **Visibility.** DRG-595 is out of the HTTP slice. No read-only sales-ops or finance layout is specified.

## 10. Existing components a later build would extend

Only after §1. This list is the attachment point, not a change.

| Existing piece | Later use |
|---|---|
| `ShellComponent` and `app.routes.ts` | New child paths would be siblings of `quotes` |
| `SidebarComponent` | A new nav item would be a new `routerLink` in the CPQ group |
| `QuotesListComponent` | Stays the quote list. Deal-type filter stays a quote filter |
| `QuotesDetailComponent` | Stays quote commands. An amendment quote, if the continuation facade is later enabled, is still a `QuoteDto` at `/quotes/:id`. The lifecycle id would be the `X-Lifecycle-Change-Id` header, which `QuoteService.create` does not read |
| `QuoteService` | Stays `/api/v1/quotes`. Lifecycle and amendment calls would be a different service. None exists in `frontend/` |
| `ApprovalsInboxComponent` | Stays quote approvals until an accepted contract says otherwise |
| `PageHeaderComponent`, `EmptyStateComponent`, `DataTableComponent`, `StatusBadgeComponent` | Already imported by the quote screens. Reuse is a proposal. Their implementations were not read |
| `csrfBootstrapInterceptor` | Already attaches `X-XSRF-TOKEN` on gateway mutations |

No new component, route, client, or feature flag is authorized by this file.
