# Agent PRD: cpq-agent-008

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-008                                  |
| **Name**          | Customer Segment Discount Agent                |
| **Group**         | Pricing - Discounts                            |
| **Primary Service** | pricing-service (8082)                       |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent), cpq-agent-007 (Volume Discount Agent) |

---

## Purpose

The Customer Segment Discount Agent applies segment-specific pricing adjustments to quote line items based on the customer's classification across three dimensions: CustomerSegment (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC), CustomerRegion (NORTH_AMERICA, EMEA, APAC, LATAM), and industry vertical. Each dimension carries its own discount factor, and the agent combines these factors according to configurable combination policies -- typically multiplicative compounding (e.g., 5% segment + 3% region + 2% industry = 9.85% combined) rather than simple addition, to prevent discount over-allocation. The agent retrieves the customer's classification from the pricing customer profile and matches it against active segment discount rule sets to determine the applicable adjustments. This ensures that pricing reflects the strategic value of different customer segments, regional cost-of-sale variations, and industry-specific competitive dynamics.

This agent executes after cpq-agent-007 (Volume Discount) in the pricing waterfall, layering segment-based adjustments on top of any volume discounts already applied. It operates at the Automated with constraints autonomy level: standard segment discount applications within defined rule parameters are applied automatically, but any scenarios involving segment override requests (e.g., a customer requesting ENTERPRISE-tier pricing while classified as MID_MARKET), combined discount caps being exceeded, or segment reclassification during an active quote require human approval. The agent enforces a configurable maximum combined discount cap (typically 30%) across all discount types to prevent margin erosion. Every segment discount application is logged with the full classification breakdown, enabling analysis of discount distribution by segment, region, and industry for pricing strategy refinement.

---

## Key Skills / Capabilities

- **Segment Classification**: Retrieves and validates the customer's segment classification by calling `GET /api/v1/pricing-customers/{id}` to obtain the CustomerSegment enum value. Cross-references the segment against the customer's annual contract value, employee count, and deal pipeline to verify classification accuracy. Flags potential misclassifications (e.g., a customer with ENTERPRISE-level deal sizes classified as SMB) as advisory alerts to the sales representative.

- **Region-Specific Adjustment**: Applies regional pricing adjustments based on the customer's CustomerRegion value (NORTH_AMERICA, EMEA, APAC, LATAM). Retrieves region-specific discount rules via `GET /api/v1/rules/by-rule-set/{ruleSetId}` for active region discount rule sets. Regional adjustments account for local market conditions, competitive intensity, cost-of-sale differences, and currency purchasing power parity. For multi-region deals, applies the primary region's adjustment with a notation of the secondary regions involved.

- **Industry Discount Application**: Applies industry-vertical-specific discounts based on the customer's industry classification (e.g., Healthcare, Financial Services, Education, Government, Technology). Retrieves industry discount rules from the active rule sets and applies the matching discount factor. Supports industry-specific promotional rates (e.g., Education sector 15% standard discount) and industry compliance requirements that may affect pricing structure.

- **Combined Discount Cap Enforcement**: Calculates the total combined discount across all discount types applied to a line item (volume + segment + region + industry + promotional + deal-specific) and enforces the maximum discount cap defined in the pricing policy. Calls `GET /api/v1/rule-sets` to retrieve the active cap configuration. When the combined discount would exceed the cap, the agent reduces the segment discount contribution to bring the total within bounds, logs the cap enforcement event, and notifies the sales representative of the adjustment with a breakdown showing which discount components were applied and which were capped.

- **Segment Override Handling**: Manages requests to apply a different segment's pricing than the customer's classified segment. When a sales representative requests an override (e.g., offering ENTERPRISE pricing to a MID_MARKET customer as a strategic concession), the agent calculates the financial impact of the override, compares it against historical overrides for similar customers, and routes the override request through the appropriate approval workflow via `POST /api/v1/quotes/{quoteId}/approvals`. Tracks override frequency by segment pair for pricing strategy analysis.

