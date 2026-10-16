# Renewals & Amendments Across CPQ, Subscription, Entitlement and Telecom Platforms
*Research date: 16 Oct 2026. Every factual claim cites a source ID from the Sources list (section 8). Text marked **[INFERENCE]** is my own analysis or design, not something a vendor documents.*

**Context:** a legacy software vendor whose revenue is about 60% on-premise (transactional/perpetual and capacity-based licences) and about 40% SaaS subscription. The primary references are the subscription-heavy platforms Salesforce CPQ / Revenue Cloud and DealHub. On-premise perpetual, maintenance and capacity licensing is treated as the harder contrast case.

---

## 1. Headline
1. All the subscription-native platforms run renewals and amendments on the **same backbone**: an installed record (subscription, asset or entitlement), a change transaction (quote, then order) and an effective-dated ledger of versions. Most still give users **different entry points and pricing rules** for "renew" and "amend". Salesforce RCA, Zuora, DealHub, Conga and Oracle CPQ are the most unified. Salesforce CPQ (managed package), HubSpot and Chargebee keep the two motions visibly separate.
2. On-prem entitlement engines (Revenera FlexNet Operations, Thales Sentinel EMS) model renewal, upgrade and upsell as **typed child line items of a parent entitlement**. Licence regeneration ("fulfillment") is a second step. Capacity and usage are attributes or meters, not quantities on a subscription. Customer-side SAM tools (Snow/Flexera) model maintenance periods and upgrade rights rather than renewals as such.
3. The proposed universal model (section 7) is one **Lifecycle Change** capability. It has one change-request entity, one action-code vocabulary and pluggable pricing, proration, co-term and fulfilment policies, so it covers both SaaS subscriptions and perpetual, maintenance and capacity licences.

---

## 2. Primary references (subscription-heavy)

### 2.1 Salesforce CPQ (managed package, SBQQ)
**Renewals**
- Trigger: two checkboxes on the Contract. *Renewal Forecast* creates a renewal Opportunity. *Renewal Quoted* creates the renewal Opportunity plus a primary renewal Quote and Quote Lines [S2][S3].
- Renewal quote lines are built from the contract's **active subscription records** [S1][S4].
- Dates: the Contract fields *Amendment & Renewal Behavior* (Latest End Date / Earliest End Date) and *Disable Amendment Co-Term* set renewal and amendment dates. By default a renewal line starts the day after its parent subscription ends [S4][S5].
- Pricing: the Account field *Renewal Pricing Method* has three values. **Same** (default when blank) reuses the subscription's prices. **List** uses the price-book list price. **Uplift** applies `SBQQ__RenewalUpliftRate__c`; a rate set on the Subscription overrides one set on the Contract. A subscription *Renewal Price* overrides the customer-price mapping [S6][S4].
- Quantity consolidation: the options *Combine Subscription Quantities* and *Combine Asset Quantities* merge lines on the renewal quote. Assets (non-subscription products) can be renewed too [S4].

**Amendments**
- An amendment quote does **not** change the contract. The contract is updated only when an order is created from the quote and marked Contracted [S1].
- CPQ does not edit the original subscriptions. It **adds new subscription records** for the deltas and sets a *Terminated Date* (the day before the amendment start) on subscriptions that are removed. The subscription list therefore works as a **ledger**, and future amendments start from that ledger [S1].
- *Amendment Start Date* on the Contract sets the default effective date [S5].
- Pricing per third-party practitioner sources: existing lines keep their contracted price and discount, and only the delta is priced and prorated to the contract end. To reprice, zero the old line and add a new one [S7][S8]. **Not confirmed in official Salesforce docs.**

**APIs and schema**
- *CPQ Contract API* "to amend and renew": `SBQQ.ContractManipulationAPI.ContractAmender` returns a QuoteModel for an amendment quote [S9][S10].
- Quote `SBQQ__Type__c` values are Quote, Amendment, Renewal and Re-Quote. The lookups are `SBQQ__AmendedContract__c` and `SBQQ__RenewedContract__c` (third-party object-model PDF) [S11].

**Unified or separate?** Separate user actions, separate API classes, separate quote types and separate date/pricing rules. They share the same Contract → Subscription ledger. **[INFERENCE]** This is a shared data model with two distinct capabilities on top.

### 2.2 Salesforce Revenue Cloud (Advanced) / Agentforce Revenue Management
- Three standard invocable actions:
  - `initiateAmendment`: inputs `amendAssetIds`, `amendStartDate`, `quantityChange` (a delta), `amendOutputType` = Quote or Order, `skipPricing` [S12].
  - `initiateRenewal`: inputs `renewAssetIds`, `renewOutputType`, `renewStartDate`/`renewEndDate` (needed for early renewals or expired assets), `rampOptionsDetails` [S13].
  - `initiateCancellation` [S14].
