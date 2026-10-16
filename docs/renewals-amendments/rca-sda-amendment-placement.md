# Where RCA puts "amendment" architecturally, and what that means for the amendment document entity

Date: 2026-10-16. Web research only. Context comes from `cpq-renewals-amendments-research.md` and `amendment-document-design-spike.md`, which this file does not repeat. Unmarked statements are documented by Salesforce. Anything marked **[INFERENCE]** is my synthesis.

## 0. Is "SDA" a real Salesforce term?
**No.** I found no Salesforce publication called "Salesforce Domain Architecture". The phrase shows up only as informal shorthand for the architect certification tracks (Application Architect and System Architect "domain" credentials). Salesforce Architects (architect.salesforce.com) publishes the Well-Architected Framework, Decision Guides and data-model diagrams, but no layered domain model for revenue.

The nearest published structure, and the one I used as the "SDA guide", has three parts:
- **The pillars of Agentforce Revenue Management** (the new name for Revenue Cloud), as listed on Trailhead: Product Catalog Management, Salesforce Pricing, **Transaction Management (CPQ + Assets)**, **Salesforce Contracts**, Dynamic Revenue Orchestrator, and Billing. Trailhead says the pillars are "built on a foundation of powerful APIs" [T1].
- **The feature areas in the Revenue Management Developer Guide**, for example "Transaction Management Standard Objects" [D1].
- **The Data Model Gallery's diagrams for Revenue Management**, for example "Transaction Management: Asset" and "Transaction Management: Contract" [G1][G2].

In this file, "domain" means a pillar and "subdomain" means a component inside a pillar. Salesforce does not use the terms "bounded context" or "layer" for these.

## 1. Where RCA places each amendment artifact
| Artifact | RCA home (pillar → component) | Evidence |
|---|---|---|
| `initiateAmendment` (inputs `amendAssetIds`, `amendStartDate`, `quantityChange`, `amendOutputType` = Quote or Order, optional `amendContractId` and `amendOpportunityId`, `skipPricing`; outputs `amendRecordId` and `requestIdentifier`) | Transaction Management → Asset Lifecycle Management (ALM). It is a headless invocable action at `/actions/standard/initiateAmendment` and runs asynchronously. | [D2]; the release note describes the Amend/Renew/Cancel flow, which can be called from Account and Contract records [H1] |
| Amendment intent on the transaction: `QuoteAction` (Type = Add, **Amend**, Renew, Cancel, Transfer, and others; subtypes include FieldAmendment, UpgradeFrom/To, SwapIn/Out; `SourceAssetId`), and `OrderAction` on the order | Transaction Management → CPQ (quote and order capture) | [D3]; the asset diagram includes Order Action [G1] |
| Record of change: `AssetAction` (business category such as Upsells, Downsells or Cancellations; `ActionDate` kept separate from the effective start of the state period; `CanRollBack`). It is **read-only and system-populated**. | Transaction Management → ALM. The diagram is "Transaction Management: Asset". | [D4][G1] |
| Effective-dated state: `AssetStatePeriod` (quantity, amount and MRR per span; `SegmentIdentifier` for ramps). It is read-only. | Transaction Management → ALM | [D5][G1] |
| Traceability: `AssetActionSource`, which points back to the originating OrderItem, WorkOrderLineItem or external transaction id | Transaction Management → ALM | [D6] |
| Contract record and `AssetContractRelationship` | Transaction Management. The diagram is "Transaction Management: Contract". | [G2] |
| Legal amendment of the agreement: the "Amend Contract" action, which saves the change as a new **ContractDocumentVersion** or as a new contract linked to the existing one. It has a contract state model (draft → negotiate → approve → e-sign → activate), DocuSign, and version comparison. | **Salesforce Contracts** (a separate pillar) | [T2][T3][D7] |
| Repricing the change | Salesforce Pricing (pricing procedures). The amendment action *calls* pricing and can skip it with `skipPricing`. | [D2][W1] |
| Fulfilling the amendment order | Dynamic Revenue Orchestrator (decomposes the order and builds the fulfilment plan) | [W1] |

