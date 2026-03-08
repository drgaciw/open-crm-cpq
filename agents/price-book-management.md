# Agent PRD: cpq-agent-035

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-035                                  |
| **Name**          | Price Book Management Agent                    |
| **Group**         | Integration & Operations                       |
| **Primary Service** | pricing-service (8082), product-service (8083) |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent)   |

---

## Purpose

The Price Book Management Agent assists in creating, maintaining, and optimizing price book hierarchies across regions, customer segments, and product lines. Price books are the foundation of the pricing waterfall -- they define the base list prices that cpq-agent-004 (List Price Application Agent) resolves for each product-customer combination. This agent ensures price book completeness (every active product has a price entry in every applicable price book), identifies gaps where products lack regional or segment-specific pricing, and assists with bulk price updates such as annual price increases or currency-driven adjustments.

The agent operates at an Advisory level: it analyzes price book health, identifies issues, and recommends actions, but all price book modifications require explicit confirmation from the pricing administrator. This ensures that pricing changes -- which can have immediate commercial impact across all active quotes -- are never applied without human review and approval.

---

## Key Skills / Capabilities

- **Price Book Health Analysis**: Scans all active price books via `GET /api/v1/pricing/price-books` and cross-references entries against the active product catalog from `GET /api/v1/products?status=ACTIVE`. Identifies products missing from specific price books, entries with stale effective dates, and price books with no active entries.

- **Gap Analysis**: Generates a coverage matrix showing which products have entries in which price books. Highlights gaps where a product is available in a region but has no regional price book entry, forcing fallback to the global default. Recommends specific entries to create based on product-region-segment combinations.

- **Bulk Price Update Assistance**: Assists with planning and executing bulk price updates (annual increases, FX adjustments) by calculating proposed new prices, showing the impact on active quotes, and generating the update batch for review. Does not execute updates autonomously.

- **Price Book Hierarchy Validation**: Validates the price book resolution hierarchy (customer-specific > region+currency > global default) to ensure there are no circular references, missing fallbacks, or ambiguous resolution paths.

- **Product Event Monitoring**: Subscribes to `product:updated` Redis Pub/Sub channel to detect when new products are added or existing products change status. Proactively alerts when a new product lacks price book coverage.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Price Book ID                | String (UUID)     | Price book selection / all active books          |
| Product Catalog              | List of products  | From `GET /api/v1/products?status=ACTIVE`       |
| Region/Segment Filters       | String            | Optional scope restriction                       |
| Bulk Update Parameters       | JSON object       | Percentage increase, effective date, scope       |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Coverage Matrix               | JSON table (product x priceBook) | Informational; pricing admin reviews gaps |
| Gap Report                    | JSON array of missing entries | Advisory; admin decides which gaps to fill |
| Bulk Update Preview           | JSON array (current price, proposed price, delta) | Admin must approve before execution |
| Health Score                  | JSON object (coverage%, staleness%, issues) | Dashboard metric |

---

## Typical Workflow

1. **Scan Price Books**: Retrieves all active price books and their entries.

2. **Cross-Reference Catalog**: Compares price book entries against the active product catalog.

3. **Generate Coverage Matrix**: Builds the product-vs-price-book coverage matrix.

4. **Identify Gaps and Issues**: Highlights missing entries, stale prices, and hierarchy problems.

5. **Generate Recommendations**: Produces actionable recommendations for gap filling and updates.

6. **Present Report**: Returns the complete analysis to the pricing administrator for review.

---

## Edge Cases & Exceptions

- **New Product Without Any Price Entry**: Immediately alerts the pricing team when a new ACTIVE product has zero price book entries.

- **Conflicting Prices Across Books**: When the same product has different prices in overlapping price books (e.g., both a regional and segment book apply), validates that the resolution hierarchy produces the intended price.

- **Bulk Update Affecting Active Quotes**: When a proposed bulk update would change prices on products in active (non-expired) quotes, generates an impact report showing affected quotes.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Price book coverage for active products | >= 95%        |
| Gap detection accuracy               | 100%            |
| Time to detect new product without pricing | < 1 hour   |
| Stale entry detection rate           | 100%            |
