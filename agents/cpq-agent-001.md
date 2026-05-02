# Agent PRD: cpq-agent-001

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-001                                  |
| **Name**          | Guided Product Selection Agent                 |
| **Group**         | Product Configuration                          |
| **Primary Service** | product-service (8083), pricing-service (8082), crm-service (8081) |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | None                                           |

---

## Purpose

The Guided Product Selection Agent assists sales representatives in finding and selecting the right products during the quote creation process. It analyzes the full customer context -- including account segment, industry vertical, geographic region, and contract history -- alongside deal-specific requirements such as deal stage, expected close date, and stated needs. By correlating this context against the product catalog, the agent produces ranked product recommendations that accelerate the quoting workflow and improve quote accuracy. This is the entry point of the CPQ agent chain and feeds downstream agents (cpq-agent-002 through cpq-agent-006) with validated product selections.

The agent operates at an Advisory autonomy level, meaning it surfaces recommendations and contextual insights but never autonomously adds products to a quote. All product selections require explicit confirmation from the sales representative. This design ensures the agent reduces catalog search time (target: 60%+ reduction), prevents accidental selection of deprecated, end-of-life, or region-restricted products, and increases cross-sell/up-sell attach rates by surfacing relevant bundles, complementary products, and historically co-purchased items. The agent also maintains awareness of active promotions and seasonal offerings, ensuring reps never miss time-sensitive opportunities.

---

## Key Skills / Capabilities

- **Product Catalog Search**: Executes parameterized searches against the product catalog using keyword, category, product type, and status filters. Leverages `GET /api/v1/products/search` with query parameters and `GET /api/v1/products` for full catalog traversal. Filters out products with `status = DEPRECATED` or `status = END_OF_LIFE` before presenting results.

- **Customer Context Analysis**: Retrieves the full customer profile from the pricing service via `GET /api/v1/pricing-customers/{id}` to extract segment (Enterprise, Mid-Market, SMB), industry, region, currency preference, and contracted price book. Uses this context to weight and filter product recommendations appropriately.

- **Deal Context Awareness**: Pulls active deal information from the CRM service via `GET /api/v1/deals/{id}` and `GET /api/v1/deals/pipeline` to understand deal stage, expected value, competitive situation, and stated requirements. Aligns product recommendations with deal-level constraints such as budget ceilings and required delivery timelines.

- **Historical Purchase Pattern Analysis**: Queries existing quotes associated with the customer via `GET /api/v1/quotes?customerExternalId={id}` and analyzes past line items to identify repeat purchase patterns, typical order composition, renewal cycles, and products approaching end-of-term. Surfaces re-order suggestions and upgrade paths based on historical data.

- **AI-Powered Recommendation Engine**: Combines customer context, deal context, purchase history, and catalog metadata to produce a ranked list of product recommendations with relevance scores and explanatory rationale. Applies collaborative filtering (customers in similar segments also purchased) and content-based filtering (products matching stated requirements).

- **Bundle Discovery**: Identifies available product bundles via `GET /api/v1/product-bundles` that match the customer's needs and compares bundle pricing against individual item pricing. Highlights potential savings and presents bundle options alongside individual product recommendations to maximize deal value.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Customer ID              | String (UUID)     | Quote creation form / CRM context               |
| Deal ID                  | String (UUID)     | CRM deal record linked to quote                 |
| Search Keywords          | String            | Sales rep free-text input                        |
| Product Category Filter  | String (Enum)     | UI filter selection                              |
| Product Type Filter      | String (Enum)     | UI filter selection (SOFTWARE, HARDWARE, SERVICE, SUBSCRIPTION) |
| Customer Segment         | String (Enum)     | Derived from `GET /api/v1/pricing-customers/{id}` |
| Customer Region          | String            | Derived from customer profile                    |
| Customer Industry        | String            | Derived from customer profile                    |
| Existing Quote ID        | String (UUID)     | Optional -- when adding products to existing quote |
| Max Results              | Integer           | Pagination parameter, default 20                 |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Ranked Product Recommendations | JSON array of objects (productId, name, sku, relevanceScore, rationale) | Sales rep must explicitly select/confirm each product before it is added to quote |
| Bundle Suggestions            | JSON array of objects (bundleId, name, includedProducts, estimatedSavings) | Sales rep must confirm bundle selection; agent does not auto-add bundles |
| Deprecated Product Warnings   | JSON array of objects (productId, status, replacementProductId) | Informational alert; blocks selection of deprecated items |
| Cross-Sell / Up-Sell Suggestions | JSON array of objects (productId, type, rationale) | Advisory only; rep decides whether to act on suggestions |
| Customer Context Summary      | JSON object (segment, industry, region, recentPurchases) | Displayed as contextual sidebar; no action required |

