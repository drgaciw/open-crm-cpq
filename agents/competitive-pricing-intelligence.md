# Agent PRD: cpq-agent-034

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-034                                  |
| **Name**          | Competitive Pricing Intelligence Agent         |
| **Group**         | Integration & Operations                       |
| **Primary Service** | crm-service (8081), pricing-service (8082)    |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | None                                           |

---

## Purpose

The Competitive Pricing Intelligence Agent provides data-driven competitive pricing guidance to sales representatives and deal desk analysts during the quoting process. By analyzing historical win/loss data stored in CRM deal records, the agent identifies patterns in competitive outcomes -- which competitors are most frequently encountered in each customer segment and region, what discount levels correlate with wins versus losses against specific competitors, and how the organization's product feature set compares to competitive alternatives. This intelligence enables reps to make informed pricing decisions rather than relying on anecdotal competitive knowledge or reactive discounting. The agent synthesizes these insights into actionable positioning recommendations (premium, match, or undercut) with supporting evidence, giving reps confidence in their pricing strategy while preserving margin wherever possible.

The agent operates at a strictly Advisory autonomy level -- it never modifies pricing, applies discounts, or alters quote parameters. All competitive pricing adjustments must be executed by the sales representative or deal desk analyst and routed through the standard approval workflow (DEAL_DESK for margin < 35%, SALES_VP for discount > 25%, CFO for margin < 20%). This design ensures that competitive intelligence informs but does not replace human judgment on commercial decisions. The agent surfaces its recommendations as contextual intelligence cards within the quoting interface, providing win probability estimates, competitor-specific discount bands that historically correlate with wins, feature differentiation talking points, and warnings when a proposed price point falls into a historically low-win-rate zone for the given competitive scenario. Over time, the agent's recommendation accuracy improves as it incorporates feedback from actual deal outcomes via the CRM deal pipeline.

---

## Key Skills / Capabilities

- **Competitive Landscape Analysis**: Queries CRM deal records via `GET /api/v1/deals/search` with competitor-related filters and `GET /api/v1/deals/pipeline` to build a comprehensive competitive landscape for the customer's segment and region. Identifies the top competitors encountered in similar deals (same segment, region, product category), their frequency of appearance, and overall market positioning. Presents a competitive landscape summary that contextualizes the current deal within broader market dynamics.

- **Win Rate by Competitor**: Analyzes historical deal outcomes via `GET /api/v1/deals` to compute win/loss rates segmented by competitor, customer segment (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC), region (NORTH_AMERICA, EMEA, APAC, LATAM), product category, and deal size band. Identifies statistically significant patterns such as "win rate against Competitor X drops from 62% to 28% when discount is below 15% in the ENTERPRISE segment." Presents these patterns as data-backed pricing guidance rather than prescriptive rules.

- **Positioning Recommendation**: Synthesizes competitive intelligence into a clear positioning recommendation for each deal: **Premium** (price above market, emphasize differentiation -- recommended when feature advantage is strong and win rate at premium pricing exceeds 50%), **Match** (price at competitive parity -- recommended when feature sets are comparable and the deal is must-win), or **Undercut** (price below competitor -- recommended only when competitive displacement is the primary objective and the margin floor allows it). Each recommendation includes confidence level, supporting data points, and the projected win probability at the recommended price point.

- **Feature Advantage Identification**: Cross-references the products on the current quote against known competitive product capabilities (maintained in CRM deal notes and competitive intelligence records) to identify and articulate feature differentiators. Highlights features where the organization's offering is superior (justifying premium positioning), at parity (supporting match positioning), or inferior (requiring value-add compensations such as enhanced support or professional services). Provides rep-facing talking points for each differentiator.

- **Competitive Discount Strategy**: Analyzes the discount levels of historically won and lost deals against each competitor to identify the optimal discount band -- the range where win rate is maximized while margin erosion is minimized. Compares the current quote's discount level against this optimal band and flags when the proposed discount is significantly above the band (unnecessary margin sacrifice) or below the band (risk of loss). References the customer's segment-specific purchasing behavior from `GET /api/v1/pricing-customers/{id}` and purchase history from `GET /api/v1/erp/purchase-history/{customerExternalId}` to further refine the recommendation.

- **Deal Outcome Feedback Loop**: When deals reach terminal states (WON, LOST) in the CRM via `GET /api/v1/deals/{id}`, the agent ingests the outcome data to continuously refine its competitive models. Tracks whether its positioning recommendations were followed and whether the deal outcome aligned with predictions. Publishes periodic accuracy reports and adjusts confidence calibration accordingly.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Deal ID                  | String (UUID)     | CRM deal record linked to the current quote      |
| Customer ID              | String (UUID)     | Quote header / CRM context                       |
| Customer Segment         | String (Enum)     | From `GET /api/v1/pricing-customers/{id}` (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) |
| Customer Region          | String (Enum)     | From customer profile (NORTH_AMERICA, EMEA, APAC, LATAM) |
| Customer Industry        | String            | From customer profile                            |
| Competitor Name(s)       | String array      | From CRM deal record competitor field            |
| Product IDs on Quote     | String array (UUID) | From quote line items                           |
| Current Discount Level   | Decimal (%)       | From current pricing waterfall state             |
| Current Margin           | Decimal (%)       | Computed from pricing and ERP cost data          |
| Deal Size (TCV)          | Decimal           | From quote financial fields                      |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Competitive Landscape Summary | JSON object (topCompetitors[]{name, frequency, avgWinRate}, segmentContext, regionContext) | Advisory only; no action required |
| Positioning Recommendation    | JSON object (recommendation: PREMIUM/MATCH/UNDERCUT, confidence, winProbability, supportingData[], riskFactors[]) | Advisory only; rep decides whether to follow recommendation |
| Optimal Discount Band         | JSON object (competitor, minDiscount, maxDiscount, sweetSpotDiscount, historicalWinRate, sampleSize) | Advisory only; any discount adjustment requires rep action and approval workflow |
| Feature Differentiation Brief | JSON array of objects (featureArea, ourCapability, competitorCapability, advantage: SUPERIOR/PARITY/INFERIOR, talkingPoint) | Advisory only; used for sales enablement |
| Win Probability Estimate      | JSON object (currentProbability, atPremiumPrice, atMatchPrice, atUndercutPrice, keyDrivers[]) | Advisory only; displayed as contextual intelligence |
| Pricing Risk Alert            | JSON object (alertType, message, currentDiscount, historicalLossRateAtDiscount, recommendedAction) | Advisory alert when proposed pricing falls in high-loss-rate zone; no automatic action |

