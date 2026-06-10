## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-06-10 - Resolve N+1 Query in PriceBookInternalResolutionService
**Learning:** The `PriceBookInternalResolutionService.resolveBatch` method previously queried `priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse` in a loop for each product external ID, causing an N+1 query performance bottleneck. Batch fetching with `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` reduces database calls to O(1) and speeds up resolution.
**Action:** Always batch query resolution lists where a loop makes repetitive repository fetch calls, and store results in a HashMap to maintain O(1) loop lookups within the application.