- Lifecycle objects:
  - **AssetAction** records each change. Its category list covers Initial Sale, Renewals, Upsells, Downsells, Cross-Sells, Upgrades, Downgrades, Swaps, Cancellations, Terms And Conditions Changes, transfers and Other. It separates *ActionDate* from the effective start of the state period and keeps rollups such as `TotalRenewalsAmount` [S15].
  - **AssetStatePeriod** is a time span with constant quantity, amount and MRR [S16].
- "A quote is generated for every asset management request … amendment … renewal … or cancellation. Each request is processed as a quote and completed through an order." Amendment quotes show Start Quantity, delta Quantity and End Quantity [S17].
- **Unified or separate?** One lifecycle backbone (Asset, AssetAction, AssetStatePeriod, Quote, Order) with three typed entry actions. **[INFERENCE]** This is the closest published model to the "one capability" target.

### 2.3 DealHub (subscription engine documented as the Subskribe API)
- One **Order** resource with `orderType` ∈ {NEW, CANCEL, AMENDMENT, RENEWAL, RESTRUCTURE}. Renewals use `renewalForSubscriptionId`. Amendments and restructures use `subscriptionId` [S18][S19].
- Line item `action` values include UPDATE and NONE. Every existing line must be listed (`populateMissingLines=true` helps). Orders are created as **DRAFT** and must be *executed*. `isDryRun` previews without saving [S19].
- `lineItemsNetEffect` uses a **replacement** approach: −10 old, +50 new, rather than +40. The response carries `subscriptionTargetVersion`, `renewalForSubscriptionVersion`, `autoRenew` and `subscriptionDurationModel` (TERMED) [S19].
- Only ACTIVE or PENDING subscriptions can be changed [S19].
- Subscription endpoints: `/draftAmendment`, `/draftRenewal`, `/modifiable`, `/reversible`, `/change-events`, `/{version}/revert`, `/renewalOpportunity` [S20].
- Marketing: CPQ covers "new business, renewals, co-terminations, expansions, and amendments". Lifecycle events (amendments, co-terms, renewals, expansions, cancellations) are "governed, automated workflow events" with automatic proration from the effective date [S21][S22].
- **Unified or separate?** Unified. A single order type enum works over a versioned subscription.

---

## 3. Other CPQ platforms named in the brief

