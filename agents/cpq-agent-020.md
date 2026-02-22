# Agent PRD: cpq-agent-020

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-020                                  |
| **Name**          | Term & Co-Terming Agent                        |
| **Group**         | Subscription & Licensing                       |
| **Primary Service** | quote-service (8084), pricing-service (8082)  |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-004                                  |

---

## Purpose

The Term & Co-Terming Agent manages subscription term alignment and proration calculations for multi-product quotes where line items may have different start dates, end dates, or billing cycles. Its primary function is implementing the logic encoded in `amendment-proration.drl` (salience 540), which computes prorated charges using the formula `prorated = deltaAmount * remainingDays / fullTermDays` whenever a subscription is amended mid-term. The agent ensures that when new products are added to an existing customer's subscription portfolio, the new line items are co-termed to align with the master contract's end date rather than starting an independent term cycle. This eliminates the operational complexity of managing multiple renewal dates per customer and ensures a single unified renewal event.

The agent operates at a Semi-autonomous level because proration and co-terming calculations involve financial commitments that require human visibility. For straightforward co-terming scenarios -- where the new product simply adopts the master contract's end date and the proration math is unambiguous -- the agent auto-calculates the prorated amount and presents it for rep confirmation. For complex scenarios -- such as multi-year ramp deals where pricing changes per year, mid-term downgrades requiring credit calculations, or co-terming across contracts with different billing frequencies -- the agent calculates the proposed adjustment and halts for mandatory rep review before applying it to the quote. The agent also tracks term-level metadata such as auto-renewal flags, cancellation notice periods, and ramp schedules, propagating these attributes consistently across all co-termed line items.

---

## Key Skills / Capabilities

- **Proration Calculation**: Implements the core proration formula from `amendment-proration.drl` (salience 540): `prorated = deltaAmount * remainingDays / fullTermDays`. Calculates the remaining days from the amendment effective date to the master contract end date, determines the delta amount (difference between new and old pricing), and computes the prorated charge or credit. Executes via `POST /api/v1/pricing/execute` with amendment context parameters including the original line item pricing and the new pricing.

- **Co-Term Date Resolution**: Retrieves the customer's master contract from `GET /api/v1/erp/contracts/{customerExternalId}` to determine the co-term target date. Aligns all new subscription line items to this date by adjusting their start/end dates and computing the appropriate prorated first-period charge. When multiple active contracts exist, the agent identifies the primary contract (highest ARR or explicitly flagged as master) and uses its end date as the co-term anchor.

- **Amendment Impact Analysis**: For mid-term amendments (DealType = AMENDMENT), the agent retrieves the existing quote and its line items via `GET /api/v1/quotes/{quoteId}/line-items` to determine the current term parameters. Calculates the financial impact of the amendment -- upgrade credits, downgrade refunds, or quantity change adjustments -- and presents a detailed amendment summary showing the before/after state and the net proration amount.

- **Multi-Year Ramp Schedule Management**: Handles ramp deals where the unit price or quantity changes across annual periods within a multi-year term. Retrieves ramp schedule definitions from the pricing rules via `GET /api/v1/rules/by-rule-set/{ruleSetId}` and ensures that proration calculations for mid-ramp amendments reference the correct year's pricing rather than the initial or final year rate.

- **Term Metadata Propagation**: Manages term-level attributes including auto-renewal (true/false), cancellation notice period (30/60/90 days), billing alignment day (1st or 15th of month), and payment terms (Net 30/60/90). Propagates these consistently across all co-termed line items when new products are added. Retrieves existing term metadata from the quote via `GET /api/v1/quotes/{id}`.

- **Billing Frequency Reconciliation**: When co-terming products with different billing frequencies (e.g., adding a MONTHLY product to a contract billed ANNUALLY), the agent calculates the appropriate conversion. For annual-to-monthly alignment, it derives the monthly equivalent and adjusts the first partial period. Uses `POST /api/v1/pricing/simulate` to preview the reconciled billing schedule before applying it.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Quote ID                 | String (UUID)     | Active quote context                             |
| Customer ID              | String (UUID)     | Quote context / CRM record                      |
| Deal Type                | Enum (DealType)   | Quote metadata (NEW, AMENDMENT, RENEWAL, EXPANSION) |
| Master Contract ID       | String (UUID)     | Derived from `GET /api/v1/erp/contracts/{customerExternalId}` |
| Amendment Effective Date | Date (ISO-8601)   | Sales rep input or system-generated (today)      |
| New Line Item Pricing    | Decimal           | Output from cpq-agent-004 or cpq-agent-019      |
| Original Line Item Pricing | Decimal         | Existing quote line item for amendments          |
| Requested Term Length    | Integer (months)  | Sales rep input (12, 24, 36 months)              |
| Billing Frequency        | Enum (BillingFrequency) | Sales rep selection                        |
| Auto-Renewal Flag        | Boolean           | Sales rep or contract template default           |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Prorated Charge Calculation   | JSON object (deltaAmount, remainingDays, fullTermDays, proratedAmount, effectiveDate, endDate) | Rep must confirm prorated amount before it is applied to the quote |
| Co-Term Alignment Summary     | JSON object (masterContractEndDate, adjustedStartDate, adjustedEndDate, firstPeriodProration) | Rep must confirm co-term date alignment for new line items |
| Amendment Impact Report       | JSON object (originalLineItems, amendedLineItems, netProratedCredit, netProratedCharge) | Mandatory review for all amendments; rep must approve net financial impact |
| Ramp Schedule Preview         | JSON array of objects (year, quantity, unitPrice, annualTotal) | Rep must review and confirm multi-year ramp schedule |
| Billing Reconciliation Detail | JSON object (originalFrequency, targetFrequency, conversionFactor, adjustedAmount) | Advisory for simple conversions; mandatory review for cross-frequency co-terming |

