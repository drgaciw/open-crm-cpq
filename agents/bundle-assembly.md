# Agent PRD: cpq-agent-003

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-003                                  |
| **Name**          | Bundle Assembly Agent                          |
| **Group**         | Product Configuration                          |
| **Primary Service** | product-service (8083), quote-service (8084)  |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-001 (Guided Product Selection Agent), cpq-agent-002 (Option & Feature Configuration Agent) |

---

## Purpose

The Bundle Assembly Agent is responsible for creating, modifying, and validating product bundles within the context of a quote. In enterprise CPQ scenarios, bundles are a primary packaging mechanism that combines multiple products into a single commercial offering with unified pricing, simplified line item presentation, and enforced composition rules. This agent manages the complete bundle lifecycle -- from initial bundle discovery through composition management to quote line item mapping. It understands bundle structures including minimum and maximum item counts, required versus optional items, and bundle-level discount mechanics (PERCENTAGE, FIXED_AMOUNT, OVERRIDE). When a sales rep is building a quote with individually selected products, the agent continuously monitors the selection set and detects when the chosen products form a partial or complete match against a known bundle definition, proactively suggesting bundle conversion when doing so would benefit the customer or simplify the quote.

The agent operates at a Semi-autonomous level. It can automatically enforce hard bundle constraints (minimum item requirements, required item inclusion) and calculate bundle pricing adjustments without human approval, but bundle creation, bundle conversion suggestions, and any modifications that alter the commercial terms of the quote require explicit rep confirmation. The agent coordinates closely with cpq-agent-002 for individual product configurations within a bundle (each bundle item may itself be configurable) and passes completed bundle structures to cpq-agent-004 for pricing waterfall initiation. It also validates that bundle-level configuration rules do not conflict with item-level configuration rules, resolving or escalating conflicts as appropriate.

---

## Key Skills / Capabilities

- **Bundle Discovery & Matching**: Retrieves all available bundle definitions via `GET /api/v1/product-bundles` and maintains an in-session index of bundle compositions. Continuously compares the current set of products selected on the quote against known bundle definitions using set-intersection scoring. When a subset of selected products matches >= 70% of a bundle's composition, triggers a proactive bundle suggestion with estimated savings.

- **Bundle Composition Management**: Manages the item roster within a bundle using `POST /api/v1/product-bundles/{id}/items` to add items and `DELETE /api/v1/product-bundles/{bundleId}/items/{itemId}` to remove them. Enforces structural constraints: minimum item count (bundle must contain at least N items), maximum item count (bundle cannot exceed M items), required items (must be included, cannot be removed), and optional items (can be added or removed freely).

- **Bundle Constraint Validation**: Before any bundle modification is committed, validates the resulting bundle state against all defined constraints via `POST /api/v1/configuration/validate`. Checks include: required items present, min/max item counts satisfied, no duplicate items, all items are active (non-deprecated) products, and bundle-level rules do not conflict with item-level configuration rules from cpq-agent-002.

- **Bundle-to-Quote Line Item Mapping**: Translates a validated bundle structure into quote line items via `POST /api/v1/quotes/{quoteId}/line-items`. Maps the bundle as a parent line item with individual bundle items as child line items. Applies bundle-level discount types (PERCENTAGE: percentage off combined item prices; FIXED_AMOUNT: fixed currency reduction; OVERRIDE: replace combined price with a flat bundle price). Maintains parent-child referential integrity.

- **Dynamic Bundle Suggestion**: Monitors the evolving product selection during quote building and applies pattern matching algorithms to detect emerging bundle opportunities. Calculates the price differential between the individual item total and the bundle price, presenting the savings amount and percentage to the rep. Tracks suggestion acceptance rates and adjusts suggestion thresholds to minimize noise.

---

## Inputs

| Input                          | Type              | Source                                          |
|--------------------------------|-------------------|-------------------------------------------------|
| Quote ID                       | String (UUID)     | Active quote session                             |
| Selected Product IDs           | Array of String (UUID) | Outputs from cpq-agent-001                 |
| Product Configurations         | Array of JSON objects | Outputs from cpq-agent-002 (validated configurations) |
| Bundle ID                      | String (UUID)     | Rep selection from bundle catalog or suggestion  |
| Bundle Item Action             | JSON object (bundleId, productId, action: ADD/REMOVE) | Rep UI interaction |
| Customer Segment               | String (Enum)     | Passed from cpq-agent-001 customer context       |
| Customer Region                | String            | Passed from cpq-agent-001 customer context       |
| Existing Quote Line Items      | JSON array        | Retrieved via `GET /api/v1/quotes/{quoteId}/line-items` |
| Bundle Discount Type Preference| String (Enum)     | Optional -- PERCENTAGE, FIXED_AMOUNT, OVERRIDE   |

---

## Outputs