| Platform | Renewal model | Amendment model | Unified? | Published schema / API |
|---|---|---|---|---|
| **Conga CPQ** | Asset-Based Ordering. **Renew** creates a renewed line, and the asset status becomes Renewed after the order activates [S23][S24]. *Renewal Adjustment Type* (% Uplift or Uplift Amount) applies only to the renewal [S25]. Auto and OnDemand renewal modes; OnDemand uses a *Renewal Lead Time* [S25]. | **Change** amends the existing asset line with no new line. Quantity is entered as an **absolute** value. The line status becomes *Amended* and an "Amend" transaction-history entry is added [S24][S26]. Other actions: Increment, Terminate, Upgrade [S27]. Line Status values include Existing, Amended, Cancelled, Upgraded, Changed Configuration, Incremented [S28]. | Unified ABO framework with typed actions | REST `/api/asset/v1/assets/{cartId}/change`, `/renew` [S23]. SOAP `ModifyAsset` with `AssetActionDO` (deprecated) [S27] |
| **PandaDoc** | Document-centric. Renewal reminders; AI extracts renewal dates. To renew, duplicate the contract or quote. "Automatic contract renewals aren't possible" [S29]. | Edit the quote in place. The Update Quote API replaces sections and items, and anything omitted is deleted [S30]. Items carry `billing_frequency`, `contract_term`, `pricing_method` [S30]. Rules can set the contract term [S31]. | Neither exists as a modelled capability; both are document workflows | Update Quote API [S30] |
| **QuoteWerks** | Recurring line attributes: RecurringBillingCycle (Weekly to Annual), RecurringStartDate/EndDate, ContractStartDate/EndDate. Reports on contract end dates are used "to contact your customers for renewals" [S32][S33]. | **Revisions**: "Save As Next Revision" creates a new document number with a suffix and marks the old one superseded. Approval is removed on revision [S34]. | No subscription or contract lifecycle; revisions only | Field and macro names [S32] |
| **Zuora** (Billing + Zuora Quotes / CPQ X) | `RenewSubscription` order action. Term settings: `termType` TERMED/EVERGREEN, `renewalSetting` RENEW_WITH_SPECIFIC_TERM / RENEW_TO_EVERGREEN, `autoRenew` [S35][S36]. | Order actions: AddProduct, UpdateProduct, RemoveProduct, ChangePlan, TermsAndConditions, Suspend, Resume, OwnerTransfer, Cancellation [S37]. Each action carries **trigger dates** (ContractEffective, ServiceActivation, CustomerAcceptance). Changes create **charge segments** under the same charge number [S35]. | **Unified**: one Order holds multiple actions, e.g. Renew + RemoveProduct together [S35] | Orders API [S35][S37]. Quote object `zqu__Quote__c` with Subscription_Type = New / Amendment / Renewal / Cancellation; `zqu__QuoteAmendment__c`; Quote Charge Summary stores only deltas [S38][S39] |
| **Oracle CPQ** | ABO **Renew** action. The asset stays Active and the renew date comes from the transaction request date. Since 18D, "the Renew itself is essentially a Modify" [S40]. | **Modify** computes a delta BOM with action codes Add, Update, Delete and "-" (no change), plus business codes Terminate, Suspend, Resume, Renew. A **Projected Asset Cache** stacks open orders by request date. Follow-on orders and Reconfigure handle unfulfilled orders [S40]. | **Unified**: delta engine plus action codes | REST `/rest/v19/assets/{id}/actions/{modify|renew|suspend|terminate}` [S41][S42] |
| **SAP CPQ + S/4HANA BRIM** | Renewal and extension are **Change Processes**: Contract Extension, plus a *New Contract* option that renews into a new contract and cancels the old one [S43]. | Change Process Groups: Change Quantity/Price, Configuration, Swap, Co-Term, Merge, Cancellation, Add Sub-item. *Contract Change Activation Date* drives pricing. Which processes are allowed depends on contract state [S43]. | **Unified** under "change process" | Change Process Group master data synced from S/4 [S43] |
| **HubSpot** | Renewal quote on a **Contract** object. It inherits line items, including mid-term additions, with the same term by default. Acceptance creates a **new contract linked to the previous one**. Evergreen contracts have no TCV or ACV [S44]. | **Change quotes** modify the existing contract, or the contract is edited directly (beta). Proration uses a hybrid month/day method; a mid-period change creates a prorated invoice and a credit memo [S44]. Separate change and renewal quote templates [S45]. | **Separate** quote types and pipelines on a shared contract | Contracts object [S44] |
| **Chargebee** | CPQ *Renewal* quote type: the new contract starts when the current one ends, and price and items are editable [S46]. Contract terms set `action_at_term_end` = renew / evergreen / cancel / renew_once [S47][S48]. | *Amendment* quotes [S49]. API `update_for_items` with `change_option` = immediately / end_of_term / specific_date [S48]. Termination policies: credits (none, prorated, full), termination fee, cancellation cut-off period [S47]. | Separate quote types on one subscription | Subscriptions, Estimates, Quotes, Contract Terms APIs [S48] |
| **Maxio** | A renewal is "a new term for the same Subscription" and the subscription ID does not change. **Scheduled Renewal** allows product, component and quantity changes. **Auto-Renewal** allows price changes only, plus an automatic uplift via *Renewal Factor*. A *Lock-in Date* finalises the renewal [S50]. | Upgrade/downgrade with proration [S51]. CPQ marketing: "mid-term amendments, renewals, and expansion with correct co-terming and proration … one unified contract timeline" [S52]. | Separate mechanisms on one subscription timeline | Help-center docs [S50][S51] |

---

## 4. Added solution sets (scope expansion)

### 4.1 On-prem / enterprise entitlement and asset platforms (perpetual, maintenance, capacity)
- **Revenera (Flexera) FlexNet Operations**
  - Entitlement line items carry an **Order Type** ∈ {New, Renewal, Upgrade, Upsell} with a link to the **parent line item** [S53].
  - Renewal extends a non-permanent line. Permanent lines can be renewed only for products with a **date-based (version-date) feature** [S54].
  - Upgrades and upsells require configured product relationships. Bulk operations show maintenance line items with their expiry [S54].
  - Auto-renew: `isAutoRenewal`, `renewalInterval`/`renewalExpirationDate`, run by a *Line Item Auto Renewal* job. Only deployed, non-permanent lines are renewed [S53][S55].
  - Licence regeneration is a separate step: `renewFulfillment`, `upgradeFulfillment`, `upsellFulfillment`, which need an existing RENEWAL, UPGRADE or UPSELL line item [S56].
  - **Unified or separate?** One entitlement-order model with typed lines. Fulfilment is decoupled.
- **Thales Sentinel EMS**
  - Renewing a line item means "recharge" the quantity (add or subtract) and/or set a new End Date, then *Regenerate License* [S57].
  - Licence-update modes are System-specified, Add or Overwrite. Updates fail for Perpetual models and for changes to concurrent instances [S58].
  - **Capacity** is "the cumulative value of capacity … across … simultaneous sessions". It is tracked through capacity attributes with peak-capacity thresholds and notifications [S59].
  - Lifecycle covers activation, renewal, upgrade, transfer and revoke, with grace-period and expiry notifications [S60].
