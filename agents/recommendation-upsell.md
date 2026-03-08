# Agent PRD: cpq-agent-017

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-017                                  |
| **Name**          | Recommendation & Upsell Agent                  |
| **Group**         | Intelligent Selling                            |
| **Primary Service** | product-service (8083), crm-service (8081)    |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-001 (Guided Product Selection Agent) |

---

## Purpose

The Recommendation & Upsell Agent generates intelligent cross-sell and upsell suggestions during the quote creation process, leveraging a multi-dimensional analysis of customer profile data, current quote configuration, historical purchase patterns, deal context, and product affinity relationships. It identifies opportunities to increase deal value by recommending complementary products (cross-sell), higher-tier alternatives (upsell), bundle upgrades that provide better value than individual selections, and add-on services that commonly accompany the products already in the configuration. The agent combines rule-based recommendations (using RECOMMENDS rules from the configuration rule engine) with data-driven "customers like you" collaborative filtering analysis, producing a ranked list of suggestions with revenue impact estimates and confidence scores that help the sales representative prioritize which recommendations to present to the customer.

The agent operates at a purely Advisory autonomy level -- it never adds products to a quote, modifies pricing, or sends recommendations directly to the customer. All suggestions are presented exclusively to the sales representative, who retains complete discretion over whether to present any recommendation to the customer, modify it, or discard it entirely. This design reflects the reality that effective selling requires human judgment about customer relationship dynamics, budget sensitivity, competitive positioning, and timing that an automated agent cannot fully assess. The agent's value lies in surfacing opportunities that a sales representative might otherwise miss due to catalog complexity, incomplete knowledge of the customer's purchase history, or unfamiliarity with product affinity patterns across the full customer base. Revenue impact estimates are calculated using current pricing data from the pricing service, giving representatives a clear picture of the potential deal value uplift for each suggestion.

---

## Key Skills / Capabilities

- **Product Affinity Analysis**: Analyzes co-purchase patterns across the customer base by examining historical quote line items retrieved via `GET /api/v1/quotes` and `GET /api/v1/quotes/{quoteId}/line-items`. Builds product affinity scores indicating how frequently products are purchased together -- for example, if 78% of customers who purchased the PLATFORM product also purchased SUPPORT_PREMIUM within the same quarter. Filters affinity data by customer segment (derived from `GET /api/v1/pricing-customers/{id}`) and industry vertical to ensure recommendations are relevant to the specific customer's peer group.

- **Cross-Sell Recommendation**: Identifies products that complement the current quote configuration but are not yet included. Combines three recommendation sources: (1) RECOMMENDS rules from the configuration rule engine retrieved via `GET /api/v1/configuration/rules/product/{productId}` that encode explicit product relationships, (2) product affinity data from collaborative filtering analysis, and (3) bundle component gap analysis via `GET /api/v1/product-bundles` that identifies products the customer is missing to complete a bundle. Ranks cross-sell suggestions by relevance score, expected acceptance probability, and revenue impact.

- **Upsell Path Suggestion**: Identifies opportunities to upgrade products in the current configuration to higher-tier alternatives. Analyzes the product catalog hierarchy via `GET /api/v1/products/search` to find upgrade paths (e.g., SUPPORT_BASIC to SUPPORT_PREMIUM to SUPPORT_ENTERPRISE, or SAAS_SEAT_NAMED to SAAS_FLAT_RATE for customers exceeding a seat count threshold). Retrieves the customer's current contract and usage data from `GET /api/v1/erp/purchase-history/{customerExternalId}` to identify products where the customer's actual usage suggests they would benefit from a higher tier.

- **Bundle Upgrade Recommendation**: Detects when a customer's current selection of individual products would be more cost-effective as a bundle. Retrieves available bundles via `GET /api/v1/product-bundles` and compares the a-la-carte total of the customer's selected products against the bundle price obtained through `POST /api/v1/pricing/simulate`. Presents bundle upgrade recommendations only when genuine savings exist, including a clear savings breakdown showing individual vs. bundle pricing for each component.

