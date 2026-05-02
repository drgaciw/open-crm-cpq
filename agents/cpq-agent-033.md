# Agent PRD: cpq-agent-033

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-033                                  |
| **Name**          | Tax Calculation Integration Agent              |
| **Group**         | Integration & Operations                       |
| **Primary Service** | quote-service (8084)                          |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent)   |

---

## Purpose

The Tax Calculation Integration Agent manages all tax-related computations and compliance requirements throughout the quote lifecycle, integrating with external tax calculation services (Avalara AvaTax, Vertex O Series) to determine accurate tax rates based on product taxability classification, customer ship-to location, seller nexus jurisdictions, and applicable tax exemptions. Enterprise B2B transactions involve complex tax scenarios spanning multiple jurisdictions -- a single quote may contain line items taxable in different states, provinces, or countries, each with distinct tax rates, product-specific exemptions, and regulatory filing requirements. This agent abstracts that complexity away from sales representatives by automatically computing and populating the Quote.totalTax field with jurisdiction-accurate tax amounts, and maintaining per-line-item tax breakdowns that downstream systems (ERP, invoicing, revenue recognition) require for compliance.

The agent operates at an Automated with constraints autonomy level: standard tax calculations for known product types in established jurisdictions execute automatically without human intervention, but several scenarios require human review. Tax exemption claims from customers require validation of exemption certificate documentation before the exemption is applied. Tax-inclusive pricing conversions (common in EMEA and APAC regions where displayed prices must include VAT/GST) require confirmation when the conversion changes line-item amounts visible to the customer. New product types that lack a taxability classification mapping are flagged for tax operations review before the quote can be submitted. The agent also handles tax document preparation -- generating the data payloads required for tax returns, exemption certificate registrations, and audit documentation -- and ensures that every tax determination is logged with its full input context for regulatory compliance and audit defensibility.

---

## Key Skills / Capabilities

- **Tax Rate Calculation**: Integrates with external tax engines (Avalara, Vertex) to compute precise tax rates for each line item on a quote. Constructs the tax determination request using the product's tax code (mapped from the CPQ product catalog), the customer's ship-to address from `GET /api/v1/organizations/{id}`, the seller's nexus configuration, and the transaction date. Applies the computed tax amounts to individual line items and aggregates them into Quote.totalTax via `PUT /api/v1/quotes/{id}`.

- **Tax Exemption Validation**: Processes customer-claimed tax exemptions by verifying exemption certificates against the external tax service's exemption certificate management system. Retrieves the customer's contract and exemption data via `GET /api/v1/erp/contracts/{customerExternalId}` to confirm exemption eligibility. Validates that the exemption covers the specific product tax categories, jurisdictions, and date range on the quote. Flags expired, scope-mismatched, or missing exemption certificates for deal desk review before zeroing tax on the affected line items.

- **Jurisdiction Determination**: Resolves the applicable tax jurisdictions for each line item based on a multi-factor analysis: the customer's billing address from the organization record, the ship-to or service-delivery address (which may differ for multi-site deployments), the product type (tangible goods vs. SaaS vs. professional services -- each taxed differently in many jurisdictions), and the seller's registered nexus locations. Handles complex scenarios like marketplace facilitator rules, economic nexus thresholds, and bundled transaction sourcing rules.

- **Tax-Inclusive Pricing Conversion**: For regions that require tax-inclusive pricing (e.g., EU VAT, Australia GST, India GST), converts tax-exclusive list prices from the price book into tax-inclusive displayed prices. Computes the tax component embedded within the inclusive price using the reverse-calculation method (inclusive price / (1 + tax rate) = pre-tax amount). Ensures that Quote.subtotal reflects pre-tax amounts while the customer-facing quote document shows tax-inclusive line prices where required by regional regulation.