- **Snow License Manager (Flexera)**: the customer-side SAM view.
  - Maintenance and support periods per licence purchase, with **Upgrade rights** on or off.
  - Renewal = add a new agreement period, or create a new agreement and detach licences not covered.
  - Upgrade and downgrade paths; base-licence-to-upgrade quantity mapping, including metric changes such as installations → processors [S61][S62][S63].
  - Supports PVU, Oracle and SAP metrics [S64].
- **Oracle CPQ ABO** (section 3) and **Conga ABO** also manage tangible assets [S40][S27].
- **[INFERENCE]** For the on-prem 60%, the "renewal" being sold is mostly the **maintenance/support term** and/or a **term licence**, not the perpetual right. Amendments include *capacity true-ups* and *metric conversions*. These are separate commitment lines with separate pricing.

### 4.2 Telecom-style rating, billing and entitlement
- **TM Forum TMF622 Product Ordering**
  - `ProductOrderItem.action` ∈ {add, modify, delete, noChange} [S65].
  - Order states run from draft through acknowledged, inProgress, completed/failed/partial, plus cancellation states [S65].
  - Orders can reference a **Quote** (TMF648) [S66].
- **TMF637 Product Inventory**: `ProductStatusType` = created, pendingActive, active, suspended, pendingTerminate, terminated, cancelled, aborted. The values are illustrative [S67][S68].
- **Oracle BRM**
  - Plan or deal **transition** = cancel the old bundle and purchase the replacement, with configurable proration. `PIN_TRANS_PRO_NORMAL_ON_TRANSITION` prorates the last old fee and the first new fee; purchase fees can be waived [S69].
  - Proration is configured per charge offer [S70].
- **Aria Systems**: contract types include "9 – Renew existing contract automatically with different duration" (`renewal_duration_type/length`, `renewal_end_date`). Plan changes can be queued (`get_acct_plan_queued_changes_m`) [S71].
- **Unified or separate?** Telecom standards are fully unified. "Renewal" is not a first-class order action; it is a `modify` on term or contract attributes, or a contract renewal policy. **[INFERENCE from S65/S71]**

### 4.3 Manufacturing / hardware CPQ
- **Tacton**: the Installed Base API models account → **systems → assets → meters → usages/forecasts**. Service Sales quotes aftermarket upgrades and services against the configured installed base [S72][S73].
- **Oracle CPQ ABO**: configuration deltas on BOMs and open-order stacking, already covered above [S40].
- **SAP CPQ/BRIM**: Change Configuration and Swap for variant-configured products [S43].
- **[INFERENCE]** Amendment here = reconfigure an installed BOM, computed as a delta against the projected state. Renewal = renew a service contract or meter-based plan attached to the asset.

---

## 5. Cross-vendor patterns

| Pattern | Evidence |
|---|---|
| A change is a quote, then an order, then applied to the installed record; nothing changes until the order is accepted or activated | SF CPQ [S1]; RCA [S17]; DealHub DRAFT → execute [S19]; Oracle fulfilment status [S40] |
| History is append-only: new records, segments or versions, never overwrites | SF CPQ ledger [S1]; AssetStatePeriod [S16]; Zuora segments [S35]; DealHub versions/revert [S19][S20]; Aria versioned contracts (vendor blog) [S74] |
| Delta and absolute quantity semantics both exist | RCA delta `quantityChange` [S12][S17]; Conga absolute [S26]; DealHub replace (−old/+new) [S19]; Oracle delta BOM [S40] |
| Action-code vocabulary | TMF add/modify/delete/noChange [S65]; Oracle Add/Update/Delete/-/Renew/Suspend/Resume/Terminate [S40]; Zuora order actions [S37] |
| Business categorisation of a change | RCA AssetAction categories [S15]; HubSpot separate pipelines [S44] |
| Renewal pricing modes | Same / List / Uplift % [S6]; % or amount uplift [S25]; Renewal Factor [S50]; free re-price [S46] |
| Effective date separate from action date | RCA ActionDate vs state-period start [S15]; Zuora trigger dates [S35]; SAP activation date [S43] |
| Co-termination | SF CPQ co-term flags [S5]; SAP Co-Term/Merge [S43]; DealHub/Maxio co-terming [S21][S52] |
| Term-end policy | Zuora renewalSetting [S36]; Chargebee action_at_term_end [S48]; Revenera auto-renew job [S53] |
| Entitlement/licence regeneration after the commercial change | Revenera fulfilment ops [S56]; Thales Regenerate License [S57] |

---