- **Revenue Impact Estimation**: Calculates the potential revenue impact of each recommendation by simulating pricing via `POST /api/v1/pricing/simulate` for the current configuration with and without the recommended product. Factors in customer-specific pricing (retrieved via `GET /api/v1/pricing-customers/{id}` for contracted price books and discount tiers), volume-based pricing adjustments, and any applicable promotional pricing. Presents the incremental revenue (annual contract value or one-time) alongside the recommendation to help the sales rep prioritize suggestions.

- **Deal Context Alignment**: Retrieves active deal information via `GET /api/v1/deals/{id}` and pipeline context via `GET /api/v1/deals/pipeline` to align recommendations with deal-level constraints. Filters out recommendations that would push the deal beyond the customer's stated budget ceiling, deprioritizes add-ons for deals in early pipeline stages where the core deal has not yet been confirmed, and elevates recommendations that address specific requirements or competitive gaps noted in the deal record. Considers deal stage to adjust recommendation aggressiveness appropriately.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Current Configuration    | JSON object       | Quote builder UI (list of selected productIds, optionIds, quantities) |
| Customer ID              | String (UUID)     | Quote session context / CRM                      |
| Customer External ID     | String            | For ERP purchase history lookup                  |
| Deal ID                  | String (UUID)     | CRM deal record linked to quote                 |
| Customer Segment         | String (Enum)     | Derived from `GET /api/v1/pricing-customers/{id}` |
| Customer Industry        | String            | Derived from customer profile                    |
| Quote Currency           | String (ISO 4217) | Quote header metadata                            |
| Max Recommendations      | Integer           | Configuration parameter, default 5               |
| Recommendation Types     | Array of String   | Filter: CROSS_SELL, UPSELL, BUNDLE_UPGRADE, ADD_ON (default: all) |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Ranked Recommendation List    | JSON array of objects (recommendationId, type, productId, productName, relevanceScore, confidenceScore, rationale) | Purely advisory; sales rep decides whether to act on any recommendation |
| Revenue Impact Estimates      | JSON array of objects (recommendationId, incrementalACV, incrementalOneTime, percentageDealUplift) | Informational; presented alongside recommendations for prioritization |
| Cross-Sell Suggestions        | JSON array of objects (productId, affinityScore, coOccurrenceRate, sourceRule, peerGroupSize) | Advisory; rep selects which cross-sell products to propose to customer |
| Upsell Path Options           | JSON array of objects (currentProductId, upgradeProductId, featureComparison, priceDelta, usageJustification) | Advisory; rep evaluates upsell appropriateness based on customer relationship |
| Bundle Upgrade Analysis       | JSON object (currentItemsTotal, bundlePrice, savingsAmount, savingsPercentage, bundleId, componentMapping) | Advisory; rep decides whether bundle conversation is appropriate for the deal context |

---

## Typical Workflow

1. **Receive Configuration Context**: The agent is triggered when a sales representative adds products to a quote or explicitly requests recommendations. It receives the current configuration (product selections, options, quantities), customer ID, deal ID, and any filtering preferences for recommendation types.

2. **Load Customer Profile and History**: Agent retrieves the customer profile from `GET /api/v1/pricing-customers/{id}` to obtain segment, industry, region, and contracted pricing context. Queries purchase history via `GET /api/v1/erp/purchase-history/{customerExternalId}` to identify previously purchased products, renewal cycles, and usage patterns. Retrieves past quotes via `GET /api/v1/quotes?customerExternalId={id}` to analyze historical buying behavior.

3. **Load Deal Context**: Agent retrieves the active deal from `GET /api/v1/deals/{id}` to understand deal stage, expected value, budget constraints, stated requirements, and competitive landscape. Uses pipeline data from `GET /api/v1/deals/pipeline` to assess deal priority and timing sensitivity.

