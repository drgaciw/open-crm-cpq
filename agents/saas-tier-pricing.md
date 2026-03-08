# Agent PRD: cpq-agent-019

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-019                                  |
| **Name**          | SaaS Tier Pricing Agent                        |
| **Group**         | Subscription & Licensing                       |
| **Primary Service** | pricing-service (8082), product-service (8083) |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004                                  |

---

## Purpose

The SaaS Tier Pricing Agent is responsible for calculating and applying tier-based subscription pricing across the platform's Standard, Professional, and Enterprise plan hierarchy. It resolves the correct per-unit price for each subscription tier based on the product type (SAAS_SEAT_NAMED, SAAS_SEAT_CONCURRENT, or SAAS_FLAT_RATE), the customer's negotiated price book, seat quantity, and billing frequency. The agent enforces feature gating rules embedded in each tier definition -- ensuring that features restricted to higher tiers cannot be quoted at lower-tier prices -- and applies billing frequency discounts automatically (e.g., 15% discount for annual billing versus monthly, 20% for multi-year commitments). It receives validated product selections from cpq-agent-004 (the core pricing engine) and returns fully resolved tier pricing that downstream agents consume for quote assembly.

This agent operates at an Automated with constraints autonomy level, meaning it can resolve tier pricing and apply billing discounts without human intervention as long as the resulting unit price stays within the guardrails defined in the customer's price book and the product's configured floor/ceiling price boundaries. When a requested tier-quantity combination would breach a price floor (e.g., an Enterprise tier deal with aggressive volume discounts that pushes the effective per-seat price below the configured minimum), the agent halts automatic pricing and flags the line item for manual review by the sales representative. The agent also validates tier eligibility -- verifying that the customer's segment and contract terms qualify for the requested tier -- and blocks downgrades that would violate active feature dependencies or contractual commitments.

---

## Key Skills / Capabilities

- **Tier Price Resolution**: Resolves the base unit price for a given product-tier-quantity combination by querying the pricing engine via `POST /api/v1/pricing/execute` with the product ID, customer ID, tier identifier, and seat count. Applies the customer's negotiated price book retrieved from `GET /api/v1/pricing-customers/{id}` to determine whether standard list pricing or a custom contracted rate applies. Handles all three SaaS product types: SAAS_SEAT_NAMED (per-named-user pricing), SAAS_SEAT_CONCURRENT (concurrent-session pricing with peak utilization factors), and SAAS_FLAT_RATE (flat monthly/annual fee irrespective of user count).

- **Billing Frequency Discount Application**: Calculates and applies billing frequency adjustments based on the selected BillingFrequency enum value. Retrieves applicable discount rules from `GET /api/v1/rules/by-rule-set/{ruleSetId}` where the rule set governs billing frequency incentives. Standard discount tiers: MONTHLY (0% -- baseline), QUARTERLY (5%), ANNUAL (15%), MULTI_YEAR (20%). Validates that the effective discounted price does not breach the product's configured price floor.

- **Feature Gating Enforcement**: Queries the product's option groups via `GET /api/v1/product-options/groups/product/{productId}` to retrieve the feature matrix for each tier. Validates that all features included in the quote line item are permissible at the selected tier level. Blocks attempts to include Enterprise-only features (e.g., SSO, advanced analytics, custom integrations) on Standard or Professional tier quotes and surfaces the minimum required tier to the sales representative.

- **Tier Eligibility Validation**: Cross-references the customer's segment, contract history, and existing subscriptions against tier eligibility rules. Retrieves customer details from `GET /api/v1/pricing-customers/{id}` and existing contracts from `GET /api/v1/erp/contracts/{customerExternalId}`. Enforces minimum seat counts per tier (e.g., Enterprise requires >= 50 seats) and validates that downgrade requests do not orphan features currently in use by the customer.

- **Price Simulation and What-If Analysis**: Supports pre-commit pricing exploration via `POST /api/v1/pricing/simulate` allowing sales reps to compare tier-quantity-frequency combinations without affecting the live quote. Returns side-by-side comparisons of total cost across tiers, highlighting the optimal price point and the savings delta between annual and monthly billing.

- **Bundle-Tier Alignment**: Retrieves product bundle definitions via `GET /api/v1/product-bundles/product/{productId}` and ensures that bundled products are priced at a consistent tier. Prevents mixed-tier bundles (e.g., Standard CRM bundled with Enterprise Analytics) unless explicitly permitted by the bundle configuration, and applies bundle-level discounts on top of tier pricing when applicable.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Product ID               | String (UUID)     | Quote line item / cpq-agent-004 output          |
| Product Type             | Enum (ProductType) | Product catalog (`GET /api/v1/products/{id}`)   |
| Tier Identifier          | String            | Sales rep selection (STANDARD, PROFESSIONAL, ENTERPRISE) |
| Seat Quantity            | Integer           | Sales rep input / quote line item               |
| Billing Frequency        | Enum (BillingFrequency) | Sales rep selection (MONTHLY, QUARTERLY, ANNUAL, MULTI_YEAR) |
| Customer ID              | String (UUID)     | Quote context / CRM record                      |
| Price Book ID            | String (UUID)     | Derived from `GET /api/v1/pricing-customers/{id}` |
| Deal Type                | Enum (DealType)   | Quote metadata (NEW, AMENDMENT, RENEWAL, EXPANSION) |
| Existing Contract ID     | String (UUID)     | Optional -- for renewal/amendment context via `GET /api/v1/erp/contracts/{customerExternalId}` |
| Requested Features       | List of String    | Optional -- specific features selected by rep    |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Resolved Tier Pricing         | JSON object (productId, tier, unitPrice, billingFrequency, frequencyDiscount, effectiveUnitPrice, totalPrice) | Automatic unless effective price breaches floor -- then requires rep confirmation |
| Billing Frequency Comparison  | JSON array of objects (frequency, unitPrice, discount, totalAnnualCost, savings) | Informational; presented to rep for frequency selection |
| Feature Gating Violations     | JSON array of objects (featureId, featureName, requiredTier, currentTier) | Blocks quote progression; rep must upgrade tier or remove feature |
| Tier Eligibility Warnings     | JSON array of objects (rule, violation, recommendation) | Advisory; rep must acknowledge before proceeding with ineligible tier |
| Price Floor Breach Alert      | JSON object (productId, floorPrice, computedPrice, delta, requiredAction) | Mandatory review; quote cannot proceed without rep override or price adjustment |

