💡 What: Replaced N+1 queries in `PriceBookInternalResolutionService.resolveBatch` with a single `IN` query. Added `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` to `PriceBookEntryRepository`.

🎯 Why: The previous implementation queried the database individually for each `productExternalId` inside a `for` loop, leading to severe N+1 performance degradation when resolving large batches of product prices.

📊 Impact: Reduces database queries from O(N) to O(1) per batch resolution call, dramatically speeding up quote pricing for quotes with many line items.

🔬 Measurement: Verifiable via existing unit tests (`PriceBookInternalResolutionServiceTest`). Further backend benchmarking could measure throughput difference on quotes with >100 line items.