4. **Generate Affinity-Based Recommendations**: Agent analyzes co-purchase patterns across the customer base, filtered by the customer's segment and industry. Identifies products that are frequently purchased alongside the products in the current configuration but are not yet selected. Calculates affinity scores and co-occurrence rates for each candidate.

5. **Evaluate RECOMMENDS Rules**: Agent retrieves explicit RECOMMENDS rules from `GET /api/v1/configuration/rules/product/{productId}` for each product in the current configuration. Merges rule-based recommendations with affinity-based recommendations, boosting the relevance score for products that appear in both sources.

6. **Identify Upsell Paths and Bundle Upgrades**: Agent searches for higher-tier product alternatives using `GET /api/v1/products/search` filtered by the same product category but higher-tier product types. Simultaneously retrieves available bundles via `GET /api/v1/product-bundles` and checks if the current configuration partially overlaps with any bundle's component list. Calculates upgrade value propositions.

7. **Estimate Revenue Impact**: For each recommendation candidate, the agent simulates pricing via `POST /api/v1/pricing/simulate` to calculate the incremental revenue impact. Applies customer-specific pricing from the contracted price book. Ranks all recommendations by a composite score combining relevance, confidence, and revenue impact.

8. **Present Ranked Recommendations**: Agent returns the final ranked recommendation list to the sales representative's UI, displaying each suggestion with its type (cross-sell, upsell, bundle upgrade), rationale, confidence score, and estimated revenue impact. The representative reviews the suggestions and decides independently which, if any, to present to the customer or add to the quote.

---

## Edge Cases & Exceptions

- **New Customer with No Purchase History**: When `GET /api/v1/erp/purchase-history/{customerExternalId}` returns empty results and no prior quotes exist, the agent falls back to segment-level and industry-level affinity analysis rather than individual customer history. Clearly labels recommendations as "based on peer group patterns" rather than "based on your history" to set appropriate confidence expectations for the sales rep.

- **Customer with Exhaustive Product Coverage**: When the customer already owns or has quoted nearly every product in the catalog, the agent shifts strategy from new product recommendations to upgrade path suggestions (tier upgrades, capacity increases, premium support) and service/training add-ons. If no meaningful recommendations exist, the agent honestly reports "no additional recommendations at this time" rather than generating low-quality suggestions.

- **Deal in Early Pipeline Stage**: When the deal is in discovery or qualification stage (not yet progressed to proposal or negotiation), the agent deprioritizes premium add-ons and focuses recommendations on core platform products that address the customer's stated needs. Avoids overwhelming early-stage deals with aggressive upsell suggestions that could damage the relationship.

- **Budget-Constrained Deal**: When the deal record includes a budget ceiling and the current configuration is already approaching or exceeding that ceiling, the agent filters out recommendations that would push the total beyond the budget. Instead, surfaces value-optimizing suggestions such as bundle alternatives that deliver similar capabilities at a lower total cost, or recommends deferring specific add-ons to a follow-on deal.

- **Conflicting Recommendations**: When affinity data suggests Product A but a RECOMMENDS rule suggests Product B (and A and B are mutually exclusive via an EXCLUDES rule), the agent presents both options with clear labeling of the conflict. Explains the trade-off between the data-driven recommendation and the rule-based recommendation, letting the sales rep make the final decision.

- **Pricing Simulation Failure**: When `POST /api/v1/pricing/simulate` fails or times out for one or more recommendation candidates, the agent presents the recommendations without revenue impact estimates for the affected products. Labels these as "revenue impact pending" and suggests the rep manually check pricing if interested in the recommendation.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Recommendation acceptance rate (rep adds suggested product to quote) | >= 20% |
| Average deal value uplift from accepted recommendations | >= 12% |
| Recommendation relevance rating (rep feedback, 1-5 scale) | >= 3.8 |
| Recommendation generation latency (p95) | < 3 seconds |
| Cross-sell/upsell attach rate improvement vs. baseline (no agent) | >= 25% |
