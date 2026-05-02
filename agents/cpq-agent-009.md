# Agent PRD: cpq-agent-009

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-009                                  |
| **Name**          | Promotional Campaign Agent                     |
| **Group**         | Pricing - Discounts                            |
| **Primary Service** | pricing-service (8082)                       |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent)   |

---

## Purpose

The Promotional Campaign Agent manages the discovery, validation, and application of time-limited promotional pricing, campaign-based discounts, and seasonal offers within the CPQ pricing waterfall. It operates against the RuleSet entity's temporal fields -- `effectiveDate`, `expirationDate`, and `status` (DRAFT, ACTIVE, DEPRECATED) -- to ensure that only currently valid promotions are applied and that expired or not-yet-active campaigns are correctly excluded. The agent handles the full promotional lifecycle: identifying which active campaigns a quote is eligible for based on product, customer segment, region, and date criteria; validating that the quote meets all campaign prerequisites (minimum order value, required product combinations, customer eligibility); applying the promotional discount according to the campaign's terms (flat amount, percentage, or fixed price override); and managing stacking rules that govern how promotional discounts interact with volume, segment, and other discount types already in the pricing waterfall.

This agent operates at a Semi-autonomous level, reflecting the inherently time-sensitive and commercially impactful nature of promotional pricing. Standard promotions that match all eligibility criteria and do not conflict with other discounts are applied automatically, but the agent requires human approval for several scenarios: when multiple competing promotions apply to the same line item and priority must be determined, when a promotional discount combined with other discounts would push the total discount beyond the campaign's stacking cap, when a campaign is within 48 hours of expiration and the quote is still in DRAFT status (to prevent last-minute pricing commitments that cannot be fulfilled), and when a customer requests retroactive application of a promotion that was active at the time of initial quoting but has since expired. The agent also provides campaign attribution data, tracking which promotions drove which deals to enable marketing ROI analysis and future campaign planning.

---

## Key Skills / Capabilities

- **Campaign Discovery**: Identifies all active promotional campaigns that could apply to the current quote by scanning active rule sets via `GET /api/v1/rule-sets` filtered by type = PROMOTIONAL, status = ACTIVE, and where the current date falls between `effectiveDate` and `expirationDate`. Retrieves the detailed rules for each qualifying campaign via `GET /api/v1/rules/by-rule-set/{ruleSetId}`. Evaluates campaign scope (global, segment-specific, region-specific, product-specific) against the quote's context to produce a shortlist of applicable campaigns.

- **Eligibility Validation**: Performs comprehensive eligibility checking for each candidate campaign against the quote's attributes. Validates: product eligibility (campaign applies to the quoted product or product family), customer segment eligibility (campaign restricted to specific segments via `GET /api/v1/pricing-customers/{id}`), minimum order value thresholds, required product combinations (e.g., "buy product A and B together to qualify"), maximum redemption limits (campaign usage count against cap), and geographic restrictions (campaign available in customer's region). Returns a detailed eligibility result for each campaign with pass/fail reasons.

- **Promotion Application**: Applies the validated promotional discount to qualifying line items according to the campaign's discount model. Supports three models: percentage discount (e.g., 20% off), flat amount discount (e.g., $500 off per unit), and fixed price override (e.g., promotional price of $99/seat). Executes the discount through `POST /api/v1/pricing/execute` with the promotional rule context and writes the result to the line item via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}`. Stamps the line item with the campaign ID and promotional code for tracking.

- **Stacking Conflict Resolution**: Evaluates how the promotional discount interacts with other discounts already applied in the pricing waterfall (volume from cpq-agent-007, segment from cpq-agent-008). Retrieves stacking policies from the campaign rule set. Implements four stacking modes: EXCLUSIVE (promotion replaces all other discounts), ADDITIVE (promotion adds to existing discounts), BEST_OF (whichever yields greater discount applies), and CAPPED (promotion stacks up to a combined discount maximum). When conflicts arise between multiple promotions or between a promotion and existing discounts, presents the options to the sales representative with financial impact analysis for each resolution path.

- **Campaign Attribution**: Records detailed campaign attribution data for every promotional discount applied, linking the promotion to the quote, line item, customer, deal, and eventual revenue outcome. This data feeds marketing analytics dashboards and campaign ROI calculations. Tracks: campaign ID, promotion code (if applicable), discount amount attributed to the campaign, quote stage at time of application, and eventual win/loss outcome (updated asynchronously when the quote reaches a terminal state via the `quote:status-changed` event channel).