## 6. Unified vs separate: verdict
- **Unified backbone, typed entry points:** Salesforce RCA, DealHub, Zuora, Conga ABO, Oracle ABO, SAP change processes, TMF, Revenera.
- **Separate capabilities on a shared record:** Salesforce CPQ (managed package), HubSpot, Chargebee, Maxio.
- **No lifecycle capability (document/revision only):** PandaDoc, QuoteWerks.
- **[INFERENCE]** Recommendation: build **one capability** (Lifecycle Change) whose *intent* (Renew, Amend, Cancel, Restructure, …) selects default policies. Renewal is then an amendment that also extends the term and applies a renewal pricing policy. Oracle states this explicitly ("Renew … essentially a Modify") [S40], and Zuora lets one order combine Renew with product changes [S35].

---

## 7. Proposed universal business capability model and meta model **[INFERENCE: design synthesis grounded in sections 2–5]**

### 7.1 Capability map (L1 → L2)
1. **Installed Commitment Management**: maintain the effective-dated record of what the customer owns or subscribes to (subscription, asset, entitlement, maintenance, capacity pool). *Basis: S1, S15, S16, S35, S53, S72.*
2. **Lifecycle Change Initiation**: manual, scheduled (renewal lead time), term-end auto policy, threshold/usage, true-up, end-of-life. *Basis: S2, S25, S48, S53, S59.*
3. **Change Scoping & Projection**: choose commitments, project the state at the effective date (including open orders), co-term. *Basis: S40, S5, S43.*
4. **Change Configuration**: apply action codes (add, update, remove, renew, upgrade, swap, convert metric, suspend, resume, transfer). *Basis: S15, S37, S40, S62.*
5. **Change Pricing**: renewal price policy, amendment price policy, proration, credits and termination fees, ramps. *Basis: S6, S25, S44, S47, S50, S69.*
6. **Approval & Acceptance**: approvals, document generation, e-signature, expiry, revisions. *Basis: S34, S46.*
7. **Order & Apply**: convert to an order and apply it to the ledger (new version or segment); supersede or link contracts. *Basis: S1, S17, S19, S44.*
8. **Fulfilment & Entitlement Sync**: regenerate licence keys and notify provisioning and billing. *Basis: S56, S57, S22.*
9. **Lifecycle Analytics**: classify changes (upsell, downsell, churn, renewal) and compute ARR/MRR deltas, TCV and renewal rate. *Basis: S15, S44.*

### 7.2 Meta model (entities and key attributes)
```
Account 1─* CommercialAgreement (Contract)
  CommercialAgreement: id, type{MasterSubscription|LicenseAndMaintenance|ServiceContract|Hybrid},
     termModel{Termed|Evergreen|Perpetual}, startDate, endDate, renewalPolicy, coTermPolicy,
     priorAgreementId (renewal chain), mergedFromIds[]                     ← S44, S43, S36
  CommercialAgreement 1─* CommitmentLine
  CommitmentLine: id, productRef, licenseModel{Subscription|TermLicense|Perpetual|Maintenance|
     Capacity|Usage|Hardware}, metric{seat|core|PVU|TB|session|unit}, quantity/capacity,
     isPermanent, parentLineId, relatedPerpetualLineId (maintenance→licence),
     status{PendingActive|Active|Suspended|InGrace|PendingTerminate|Terminated|Expired|Superseded}
                                                                            ← S53, S59, S64, S67
  CommitmentLine 1─* CommitmentStatePeriod (version/segment): validFrom, validTo, qty, unitPrice,
     netPrice, MRR/ARR, priceBasis, sourceChangeLineId                      ← S16, S35, S1
  CommitmentLine 1─* EntitlementFulfillment: licenseKey/activationId, hostId, regeneratedFrom   ← S56, S57
  CommitmentLine *─* MeterReading/UsageRecord (capacity & usage)            ← S59, S72

LifecycleChange (the single capability's root entity)
  id, intent{Renew|Amend|Cancel|Restructure|Transfer|Suspend|Resume|TrueUp|Convert},
  initiationTrigger{Manual|LeadTime|TermEndAuto|Threshold|TrueUpCycle|EndOfLife|API},
  actionDate, effectiveDate(s){contractEffective, serviceActivation, customerAcceptance},
  targetAgreementIds[], outputMode{Quote|DirectOrder}, dryRun, status (see 7.3),
  targetVersion, supersedesChangeId (revision)                              ← S12, S19, S35, S34
  LifecycleChange 1─* ChangeLine
  ChangeLine: commitmentLineRef?, actionCode{Add|Update|Remove|NoChange|Renew|Upgrade|Downgrade|
     Swap|ConvertMetric|Suspend|Resume|Terminate}, quantitySemantics{Delta|Absolute|Replace},
     startQty, deltaQty, endQty, termStart, termEnd, businessCategory{InitialSale|Renewal|Upsell|
     Downsell|CrossSell|Upgrade|Downgrade|Swap|Cancellation|T&C|Transfer|TrueUp}
                                                                            ← S15, S17, S19, S26, S40, S65
  ChangeLine *─1 PricingPolicy: renewalMode{Same|List|UpliftPct|UpliftAmt|RenewalPriceOverride|
     Renegotiate}, amendMode{RetainContracted|List|Override}, prorationMethod{Daily|MonthDay|
     None|NextCycle}, creditPolicy{None|Prorated|Full}, terminationFee, ramp/segments
                                                                            ← S6, S25, S50, S44, S47, S13
  LifecycleChange 1─* Quote (revisioned) 1─0..1 Order ─▶ applies to CommitmentStatePeriod
                                                                            ← S1, S17, S66
Policies (configurable, inherited Account→Agreement→Line, most specific wins):
  RenewalPolicy{leadTimeDays, autoRenew, termEndAction{Renew|RenewOnce|Evergreen|Cancel},
     renewalTerm, cutOffDays, gracePeriod}                                  ← S25, S48, S47, S59
  CoTermPolicy{LatestEnd|EarliestEnd|None|AlignToAgreement}                ← S5, S43
```
The "most specific wins" inheritance follows SF CPQ, where subscription-level uplift overrides contract-level uplift [S6].

