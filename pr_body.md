💡 What: Updated `PriceBookInternalResolutionService.resolveBatch` to batch fetch entries using a new `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` method in `PriceBookEntryRepository`. Results are loaded into a Map for O(1) in-memory lookups instead of sequential DB calls.
🎯 Why: The original code executed a database query to find a PriceBookEntry for *each* product external ID within a for loop, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
📊 Impact: Reduces database queries from O(N) to O(1) per batch resolution request.
🔬 Measurement: Run the test suite via `./mvnw test -pl services/pricebook-service` to confirm functionality remains unchanged.
