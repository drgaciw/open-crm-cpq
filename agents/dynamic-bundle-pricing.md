# Agent PRD: cpq-agent-018

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-018                                  |
| **Name**          | Dynamic Bundle Pricing Agent                   |
| **Group**         | Intelligent Selling                            |
| **Primary Service** | pricing-service (8082), product-service (8083) |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-003 (Bundle Assembly Agent), cpq-agent-004 (List Price Application Agent) |

---

## Purpose

The Dynamic Bundle Pricing Agent calculates and applies pricing adjustments when products are assembled into bundles, ensuring that bundle pricing reflects the intended commercial strategy -- whether that means a flat bundle discount, component-level price adjustments, tiered bundle pricing based on the number of components, or a fixed bundle price that replaces individual component pricing entirely. The agent receives a fully assembled bundle from cpq-agent-003 (Bundle Assembly Agent) and the base list prices from cpq-agent-004 (List Price Application Agent), then determines the appropriate bundle pricing model and calculates the effective price for each component and the bundle as a whole.

The agent operates at a Semi-autonomous level, meaning it automatically applies standard bundle pricing rules defined in the pricing engine but escalates non-standard scenarios -- such as custom bundle compositions not covered by existing pricing rules, bundles that combine products from different price books, or bundle configurations that result in negative component-level prices after adjustment -- to the sales representative for manual review. The agent produces a transparent pricing breakdown showing the a-la-carte total, bundle price, savings amount, and per-component price allocation, ensuring full audit trail compliance and enabling downstream agents (cpq-agent-011 for margin enforcement, cpq-agent-012 for profitability alerts) to evaluate margins at both the bundle and component level.

---

## Key Skills / Capabilities

- **Bundle Price Calculation**: Retrieves the bundle definition and pricing rules via `GET /api/v1/product-bundles/{bundleId}` and executes bundle-specific pricing logic through `POST /api/v1/pricing/execute`. Supports four pricing models: percentage discount off a-la-carte total, fixed bundle price, component-level price adjustments (per-component discount percentages), and tiered pricing based on bundle size or configuration complexity.

- **A-La-Carte vs Bundle Comparison**: Computes the total price of all bundle components priced individually using their list prices from cpq-agent-004 and compares against the calculated bundle price. Generates a savings analysis showing absolute savings, percentage savings, and per-component price differences.

- **Component Price Allocation**: When a bundle has a fixed total price, allocates the bundle price across individual components using configurable allocation methods: proportional (based on list price ratios), equal distribution, or custom weights. Ensures no component receives a negative allocated price and that allocations sum exactly to the bundle total.

- **Bundle Discount Stacking**: Determines how bundle discounts interact with other discount types in the pricing waterfall. Evaluates stacking with volume discounts (cpq-agent-007), segment discounts (cpq-agent-008), or promotional discounts (cpq-agent-009) based on pricing rules configuration.

- **Cross-Bundle Pricing Optimization**: When a quote contains multiple bundles, analyzes whether reorganizing the configuration into different bundle combinations would yield better pricing. Surfaces optimization suggestions to the sales representative.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Bundle ID                | String (UUID)     | From cpq-agent-003 (Bundle Assembly Agent)      |
| Bundle Components        | List of objects   | Product IDs, quantities, options from bundle assembly |
| Component List Prices    | Map (productId -> BigDecimal) | From cpq-agent-004 (List Price Application Agent) |
| Customer ID              | String (UUID)     | Quote session context                            |
| Quote ID                 | String (UUID)     | Active quote reference                           |
| Price Book ID            | String (UUID)     | Resolved price book from cpq-agent-004           |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Bundle Effective Price        | BigDecimal              | Auto-applied for standard bundles; rep reviews non-standard |
| Component Price Breakdown     | JSON array (componentId, listPrice, bundlePrice, savings) | Informational; displayed in quote line items |
| Savings Analysis              | JSON object (aLaCarteTotal, bundleTotal, savingsAmount, savingsPercent) | Displayed to rep and optionally on customer proposal |
| Optimization Suggestions      | JSON array              | Advisory; rep decides whether to restructure bundles |

---

## Typical Workflow

1. **Receive Bundle Configuration**: Triggered after cpq-agent-003 completes bundle assembly and cpq-agent-004 applies list prices to individual components.

2. **Resolve Bundle Pricing Rules**: Retrieves the bundle pricing configuration via `GET /api/v1/product-bundles/{bundleId}` to determine the applicable pricing model.

3. **Calculate Bundle Price**: Executes the pricing calculation via `POST /api/v1/pricing/execute` with the bundle context.

4. **Allocate Component Prices**: Distributes the bundle total price across individual components using the configured allocation method.

5. **Evaluate Discount Stacking**: Checks whether the bundle discount stacks with other discounts in the pricing waterfall.

6. **Generate Comparison Analysis**: Computes the a-la-carte vs bundle comparison and prepares the pricing breakdown.

7. **Return Pricing Results**: Passes results downstream to cpq-agent-011 and cpq-agent-012 for margin validation.

---

## Edge Cases & Exceptions

- **Custom Bundle Not in Price Book**: Flags the bundle for manual pricing review. Provides the a-la-carte total as a reference point.

- **Mixed Price Book Components**: Resolves each component's price from its appropriate price book before applying the bundle discount.

- **Negative Component Allocation**: Enforces a zero-floor on component prices and redistributes the shortfall across higher-value components.

- **Bundle Within Bundle**: Resolves inner bundle pricing first, then applies outer bundle pricing. Limits nesting to two levels.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Bundle pricing calculation accuracy   | 100%           |
| Bundle discount application latency (p95) | < 1 second |
| Component price allocation accuracy   | 100%           |
| Non-standard bundle escalation rate   | < 10%          |