- **Segment Discount Simulation**: Provides what-if analysis showing the pricing impact of different segment classifications on a quote. Calls `POST /api/v1/pricing/simulate` with alternative segment parameters to calculate pricing under different segment, region, and industry combinations. Returns a comparison matrix showing the discount variance across segments, enabling sales representatives to understand the pricing sensitivity to segment classification and supporting pricing negotiations with informed data.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Quote context from pricing waterfall             |
| Line Item ID                 | String (UUID)     | Individual line item being priced                |
| Product External ID          | String            | Product identifier for segment rule lookup       |
| Base Unit Price              | BigDecimal        | Output from cpq-agent-004 (after volume discount from cpq-agent-007) |
| Customer External ID         | String            | Customer identifier for segment lookup           |
| Customer Segment             | Enum (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) | From `GET /api/v1/pricing-customers/{id}` |
| Customer Region              | Enum (NORTH_AMERICA, EMEA, APAC, LATAM) | From `GET /api/v1/pricing-customers/{id}` |
| Customer Industry            | String            | From `GET /api/v1/pricing-customers/{id}`        |
| Previously Applied Discounts | List of Discount  | Accumulated from cpq-agent-007 and others in the waterfall |
| Active Segment Rule Sets     | List of RuleSet   | From `GET /api/v1/rule-sets` (type = SEGMENT_DISCOUNT, status = ACTIVE) |
| Deal Context                 | JSON object       | From `GET /api/v1/deals/{id}` (deal size, stage, strategic flags) |

---

## Outputs

| Output                             | Format                  | Human-in-the-Loop Checkpoint                     |
|------------------------------------|-------------------------|--------------------------------------------------|
| Applied Segment Discount           | JSON object (segmentFactor, regionFactor, industryFactor, combinedDiscount, model: MULTIPLICATIVE or ADDITIVE) | Automatic within defined segment rules; no approval needed for standard application |
| Updated Line Item Pricing          | JSON object (lineItemId, prePriceSegmentDiscount, postSegmentDiscountPrice, discountBreakdown) | Automatic; written to quote line item via PUT endpoint |
| Discount Cap Enforcement Notice    | JSON object (originalCombinedDiscount, cappedCombinedDiscount, capThreshold, adjustedComponents) | Advisory notification to sales rep; cap enforcement is automatic |
| Segment Override Request           | JSON object (currentSegment, requestedSegment, financialImpact, historicalPrecedents) | Requires human approval via approval workflow |
| Segment Simulation Results         | JSON object (scenarios: array of segment/region/industry combinations with pricing) | Advisory only; presented to sales rep for negotiation support |
| Audit Trail Entry                  | JSON object (agentId, segmentClassification, regionClassification, industryClassification, factors, combinedDiscount, capApplied, timestamp) | Automatic; written to `ai.agent_audit_log` |

---

## Typical Workflow

1. **Receive Pricing Request**: The pricing waterfall reaches the segment discount stage after cpq-agent-007 has applied any applicable volume discounts. The agent receives the quote ID, line item ID, product external ID, current effective price (post-volume-discount), and accumulated discount context.

2. **Load Customer Classification**: Agent calls `GET /api/v1/pricing-customers/{id}` to retrieve the customer's full classification profile: CustomerSegment, CustomerRegion, industry, annual contract value, employee count, and any segment override flags. Validates that all classification dimensions are populated; logs a warning and uses defaults for any missing dimensions.

3. **Retrieve Segment Discount Rules**: Agent calls `GET /api/v1/rule-sets` filtered by type = SEGMENT_DISCOUNT and status = ACTIVE, then calls `GET /api/v1/rules/by-rule-set/{ruleSetId}` for each matching rule set. Loads the discount factor tables for each classification dimension (segment factor, region factor, industry factor) and the combination policy (multiplicative or additive).

4. **Calculate Individual Factors**: Agent resolves the discount factor for each dimension independently. Looks up the segment discount (e.g., ENTERPRISE = 8%, SMB = 3%), the region adjustment (e.g., APAC = 2%, NORTH_AMERICA = 0%), and the industry discount (e.g., Education = 5%, Technology = 1%). Records each factor with its source rule for audit purposes.