- **Tax Document Preparation**: Generates structured tax data payloads required for compliance documentation, including transaction-level tax summaries, jurisdiction breakdowns, exemption certificate references, and tax determination audit trails. Prepares data for tax return filing integrations and ensures each tax determination is immutably logged with its full input/output context. Retrieves pricing audit records via `GET /api/v1/pricing/audit/{quoteExternalId}` to cross-reference tax calculations against the pricing waterfall for end-to-end audit coherence.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Quote ID                 | String (UUID)     | Quote context from pricing workflow              |
| Quote External ID        | String            | Used for audit log cross-reference               |
| Line Items               | JSON array        | Retrieved via `GET /api/v1/quotes/{quoteId}/line-items` |
| Customer Organization ID | String (UUID)     | Quote header / CRM context                       |
| Ship-To Address          | JSON object       | Customer organization or deal-specific override   |
| Product Tax Code Mapping | String            | Mapped from product catalog productType          |
| Transaction Date         | Date              | Quote creation date or specified effective date   |
| Tax Exemption Certificate ID | String        | Optional -- customer-claimed exemption reference |
| Tax-Inclusive Region Flag | Boolean          | Derived from customer region configuration       |
| External Tax Service Provider | String (Enum) | Configuration: AVALARA, VERTEX, INTERNAL         |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Line Item Tax Amounts         | JSON array of objects (lineItemId, taxableAmount, taxRate, taxAmount, jurisdictionBreakdown[]) | Automatic for standard calculations; displayed for rep review on quote summary |
| Quote Total Tax               | Decimal                 | Auto-populated on Quote.totalTax via `PUT /api/v1/quotes/{id}`; no approval gate for standard jurisdictions |
| Tax Exemption Validation Result | JSON object (exemptionId, status, coveredJurisdictions, coveredProductTypes, expirationDate, validationIssues[]) | Exemption application requires deal desk confirmation when certificate has validation issues |
| Tax-Inclusive Price Conversion | JSON array of objects (lineItemId, exclusivePrice, inclusivePrice, embeddedTaxAmount, taxRate) | Requires rep confirmation when converting changes customer-visible pricing |
| Tax Document Package          | JSON object (transactionSummary, jurisdictionBreakdowns[], exemptionReferences[], auditTrail) | Report generation is automatic; distribution to external tax filing systems requires approval |
| Unmapped Product Tax Code Alert | JSON object (productId, productType, suggestedTaxCode, requiresReview: true) | Blocks quote submission until tax operations assigns a valid tax code |

---

## Typical Workflow

1. **Receive Tax Calculation Trigger**: When a quote's pricing waterfall completes (after list price, discounts, and surcharges are applied by upstream agents), the tax calculation agent receives the quote ID. Retrieves all line items via `GET /api/v1/quotes/{quoteId}/line-items` and the customer organization via `GET /api/v1/organizations/{id}` to obtain address and tax profile information.

2. **Resolve Tax Jurisdictions**: For each line item, determines the applicable tax jurisdictions based on the customer's ship-to address, the product type's taxability classification, and the seller's nexus registrations. Handles multi-jurisdiction scenarios where a single line item may be subject to state, county, city, and special district taxes (US) or VAT/GST at national and sub-national levels (EU, India, Canada).

3. **Map Product Tax Codes**: Maps each line item's product type (from the CPQ product catalog) to the corresponding tax code in the external tax engine's taxonomy. SAAS_SEAT_NAMED and SAAS_FLAT_RATE products may be classified as digital services, HARDWARE as tangible personal property, PROFESSIONAL_SERVICES as non-taxable services in some jurisdictions. Flags unmapped products for tax operations review.

4. **Check Tax Exemptions**: Queries the customer's exemption certificate status via `GET /api/v1/erp/contracts/{customerExternalId}` and the external tax service's exemption registry. Validates that claimed exemptions cover the specific product categories and jurisdictions on the quote. Applies valid exemptions to zero-rate applicable line items. Flags expired or scope-mismatched certificates for review.

