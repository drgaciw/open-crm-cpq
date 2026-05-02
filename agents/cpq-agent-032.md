# Agent PRD: cpq-agent-032

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-032                                  |
| **Name**          | Currency & FX Management Agent                 |
| **Group**         | Integration & Operations                       |
| **Primary Service** | pricing-service (8082), quote-service (8084)  |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent)   |

---

## Purpose

The Currency & FX Management Agent is responsible for all multi-currency operations throughout the quote lifecycle, ensuring that every price, discount, and total on a quote is expressed in the correct currency with current exchange rates. Enterprise B2B transactions routinely span multiple currencies -- a customer headquartered in Frankfurt may require EUR-denominated quotes while the seller's list prices are maintained in USD and ERP cost records are stored in GBP. This agent bridges those currency boundaries by retrieving real-time FX rates from the ERP integration layer via `GET /api/v1/erp/fx-rates/{baseCurrency}/{targetCurrency}`, selecting the appropriate regional price book whose native currency matches the customer's preferred currency, and converting amounts when the source currency of a cost or price record differs from the quote's target currency. It feeds converted prices into the pricing waterfall so downstream agents (discount, margin, approval) operate on consistent, currency-normalized figures.

The agent operates at an Automated with constraints autonomy level, meaning it can autonomously perform FX lookups, select regional price books, and convert line-item amounts without human intervention as long as the FX rate is fresh (within the configured staleness threshold, default 4 hours). When an FX rate exceeds the staleness window, the agent halts automated conversion, flags the affected line items with a stale-rate warning, and escalates to the sales representative or deal desk for manual rate confirmation before proceeding. The agent also monitors intra-quote currency drift -- situations where a quote's line items reference multiple price books in different currencies -- and enforces single-currency consistency on the quote header by converting all amounts to the quote's designated currency. Additionally, it assesses currency risk exposure for large deals by computing the potential impact of rate fluctuation on margin, flagging deals where a 2% adverse FX movement would reduce gross margin below the configured threshold.

---

## Key Skills / Capabilities

- **FX Rate Retrieval**: Fetches current exchange rates from the ERP integration layer via `GET /api/v1/erp/fx-rates/{baseCurrency}/{targetCurrency}` for any supported currency pair. Caches rates for the configured TTL (default 4 hours) to minimize redundant API calls while ensuring freshness. Supports inverse rate derivation (if USD/EUR is available, EUR/USD is computed automatically) and triangulated cross-rates through a common base currency when direct pairs are unavailable.

- **Currency Conversion**: Applies exchange rates to convert monetary amounts across the quote lifecycle. Converts ERP cost figures retrieved via `GET /api/v1/erp/costs/{productExternalId}` from their native currency to the quote currency for margin calculations. Converts list prices from price book currency to quote currency when a regional price book's currency differs from the customer's preferred currency. Handles rounding according to currency-specific decimal precision rules (e.g., 2 decimals for USD/EUR, 0 for JPY, 3 for BHD).

- **Stale Rate Detection**: Continuously monitors the timestamp of cached FX rates against the configured staleness threshold. When a rate exceeds the TTL, the agent marks all dependent calculations as provisional, attaches a stale-rate warning to each affected line item, and prevents quote submission via `POST /api/v1/quotes/{id}/submit` until the rate is refreshed or manually confirmed. Logs all stale-rate events for operational monitoring.

- **Regional Price Book Currency Selection**: Analyzes the customer's region (NORTH_AMERICA, EMEA, APAC, LATAM) and preferred currency from `GET /api/v1/pricing-customers/{id}` to select the most appropriate price book. Queries available price books via `GET /api/v1/price-books` filtered by region and currency, preferring the default price book (`isDefault = true`) whose `effectiveDate` is current and `expirationDate` has not passed. Falls back to the global default price book with currency conversion when no region-specific book exists.