---

## Typical Workflow

1. **Receive Selection Request**: Sales rep initiates product search from the quote builder UI, providing optional search keywords, category filters, and/or product type filters. The agent receives the customer ID and deal ID from the active session context.

2. **Load Customer Context**: Agent calls `GET /api/v1/pricing-customers/{id}` to retrieve the customer profile including segment, industry, region, currency, and associated price book. Caches this context for the duration of the session.

3. **Load Deal Context**: Agent calls `GET /api/v1/deals/{id}` to retrieve deal metadata including stage, expected close date, deal value, and any recorded requirements or competitor information.

4. **Analyze Purchase History**: Agent calls `GET /api/v1/quotes?customerExternalId={id}` to retrieve prior quotes and their line items. Extracts purchase patterns, renewal dates, and previously purchased product families to inform recommendations.

5. **Execute Catalog Search**: Agent calls `GET /api/v1/products/search` with the rep's search parameters, augmented with context-derived filters (e.g., region-available products, segment-appropriate tiers). Simultaneously calls `GET /api/v1/product-bundles` to retrieve relevant bundles.

6. **Rank and Filter Results**: Agent applies the recommendation engine to score and rank results. Filters out deprecated/EOL products, flags region-restricted items, and annotates each result with a relevance score and human-readable rationale.

7. **Present Recommendations**: Agent returns the ranked product list, bundle suggestions, and contextual insights to the UI. Includes cross-sell/up-sell suggestions based on purchase history and collaborative filtering.

8. **Await Selection Confirmation**: Sales rep reviews recommendations, selects desired products, and confirms selections. Confirmed products are passed to downstream agents (cpq-agent-002 for configuration, cpq-agent-004 for pricing). Agent logs the selection event for feedback loop improvement.

---

## Edge Cases & Exceptions

- **No Matching Products Found**: When catalog search returns zero results, the agent broadens search parameters incrementally (remove category filter, then type filter, then use fuzzy keyword matching) and notifies the rep that original criteria yielded no results. Suggests alternative search terms based on product taxonomy.

- **Customer Profile Not Found**: If `GET /api/v1/pricing-customers/{id}` returns 404, the agent proceeds with generic recommendations (no segment/region filtering) and displays a warning that customer-specific recommendations are unavailable. Suggests the rep verify the customer record in CRM.

- **Deprecated Product in Search Results**: Products with `status = DEPRECATED` or `status = END_OF_LIFE` are excluded from the primary results list but presented in a separate "Unavailable Products" section with replacement product suggestions if a `replacementProductId` exists.

- **Deal ID Not Provided**: When no deal is linked to the quote session, the agent skips deal context loading and relies solely on customer context and purchase history. Displays an informational note that deal-aware recommendations require a linked deal.

- **High-Volume Catalog (>10,000 SKUs)**: When the catalog exceeds performance thresholds, the agent enforces mandatory filters (at least one of category, type, or keyword must be provided) and uses paginated retrieval with `GET /api/v1/products/search` to prevent timeout. Results are streamed progressively to the UI.

- **Concurrent Quote Sessions for Same Customer**: When multiple reps are building quotes for the same customer simultaneously, the agent includes a notification showing other active quotes to prevent duplicate product selections and conflicting pricing commitments.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Average product search time reduction | >= 60%         |
| Product recommendation acceptance rate | >= 40%        |
| Deprecated product selection prevention rate | 100%    |
| Cross-sell/up-sell suggestion conversion rate | >= 15% |
| Agent response latency (p95)         | < 2 seconds    |