---

## Typical Workflow

1. **Receive Tier Pricing Request**: The agent receives a pricing request from cpq-agent-004 or directly from the quote builder UI containing the product ID, selected tier, seat quantity, and billing frequency. The customer ID and deal type are extracted from the active quote context.

2. **Load Customer Price Book**: Agent calls `GET /api/v1/pricing-customers/{id}` to retrieve the customer's pricing profile, including their assigned price book, negotiated discount schedules, segment classification, and any contracted rate overrides specific to the product family.

3. **Resolve Base Tier Price**: Agent calls `POST /api/v1/pricing/execute` with the product ID, tier, quantity, and customer price book reference. The pricing engine returns the base unit price for the tier-quantity combination, applying any volume-based tier breaks (e.g., seats 1-25 at $45/seat, 26-100 at $38/seat).

4. **Apply Billing Frequency Discount**: Agent retrieves the billing frequency discount rules from `GET /api/v1/rules/by-rule-set/{ruleSetId}` and applies the appropriate discount based on the selected BillingFrequency. Calculates the effective unit price after discount and validates it against the product's configured price floor.

5. **Validate Feature Gating**: Agent calls `GET /api/v1/product-options/groups/product/{productId}` to retrieve the feature matrix. If the rep has selected specific features, the agent validates each feature against the selected tier's entitlements. Any feature gating violations are returned as blocking errors with the minimum required tier indicated.

6. **Check Tier Eligibility**: Agent evaluates tier eligibility rules against the customer's profile. Verifies minimum seat counts, segment requirements, and contract prerequisites. For downgrades, checks feature dependency graph to ensure no active features would be orphaned.

7. **Validate Price Boundaries**: Agent confirms the final computed price falls within the product's floor and ceiling boundaries. If the effective price breaches the floor, the agent sets the line item status to REQUIRES_REVIEW and generates a Price Floor Breach Alert for the sales representative.

8. **Return Pricing Result**: Agent returns the fully resolved tier pricing including base price, frequency discount, effective unit price, and total line item price. Includes the billing frequency comparison matrix so the rep can see savings across alternative frequencies. Passes the result to downstream agents for quote assembly.

---

## Edge Cases & Exceptions

- **Mixed Product Types in Same Tier**: When a quote contains both SAAS_SEAT_NAMED and SAAS_FLAT_RATE products at the same tier, the agent applies seat-based pricing to the named-user product and flat-rate pricing to the platform fee independently, then validates that the combined effective price is internally consistent with the tier's published pricing structure.

- **Concurrent Seat Peak Calculation**: For SAAS_SEAT_CONCURRENT products, the customer purchases a maximum concurrent session count rather than named seats. The agent retrieves historical peak utilization data from `GET /api/v1/erp/purchase-history/{customerExternalId}` and warns the rep if the requested concurrent seat count is significantly below historical peak usage (risk of overage) or significantly above (over-provisioning).

- **Mid-Tier Upgrade During Active Term**: When a customer upgrades from Standard to Professional mid-term, the agent coordinates with cpq-agent-020 (Term & Co-Terming Agent) to calculate the prorated price differential for the remaining term rather than charging the full new tier price.

- **Price Book Expiration**: If the customer's assigned price book has expired (effective end date in the past), the agent falls back to the standard list price book and generates a warning that the quoted price does not reflect any previously negotiated rates. The rep is prompted to contact deal desk for a price book renewal.

- **Zero-Seat Flat Rate Quotes**: For SAAS_FLAT_RATE products, the seat quantity field is irrelevant. The agent ignores any seat count input, applies the flat monthly or annual rate directly, and suppresses seat-count-related validations and volume discount calculations.

- **Multi-Currency Tier Pricing**: When the customer's currency differs from the price book's base currency, the agent applies the exchange rate stored in the customer's pricing profile. It does not perform live FX lookups. If no exchange rate is configured for the customer's currency, the agent blocks pricing and requires a deal desk intervention to establish the rate.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Tier price resolution accuracy       | >= 99.5%       |
| Billing frequency discount auto-application rate | >= 98% |
| Feature gating violation detection rate | 100%         |
| Price floor breach detection rate    | 100%           |
| Agent response latency (p95)         | < 1.5 seconds  |