### 7.3 Lifecycle states
- **LifecycleChange:** `Draft → Projected/Priced → InApproval → Approved → Presented → {Accepted | Rejected | Expired | Superseded(revision)} → Ordered → Applied → (Reverted)`. The terminal Applied writes new state periods and fulfilment. *Basis: draft/execute [S19]; revert [S20]; QuoteValet expiry [S33]; revision superseding [S34]; TMF order states [S65].*
- **CommitmentLine:** `PendingActive → Active ⇄ Suspended → PendingTerminate → Terminated`, plus `InGrace` and `Expired` for time-bound licences, and `Superseded` when replaced by a renewal or new contract. *Basis: [S67][S59][S43].*

### 7.4 Pricing modes (consolidated)
| Mode | Applies to | Source |
|---|---|---|
| Retain contracted price | amendment of existing lines | [S7] (third-party) |
| Same / List / Uplift % | renewal | [S6] |
| Uplift amount | renewal | [S25] |
| Renewal factor (automatic) | auto-renewal | [S50] |
| Free renegotiation | scheduled or quoted renewal | [S46][S50] |
| Proration (daily or hybrid month/day) with credit memo | mid-term change | [S44] |
| Transition proration and waived purchase fee | plan swap | [S69] |
| Termination credit/fee | cancellation | [S47] |
| **[INFERENCE]** Maintenance % of licence net, capacity true-up at contracted rate, metric-conversion credit | on-prem | design; grounded in [S62][S53] |

### 7.5 Triggers
Manual (user or API); renewal lead-time job [S25]; term-end auto policy [S36][S48][S53]; capacity/usage threshold notification [S59]; expiry/grace notifications [S60]; workflow on contract renewal dates [S44]; contract checkbox [S2]. **[INFERENCE]** Add periodic true-up and vendor end-of-life events, which matter for on-prem.

### 7.6 How the hard on-prem case fits **[INFERENCE]**
- **Perpetual licence:** a CommitmentLine with `isPermanent=true`. It is never "renewed". Its linked **Maintenance** line is renewed. Revenera does this, with version-date exceptions [S54].
- **Capacity licence:** `metric=core/PVU/TB` and quantity is capacity. Amendments are Add/Update with Delta semantics, plus a TrueUp intent fed by MeterReading [S59][S64].
- **Upgrade or metric conversion:** a ChangeLine with `actionCode=Upgrade|ConvertMetric`, mapping base qty to upgrade qty [S62]. A Fulfilment regeneration is required [S56].
- **SaaS migration of on-prem customers:** a `Restructure` intent (DealHub RESTRUCTURE [S18]; SAP New Contract/Merge [S43]). It terminates perpetual maintenance lines and adds subscription lines in **one** LifecycleChange with credit policy.

### 7.7 Primary-reference mapping
| Universal element | Salesforce CPQ | Salesforce RCA | DealHub |
|---|---|---|---|
| CommercialAgreement | Contract | Contract | Subscription |
| CommitmentLine | SBQQ__Subscription__c / Asset | Asset | Subscription charge |
| StatePeriod | additional Subscription records (ledger) | AssetStatePeriod | subscription version |
| LifecycleChange | Amendment/Renewal Quote (+Opportunity) | initiate* action → Quote | Order (orderType) |
| ChangeLine.action | implied by delta lines | AssetAction category | lineItem.action |
| Apply | Order "Contracted" | Order Activate | Execute order |

---

