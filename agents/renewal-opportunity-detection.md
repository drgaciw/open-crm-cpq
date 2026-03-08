# Agent PRD: cpq-agent-028

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-028                                  |
| **Name**          | Renewal Opportunity Detection Agent            |
| **Group**         | Analytics & Intelligence                       |
| **Primary Service** | quote-service (8080), pricing-service (8082), crm-service (8081) |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | None (runs on schedule)                        |

---

## Purpose

The Renewal Opportunity Detection Agent proactively identifies upcoming contract renewals by scanning accepted quotes that have defined end dates approaching within a configurable renewal window (default: 90 days). It extends the Renewal Intelligence Agent concept from cpq-pricing-prd.md Section 8 by implementing the full detection-to-recommendation pipeline. The agent retrieves all quotes with status ACCEPTED, computes time-to-expiration, and prioritizes renewals by a composite score that factors in contract value (ARR), customer segment, and renewal risk. Risk assessment is driven by the `churnScore` field on the Customer entity retrieved via `GET /api/v1/pricing-customers/{id}`: customers with churnScore > 70 are classified as high-risk, those between 40--70 as medium-risk, and below 40 as low-risk.

The agent implements the pricing logic codified in `renewal-uplift.drl` (salience 530): standard renewals receive an 8% uplift on the prior contract's pricing, while high-risk customers (churnScore > 70) receive a reduced 3% uplift along with the `ENABLE_12M_PRICE_PROTECTION` retention offer that locks pricing for 12 months as an incentive to renew. For each detected renewal, the agent generates a renewal strategy recommendation that includes the recommended pricing approach, suggested retention offers for at-risk accounts, optimal timing for outreach (based on historical renewal cycle analysis), and an estimated renewal probability. The agent operates on a scheduled basis -- typically daily -- and publishes its findings to a renewal dashboard consumed by customer success managers and renewal specialists. It feeds directly into cpq-agent-029 (Upgrade/Downgrade Path Agent) when subscription changes are warranted at renewal time.

---

## Key Skills / Capabilities

- **Renewal Window Scanning**: Executes a scheduled scan of all accepted quotes via `GET /api/v1/quotes` filtered by status ACCEPTED and end date within the configured renewal window. Computes days-to-expiration for each qualifying contract and sorts the renewal queue by urgency (nearest expiration first). Cross-references with `GET /api/v1/deals/search` to identify renewals that already have active deal records versus those that need new renewal deals created.

- **Risk Assessment**: Retrieves the customer profile for each renewal candidate via `GET /api/v1/pricing-customers/{id}` to obtain the `churnScore`, customer segment, annual revenue, and engagement indicators. Classifies renewal risk into three tiers: HIGH (churnScore > 70), MEDIUM (churnScore 40--70), and LOW (churnScore < 40). Enriches risk assessment with ERP data from `GET /api/v1/erp/purchase-history/{customerExternalId}` to detect declining usage patterns or reduced purchase frequency that may signal disengagement.

- **Renewal Strategy Recommendation**: Generates a tailored renewal approach based on risk tier and customer segment. For LOW-risk renewals: standard 8% uplift per renewal-uplift.drl, streamlined renewal process, and optional multi-year incentive. For MEDIUM-risk renewals: 8% uplift with proactive customer success engagement and value-realization review. For HIGH-risk renewals: 3% uplift per renewal-uplift.drl, ENABLE_12M_PRICE_PROTECTION retention offer, executive sponsor engagement, and escalation to customer success leadership. Each strategy includes specific talking points and recommended concessions.

- **Retention Offer Selection**: For high-risk and medium-risk renewals, selects from a library of retention offers calibrated to the customer's profile. The primary retention mechanism is the 12-month price protection offer enabled by `ENABLE_12M_PRICE_PROTECTION` in renewal-uplift.drl. Additional retention levers include: extended payment terms, free training or onboarding credits, temporary feature upgrades, and dedicated support tier elevation. Simulates the financial impact of each retention offer using `POST /api/v1/pricing/simulate`.