5. **Combine Factors and Apply Cap**: Agent combines the three factors according to the combination policy. For multiplicative compounding: combined = 1 - (1 - segment) * (1 - region) * (1 - industry). Checks the accumulated total discount (including volume discounts from cpq-agent-007) against the maximum discount cap. If the cap would be exceeded, reduces the segment discount contribution proportionally and generates a cap enforcement notice.

6. **Validate Stacking with Volume Discount**: Agent verifies that the segment discount stacks correctly with any volume discount already applied by cpq-agent-007. Consults the stacking policy from the rule set. If the policy is "best of," compares the segment discount total against the volume discount and applies only the larger one. If the policy is "compound," applies both sequentially.

7. **Apply Discount to Line Item**: Agent writes the resolved segment discount to the quote line item via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}`, updating the segment discount fields, combined discount percentage, and effective unit price. Passes the updated pricing context downstream to cpq-agent-009 (Promotional Campaign) or directly to cpq-agent-011 (Margin Enforcement) if no promotional discounts are active.

8. **Log Audit Trail**: Agent writes a comprehensive audit entry to `ai.agent_audit_log` including: agent ID, customer classification (segment, region, industry), individual discount factors, combination method, combined discount, cap enforcement details (if triggered), stacking validation result, and timestamp. This enables segment-level discount distribution analysis and pricing strategy optimization.

---

## Edge Cases & Exceptions

- **Missing Customer Segment Classification**: When the pricing customer record exists but the CustomerSegment field is null or unset, the agent applies a conservative default classification based on available signals: if the customer has deals in the pipeline exceeding $100K ACV, defaults to MID_MARKET; if below $25K ACV, defaults to STARTUP; otherwise defaults to SMB. Logs the default application and generates an advisory alert recommending that the customer's segment be formally classified by the account owner.

- **Multi-Region Deal with Conflicting Region Discounts**: When a quote includes line items destined for multiple regions (e.g., NORTH_AMERICA and EMEA), the agent applies region-specific discounts at the line-item level rather than the quote level. Each line item receives the discount appropriate to its delivery region. If no line-item-level region is specified, the agent uses the customer's primary region for all line items and flags the quote for region review.

- **Segment Reclassification During Active Quote**: When a customer's segment is updated (e.g., promoted from SMB to MID_MARKET) while a quote is in DRAFT or PENDING status, the agent detects the segment change on next pricing execution and automatically reprices all affected line items with the new segment discount. The agent generates a change summary showing the before/after pricing impact and notifies the quote owner that segment repricing has occurred.

- **Combined Discount Exceeds Maximum Cap**: When the sum of volume, segment, region, industry, and other accumulated discounts exceeds the configured maximum discount cap (default 30%), the agent applies a proportional reduction to the segment discount components (region and industry factors reduced first, then segment factor) to bring the total within bounds. The sales representative receives a detailed breakdown showing the uncapped calculation, the cap threshold, and which components were reduced. The rep may request an exception via the approval workflow.

- **Strategic Account Segment Override**: When a customer classified as STRATEGIC requests additional pricing concessions beyond the STRATEGIC segment discount, the agent recognizes that STRATEGIC is already the highest segment tier and cannot resolve further discounts through segment reclassification alone. The agent redirects the request to cpq-agent-010 (Deal-Specific Discount) for manual negotiated discount handling and logs the escalation.

- **Industry Not Recognized in Rule Set**: When the customer's industry classification does not match any entry in the active industry discount rule set (e.g., a niche industry like "Space Tourism" not covered by standard industry categories), the agent applies a zero industry discount factor and logs the unmatched industry. Generates an advisory alert to the pricing team suggesting the industry be added to the discount schedule or mapped to the nearest existing industry category.

---

## Success Metrics

| Metric                                              | Target          |
|-----------------------------------------------------|-----------------|
| Segment discount application accuracy               | >= 99.9%        |
| Average segment resolution latency (p95)            | < 200ms         |
| Discount cap enforcement correctness rate            | 100%            |
| Segment override approval turnaround time            | < 4 hours       |
| Audit trail completeness (all classification dimensions logged) | 100% |
