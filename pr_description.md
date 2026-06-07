💡 What:
Replaced the `for` loop in `PriceBookInternalResolutionService.resolveBatch()` that performed a separate `findByPriceBookIdAndProductExternalIdAndDeletedFalse` lookup per `productExternalId` with a single batch `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` fetch. Mapped the batch results to efficiently construct the response in memory. Updated associated unit tests to mock the new repository method.

🎯 Why:
This resolves a classic N+1 query problem, replacing N database calls with exactly 1. As the number of product line items in a quote increases, this loop previously became a significant performance bottleneck due to database connection overhead.

📊 Impact:
Significantly reduces database load and network latency by executing only 1 query for batch resolutions instead of N queries, scaling linearly and improving overall pricing calculation time for quotes with large numbers of line items.

🔬 Measurement:
Run the `pricebook-service` unit tests to ensure that batch resolution still maps matched and unmatched line items properly. Run a trace or check database logs while simulating pricing for quotes with multiple items to verify only a single DB query hits the `price_book_entry` table.
