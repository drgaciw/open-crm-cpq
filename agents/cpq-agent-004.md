# Agent PRD: cpq-agent-004

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-004                                  |
| **Name**          | List Price Application Agent                   |
| **Group**         | Pricing - Base                                 |
| **Primary Service** | pricing-service (8082)                        |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-001 (Guided Product Selection Agent) |

---

## Purpose

The List Price Application Agent is the entry point of the pricing waterfall and is responsible for resolving, retrieving, and applying the correct base list price for every line item on a quote. In an enterprise CPQ system, list prices are not a single static value per product -- they are determined by a price book resolution process that considers the customer's region, preferred currency, negotiated rate cards, and temporal validity windows. This agent resolves the appropriate price book by matching the customer's region and currency against available PriceBook entities (each defined with region, currency, isDefault, effectiveDate, and expirationDate fields), retrieves the corresponding PriceBookEntry for each product (which carries the listPrice and floorPrice), and writes the initial price to each quote line item. This list price becomes the starting point from which all subsequent pricing agents (cpq-agent-005, cpq-agent-006, and downstream discount/adjustment agents) operate.

The agent operates at an "Automated with constraints" autonomy level, meaning it executes price book resolution and list price application automatically without requiring rep confirmation for standard operations, but enforces hard constraints that cannot be overridden without escalation. Specifically, the agent ensures that no line item price falls below the defined floorPrice from the PriceBookEntry -- any attempt to set a price below floor triggers an escalation to a pricing manager. When no matching PriceBookEntry exists for a product in the resolved price book, the agent falls back to the catalog-level `Product.listPrice` field and flags the line item for pricing review. The agent also handles multi-currency quotes by ensuring all prices are expressed in the quote's target currency, coordinating with the ERP adapter for exchange rate data when cross-currency price book resolution is needed.

---

## Key Skills / Capabilities

- **Price Book Resolution**: Resolves the correct price book for the customer by retrieving the customer profile via `GET /api/v1/pricing-customers/{id}` and matching the customer's region and currency against available price books. Applies a resolution hierarchy: (1) customer-specific contracted price book, (2) region+currency match with valid effectiveDate/expirationDate, (3) region-only match with default currency, (4) global default price book (isDefault = true). Logs the resolution path for audit.

- **List Price Lookup**: For each product on the quote, retrieves the PriceBookEntry from the resolved price book containing the listPrice and floorPrice. Handles products that exist in multiple price books by applying the resolution hierarchy. When no PriceBookEntry exists, falls back to the catalog-level `Product.listPrice` and marks the line item with a `FALLBACK_PRICE` flag for pricing team review.

- **Pricing Execution**: Initiates the pricing waterfall by calling `POST /api/v1/pricing/execute` with the quote context, resolved price book, and line items. This call writes the list prices to the quote's line items and returns the initialized pricing waterfall state. For what-if scenarios, uses `POST /api/v1/pricing/simulate` to calculate prices without persisting them.

- **Floor Price Enforcement**: Enforces that no line item's effective price falls below the floorPrice defined in the PriceBookEntry. This constraint is checked at application time and re-checked whenever downstream agents modify the price. Violations trigger an automatic escalation workflow to the pricing manager with the violation details, requested price, floor price, and margin impact.

- **List Price Override Handling**: Supports manual list price overrides by authorized users (pricing managers, deal desk). When an override is submitted, validates it against business rules (override must still be >= floorPrice unless explicitly exempted), logs the override with the authorizing user and business justification, and recalculates all downstream pricing from the new base.

---

## Inputs

| Input                          | Type              | Source                                          |
|--------------------------------|-------------------|-------------------------------------------------|
| Quote ID                       | String (UUID)     | Active quote session                             |
| Quote Line Items               | JSON array        | From cpq-agent-001/002/003 (product selections with configurations) |
| Customer ID                    | String (UUID)     | Active quote session                             |
| Customer Profile               | JSON object       | Retrieved via `GET /api/v1/pricing-customers/{id}` |
| Target Currency                | String (ISO 4217) | From quote header or customer preference         |
| Price Book Override ID         | String (UUID)     | Optional -- when a specific price book is mandated |
| Effective Date                 | Date (ISO 8601)   | Quote effective date for temporal price book resolution |
| List Price Override Request    | JSON object (lineItemId, overridePrice, justification, authorizingUser) | Optional -- from pricing manager |
| Simulation Mode Flag           | Boolean           | When true, uses `/pricing/simulate` instead of `/pricing/execute` |

---

## Outputs

| Output                            | Format                  | Human-in-the-Loop Checkpoint                     |
|-----------------------------------|-------------------------|--------------------------------------------------|
| Resolved Price Book               | JSON object (priceBookId, region, currency, effectiveDate, resolutionPath) | Informational; displayed in pricing details panel |
| Line Item List Prices             | JSON array of objects (lineItemId, productId, listPrice, floorPrice, currency, source: PRICEBOOK/FALLBACK) | Automated application; no approval needed for standard resolution |
| Floor Price Violations            | JSON array of objects (lineItemId, requestedPrice, floorPrice, violationAmount) | Blocks price application; requires pricing manager escalation and approval |
| Fallback Price Warnings           | JSON array of objects (lineItemId, productId, catalogListPrice, reason) | Flagged for pricing team review; does not block quote progression |
| Pricing Waterfall Initialization  | JSON object (quoteId, waterfallStage: LIST_PRICE, lineItems[], timestamp) | Passed to cpq-agent-005 for pricing method application |
| List Price Override Audit Log     | JSON object (lineItemId, originalPrice, overridePrice, justification, authorizingUser, timestamp) | Override is logged and requires pricing manager approval if not already authorized |

