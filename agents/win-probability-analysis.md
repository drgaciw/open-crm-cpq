# Agent PRD: cpq-agent-026

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-026                                  |
| **Name**          | Win-Probability Analysis Agent                 |
| **Group**         | Analytics & Intelligence                       |
| **Primary Service** | crm-service (8081), quote-service (8080), pricing-service (8082) |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | None                                           |

---

## Purpose

The Win-Probability Analysis Agent predicts the likelihood of closing a deal by synthesizing historical win/loss data, active quote characteristics, competitive intelligence, and customer behavioral signals. It ingests deal metadata from the CRM service -- including current stage, time-in-stage, deal type, and pipeline velocity -- and combines this with quote-level indicators such as discount depth, product mix complexity, ARR/TCV ratios, and subscription term length. Customer segment, industry vertical, churn score, and prior engagement history further refine the prediction. The model outputs a probability score (0--100) along with the ranked factors contributing most to the prediction, giving sales leadership and individual reps a transparent, explainable forecast rather than a black-box number.

The agent operates at a purely Advisory autonomy level: it never blocks deal progression, gates quote submission, or modifies any records. Its outputs are informational overlays displayed on the deal and quote detail views. By surfacing actionable insights -- such as "deals at NEGOTIATION stage with discounts above 25% historically close at 18% lower rates" or "comparable ENTERPRISE deals in this industry closed 12 days faster when a multi-year term was offered" -- the agent empowers reps to make data-driven adjustments to their strategy. It also powers aggregate pipeline analytics by providing weighted pipeline values based on predicted probabilities, enabling more accurate revenue forecasting for sales management.

---

## Key Skills / Capabilities

- **Historical Win/Loss Analysis**: Queries completed deals via `GET /api/v1/deals/search` with status filters for WON and LOST outcomes. Aggregates historical data by customer segment, deal type (NEW_BUSINESS, EXPANSION, RENEWAL, CHURN_WIN), product family, discount range, and deal duration to build baseline win-rate distributions. Identifies statistically significant patterns that distinguish won deals from lost deals across each dimension.

- **Feature-Based Prediction**: Constructs a multi-variable prediction model using features extracted from the active deal (`GET /api/v1/deals/{id}`), associated quote (`GET /api/v1/quotes/{id}` and `GET /api/v1/quotes/{quoteId}/line-items`), customer profile (`GET /api/v1/pricing-customers/{id}`), and pricing waterfall (`pricingWaterfallJson` from the quote entity). Key features include: deal stage ordinal, days-in-current-stage, total discount percentage, number of line items, ARR magnitude, customer segment tier, churn score, and ratio of standard to custom products.

- **Comparable Deal Matching**: Identifies the closest historical deals by computing similarity scores across customer segment, deal type, product mix, ARR range, and industry vertical. Retrieves candidate deals via `GET /api/v1/deals/search` with parameterized filters and ranks them by composite similarity. Presents the top 5 comparable deals with their outcomes and key differentiators to give the rep concrete reference points.

- **Action Recommendation**: Translates predictive insights into specific, actionable suggestions tied to controllable deal levers. Examples include: adjusting discount depth to stay within the historically optimal range, adding a multi-year commitment incentive, bundling complementary products to increase perceived value, or escalating executive sponsorship engagement. Each recommendation includes the estimated impact on win probability.

- **Timing Optimization**: Analyzes historical time-in-stage distributions for comparable deals to determine optimal pacing. Flags deals that have exceeded the median time-in-stage for their segment and deal type, identifies stage transitions that historically correlate with deal acceleration, and suggests optimal timing windows for quote delivery. Uses `GET /api/v1/deals/pipeline` to benchmark against active pipeline velocity.

- **Pipeline Forecast Contribution**: Computes probability-weighted pipeline values by applying the predicted win rate to each deal's expected ARR. Aggregates across the full pipeline retrieved from `GET /api/v1/deals/pipeline` to produce a weighted forecast rollup by segment, region, deal type, and expected close quarter. Provides confidence intervals alongside point estimates to communicate forecast uncertainty.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Deal ID                  | String (UUID)     | CRM deal detail view / pipeline dashboard       |
| Quote ID                 | String (UUID)     | Quote detail view linked to deal                |
| Customer External ID     | String            | Derived from deal or quote record               |
| Deal Stage               | String (Enum)     | From `GET /api/v1/deals/{id}` -- PROSPECTING through LOST |
| Deal Type                | String (Enum)     | From deal record -- NEW_BUSINESS, EXPANSION, RENEWAL, CHURN_WIN |
| Customer Segment         | String (Enum)     | From `GET /api/v1/pricing-customers/{id}` -- STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC |
| Customer Churn Score     | Integer (0-100)   | From customer profile                           |
| Historical Lookback Period | Integer (months) | Configuration parameter, default 24 months      |
| Comparable Deal Count    | Integer           | Max number of comparable deals to return, default 5 |
| Pipeline Scope           | String (Enum)     | Optional -- filter pipeline analysis by region/segment |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Win Probability Score         | JSON object (probability: 0-100, confidence: HIGH/MEDIUM/LOW, modelVersion) | Informational only; no blocking checkpoint. Displayed as badge on deal/quote views |
| Contributing Factor Breakdown | JSON array of objects (factor, weight, direction, description) | Advisory overlay; rep reviews factors to understand prediction drivers |
| Comparable Deal List          | JSON array of objects (dealId, similarity, outcome, keyMetrics, differentiators) | Informational reference; rep may optionally drill into comparable deals |
| Action Recommendations        | JSON array of objects (action, estimatedImpact, rationale, priority) | Advisory only; rep decides whether to implement any suggested actions |
| Pipeline Forecast Rollup      | JSON object (weightedARR, unweightedARR, dealCount, confidenceInterval, bySegment, byDealType) | Consumed by sales management dashboards; no action checkpoint |

