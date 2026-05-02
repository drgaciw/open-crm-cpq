# Agent PRD: cpq-agent-023

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-023                                  |
| **Name**          | Maintenance & Support Fee Agent                |
| **Group**         | Subscription & Licensing                       |
| **Primary Service** | pricing-service (8082), product-service (8083) |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-022                                  |

---

## Purpose

The Maintenance & Support Fee Agent calculates annual maintenance renewal fees and support tier pricing for on-premises perpetual license customers. Its core responsibility is implementing the `perpetual-maintenance.drl` rule (salience 580), which sets the annual maintenance fee at 22% of the original perpetual license price. This maintenance fee entitles the customer to software updates, patches, and a defined level of technical support for the contract year. The agent works in direct coordination with cpq-agent-022 (Perpetual License Agent), which provides the base license price that serves as the maintenance calculation anchor. Beyond the standard 22% calculation, the agent manages the full support tier hierarchy -- SUPPORT_BASIC, SUPPORT_PREMIUM, and SUPPORT_ENTERPRISE -- each with different SLA commitments, response times, and associated multipliers applied on top of the base maintenance rate.

The agent operates at a Semi-autonomous level because maintenance and support pricing decisions directly affect customer retention and long-term revenue. For standard first-year maintenance on a new perpetual license, the agent auto-calculates at the 22% rate and attaches it to the quote without intervention. However, several common scenarios require human review: maintenance renewals where the customer has lapsed (requiring a reinstatement penalty calculation), support tier upgrades or downgrades mid-term, multi-year maintenance prepayment discounts, and situations where the customer's original license price is no longer available (e.g., after a product version upgrade that changed the price basis). The agent also tracks maintenance renewal dates across the customer's license portfolio and generates proactive renewal notifications 90 days before expiration to prevent unintentional lapses.

---

## Key Skills / Capabilities

- **Base Maintenance Calculation**: Implements the `perpetual-maintenance.drl` rule (salience 580): `annualMaintenanceFee = licensePriceBase * 0.22`. Retrieves the original license price from the associated perpetual license quote line item (via cpq-agent-022 output) or from the customer's contract history via `GET /api/v1/erp/contracts/{customerExternalId}`. For core-based licenses, uses the total license fee (billable cores * per-core price) as the calculation base, not the per-core price alone.

- **Support Tier Pricing**: Applies support tier multipliers on top of the base maintenance rate. Retrieves support product definitions from `GET /api/v1/products/{id}` for SUPPORT_BASIC, SUPPORT_PREMIUM, and SUPPORT_ENTERPRISE products. Standard multipliers: SUPPORT_BASIC (1.0x -- included in base 22%), SUPPORT_PREMIUM (1.35x -- 29.7% effective rate), SUPPORT_ENTERPRISE (1.65x -- 36.3% effective rate, includes 24x7 coverage and dedicated TAM). Validates tier availability against the customer's segment via `GET /api/v1/pricing-customers/{id}`.

- **Maintenance Lapse Reinstatement**: Handles scenarios where a customer's maintenance has lapsed (expired without renewal). Calculates the reinstatement fee consisting of back-payment for all lapsed periods at the standard rate plus a reinstatement penalty (typically 5% surcharge on the cumulative back-payment). Retrieves lapse duration from `GET /api/v1/erp/contracts/{customerExternalId}` by comparing the last maintenance end date against the current date.

- **Multi-Year Maintenance Prepayment**: Supports prepaid multi-year maintenance agreements where the customer pays upfront for 2, 3, or 5 years of maintenance at a discounted rate. Retrieves applicable discount rules from `GET /api/v1/rules/by-rule-set/{ruleSetId}` (standard discounts: 2-year at 5% off, 3-year at 10% off, 5-year at 15% off). Calculates the total prepaid amount and the effective per-year rate.