- **Currency Risk Assessment**: For deals exceeding a configurable threshold (default $100,000 TCV equivalent), computes the potential margin impact of adverse FX movements at 1%, 2%, and 5% rate shifts. Compares the stress-tested margin against the approval workflow thresholds (DEAL_DESK at margin < 35%, SALES_VP at discount > 25%, CFO at margin < 20%) and proactively warns if a plausible FX movement would push the deal into a higher approval tier. Recommends FX hedging or rate-lock clauses for high-exposure deals.

- **Quote Currency Consistency Enforcement**: Validates that all line items on a quote resolve to a single presentation currency matching the quote header's currency field. When line items originate from price books in different currencies (e.g., a multi-region bundle), the agent converts all amounts to the quote currency before computing Quote.subtotal, Quote.totalDiscount, Quote.totalTax, and Quote.totalAmount. Ensures ARR, TCV, and ACV calculations on the quote header are expressed in the quote's designated currency.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Quote ID                 | String (UUID)     | Quote creation or update context                 |
| Customer ID              | String (UUID)     | Quote header / CRM context                       |
| Customer Region          | String (Enum)     | Derived from `GET /api/v1/pricing-customers/{id}` (NORTH_AMERICA, EMEA, APAC, LATAM) |
| Customer Preferred Currency | String (ISO 4217) | Derived from customer profile or quote header  |
| Source Currency           | String (ISO 4217) | Currency of the price book or ERP cost record   |
| Target Currency           | String (ISO 4217) | Quote's designated presentation currency         |
| Monetary Amount           | Decimal           | List price, cost, discount amount, or total to convert |
| FX Staleness Threshold    | Integer (minutes) | Configuration parameter, default 240 minutes     |
| Currency Risk Threshold   | Decimal           | Minimum TCV (in USD equivalent) triggering risk assessment, default 100000 |
| Product External ID       | String            | Used for ERP cost currency lookup                |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Converted Monetary Amounts    | JSON object (originalAmount, originalCurrency, convertedAmount, targetCurrency, rateUsed, rateTimestamp) | Automatic when rate is fresh; requires rep confirmation when rate is stale |
| FX Rate Cache Status          | JSON object (currencyPair, rate, fetchedAt, expiresAt, isStale) | Informational; stale status blocks quote submission until resolved |
| Regional Price Book Selection | JSON object (priceBookId, name, region, currency, effectiveDate, isDefault) | Automatic selection; rep can override to a different price book |
| Stale Rate Warning            | JSON object (currencyPair, lastRate, lastFetchedAt, staleSinceMinutes, affectedLineItems[]) | Blocks quote submission; rep or deal desk must confirm or refresh rate |
| Currency Risk Assessment      | JSON object (quoteTcv, quoteCurrency, scenarios[]{fxShiftPercent, impactAmount, projectedMargin, approvalTierTriggered}) | Advisory for deals above risk threshold; no automatic action taken |
| Currency Consistency Report   | JSON object (quoteCurrency, lineItems[]{lineItemId, sourceCurrency, convertedAmount}, allConsistent: boolean) | Automatic enforcement; inconsistencies are auto-converted with logged rationale |

---

## Typical Workflow

1. **Receive Quote Context**: When a quote is created or a line item is added, the agent receives the quote ID, customer ID, and target currency. Retrieves the customer profile via `GET /api/v1/pricing-customers/{id}` to determine region, segment, and preferred currency. If no preferred currency is set, defaults to the regional standard (USD for NORTH_AMERICA, EUR for EMEA, JPY/SGD for APAC, BRL for LATAM).

2. **Select Regional Price Book**: Queries `GET /api/v1/price-books` filtered by the customer's region and preferred currency. Selects the default price book whose effective date range is current. Retrieves price book entries via `GET /api/v1/price-books/{id}/entries` for the products on the quote. If no regional price book exists, falls back to the global default and flags that currency conversion will be applied.

3. **Fetch FX Rates**: For each unique currency pair required (price book currency to quote currency, ERP cost currency to quote currency), calls `GET /api/v1/erp/fx-rates/{baseCurrency}/{targetCurrency}`. Caches the returned rates with their timestamps. If a required rate is unavailable, attempts triangulation through USD as a common base.