---

## Typical Workflow

1. **Receive Analysis Request**: A sales rep opens a deal or quote detail view, triggering a win-probability analysis. The agent receives the deal ID and optionally the quote ID from the UI context. Alternatively, a scheduled batch process triggers analysis across all deals at PROPOSAL stage or later.

2. **Load Deal and Quote Context**: Agent calls `GET /api/v1/deals/{id}` to retrieve deal metadata including stage, type, expected close date, and days-in-current-stage. If a quote ID is provided, calls `GET /api/v1/quotes/{id}` and `GET /api/v1/quotes/{quoteId}/line-items` to retrieve quote-level detail including discount percentages, ARR, TCV, ACV, product mix, and pricing waterfall.

3. **Load Customer Profile**: Agent calls `GET /api/v1/pricing-customers/{id}` using the customer external ID from the deal or quote to retrieve segment, industry, region, churn score, and annual revenue. These customer attributes form critical prediction features.

4. **Retrieve Historical Outcomes**: Agent calls `GET /api/v1/deals/search` with filters for completed deals (WON and LOST) within the configured lookback period. Aggregates outcomes by the feature dimensions that match the current deal's profile to compute baseline win rates.

5. **Compute Comparable Deals**: Agent applies a similarity function across segment, deal type, ARR range, product family, and industry to rank historical deals by relevance. Selects the top N comparable deals and retrieves their full metadata to present as reference cases with their outcomes and distinguishing characteristics.

6. **Generate Prediction**: Agent feeds the assembled feature vector into the prediction model, producing a win probability score (0--100), a confidence level (HIGH/MEDIUM/LOW based on sample size and feature coverage), and a ranked list of contributing factors with their individual weights and directional impact.

7. **Produce Action Recommendations**: Based on the gap between current deal characteristics and the profile of historically won deals, the agent generates specific action recommendations. Each recommendation includes the estimated win-probability improvement if implemented, providing the rep with a prioritized action plan.

8. **Return Results and Log**: Agent returns the complete analysis payload to the requesting UI or dashboard. Logs the prediction, input features, and model version for future model calibration and accuracy tracking. When the deal eventually reaches a terminal state (WON or LOST), the outcome is linked back to the prediction for model feedback.

---

## Edge Cases & Exceptions

- **Insufficient Historical Data**: When fewer than 30 comparable deals exist in the lookback period for the given segment and deal type combination, the agent widens the comparison pool by relaxing segment or deal-type constraints. The confidence level is downgraded to LOW, and the output includes a disclaimer that the prediction is based on limited data. Falls back to organization-wide base rates if segment-specific data is entirely absent.

- **Deal with No Linked Quote**: When a deal exists at PROSPECTING or QUALIFICATION stage without an associated quote, the agent generates a stage-and-segment-only prediction using deal metadata and customer profile. Quote-specific features (discount depth, product mix, ARR) are excluded from the model, and the output notes that prediction accuracy improves once a quote is attached.

- **Customer Profile Missing**: If `GET /api/v1/pricing-customers/{id}` returns 404, the agent proceeds without customer-specific features (segment, churn score, industry). The prediction relies solely on deal-level and quote-level features, and the confidence is reduced to LOW. The agent displays a warning that customer enrichment would improve prediction accuracy.

- **Stale Deal (Excessive Time-in-Stage)**: Deals that have been in the same stage for more than 2x the historical median for their segment trigger a "stale deal" flag. The agent adjusts the win probability downward using a time-decay function and surfaces a specific recommendation to either advance the deal or reassess commitment.

- **Multi-Quote Deals**: When a deal has multiple associated quotes (e.g., competitive alternatives presented to the customer), the agent analyzes each quote independently and produces a composite prediction that accounts for the multi-quote dynamic. Historically, deals with 3+ quote revisions show different win patterns than single-quote deals, and this factor is incorporated.

- **Recently Changed Deal Stage**: When a deal stage was updated within the last 24 hours, the agent flags that the prediction may not yet reflect the stage transition's full impact. It computes both the pre-transition and post-transition probabilities and displays the delta, giving the rep a sense of momentum direction.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Prediction accuracy (Brier score)    | <= 0.15         |
| Forecast weighted pipeline error     | <= 10% vs. actual closed revenue |
| Action recommendation adoption rate  | >= 25%          |
| Comparable deal relevance rating (rep feedback) | >= 4.0 / 5.0 |
| Agent response latency (p95)         | < 3 seconds     |