---

## Typical Workflow

1. **Receive Term Alignment Request**: The agent is triggered when a new line item is added to a quote for an existing customer (DealType = AMENDMENT or EXPANSION) or when a rep explicitly requests co-terming for a new deal. The agent receives the quote ID, customer ID, and deal type from the active session.

2. **Retrieve Master Contract**: Agent calls `GET /api/v1/erp/contracts/{customerExternalId}` to fetch all active contracts for the customer. Identifies the master contract by selecting the contract with the highest ARR or the one explicitly flagged as the co-term anchor. Extracts the master contract end date, billing frequency, and term metadata.

3. **Load Existing Quote Context**: Agent calls `GET /api/v1/quotes/{id}` and `GET /api/v1/quotes/{quoteId}/line-items` to retrieve the current quote state including all existing line items, their term dates, and pricing. For amendments, identifies the specific line items being modified.

4. **Calculate Proration**: For amendments, the agent applies the `amendment-proration.drl` formula: computes `remainingDays` from the amendment effective date to the master contract end date, determines `fullTermDays` from the original term, calculates `deltaAmount` as the difference between new and old pricing, and derives `proratedAmount = deltaAmount * remainingDays / fullTermDays`.

5. **Align Co-Term Dates**: For new line items (EXPANSION), the agent adjusts the start date to today (or the specified effective date) and the end date to match the master contract end date. Calculates the first-period prorated charge based on the partial period from start to the next billing cycle date.

6. **Reconcile Billing Frequencies**: If the new line item's billing frequency differs from the master contract's frequency, the agent calculates the conversion factor and adjusted amounts. Uses `POST /api/v1/pricing/simulate` to generate a preview of the reconciled billing schedule across the remaining term.

7. **Present Amendment Impact Report**: Agent compiles the complete amendment impact summary showing original line items, amended line items, prorated charges or credits, co-term adjustments, and net financial impact. Presents this report to the sales representative for mandatory review and confirmation.

8. **Apply Approved Adjustments**: Upon rep confirmation, the agent updates the quote line items via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}` with the adjusted term dates, prorated amounts, and aligned billing parameters. Propagates term metadata (auto-renewal, notice period) to all co-termed line items and passes the finalized quote to downstream agents.

---

## Edge Cases & Exceptions

- **No Master Contract Found**: When `GET /api/v1/erp/contracts/{customerExternalId}` returns no active contracts (new customer or lapsed account), the agent skips co-terming logic entirely and applies standard term start/end dates based on the requested term length. Notifies the rep that no co-term anchor was found.

- **Multiple Active Contracts with Different End Dates**: When a customer has multiple non-consolidated contracts, the agent presents a contract selection UI allowing the rep to choose which contract to co-term against. Includes contract details (products, ARR, end date) to inform the selection. Does not auto-select when ambiguity exists.

- **Mid-Ramp Amendment**: When an amendment occurs during a multi-year ramp deal, the proration calculation must reference the current ramp year's pricing, not the initial or final year's rate. The agent retrieves the ramp schedule and identifies the applicable year-period pricing before applying the proration formula.

- **Negative Proration (Downgrade Credit)**: When a mid-term downgrade results in a negative proration amount (credit owed to customer), the agent calculates the credit but flags it for mandatory deal desk review since credits affect revenue recognition. The credit is not auto-applied to the quote.

- **Co-Term Resulting in Less Than 30 Days**: When co-terming would produce a stub period of fewer than 30 days (e.g., new product added 15 days before master contract renewal), the agent recommends extending the new line item to the next renewal cycle (master end date + standard term) and computing an extended first period. Presents both options (stub period vs. extended period) to the rep.

- **Cross-Currency Co-Terming**: When the new line item is quoted in a different currency than the master contract, the agent blocks automatic co-terming and requires deal desk intervention to establish the currency reconciliation approach before term alignment can proceed.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Proration calculation accuracy       | >= 99.9%       |
| Co-term date alignment success rate  | >= 95%         |
| Amendment processing time (median)   | < 3 seconds    |
| Billing frequency reconciliation accuracy | >= 99%    |
| Rep override rate on co-term suggestions | < 10%       |