- **Renewal Tracking and Notification**: Monitors maintenance expiration dates across the customer's license portfolio by querying `GET /api/v1/erp/contracts/{customerExternalId}`. Generates proactive renewal quotes 90 days before expiration, pre-populated with the current support tier and any applicable price uplift. Coordinates with `renewal-uplift.drl` (salience 530) for standard 8% uplift or reduced 3% uplift for high-churn-risk accounts.

- **License-Maintenance Linkage Validation**: Ensures every perpetual license line item in a quote has a corresponding maintenance line item. Queries quote line items via `GET /api/v1/quotes/{quoteId}/line-items` and validates that each ONPREM_PERPETUAL or ONPREM_CORE product is paired with a SUPPORT_BASIC, SUPPORT_PREMIUM, or SUPPORT_ENTERPRISE product. Flags unpaired licenses to the rep as these represent a compliance and support gap.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| License Price Base       | Decimal           | cpq-agent-022 output / contract history          |
| Product ID (License)     | String (UUID)     | Quote line item (the associated perpetual license) |
| Product ID (Support)     | String (UUID)     | Quote line item (the support SKU)                |
| Support Tier             | Enum (ProductType) | Sales rep selection (SUPPORT_BASIC, SUPPORT_PREMIUM, SUPPORT_ENTERPRISE) |
| Customer ID              | String (UUID)     | Quote context / CRM record                      |
| Contract ID              | String (UUID)     | Existing maintenance contract from `GET /api/v1/erp/contracts/{customerExternalId}` |
| Maintenance Start Date   | Date (ISO-8601)   | License effective date or renewal date           |
| Prepayment Years         | Integer           | Optional -- 1 (default), 2, 3, or 5             |
| Lapse Duration           | Integer (days)    | Calculated from contract history if applicable   |
| Renewal Uplift Override  | Decimal (percentage) | Optional -- deal desk override of standard uplift |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Maintenance Fee Calculation   | JSON object (licensePriceBase, maintenanceRate, supportTierMultiplier, annualMaintenanceFee, effectiveRate) | Automatic for first-year standard maintenance; rep confirmation for renewals with uplift |
| Lapse Reinstatement Invoice   | JSON object (lapsedPeriods, backPaymentAmount, reinstatementPenalty, totalReinstatementFee) | Mandatory rep review; all reinstatement fees require deal desk approval |
| Multi-Year Prepayment Summary | JSON object (years, annualRate, prepaymentDiscount, totalPrepaidAmount, effectiveAnnualRate) | Rep must confirm prepayment terms before applying to quote |
| Renewal Notification          | JSON object (contractId, expirationDate, currentTier, proposedRenewalRate, upliftPercentage) | Advisory; rep reviews and decides whether to send renewal quote to customer |
| License-Maintenance Gap Alert | JSON array of objects (licenseProductId, licenseName, missingMaintenanceSku) | Blocks quote submission until all licenses have paired maintenance |

---

## Typical Workflow

1. **Receive Maintenance Pricing Request**: The agent is triggered either by cpq-agent-022 when a new perpetual license is added to a quote (auto-attach maintenance) or by a renewal event when an existing maintenance contract approaches expiration. Receives the license price base, product IDs, support tier selection, and customer context.

2. **Load License and Contract Context**: Agent calls `GET /api/v1/erp/contracts/{customerExternalId}` to retrieve the customer's existing maintenance contracts, their expiration dates, current support tiers, and historical maintenance payment records. For new licenses, retrieves the license price from the cpq-agent-022 output.

3. **Calculate Base Maintenance Fee**: Agent applies the `perpetual-maintenance.drl` formula: `annualMaintenanceFee = licensePriceBase * 0.22`. For renewals, applies the `renewal-uplift.drl` logic: standard 8% uplift on the prior year's maintenance fee, reduced to 3% if the customer's churn score exceeds 70. If `churnScore > 70`, the agent also flags the retention offer `ENABLE_12M_PRICE_PROTECTION`.