- **Temporal Boundary Management**: Monitors promotional campaign expiration dates relative to the quote lifecycle. When a promotion is applied to a DRAFT quote and the campaign's `expirationDate` is approaching (configurable threshold, default 48 hours), the agent generates a proactive alert to the sales representative warning that the promotional pricing may expire before the quote is finalized. If the promotion expires while the quote is still in DRAFT status, the agent marks the promotion as "expired pending removal" and queues it for removal on the next pricing recalculation, with notification to the rep.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Quote context from pricing waterfall             |
| Line Item IDs                | List of String (UUID) | All line items in the quote                  |
| Product External IDs         | List of String    | Products on the quote for campaign matching      |
| Current Effective Prices     | Map (lineItemId -> BigDecimal) | Post-volume and post-segment pricing from upstream agents |
| Customer External ID         | String            | Customer identifier for eligibility checking     |
| Customer Segment             | Enum (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) | From `GET /api/v1/pricing-customers/{id}` |
| Customer Region              | Enum (NORTH_AMERICA, EMEA, APAC, LATAM) | From `GET /api/v1/pricing-customers/{id}` |
| Quote Creation Date          | Timestamp         | From `GET /api/v1/quotes/{id}`                   |
| Promotional Code             | String            | Optional; rep-entered promotional code           |
| Deal ID                      | String (UUID)     | From CRM context via `GET /api/v1/deals/{id}`    |
| Previously Applied Discounts | List of Discount  | Accumulated from cpq-agent-007, cpq-agent-008    |

---

## Outputs

| Output                             | Format                  | Human-in-the-Loop Checkpoint                     |
|------------------------------------|-------------------------|--------------------------------------------------|
| Eligible Campaigns List            | JSON array of objects (campaignId, name, discountType, discountValue, expirationDate, eligibilityDetails) | Advisory; displayed to sales rep for awareness |
| Applied Promotional Discount       | JSON object (campaignId, lineItemId, discountType, discountAmount, promotionalCode) | Automatic for single eligible campaign with no stacking conflicts |
| Stacking Conflict Resolution       | JSON object (conflictingDiscounts, resolutionOptions: array of scenarios with financial impact) | Requires human selection when multiple promotions compete or stacking exceeds cap |
| Expiration Warning                 | JSON object (campaignId, expirationDate, hoursRemaining, quoteStatus) | Advisory alert when promotion nears expiration on DRAFT quote |
| Campaign Attribution Record        | JSON object (campaignId, quoteId, lineItemId, discountAmount, dealId) | Automatic; written for marketing analytics |
| Audit Trail Entry                  | JSON object (agentId, campaignsEvaluated, campaignsApplied, stackingResolution, expirationChecks, timestamp) | Automatic; written to `ai.agent_audit_log` |

---

## Typical Workflow

1. **Receive Pricing Request**: The pricing waterfall reaches the promotional discount stage after cpq-agent-008 (Customer Segment Discount) has completed. The agent receives the quote context including all line items, their current effective prices, the customer profile, and any accumulated discounts from upstream agents.

2. **Discover Active Campaigns**: Agent calls `GET /api/v1/rule-sets` filtered by type = PROMOTIONAL and status = ACTIVE. For each returned rule set, validates that the current timestamp falls between `effectiveDate` and `expirationDate`. Retrieves detailed rules via `GET /api/v1/rules/by-rule-set/{ruleSetId}` for all temporally valid campaigns. If a promotional code was provided by the sales rep, also searches for code-specific campaigns.

3. **Validate Eligibility Per Campaign**: For each discovered campaign, the agent evaluates eligibility against the quote's context. Checks product scope (do any quoted products match the campaign's product list or product family), customer eligibility (does the customer's segment and region match campaign restrictions via `GET /api/v1/pricing-customers/{id}`), order value thresholds (does the quote meet minimum value requirements), and redemption limits (has the campaign's maximum usage count been reached). Produces a pass/fail eligibility result with detailed reasons for each campaign.

4. **Resolve Campaign Conflicts**: When multiple campaigns are eligible for the same line item, the agent evaluates priority and mutual exclusivity rules. If campaigns are mutually exclusive, selects the one with the highest discount value unless the campaign rule set specifies a different priority. If the conflict cannot be resolved automatically (e.g., two campaigns with equal priority and non-overlapping benefits), presents the options to the sales representative with a financial impact comparison via the UI.