---

## Typical Workflow

1. **Receive Line Items for Pricing**: After product selection (cpq-agent-001), configuration (cpq-agent-002), and optional bundle assembly (cpq-agent-003), this agent receives the set of quote line items requiring base price initialization. Retrieves the quote header to determine the target currency and effective date.

2. **Load Customer Pricing Profile**: Agent calls `GET /api/v1/pricing-customers/{id}` to retrieve the customer's pricing profile including region, preferred currency, segment-based pricing tier, contracted price book references, and any customer-specific pricing agreements.

3. **Resolve Price Book**: Agent applies the price book resolution hierarchy. First checks for a customer-specific contracted price book. If none, searches for a price book matching the customer's region and the quote's target currency with a valid date range (effectiveDate <= quote date <= expirationDate). Falls back through region-only match, then to the global default (isDefault = true). Logs the complete resolution path.

4. **Retrieve List Prices**: For each line item, the agent looks up the PriceBookEntry in the resolved price book. Extracts the listPrice and floorPrice. For products without a PriceBookEntry in the resolved book, falls back to `Product.listPrice` from the catalog and flags the item with `FALLBACK_PRICE`.

5. **Apply Currency Conversion (if needed)**: When the price book's currency differs from the quote's target currency (e.g., price book in USD, quote in EUR), the agent retrieves the current exchange rate via `GET /api/v1/erp/fx-rates/{base}/{target}` and converts the list price. Records the exchange rate and conversion timestamp for audit.

6. **Execute Pricing Initialization**: Agent calls `POST /api/v1/pricing/execute` (or `/pricing/simulate` in simulation mode) with the resolved prices to initialize the pricing waterfall. Each line item receives its list price as the starting point. Floor prices are stored as metadata for downstream constraint enforcement.

7. **Validate Floor Prices**: Agent checks that all applied list prices meet or exceed their corresponding floor prices. Any violations are flagged immediately. Standard violations block the line item from proceeding through the pricing waterfall until resolved by a pricing manager override.

8. **Emit Pricing Waterfall State**: The initialized pricing waterfall, with list prices applied to all line items, is packaged and passed to cpq-agent-005 (Pricing Method Agent) for pricing methodology application. The price book resolution metadata and floor price constraints travel with the payload for downstream reference.

---

## Edge Cases & Exceptions

- **No Price Book Matches Any Resolution Level**: When no price book matches the customer's region, currency, or default flag, the agent falls back to catalog `Product.listPrice` for all items and generates a high-priority alert to the pricing team. The entire quote is flagged as `UNRESOLVED_PRICE_BOOK` and a notification is sent to the deal desk.

- **Expired Price Book (Only Match)**: When the only matching price book has an expirationDate in the past, the agent treats it as a stale price source. Uses the expired prices as provisional values, flags all line items with `EXPIRED_PRICE_BOOK`, and triggers an automatic price book refresh request to the pricing team. The rep is warned that prices may be updated.

- **Multi-Currency Quote with Mixed Price Books**: When a quote contains products from different price books denominated in different currencies, the agent resolves each product's price from its best-matching price book, then converts all prices to the quote's target currency. Exchange rates are locked at the time of pricing execution and stored per line item for reconciliation.

- **Floor Price Exceeds List Price**: In rare data integrity scenarios where a PriceBookEntry's floorPrice is greater than its listPrice, the agent flags this as a data error, applies the listPrice as the line item price, and generates a data quality alert to the pricing administration team. The line item is marked for manual review.

- **Concurrent Price Book Update**: If a price book is updated by the pricing team while a quote is being priced, the agent detects the version mismatch on the next pricing execution call. Offers the rep the choice to reprice with the updated price book or lock to the original prices with an `ORIGINAL_PRICE_LOCKED` flag.

- **Customer-Specific Override Price Below Floor**: When a customer has a contracted price (in their customer-specific price book) that is below the standard floor price, the agent validates that the contract is active and authorized. If the contract is valid, the contracted price is applied and the floor price violation is suppressed with a `CONTRACT_OVERRIDE` flag. If the contract has expired, standard floor enforcement applies.

---

## Success Metrics

| Metric                                        | Target          |
|------------------------------------------------|-----------------|
| Price book resolution accuracy (correct book selected) | >= 99%   |
| List price application latency (p95)           | < 500ms per line item |
| Floor price violation detection rate           | 100%            |
| Fallback price usage rate (indicates price book coverage gaps) | < 5% of line items |
| Currency conversion accuracy (vs. ERP rate)    | 100% match      |
