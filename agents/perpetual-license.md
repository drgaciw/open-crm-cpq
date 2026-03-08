# Agent PRD: cpq-agent-022

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-022                                  |
| **Name**          | Perpetual License Agent                        |
| **Group**         | Subscription & Licensing                       |
| **Primary Service** | pricing-service (8082), product-service (8083) |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004                                  |

---

## Purpose

The Perpetual License Agent manages all pricing and configuration logic for on-premises perpetual license products, covering both ONPREM_PERPETUAL (standard perpetual licenses) and ONPREM_CORE (core-based or socket-based licensing). Its primary responsibility is implementing the `core-license-pack.drl` rule (salience 590), which enforces core-pack rounding: when a customer's server infrastructure requires a specific number of CPU cores, the agent rounds up to the next standard 16-core pack boundary to determine the billable core count. For example, a customer with 40 cores would be quoted 48 billable cores (3 x 16-core packs). The agent calculates one-time license fees based on these rounded quantities, applies volume discounts for large core deployments, and ensures that the license entitlement accurately reflects the customer's declared infrastructure topology.

The agent operates at an Automated with constraints autonomy level because perpetual license transactions are high-value, one-time purchases that carry significant revenue recognition implications. The agent automatically resolves the correct number of core packs, applies standard list pricing and volume discounts, and computes the total license fee without human intervention. However, it escalates to the sales representative when the deal involves non-standard configurations -- such as split-site licensing across multiple data centers, virtualization-based licensing (where hypervisor core counts may differ from physical cores), or license transfers from one server to another. The agent also enforces license compliance by validating the declared core count against known server hardware profiles retrieved from the ERP system, flagging discrepancies that could indicate under-licensing risk.

---

## Key Skills / Capabilities

- **Core Pack Rounding**: Implements the `core-license-pack.drl` rule (salience 590) that rounds up the customer's declared core count to the next multiple of 16. Calculates the number of 16-core packs required using `packs = ceil(declaredCores / 16)` and derives the billable core count as `billableCores = packs * 16`. Executes the pricing calculation via `POST /api/v1/pricing/execute` with the product ID, license type (ONPREM_CORE), and billable core count.

- **Perpetual License Fee Calculation**: Computes one-time license fees for ONPREM_PERPETUAL products based on the per-license or per-core list price. Retrieves the product's pricing structure from `GET /api/v1/products/{id}` and the customer's price book from `GET /api/v1/pricing-customers/{id}`. Applies volume discount tiers (e.g., 1-5 licenses at list, 6-20 at 10% off, 21+ at 18% off) and computes the total one-time fee.

- **Infrastructure Topology Validation**: Retrieves the customer's known server infrastructure profile from `GET /api/v1/erp/contracts/{customerExternalId}` and cross-references the declared core count against known hardware configurations. Flags discrepancies where the declared core count is significantly lower than the expected count based on server model and quantity, which may indicate under-licensing compliance risk.

- **Cost Basis Retrieval**: Retrieves the product's cost of goods sold (COGS) from `GET /api/v1/erp/costs/{productExternalId}` to calculate margin for the perpetual license transaction. Ensures the quoted price maintains the minimum margin threshold configured for the product family. Feeds margin data to downstream approval agents when threshold violations occur.

- **License Entitlement Generation**: Produces a structured license entitlement record containing the product ID, licensed core count, server restrictions (if any), license key parameters, and maintenance eligibility status. This entitlement feeds into the downstream license provisioning system and serves as the contractual record of what the customer has purchased.

- **Upgrade Path Analysis**: For existing perpetual license customers, retrieves their current license entitlements from `GET /api/v1/erp/contracts/{customerExternalId}` and calculates upgrade pricing. Supports in-place upgrades (additional cores on the same server), cross-grade scenarios (moving from per-server to per-core licensing), and version upgrades (moving to a newer product version with trade-in credit for the existing license).

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Product ID               | String (UUID)     | Quote line item / cpq-agent-004 output          |
| Product Type             | Enum (ProductType) | Product catalog (ONPREM_PERPETUAL or ONPREM_CORE) |
| Declared Core Count      | Integer           | Sales rep input based on customer infrastructure |
| Server Count             | Integer           | Sales rep input for per-server licensing          |
| Customer ID              | String (UUID)     | Quote context / CRM record                      |
| Price Book ID            | String (UUID)     | Derived from `GET /api/v1/pricing-customers/{id}` |
| Deal Type                | Enum (DealType)   | Quote metadata (NEW, EXPANSION)                  |
| Existing License ID      | String (UUID)     | Optional -- for upgrades/cross-grades from existing entitlement |
| Virtualization Flag      | Boolean           | Sales rep input indicating virtual vs. physical cores |
| Data Center Locations    | List of String    | Optional -- for split-site licensing scenarios    |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Core Pack Calculation         | JSON object (declaredCores, packSize, numberOfPacks, billableCores, pricePerCore, totalLicenseFee) | Automatic; rep sees rounded core count for verification |
| Perpetual License Fee         | JSON object (productId, licenseType, quantity, unitPrice, volumeDiscount, totalFee, billingFrequency=ONE_TIME) | Automatic within price floor; escalated if margin < threshold |
| Infrastructure Compliance Alert | JSON object (declaredCores, expectedCores, serverModel, discrepancyPercentage, recommendation) | Advisory; rep reviews compliance risk before finalizing |
| License Entitlement Record    | JSON object (entitlementId, productId, licensedCores, serverRestrictions, maintenanceEligible, effectiveDate) | Informational; auto-generated upon quote acceptance |
| Upgrade Path Options          | JSON array of objects (upgradeType, currentEntitlement, newEntitlement, creditAmount, netCost) | Rep must select upgrade path; agent does not auto-apply |