## 8. Sources
- S1 https://trailhead.salesforce.com/content/learn/modules/contract-amendments-with-salesforce-cpq/change-an-existing-contract
- S2 http://cpqguide.com/salesforce-cpq-renewal-pricing-method/
- S3 https://levelshift.com/blogs/managing-subscription-products-with-salesforce-cpq
- S4 https://resources.docs.salesforce.com/latest/latest/en-us/sfdc/pdf/salesforce_cpq_contract_configuration_tip_sheet.pdf
- S5 https://trailhead.salesforce.com/content/learn/modules/contract-amendments-with-salesforce-cpq/explore-significant-date-fields-and-multidimensional-quoting
- S6 https://help.salesforce.com/s/articleView?id=000382486&language=en_US&type=1
- S7 https://www.milomassimo.com/Salesforce-CPQ-Amendments-What-You-Need-to-Know.html
- S8 https://thecloudupdate.co/salesforce-cpq-contracts-and-subscriptions/
- S9 https://developer.salesforce.com/docs/revenue/cpq-developer-guide/guide/cpq-api-contract-parent.html
- S10 https://developer.salesforce.com/docs/revenue/cpq-developer-guide/guide/cpq-api-contract-amender.html
- S11 https://audit9.blog/wp-content/uploads/2018/03/salesforce-cpq-object-model.pdf
- S12 https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/actions_obj_amend_assets.htm
- S13 https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/actions_obj_renew_assets.htm
- S14 https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/actions_obj_cancel_assets.htm
- S15 https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/sforce_api_objects_assetaction.htm
- S16 https://developer.salesforce.com/docs/atlas.en-us.revenue_lifecycle_management_dev_guide.meta/revenue_lifecycle_management_dev_guide/sforce_api_objects_assetstateperiod.htm
- S17 https://trailhead.salesforce.com/content/learn/modules/asset-lifecycle-management-with-revenue-cloud/manage-customer-asset-amendments
- S18 https://developers.dealhub.io/reference/updateOrder
- S19 https://developers.dealhub.io/docs/amend-a-subscription
- S20 https://apis.io/apis/dealhub/dealhub-subscriptions-api/
- S21 https://dealhub.io/platform/cpq/
- S22 https://dealhub.io/platform/subscription-billing/
- S23 https://documentation.conga.com/en/cpq-for-advantage-platform/current/cpq-for-users/asset-management/agreement-flow-for-asset-using-api
- S24 https://documentation.conga.com/en/cpq-for-salesforce/current/cpq-for-users/managing-assets/changing-an-asset/impact-of-changing-the-asset-on-order-line-items-and-asset-transaction-history
- S25 https://documentation.conga.com/en/cpq-for-salesforce/current/cpq-for-users/managing-assets/renewing-an-asset/renewing-assets-automatically/types-of-asset-renewal-modes/renewing-assets-in-ondemand-renewal-mode (plus the search summary of Conga "To renew an asset", Renewal Adjustment Type)
- S26 https://documentation.conga.com/en/cpq-for-salesforce/current/cpq-for-users/managing-assets/changing-an-asset
- S27 https://documentation.conga.com/en/cpq-for-salesforce/current/cpq-for-soap-api-developers/api-reference/cpq-web-service-apttus_cpqapi/modifying-assets-deprecated
- S28 https://documentation.conga.com/en/cpq-for-salesforce/current/cpq-for-users/managing-assets/billing-for-assets
- S29 https://www.pandadoc.com/contract-renewal/
- S30 https://developers.pandadoc.com/docs/update-quotes
- S31 https://www.pandadoc.com/docs/cpq/how-to-guides/quote-rules-formulas/use-quote-rules-formulas/
- S32 https://desktop.quotewerks.com/updates/updateagent.asp?FeatureID=2138
- S33 https://info.quotewerks.com/knowledge-base/sale-info-tab
- S34 https://info.quotewerks.com/knowledge-base/how-do-i-create-document-revisions
- S35 https://developer.zuora.com/docs/get-started/api-tutorials/orders-tutorials/modify-subscriptions
- S36 https://developer.zuora.com/v1-api-reference/api/subscriptions/put_subscription
- S37 https://docs.zuora.com/en/zuora-billing/manage-accounts-subscriptions-and-non-subscriptions/manage-subscription-transactions/orders/orders-introduction/order-actions
- S38 https://docs.zuora.com/en/zuora-cpq/additional-and-supplementary-knowledge-of-cpq/zuora-cpq-object-model/zuora-quotes
- S39 https://docs.zuora.com/en-us/zuora-cpq/manage-subscriptions/subscription-management-in-cpq-x
- S40 https://help.bigmachines.com/cpq/Content/ABO/Subscription_Ordering.htm
- S41 https://docs.oracle.com/en/cloud/saas/configure-price-quote/cxcpq/api-assets-actions.html
- S42 https://docs.oracle.com/en/cloud/saas/configure-price-quote/cxcpq/op-rest-v19-assets-id-actions-terminate-post.html
- S43 https://community.sap.com/t5/crm-and-cx-blog-posts-by-sap/change-process-in-cpq-brim-s-4-solution-quotation-integration/ba-p/14107541
- S44 https://knowledge.hubspot.com/contracts/understand-contracts-in-hubspot
- S45 https://knowledge.hubspot.com/quotes/create-change-and-renewal-quote-templates
- S46 https://www.chargebee.com/docs/cpq/generate-quote/quote-for-subscription-renewal
- S47 https://www.chargebee.com/docs/billing/2.0/subscriptions/contract-terms
- S48 https://apidocs.chargebee.com/docs/api/subscriptions/update-subscription-for-items
- S49 https://www.chargebee.com/docs/cpq/generate-quote
- S50 https://docs.maxio.com/billing/subscriptions/configuration/term-renewal/term-subscription-renewals
- S51 https://docs.maxio.com/billing/subscriptions/management/subscription-actions/upgrade-or-downgrade-a-subscription
- S52 https://www.maxio.com/cpq
- S53 https://docs.revenera.com/fno2025r2_onprem/producer/Content/helplibrary/opsConfEnt.htm
- S54 https://docs.revenera.com/fno_CURRENT/producer/Content/helplibrary/opsBulkOps.htm
- S55 https://docs.revenera.com/fno2025r2_onprem/soapapi/CT-EntitlementOrderService-tns_entitlementLineItemPropertiesType.htm
- S56 https://docs.revenera.com/fno2025r1_onprem/soapapi/ME-LicenseService.htm
- S57 https://docs.sentinel.thalesgroup.com/softwareandservices/ems/EMSdocs/UG/entitlementActions.htm
- S58 https://docs.sentinel.thalesgroup.com/softwareandservices/ems/EMSdocs/LDK-UG/creatingEntitlement.htm
- S59 https://manualzz.com/doc/39128175/safenet-ems-user-s-guide (third-party mirror of the SafeNet/Sentinel Cloud EMS User's Guide)
- S60 https://docs.sentinel.thalesgroup.com/softwareandservices/rms/scl/9.4/pdf/Solution%20Guide.pdf
- S61 https://docs.flexera.com/snow-license-manager-ui/licenses/add-license-purchase
- S62 https://docs.flexera.com/snow-license-manager-ui/concepts-terminology-and-features/license/upgrade-and-downgrade
- S63 https://docs.flexera.com/snow-license-manager-ui/agreements/manage-agreements
- S64 https://fr.insight.com/content/dam/insight-web/emea/partner/snow-software/Snow%20License%20Manager_ENG_Snow%20Software.pdf
- S65 https://context7.com/tmforum-apis/tmf622_productorder/llms.txt (and https://github.com/tmforum-apis/TMF622_ProductOrder)
- S66 https://tmf-open-api-table-documents.s3.eu-west-1.amazonaws.com/OpenApiTable/TMF622_Product_Ordering/5.0.0/user_guides/TMF622_Product_Ordering_userguide.pdf
- S67 https://apis.io/schemas/tm-forum/tm-forum-productstatustype/
- S68 https://engage.tmforum.org/discussion/tmf637-productstatustype-aborted
- S69 https://docs.oracle.com/en/industries/communications/billing-revenue/15.2/opcode-guide/subscription-opcode-workflows.html
- S70 https://docs.oracle.com/en/industries/communications/billing-revenue/15.1/billing/proration1.html
- S71 https://knowledge.ariasystems.net/Users_Guide/Release_Notes/Aria_Billing_Release_Notes/Release_60
- S72 https://www.tacton.com/wp-content/uploads/2025/10/API-for-Installed-Base-v1-Tacton-Docs_2.pdf
- S73 https://www.tacton.com/cpq-blog/transform-manufacturing-sales-with-tactons-service-sales-solution/
- S74 https://www.ariasystems.com/revenue-assurance-what-agentic-ai-demands-from-billing/

### Caveats
- Some details come from search-result summaries of the cited pages rather than full-page reads. This applies to S25 (Conga renewal adjustment), S28, S36, S41/S42, S48, S49, S51, S58–S60, S65, S67–S70 and S72. Two Conga pages and one Salesforce Help page would not render for the fetch tool (browser-gated or timed out).
- S2, S3, S7, S8, S11 and S59 are third-party sources, not vendor docs.
- DealHub's developer documentation is branded as the **Subskribe API** [S19]. **[INFERENCE]** This suggests the subscription engine came from Subskribe.
- No vendor publishes a formal "business capability model" for renewals/amendments. The closest published domain models are the Salesforce RCA objects [S15][S16], the Zuora Quotes object model [S38], Oracle ABO concepts [S40] and the TMF622/637 schemas [S65][S67].