**Placement in plain terms:**
- RCA has **no standalone "Amendment" entity**. An amendment is a *transaction type*: it is a QuoteAction or OrderAction on an ordinary Quote or Order. Its committed result is AssetAction plus AssetStatePeriod on the asset of record.
- Both halves live in **one pillar, Transaction Management**, which Salesforce describes as "CPQ + Assets". Each request "is processed as a quote and completed through an order" [T4].
- The **legal document** side of an amendment is in a *different* pillar, Salesforce Contracts.

**[INFERENCE]** Asset state changes are tied to the *order*, not to the contract document. The amend flow's required permissions include Assetize Order, PlaceOrder and CreateContract, but nothing from contract lifecycle management (CLM) [H1]. So applying a change to the asset does not wait for the document to be executed. The two are linked through `amendContractId` and `AssetContractRelationship`, but each follows its own lifecycle.

## 2. Mapping onto open-crm-cpq
| open-crm-cpq | Closest RCA counterpart |
|---|---|
| quote-service: `QuoteContinuationRequest`, `QuoteContinuationPolicy`, LifecycleChange | CPQ component: Quote plus QuoteAction (intent and pricing) |
| contract-asset-management (CAM): `AcceptedQuoteSubscriptionService`, `subscription_rate_periods`, `applied_quote_acceptances` | ALM component: AssetAction, AssetStatePeriod and AssetActionSource, plus the Contract record and its asset links |
| Proposed AmendmentDocument (states, numbering, e-sign, EXECUTED) | Salesforce Contracts: ContractDocumentVersion, the Amend Contract action and the state model |
| pricing-service, product-service, pricebook-service (outside the continuation path today) | Salesforce Pricing and Product Catalog, which the amendment calls as services |

**What this means [INFERENCE]:**
1. Salesforce treats quote-service and CAM as two **subdomains of one domain**, and the split between them already matches RCA. Intent and pricing sit on the transaction side. The immutable, effective-dated record sits on the asset side and is written only by the platform.
2. The amendment *document* is not a Transaction Management concept in RCA. It corresponds to the Contracts pillar. On the "SDA" reading, that makes the document neither a quote-service concept nor strictly a CAM concept. It is closest to the spike's option C, a contract-document context.
3. Option B (quote-service) has the least support. RCA never makes the quote the record of a change. A quote only carries the QuoteAction intent.
4. One difference matters for the open "apply on Executed vs Accepted" decision. RCA applies changes to the asset at order activation and keeps document execution on a separate track. This supports **applying on acceptance** (or on the order) and treating EXECUTED as a parallel legal status, not as a gate on the ledger.

**Correction to the spike, §0 "Market context":** Salesforce Contracts *does* document an amendment as a versioned, state-modelled legal document with e-sign [T2][T3][D7]. Conga CLM also documents an "Amend" action that creates a new agreement version sharing the base agreement number, with the decimal incremented [C1]. The "market gap" claim should be narrowed. The gap is the integration of a numbered, executed amendment with the effective-dated subscription ledger, not the document itself.

## 3. RCA conventions that constrain where the entity lives
- **Transactions are kept separate from the asset of record.** Quote and Order lines are mutable. AssetAction and AssetStatePeriod "can't be edited" and are system-populated [D4][D5]. *Constraint:* whichever service owns the ledger (CAM) must be the only writer of applied state. AmendmentDocument must not write rate periods directly.
- **The action date is kept separate from the effective date.** Rollups are kept on the action [D4]. *Implication:* the document's signed or executed date stays separate from the change's effective date.
- **Traceability runs back to the source.** AssetActionSource links each change to its originating transaction line [D6]. *Implication:* every applied period should reference the LifecycleChange line and, where one exists, the document version.
- **Pricing is a shared service.** Amendments call pricing procedures (with `skipPricing`) and never embed price logic [D2][W1]. *Implication:* this supports moving continuation pricing out of quote-service into pricing-service (RA-08 and the sanity check). It also means the document only pins price results and never recomputes them.
- **Orchestration happens downstream of the order.** DRO decomposes and fulfils orders [W1]. *Implication:* the amendment entity is not an orchestration concern. Apply, ERP sync and e-sign callbacks belong in an outbox or saga, not inside the document aggregate.
- **Actions are API-first and headless.** They are invocable actions, async with a `requestIdentifier`, and can be called from Account or Contract records [D2][H1][W1]. *Implication:* expose `initiateAmendment`-style commands keyed by asset or subscription ids, with an optional contract id.
- **Contracts are linked to assets, not the owners of them.** `AssetContractRelationship` [G2]. *Implication:* a thin Contract header in CAM that links to subscriptions is consistent with RCA. Contract *documents* are a separate concern.