---

## Typical Workflow

1. **Receive License Pricing Request**: The agent is triggered when an ONPREM_PERPETUAL or ONPREM_CORE product is added to a quote. Receives the product ID, product type, declared core count (or server count for per-server licensing), and customer context from cpq-agent-004 or the quote builder UI.

2. **Load Product and Pricing Data**: Agent calls `GET /api/v1/products/{id}` to retrieve the product's licensing model (per-core, per-server, per-instance), pricing tiers, and pack size configuration. Calls `GET /api/v1/pricing-customers/{id}` to retrieve the customer's price book and any negotiated perpetual license rates.

3. **Calculate Core Packs**: For ONPREM_CORE products, the agent applies the `core-license-pack.drl` rounding logic: divides the declared core count by 16, rounds up to the next integer, and multiplies by 16 to determine billable cores. For virtualized environments, applies the applicable virtualization licensing policy (e.g., license all physical cores on the host regardless of allocated virtual cores).

4. **Compute License Fee**: Agent calls `POST /api/v1/pricing/execute` with the product ID, billable core count (or server count), and customer price book reference. The pricing engine returns the per-unit price after applying volume discounts. The agent computes the total one-time license fee and sets BillingFrequency to ONE_TIME.

5. **Validate Margin**: Agent retrieves the product's COGS from `GET /api/v1/erp/costs/{productExternalId}` and calculates the gross margin on the license transaction. If the margin falls below the product family's minimum threshold, the agent flags the line item for approval routing via cpq-agent-024.

6. **Validate Infrastructure Compliance**: Agent retrieves the customer's known infrastructure data from `GET /api/v1/erp/contracts/{customerExternalId}` and compares the declared core count against expected values. If a discrepancy exceeds 20%, generates an Infrastructure Compliance Alert for the rep.

7. **Generate License Entitlement**: Agent produces a license entitlement record containing the product version, licensed core count, server restrictions, maintenance eligibility, and effective date. This record is attached to the quote line item and will be provisioned upon deal close.

8. **Return Pricing Result**: Agent returns the complete perpetual license pricing result including core pack calculation, license fee, margin analysis, compliance status, and entitlement record. For upgrades, includes the trade-in credit calculation and net cost. Passes the result to downstream agents for quote assembly.

---

## Edge Cases & Exceptions

- **Fractional Core Count Declared**: When a customer or rep enters a non-integer core count (e.g., from virtualization environments reporting partial vCPU allocations), the agent rounds up to the next whole integer before applying the 16-core pack rounding. Logs a warning that fractional cores were normalized.

- **Virtualization Licensing Ambiguity**: When the virtualization flag is set but the licensing policy for the product is unclear (e.g., product predates virtualization-specific terms), the agent halts automatic pricing and escalates to deal desk for manual determination of the applicable licensing model (per-physical-core vs. per-virtual-core).

- **Split-Site Licensing**: When the customer deploys the software across multiple data centers, each site's cores must be licensed independently (no cross-site pack sharing). The agent calculates core packs per site, sums the total across sites, and notes that the aggregate may exceed what a single consolidated pack calculation would yield. Presents both calculations for rep review.

- **License Downgrade / Partial Return**: Perpetual licenses are generally non-refundable. When a customer requests to reduce their licensed core count, the agent blocks automatic processing and routes the request to deal desk with a recommendation to credit the reduction toward a future purchase rather than issuing a refund.

- **End-of-Sale Product**: When the requested product has an end-of-sale status but has not yet reached end-of-life, the agent permits the quote but generates a prominent warning that the product will no longer be available for new purchases after the end-of-sale date. Recommends the successor product as an alternative.

- **Extremely Large Core Count (>1,000)**: For enterprise deployments exceeding 1,000 cores, the agent applies the highest volume discount tier automatically but flags the deal for enterprise licensing review since deployments of this scale often qualify for custom enterprise licensing agreements (ELAs) that are priced differently from standard per-core models.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Core pack rounding accuracy          | 100%           |
| License fee calculation accuracy     | >= 99.9%       |
| Infrastructure compliance flag rate  | >= 90% of discrepancies detected |
| Margin threshold enforcement rate    | 100%           |
| Agent response latency (p95)         | < 1.5 seconds  |