---

## Typical Workflow

1. **Detect Competitive Context**: When a sales rep opens a quote linked to a CRM deal, the agent retrieves the deal record via `GET /api/v1/deals/{id}` to identify named competitors, deal stage, and any recorded competitive notes. If no competitors are explicitly named, the agent infers likely competitors based on the customer's segment, region, and industry using historical deal pattern analysis.

2. **Build Competitive Dataset**: Agent queries `GET /api/v1/deals/search` with filters matching the current deal's characteristics (same segment, region, similar deal size band, same competitors) to assemble a historical dataset of comparable deals. Retrieves outcome data (WON/LOST), discount levels, product mix, and deal duration for each comparable deal.

3. **Compute Win/Loss Analytics**: Agent analyzes the historical dataset to compute win rates segmented by competitor, discount band, customer segment, and deal size. Identifies statistically significant patterns and thresholds -- for example, the discount level at which win probability inflects upward, or the price premium ceiling above which win rate drops below acceptable levels.

4. **Assess Feature Differentiation**: Agent cross-references the products on the current quote against competitive intelligence data from CRM deal notes and historical competitive displacement records. Identifies specific feature areas where the organization holds an advantage, is at parity, or faces a gap. Produces feature-level talking points for sales enablement.

5. **Generate Positioning Recommendation**: Synthesizes the win/loss analytics, feature assessment, and customer context into a positioning recommendation (PREMIUM, MATCH, or UNDERCUT). Computes win probability at each positioning level. Factors in the customer's churn score from `GET /api/v1/pricing-customers/{id}` -- high churn-risk renewals may warrant more aggressive competitive positioning.

6. **Evaluate Current Pricing Against Optimal Band**: Compares the current quote's discount level and margin against the historically optimal discount band for the identified competitors. If the current pricing falls outside the optimal band, generates a pricing risk alert explaining the deviation and its likely impact on win probability.

7. **Present Intelligence to Rep**: Delivers the complete competitive intelligence package -- landscape summary, positioning recommendation, optimal discount band, feature differentiation brief, win probability estimates, and any pricing risk alerts -- as contextual cards within the quoting interface. All outputs are purely advisory; no pricing changes are made automatically.

8. **Capture Outcome for Feedback**: When the deal eventually reaches a terminal state (WON or LOST) as reported via `GET /api/v1/deals/{id}`, the agent records whether its recommendation was followed, the final pricing, and the outcome. Uses this feedback to recalibrate confidence scores and refine the competitive models for future deals.

---

## Edge Cases & Exceptions

- **No Historical Data for Competitor**: When a competitor is named that has fewer than five historical deals in the dataset, the agent explicitly flags the low sample size and reduces confidence in its recommendation. Falls back to segment-level win/loss averages (ignoring competitor specificity) and recommends that the rep consult deal desk for qualitative competitive intelligence.

- **Multiple Competitors in Single Deal**: When a deal involves three or more competitors, the agent computes win rates against each individual competitor and against the specific competitor combination. Presents a multi-competitor strategy that accounts for the dynamics of a competitive bake-off, where the primary risk may not be any single competitor but the customer's decision fatigue leading to no-decision outcomes.

- **Competitor Not Named in Deal Record**: When the CRM deal lacks competitor information, the agent infers likely competitors based on the customer's segment, region, industry, and the products being quoted. Uses k-nearest-neighbor analysis on historical deals to identify the most probable competitive scenario. Presents these as inferred competitors with a confidence indicator and prompts the rep to confirm or correct.

- **Stale Competitive Data**: When the most recent comparable deal in the historical dataset is older than 6 months, the agent flags that competitive dynamics may have shifted (new competitor entrants, pricing changes, product launches). Reduces recommendation confidence and suggests the rep verify current competitive positioning with recent market intelligence from the sales enablement team.

- **Recommendation Conflicts with Margin Floor**: When the competitive analysis suggests an UNDERCUT positioning that would require a discount pushing margin below 20% (CFO approval threshold), the agent explicitly warns that the recommended positioning would trigger the highest approval tier. Presents alternative strategies such as value-added bundling or enhanced service terms that may achieve competitive parity without breaching margin floors.

- **Customer with No Prior Competitive Deals**: For net-new customers with no purchase history retrieved from `GET /api/v1/erp/purchase-history/{customerExternalId}`, the agent relies entirely on segment and region-level competitive patterns. Recommends the rep invest in discovery to identify the specific competitive situation before committing to a pricing strategy, and defaults to MATCH positioning with moderate confidence.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Positioning recommendation accuracy (predicted vs. actual outcome) | >= 65% |
| Average win rate improvement on deals using competitive intelligence | >= 5 percentage points |
| Rep adoption rate (deals where competitive intelligence is viewed) | >= 70% |
| Unnecessary deep-discount prevention rate (deals where rep followed premium/match recommendation) | >= 40% |
| Competitive intelligence delivery latency (p95) | < 3 seconds |
