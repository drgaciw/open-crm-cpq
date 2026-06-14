🎯 What:
Updated `PriceBookInternalResolutionService.resolveBatch` to batch fetch product price book entries using a new `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` method in `PriceBookEntryRepository`. Used a `LinkedHashMap` to store entries for O(1) in-memory lookup.

💡 Why:
The original code executed a database query (`findByPriceBookIdAndProductExternalIdAndDeletedFalse`) for *each* `productExternalId` inside a loop, causing a severe N+1 query performance bottleneck when processing large lists of products.

📊 Impact:
Execution time for fetching batch price book entries is significantly reduced. Database calls are minimized from O(N) (one per product ID) to O(1) (a single batch fetch), drastically lowering network latency and database overhead.

🔬 Measurement:
Run the unit test `PriceBookInternalResolutionServiceTest` or start the application and test batch resolution with multiple `productExternalId`s. You can verify through logs or query performance analysis that only one query is dispatched to the database.
