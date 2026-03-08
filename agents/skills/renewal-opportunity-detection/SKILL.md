---
name: renewal-opportunity-detection
description: Scans for upcoming renewal windows, assesses churn risk, and recommends renewal strategies with uplift pricing.
---

# Renewal Opportunity Detection

Use this skill when you need to identify upcoming contract renewals, assess the risk of churn, and recommend a renewal strategy including uplift pricing.

## Scope

- Scan active contracts to identify renewals within a configurable time window
- Assess churn risk using usage, engagement, and support metrics
- Recommend renewal strategies (standard uplift, retention offer, early renewal incentive)
- Apply renewal-uplift pricing rules via renewal-uplift.drl (salience 530)

## Inputs

- Customer external ID
- Renewal window (days ahead to scan, default 90)
- Usage and engagement metrics (if available)
- Support ticket history (if available)

## Workflow

1. Retrieve active contracts for the customer and identify those with renewal dates within the scan window.
2. For each contract approaching renewal, gather usage, engagement, and support data.
3. Calculate a churn score based on available signals.
4. Classify risk: HIGH (churnScore > 70), MEDIUM (40-70), LOW (< 40).
5. Recommend a renewal strategy based on risk classification and contract value.
6. Apply the renewal-uplift.drl rule (salience 530) to calculate renewal pricing.
7. Generate a renewal opportunity summary with pricing, risk, and strategy.

## Minimum Checks

- Contract data is retrieved and renewal dates are validated
- Churn score is calculated and risk classification is applied
- Renewal pricing complies with the renewal-uplift.drl rule
- HIGH risk renewals are flagged for immediate sales attention
- Renewal strategy recommendation is included in the output

## Useful Commands

```bash
# Retrieve contracts for a customer
curl -X GET "${CPQ_BASE_URL}/api/v1/erp/contracts/{customerExternalId}" \
  -H "Authorization: Bearer ${TOKEN}"

# Retrieve quote history for a customer
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes?customerExternalId={id}" \
  -H "Authorization: Bearer ${TOKEN}"
```