- **Renewal Forecast Generation**: Aggregates all detected renewals into a forward-looking renewal forecast organized by month, customer segment, risk tier, and expected ARR. Applies renewal probability estimates (derived from historical renewal rates by segment and risk tier) to produce a probability-weighted renewal revenue forecast. Outputs are consumed by finance and sales leadership for revenue planning. Uses `GET /api/v1/deals/pipeline` to reconcile the renewal forecast against actively managed renewal deals.

- **Renewal Timeline Optimization**: Analyzes historical renewal patterns to determine the optimal outreach timing for each customer segment. Identifies the "golden window" -- the period before contract expiration during which renewal engagement historically yields the highest success rate. For ENTERPRISE and STRATEGIC segments, this is typically 60--90 days before expiration; for SMB and STARTUP segments, 30--45 days. Generates calendar-based outreach recommendations with escalation milestones.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Renewal Window (days)    | Integer           | Configuration parameter, default 90 days        |
| Quote Status Filter      | String (Enum)     | Hardcoded to ACCEPTED for renewal scanning      |
| Customer External ID     | String            | Derived from each qualifying quote record       |
| Customer Churn Score     | Integer (0-100)   | From `GET /api/v1/pricing-customers/{id}`        |
| Customer Segment         | String (Enum)     | From customer profile -- STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC |
| Historical Renewal Rate  | Decimal (%)       | Computed from historical data by segment and risk tier |
| ERP Purchase History     | JSON array        | From `GET /api/v1/erp/purchase-history/{customerExternalId}` |
| ERP Active Contracts     | JSON array        | From `GET /api/v1/erp/contracts/{customerExternalId}` |
| Scan Schedule            | Cron expression   | Configuration parameter, default `0 6 * * *` (daily at 6 AM) |
| Retention Offer Library  | JSON array        | Configuration -- available retention offers and eligibility criteria |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Renewal Queue                 | JSON array of objects (quoteNumber, customerName, customerExternalId, endDate, daysToExpiration, currentARR, riskTier, renewalProbability, priorityScore) | Informational dashboard; no blocking checkpoint. Renewal specialists review and prioritize |
| Renewal Strategy Recommendations | JSON array of objects (quoteNumber, riskTier, recommendedUplift, retentionOffers, outreachTiming, talkingPoints, escalationPath) | Advisory; renewal specialist reviews strategy before initiating customer contact |
| Risk-Flagged Accounts         | JSON array of objects (customerExternalId, churnScore, riskFactors, recommendedActions, escalationLevel) | High-risk accounts (churnScore > 70) generate alerts to customer success leadership for review |
| Renewal Revenue Forecast      | JSON object (byMonth, bySegment, byRiskTier, weightedARR, unweightedARR, atRiskARR) | Consumed by finance dashboards; no action checkpoint |
| Renewal Deal Suggestions      | JSON array of objects (customerExternalId, suggestedDealType: RENEWAL, suggestedARR, suggestedStartDate) | Advisory; renewal specialist must manually create the renewal deal in CRM |

---

## Typical Workflow

1. **Execute Scheduled Scan**: On the configured schedule (default: daily at 6 AM), the agent initiates a renewal window scan. Calls `GET /api/v1/quotes` with filters for status ACCEPTED and end date within the next N days (configurable, default 90). Retrieves all qualifying quotes as renewal candidates.

2. **Enrich with Customer Context**: For each renewal candidate, the agent calls `GET /api/v1/pricing-customers/{id}` to retrieve the customer profile including churn score, segment, industry, region, and annual revenue. Calls `GET /api/v1/erp/contracts/{customerExternalId}` to obtain the full contract portfolio and `GET /api/v1/erp/purchase-history/{customerExternalId}` to assess recent engagement trends.

3. **Classify Risk Tier**: Agent applies the risk classification logic: HIGH (churnScore > 70), MEDIUM (churnScore 40--70), LOW (churnScore < 40). Augments the churn score classification with behavioral signals from ERP data -- declining purchase frequency, reduced support ticket volume, or no product usage expansion in the last 6 months elevate the risk tier by one level.

