---
status: done
id: m3-be-quote-clone-lines
wave: M
pr: M3
---

# m3-be-quote-clone-lines

## Summary

Wave M3: `QuoteService.clone` now copies non-deleted line items as a commercial snapshot onto a new DRAFT quote. Totals roll up the same way as `QuoteLineItemService.recalculateQuoteTotals` (sum `extendedAmount` → subtotal, sum `discountAmount` → totalDiscount, `totalAmount = subtotal - totalDiscount`). Does **not** call `Quote.recalculateTotals()` (10% tax stub). Skips line `pricingExplanationCode` / `pricingWaterfallJson` and header waterfall / ARR / TCV / ACV / acceptedAt / approval*. DTO includes copied `lineItems`. No FE (M1 owns clone copy). No Kafka/Redis on clone.

## Acceptance

| Criterion | Result |
|-----------|--------|
| Non-deleted lines via `findByQuoteIdAndDeletedFalseOrderBySortOrderAsc` | Yes |
| New entities (no id/uuid/audit copied) | Yes — builder omit; persist assigns |
| Commercial snapshot fields copied | Yes — product*, quantity, UoM, list/unit/net/extended, discount rate/amount, billingFrequency, sortOrder, notes, metadata |
| Skip line waterfall / explanation | Yes |
| Header waterfall / ARR / TCV / ACV / acceptedAt / approval* omitted | Yes — builder defaults DRAFT + NONE |
| Totals like `recalculateQuoteTotals`, not `Quote.recalculateTotals()` | Yes |
| `toDto` includes `lineItems` | Yes — attach collection before map |
| `QuoteServiceCloneTest` green | Yes — 5 tests |
| No FE / dual-read flip / product-validate delete / WhatsApp #430 | Confirmed |

## Files

| Path | Change |
|------|--------|
| `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java` | `clone` copies lines + rollup + attach for DTO |
| `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteServiceCloneTest.java` | Mockito: N lines, empty, deleted omitted, totals, waterfall null |
| `docs/forge/impl/m3-be-quote-clone-lines.md` | This note |

## Behavior notes

- Clone stays **DRAFT** with new `quoteNumber` and name + `" (Copy)"`.
- Soft-deleted source lines never appear because the repository query filters `deleted = false`.
- Empty source → empty `lineItems`, zero totals; `saveAll` not called.
- Calculate is still required on the clone for waterfall / ARR (not copied by design).
- No outbox / Redis / Kafka side effects on clone.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| BE `QuoteService.clone` (`--file` QuoteService.java) | **LOW** (exact, 1) | → `QuoteController.cloneQuote`; process `CloneQuote` |
| Index | 1 commit behind HEAD (stale WARN) | Treat as current; UNKNOWN ≠ unused — N/A (exact LOW) |

`rg` confirm: Java callers of `clone` / `cloneQuote` are controller + service only. No HIGH/CRITICAL/UNKNOWN on this symbol.

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`) — Worktree: `.worktrees/forge-m3` (`feat/forge-m3-be-quote-clone-lines`).

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend
./mvnw -pl services/quote-service -am test -Dtest=QuoteServiceCloneTest -Dsurefire.failIfNoSpecifiedTests=false
```

Result: Tests run: 5, Failures: 0, Errors: 0 (JDK 21.0.2).