4. **Validate Rate Freshness**: Checks each cached rate's timestamp against the staleness threshold. Fresh rates proceed automatically. Stale rates trigger a warning attached to all affected line items and the quote header, pausing automated conversion until the rep confirms the stale rate is acceptable or a fresh rate is retrieved.

5. **Convert Line Item Amounts**: For each line item, converts the list price from the price book's currency to the quote currency using the applicable FX rate. Similarly converts ERP cost figures retrieved via `GET /api/v1/erp/costs/{productExternalId}` for margin calculations. Applies currency-specific rounding rules and records the conversion metadata (source amount, rate used, timestamp) on each line item.

6. **Enforce Quote Currency Consistency**: After all line items are converted, validates that Quote.subtotal, Quote.totalDiscount, Quote.totalTax, and Quote.totalAmount are computed in a single consistent currency. Recalculates ARR, TCV, and ACV in the quote currency. Updates the quote via `PUT /api/v1/quotes/{id}` with the currency-consistent financial summary.

7. **Assess Currency Risk**: For quotes where TCV (converted to USD equivalent) exceeds the risk threshold, computes margin impact at 1%, 2%, and 5% adverse FX shifts. If any scenario pushes projected margin below 35% (DEAL_DESK threshold) or 20% (CFO threshold), attaches a currency risk advisory to the quote recommending rate-lock contract clauses or hedging consideration.

8. **Monitor Through Quote Lifecycle**: Continues monitoring FX rates for open quotes in DRAFT and IN_REVIEW states. If a rate changes by more than 1% since the last conversion, notifies the quote owner and recalculates affected amounts. Upon quote state transition to APPROVED, locks the FX rates used for the final pricing snapshot in the pricing audit log.

---

## Edge Cases & Exceptions

- **FX Rate Service Unavailable**: When `GET /api/v1/erp/fx-rates/{baseCurrency}/{targetCurrency}` returns an error or times out, the agent checks for the most recent cached rate regardless of staleness. If a cached rate exists, it is used with a prominent stale-rate warning. If no cached rate exists for the pair, the agent blocks currency conversion for affected line items, marks the quote as "FX Rate Pending," and notifies the rep to either wait for service recovery or manually enter an override rate.

- **Exotic Currency Pair Without Direct Rate**: When a direct FX rate is unavailable for a currency pair (e.g., BRL to SGD), the agent attempts triangulated conversion through USD (BRL -> USD -> SGD) using two separate rate lookups. If triangulation also fails, the agent presents the rep with available currency options and recommends switching the quote currency to one with available rates.

- **Intra-Quote Multi-Currency Conflict**: When a quote contains line items sourced from price books in three or more different currencies (e.g., a global enterprise deal with products from NA, EMEA, and APAC price books), the agent converts all amounts to the quote header currency but flags the deal as high FX complexity. Recommends splitting into regional sub-quotes if the currency risk assessment exceeds tolerance thresholds.

- **FX Rate Significant Movement During Approval**: If the FX rate changes by more than 2% between quote creation and approval, the agent triggers a re-pricing notification. The quote remains in its current state, but a warning is attached indicating the financial terms may have materially shifted. The approver sees both original-rate and current-rate calculations side by side.

- **Zero-Decimal Currency Handling**: Currencies like JPY, KRW, and VND that use zero decimal places require special rounding logic. The agent applies currency-specific precision from the ISO 4217 standard, ensuring that converted amounts do not introduce fractional units that would be invalid in the target currency's financial system.

- **Quote Currency Change After Line Items Exist**: When a rep changes the quote's target currency after line items have already been priced, the agent re-converts all existing line items to the new currency, re-validates FX rate freshness for the new currency pairs, and recalculates all quote-level financial fields. Presents a before/after summary for rep confirmation before committing the currency change.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| FX rate retrieval success rate       | >= 99.5%        |
| Stale rate detection accuracy        | 100%            |
| Currency conversion precision compliance | 100% (per ISO 4217) |
| Average FX conversion latency (p95)  | < 500ms         |
| Currency risk assessment coverage (deals above threshold) | 100% |