| Output                            | Format                  | Human-in-the-Loop Checkpoint                     |
|-----------------------------------|-------------------------|--------------------------------------------------|
| Bundle Composition State          | JSON object (bundleId, items[], requiredItems[], optionalItems[], constraints) | Continuously updated; rep sees real-time composition |
| Bundle Suggestion Alerts          | JSON array of objects (bundleId, matchScore, matchedProducts, missingProducts, estimatedSavings) | Rep must confirm to convert individual items to bundle |
| Constraint Violation Errors       | JSON array of objects (constraintType, message, resolution) | Blocks bundle modification until resolved |
| Quote Line Item Payload           | JSON array of objects (lineItemType: BUNDLE_PARENT/BUNDLE_CHILD, productId, quantity, unitPrice, discount) | Rep must confirm before line items are written to quote |
| Bundle Savings Summary            | JSON object (individualTotal, bundlePrice, savingsAmount, savingsPercent) | Informational; displayed alongside bundle suggestion |
| Configuration Conflict Report     | JSON array of objects (bundleRuleId, itemRuleId, conflictType, resolution) | Escalated to rep if auto-resolution is not possible |

---

## Typical Workflow

1. **Monitor Product Selections**: As cpq-agent-001 confirms product selections and cpq-agent-002 completes configurations, this agent receives the growing list of selected and configured products. Maintains a running set of product IDs on the active quote.

2. **Scan for Bundle Matches**: Agent compares the current product set against all active bundle definitions retrieved via `GET /api/v1/product-bundles`. Computes a match score for each bundle (number of selected products that appear in the bundle's item list divided by the bundle's total required and optional item count).

3. **Surface Bundle Suggestions**: When a match score exceeds the configured threshold (default: 70%), the agent retrieves the full bundle details via `GET /api/v1/product-bundles/{id}` and calculates the price differential. Presents the suggestion to the rep with matched products, missing products needed to complete the bundle, and estimated savings.

4. **Assemble Bundle on Confirmation**: If the rep confirms a bundle suggestion or manually selects a bundle from the catalog, the agent initializes the bundle composition. Auto-includes all required items and marks optional items for rep selection. For each item, references the existing product configuration from cpq-agent-002 or triggers new configuration if needed.

5. **Validate Bundle Constraints**: Agent calls `POST /api/v1/configuration/validate` to verify the bundle meets all structural and rule constraints. Checks min/max item counts, required item presence, item compatibility rules, and cross-validates against item-level configuration rules. Surfaces any violations with specific resolution guidance.

6. **Resolve Configuration Conflicts**: If bundle-level rules conflict with item-level configuration rules (e.g., bundle requires Standard edition but item was configured as Enterprise), the agent attempts auto-resolution by adjusting the item configuration to match bundle requirements. If auto-resolution changes commercial terms, escalates to the rep for approval.

7. **Map Bundle to Quote Line Items**: Once the bundle is validated, the agent constructs the quote line item payload. Creates a parent line item for the bundle and child line items for each bundle item. Applies the appropriate discount type (PERCENTAGE, FIXED_AMOUNT, or OVERRIDE) to calculate the bundle price. Calls `POST /api/v1/quotes/{quoteId}/line-items` to write the line items after rep confirmation.

8. **Emit Bundle for Pricing**: The completed bundle structure with its line item mapping is passed to cpq-agent-004 (List Price Application) to initiate the pricing waterfall. The bundle's discount type and amount are preserved as pricing inputs alongside the individual item prices.

---

## Edge Cases & Exceptions

- **Overlapping Bundle Eligibility**: When the selected products qualify for multiple bundles simultaneously, the agent ranks all eligible bundles by savings amount and presents the top three options with a comparison matrix showing the composition differences, price impacts, and constraint requirements for each. The rep selects the preferred bundle.

- **Partial Bundle with Missing Deprecated Products**: When a bundle match is detected but one or more required items in the bundle definition have been deprecated, the agent flags the bundle as partially available. If replacement products exist, suggests substitution and recalculates the bundle price. If no replacement exists, marks the bundle as unavailable with an explanation.

- **Bundle Item Quantity Mismatch**: When the rep needs multiple quantities of a bundle item (e.g., 5 licenses of a software product within a bundle that defines a single instance), the agent validates whether the bundle definition supports quantity overrides per item. If not, suggests creating multiple bundle instances or converting to individual line items.

- **Mid-Quote Bundle Definition Change**: If a bundle definition is updated by product management while a rep is actively composing it on a quote, the agent detects the version mismatch on the next validation call. Presents a diff of changes to the rep and offers to revalidate the current composition against the updated definition or lock to the original version.

- **Nested Bundle Structures**: When a bundle contains another bundle as an item (bundle-within-a-bundle), the agent enforces a maximum nesting depth of 2 levels. Validates inner bundles independently before validating the outer bundle. Prevents circular bundle references during composition.

- **Bundle Removal with Dependent Pricing**: When a rep removes a bundle from the quote and the bundle's discount was applied as a parent-level OVERRIDE that affected downstream pricing calculations, the agent recalculates all affected line items to their individual prices and notifies the rep of the total price impact before confirming the removal.

---

## Success Metrics

| Metric                                        | Target          |
|------------------------------------------------|-----------------|
| Bundle suggestion relevance rate (accepted or saved for later) | >= 35% |
| Average bundle assembly time                   | < 2 minutes     |
| Bundle constraint validation pass rate (first attempt) | >= 90% |
| Quote line item mapping accuracy               | 100%            |
| Bundle savings per quote (avg, when bundle applied) | >= 8% of line item total |
