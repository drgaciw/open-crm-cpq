---
name: currency-fx-management
description: Retrieves FX rates, performs currency conversions, and detects stale rates with a 4-hour cache TTL.
---

# Currency FX Management

Use this skill when you need to retrieve foreign exchange rates, convert pricing between currencies, or validate that FX rates are current and not stale.

## Scope

- Retrieve current FX rates for currency pairs
- Convert quote pricing and line items between currencies
- Detect and flag stale FX rates beyond the 4-hour cache TTL
- Support multi-currency quotes with consistent rate application

## Inputs

- Base currency code (e.g., USD)
- Target currency code (e.g., EUR)
- Amount to convert (optional)
- Quote ID (for full quote conversion)

## Workflow

1. Determine the required currency pair from the quote or explicit parameters.
2. Retrieve the current FX rate for the base/target currency pair.
3. Check the rate timestamp against the 4-hour cache TTL to detect staleness.
4. If the rate is stale, flag a warning and attempt to fetch a fresh rate.
5. Apply the FX rate to convert amounts, ensuring consistent rounding rules.
6. For full quote conversions, apply the same rate across all line items to maintain consistency.

## Minimum Checks

- FX rate is retrieved successfully for the requested currency pair
- Rate timestamp is within the 4-hour cache TTL
- Stale rates are flagged and not silently used for pricing
- Conversion arithmetic uses consistent rounding (2 decimal places for most currencies)
- Multi-line quotes use a single rate snapshot for consistency

## Useful Commands

```bash
# Retrieve FX rate for a currency pair
curl -X GET "${CPQ_BASE_URL}/api/v1/erp/fx-rates/{baseCurrency}/{targetCurrency}" \
  -H "Authorization: Bearer ${TOKEN}"
```