## 4. Recommendation
**Keep LifecycleChange in quote-service and the ledger in CAM. Put AmendmentDocument, together with the thin Contract header, in CAM as a distinct "contract-documents" module.** That module should have its own aggregate, schema and API path (`/contracts/{id}/amendments`), and it should talk to the ledger code only through internal commands and events, so it can later be extracted into option C. **[INFERENCE]**

Why:
- The structure that best maps to "SDA" (the RCA pillars) puts the record-of-change next to the asset of record. It also puts contract documents in their own pillar, which argues for a separate *context*.
- RCA's pillars share one database and one platform. They are packaging boundaries, not network boundaries. Adopting a separate service now would add the cost of a saga and the risk of split-brain between "executed" and "applied" that the spike flagged.
- A module inside CAM keeps the context boundary without that cost.

Tradeoffs:
- **Option A as a module (recommended).** Strongest idempotency and a single writer of applied state. Cost: CAM grows, and the discipline of keeping the module boundary must be enforced.
- **Option C (separate service).** Most faithful to the Contracts-pillar split. Gives independent retention, legal hold and CLM-style state models. Cost: an extra service, plus eventual consistency on apply.
- **Option B (quote-service).** Contradicts RCA, where the quote is intent only. Not recommended.

**Extract to option C if:** independent retention or legal-hold rules (DRG-313) are needed, a full CLM capability is in scope (clauses, redlining, non-sales contract types), or contract documents become multi-contract or tenant-specific.

**Confidence: medium.** The pillar and object placements are well documented. Their mapping onto service boundaries, and the reading of the pillars as an "SDA" model, are inference, because Salesforce publishes no layered domain architecture.

## Sources
- [T1] https://trailhead.salesforce.com/content/learn/modules/efficient-sales-with-revenue-cloud/get-started-with-transaction-management
- [T2] https://trailhead.salesforce.com/content/learn/modules/deep-dive-into-industries-contract-lifecycle-management/work-with-contracts
- [T3] https://trailhead.salesforce.com/content/learn/modules/salesforce-contracts-foundations/dive-into-salesforce-contracts-capabilities
- [T4] https://trailhead.salesforce.com/content/learn/modules/asset-lifecycle-management-with-revenue-cloud/manage-customer-asset-amendments
- [D1] https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/quote_and_order_capture_standard_objects.htm
- [D2] https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/actions_obj_amend_assets.htm
- [D3] https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/sforce_api_objects_quoteaction.htm
- [D4] https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/sforce_api_objects_assetaction.htm
- [D5] https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/sforce_api_objects_assetstateperiod.htm
- [D6] https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/sforce_api_objects_assetactionsource.htm
- [D7] https://developer.salesforce.com/docs/atlas.en-us.clm_developer_guide.meta/clm_developer_guide/sforce_api_objects_contractdocumentversion.htm
- [G1] https://developer.salesforce.com/docs/platform/data-models/guide/transaction-management-asset.html
- [G2] https://developer.salesforce.com/docs/platform/data-models/guide/transaction-management-contract.html
- [H1] https://help.salesforce.com/s/articleView?id=release-notes.rn_revenue_lifecycle_management_asset_amend_renew_cancel.htm&language=en_US&release=248&type=5
- [W1] https://www.salesforce.com/sales/revenue-lifecycle-management/revenue-cloud/
- [C1] https://documentation.conga.com/en/clm-for-salesforce/current/about-conga-clm

Caveats:
- D3, D6, D7, T2 and H1 were read from search-result extracts, not full-page fetches.
- The architect.salesforce.com DRO diagram URL returned 404.
- No published Salesforce source describes "layers" for Revenue Cloud.