5. **Evaluate Stacking with Existing Discounts**: For each eligible campaign, the agent checks the stacking mode (EXCLUSIVE, ADDITIVE, BEST_OF, CAPPED) against discounts already applied by cpq-agent-007 and cpq-agent-008. Calculates the combined discount under each stacking scenario. If the combined discount exceeds the campaign's stacking cap or the global discount cap, adjusts the promotional discount accordingly and notifies the rep.

6. **Apply Promotional Discounts**: Agent applies validated promotional discounts by calling `POST /api/v1/pricing/execute` with the promotional campaign context for each qualifying line item. Writes the promotional discount to each line item via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}`, including the campaign ID, promotional code, and discount amount. Updates the line item's discount breakdown to reflect the promotional component.

7. **Check Temporal Boundaries**: Agent evaluates the expiration date of each applied campaign relative to the quote's current status. If the quote is in DRAFT status and any applied campaign expires within 48 hours, generates an expiration warning for the sales representative. Schedules a background check to monitor promotion validity through the quote lifecycle. If the quote is in PENDING_APPROVAL status with an expiring promotion, escalates the approval urgency.

8. **Record Attribution and Audit**: Agent writes campaign attribution records linking each promotional discount to the campaign, quote, line item, and deal. Simultaneously writes a comprehensive audit entry to `ai.agent_audit_log` including all campaigns evaluated, eligibility results, conflict resolution decisions, stacking calculations, temporal boundary checks, and the final applied promotional discounts.

---

## Edge Cases & Exceptions

- **Expired Promotion Retroactive Application**: When a sales representative requests application of a promotion that was active at the time the quote was originally created but has since expired (the campaign's `expirationDate` has passed), the agent calculates what the discount would have been, presents the financial impact, and routes the retroactive application request through the approval workflow via `POST /api/v1/quotes/{quoteId}/approvals`. The approval request includes the campaign details, original effective dates, the elapsed time since expiration, and a precedent analysis showing how similar retroactive requests were handled historically.

- **Campaign Reaches Maximum Redemption During Quote Lifecycle**: When a campaign has a maximum redemption limit and that limit is reached while a quote with the campaign applied is still in DRAFT or PENDING status, the agent must handle the race condition. It checks redemption counts at both application time and at quote finalization time. If the limit is exceeded between these checkpoints, the agent removes the promotional discount, notifies the rep, and suggests alternative active campaigns that may apply.

- **Promotional Code Invalid or Expired**: When a sales representative enters a promotional code that does not match any active campaign, the agent returns a clear error distinguishing between "code not recognized" (no matching campaign exists), "code expired" (campaign exists but `expirationDate` has passed), "code not yet active" (campaign exists but `effectiveDate` is in the future), and "code not eligible" (campaign exists and is active but the quote does not meet eligibility criteria). Each error includes specific guidance for resolution.

- **Multiple Promotions with Conflicting Stacking Modes**: When one eligible campaign specifies EXCLUSIVE stacking (replaces all other discounts) while another specifies ADDITIVE stacking, the agent cannot automatically resolve the conflict. It presents both options to the sales representative with a full financial comparison: Option A shows the exclusive promotion's discount replacing all others, Option B shows the additive promotion stacked on top of existing discounts. The rep's selection is logged for future conflict resolution pattern analysis.

- **Promotion Applied to Bundle vs. Individual Items**: When a promotion targets a specific product that appears both as a standalone line item and as a component within a bundle on the same quote, the agent must determine where to apply the promotion. If the promotion specifies "standalone only," it applies only to the standalone line item. If the promotion is product-level, the agent applies it to both but ensures the bundle's internal discount calculation (from cpq-agent-018) is not double-counted. Flags the dual-application scenario for rep review.

- **Campaign Status Transition During Pricing**: When a campaign's status changes from ACTIVE to DEPRECATED during an in-flight pricing execution (e.g., the pricing admin deprecates the campaign between the agent's discovery and application steps), the agent detects the status change on the application call and gracefully handles the failure. It removes the campaign from the eligible list, recalculates pricing without the deprecated campaign, notifies the rep that the promotion is no longer available, and suggests alternative active campaigns.

---

## Success Metrics

| Metric                                              | Target          |
|-----------------------------------------------------|-----------------|
| Promotional discount application accuracy           | >= 99.5%        |
| Campaign eligibility evaluation latency (p95)       | < 500ms         |
| Expired promotion prevention rate                   | 100%            |
| Campaign attribution completeness                   | >= 98%          |
| Stacking conflict resolution within SLA (manual)    | < 2 hours       |