4. **Compute Renewal Pricing**: For each renewal, the agent applies the renewal-uplift.drl pricing logic. Standard renewals receive 8% uplift on the prior contract's effective pricing. High-risk renewals (churnScore > 70) receive 3% uplift and are flagged for the ENABLE_12M_PRICE_PROTECTION retention offer. Calls `POST /api/v1/pricing/simulate` with the renewal parameters to generate the fully calculated renewal pricing.

5. **Generate Strategy Recommendations**: Based on risk tier, customer segment, and contract value, the agent produces a tailored renewal strategy for each account. Includes specific recommendations for: pricing approach, retention offers (if applicable), outreach timing, stakeholder engagement plan, and escalation triggers if the renewal is not progressing.

6. **Check for Existing Renewal Deals**: Agent calls `GET /api/v1/deals/search` with the customer external ID and deal type RENEWAL to determine whether an active renewal deal already exists in the pipeline. If yes, links the renewal recommendation to the existing deal. If no, suggests creation of a new renewal deal with pre-populated attributes.

7. **Publish Renewal Queue and Forecast**: Agent assembles the prioritized renewal queue and publishes it to the renewal dashboard. Simultaneously computes the aggregate renewal forecast by applying segment-specific historical renewal rates to produce probability-weighted revenue projections. High-risk accounts generate proactive alerts to customer success leadership.

8. **Feed Downstream Agents**: For renewals where subscription changes may be appropriate (upgrade, downgrade, or migration), the agent passes the renewal context to cpq-agent-029 (Upgrade/Downgrade Path Agent) for further analysis. Logs all scan results, risk classifications, and recommendations for audit and model improvement.

---

## Edge Cases & Exceptions

- **Quote with No End Date**: Some accepted quotes (particularly one-time purchases or perpetual licenses) may lack an `endDate` field. The agent excludes these from the renewal scan but flags perpetual license customers separately as potential migration-to-SaaS candidates for cpq-agent-029, which implements the migration-credit.drl logic.

- **Customer with Multiple Expiring Contracts**: When a customer has multiple quotes expiring within the renewal window, the agent consolidates them into a single renewal opportunity rather than generating separate recommendations for each. Computes aggregate ARR at risk and recommends a unified renewal negotiation strategy that addresses all expiring contracts together for maximum retention leverage.

- **Churn Score Not Available**: If the customer profile lacks a churn score (field is null), the agent defaults to MEDIUM risk classification and flags the account for customer success review to establish a proper churn assessment. Recommends proactive outreach regardless of default classification to compensate for the data gap.

- **Recently Won-Back Customer**: For customers with deal type CHURN_WIN in their recent history (within 12 months), the agent applies heightened sensitivity to renewal pricing. Even if the current churn score is low, the agent recommends conservative uplift (3% instead of 8%) and ENABLE_12M_PRICE_PROTECTION to solidify the relationship. Flags these renewals with a "recently recovered" tag.

- **Contract Already Renewed Early**: When a renewal deal for the same contract already exists with status WON in the CRM, the agent detects the overlap via `GET /api/v1/deals/search` and excludes the contract from the renewal queue. Logs the early renewal for analytics and adjusts the customer's renewal propensity score upward.

- **High-Value Strategic Account Exception**: Renewals for STRATEGIC segment customers with ARR exceeding a configurable threshold (e.g., $500K) bypass the standard recommendation engine and are flagged for dedicated account team review. The agent provides data and analysis but defers strategy formulation to the strategic account team, recognizing that these relationships require bespoke handling.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Renewal detection rate (renewals identified vs. total expirations) | >= 98% |
| Average lead time (days before expiration when renewal is flagged) | >= 75 days |
| Renewal close rate for agent-identified opportunities | >= 85% |
| High-risk account intervention rate (flagged and actioned before expiration) | >= 95% |
| Renewal forecast accuracy (predicted vs. actual renewal revenue) | >= 90% |