5. **Execute Tax Determination**: Submits the tax calculation request to the configured external tax service (Avalara or Vertex) with the full transaction context: line items with amounts, tax codes, ship-from/ship-to addresses, exemption references, and transaction date. Receives per-line-item tax rates and amounts with full jurisdiction breakdowns.

6. **Apply Tax-Inclusive Conversion**: For quotes destined to tax-inclusive regions (EMEA VAT, APAC GST), performs the reverse-calculation to split inclusive prices into pre-tax amount and embedded tax component. Updates line item display prices to show tax-inclusive amounts while maintaining pre-tax amounts in Quote.subtotal for internal financial reporting. Presents the conversion to the rep for confirmation if it changes customer-facing amounts.

7. **Update Quote Tax Fields**: Aggregates line-item tax amounts into Quote.totalTax and updates the quote via `PUT /api/v1/quotes/{id}`. Recalculates Quote.totalAmount as Quote.subtotal - Quote.totalDiscount + Quote.totalTax. Ensures all financial fields (ARR, TCV, ACV) exclude tax amounts per accounting standards.

8. **Generate Tax Documentation**: Prepares the compliance documentation package including the full tax determination audit trail, jurisdiction breakdown summaries, exemption certificate references, and input/output snapshots. Cross-references against the pricing audit log via `GET /api/v1/pricing/audit/{quoteExternalId}` to ensure end-to-end calculation traceability.

---

## Edge Cases & Exceptions

- **External Tax Service Unavailable**: When the configured tax engine (Avalara/Vertex) is unreachable or returns errors, the agent falls back to internally maintained tax rate tables for common jurisdictions (with a "provisional tax" flag). The quote can proceed through DRAFT and IN_REVIEW states with provisional tax, but submission via `POST /api/v1/quotes/{id}/submit` is blocked until the external service confirms the calculation or a deal desk analyst manually overrides the tax amount.

- **Product Without Tax Code Mapping**: When a line item's product type has no mapped tax code in the external tax engine's taxonomy, the agent cannot determine taxability. It flags the line item with an "Unmapped Tax Code" alert, blocks quote submission, and notifies tax operations to assign the appropriate classification. Provides a suggested mapping based on the product's category and type for expedited resolution.

- **Multi-Jurisdiction Split Shipment**: When a single quote has line items shipping to different addresses (e.g., software licenses delivered electronically to HQ in California, hardware shipped to a warehouse in Texas, and professional services delivered at a site in New York), the agent computes separate tax jurisdictions for each delivery destination. Presents a per-destination tax summary on the quote and ensures the aggregate Quote.totalTax accounts for all jurisdictions.

- **Tax Exemption Certificate Expired During Quote Lifecycle**: If a customer's tax exemption certificate expires between quote creation and quote acceptance, the agent detects the expiration during any re-calculation event and removes the exemption from affected line items. Notifies the rep that the previously exempt amounts now carry tax, requiring customer communication and potentially re-approval if the total amount changes materially.

- **Tax Rate Change Between Quote Creation and Acceptance**: When a jurisdiction changes its tax rate (e.g., a state increases sales tax from 6% to 6.5%) while a quote is open, the agent detects the rate change on the next recalculation. Presents the rep with both the original-rate and current-rate tax calculations, recommending whether to honor the original rate (if a rate-lock clause exists in the customer contract) or apply the updated rate.

- **Reverse Charge Mechanism for Cross-Border B2B**: In B2B transactions where the reverse charge mechanism applies (common in EU cross-border VAT), the agent detects the scenario based on the seller's and buyer's country, the buyer's VAT registration status, and the product type. Applies zero-rate VAT to the seller's invoice and includes the required reverse charge notation in the tax documentation package.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Tax calculation accuracy vs. external engine | >= 99.9% |
| Tax exemption validation correctness | >= 99%          |
| Average tax determination latency (p95) | < 1 second   |
| Unmapped product tax code resolution time | < 4 hours  |
| Tax audit documentation completeness rate | 100%        |