4. **Apply Support Tier Multiplier**: Agent retrieves the selected support tier product from `GET /api/v1/products/{id}` and applies the tier-specific multiplier to the base maintenance fee. For SUPPORT_PREMIUM, multiplies by 1.35; for SUPPORT_ENTERPRISE, multiplies by 1.65. Validates that the customer's segment qualifies for the selected tier (e.g., SUPPORT_ENTERPRISE may require Enterprise segment designation).

5. **Handle Lapse Reinstatement (if applicable)**: If the customer's maintenance has lapsed, the agent calculates the reinstatement fee: sums the annual maintenance fee for each lapsed period, adds the 5% reinstatement penalty on the cumulative back-payment, and presents the total reinstatement cost. Routes the reinstatement calculation to deal desk for mandatory approval.

6. **Apply Multi-Year Prepayment Discount (if applicable)**: If the rep has selected a multi-year prepayment option, the agent retrieves the discount schedule from `GET /api/v1/rules/by-rule-set/{ruleSetId}` and applies the appropriate discount to the total prepaid amount. Calculates the effective annual rate for the rep's comparison.

7. **Validate License-Maintenance Pairing**: Agent scans all quote line items via `GET /api/v1/quotes/{quoteId}/line-items` and verifies that every ONPREM_PERPETUAL and ONPREM_CORE product has a corresponding maintenance/support line item. Generates a gap alert for any unpaired licenses, blocking quote submission until resolved.

8. **Return Maintenance Pricing Result**: Agent returns the complete maintenance pricing result including the base calculation, support tier adjustment, any reinstatement or prepayment details, and the license-maintenance pairing validation status. Updates the quote line item via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}` with the calculated maintenance fee.

---

## Edge Cases & Exceptions

- **License Price Basis No Longer Available**: When a customer renews maintenance on a perpetual license purchased years ago and the original license price is no longer in the system (e.g., product has been deprecated and price history archived), the agent falls back to the most recent known maintenance fee and applies the renewal uplift on that amount. Flags the line item with an advisory noting the original price basis is unavailable.

- **Support Tier Downgrade Mid-Term**: When a customer requests a support tier downgrade (e.g., Enterprise to Premium) during an active maintenance term, the agent calculates the credit for the unused portion of the higher tier and the charge for the remaining portion at the lower tier. Routes the downgrade to deal desk for approval since support downgrades may affect contractual SLA commitments.

- **Maintenance on Partial Core Upgrade**: When a customer has added cores to their perpetual license mid-year (processed by cpq-agent-022), the maintenance fee must be recalculated on the new total license value. The agent computes the incremental maintenance fee for the additional cores, prorated from the upgrade effective date to the maintenance renewal date, and adds it to the existing maintenance stream.

- **Customer Refuses Maintenance at Purchase**: While rare, some customers decline maintenance on a new perpetual license. The agent generates a License-Maintenance Gap Alert but allows the rep to acknowledge and override the warning. Logs the override for compliance tracking and notes that the customer will face reinstatement penalties if they later wish to add maintenance.

- **Multi-Product Maintenance Bundle Discount**: When a customer holds perpetual licenses for three or more products from the same product family, the agent checks for maintenance bundle discount eligibility. Retrieves bundle rules from `GET /api/v1/product-bundles/product/{productId}` and applies the portfolio discount (typically 5-10%) across all maintenance line items for that family.

- **Maintenance on Transferred License**: When a perpetual license is transferred from one customer entity to another (e.g., due to acquisition or divestiture), the agent must re-anchor the maintenance to the new customer's pricing profile. Retrieves the new customer's price book from `GET /api/v1/pricing-customers/{id}` and recalculates the maintenance fee using the new customer's contracted rates, which may differ from the original customer's rates.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Maintenance fee calculation accuracy | >= 99.9%       |
| License-maintenance pairing enforcement rate | 100%    |
| Renewal notification lead time       | >= 90 days before expiration |
| Lapse reinstatement processing time  | < 24 hours (deal desk turnaround) |
| Support tier pricing accuracy        | >= 99.5%       |
