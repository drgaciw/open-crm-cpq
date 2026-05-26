## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-05-26 - Resolve N+1 Query in PriceBookInternalResolutionService
**Learning:** Found another N+1 query pattern during batch resolutions where a repository `findById` method was called inside a loop over a list of external IDs. The solution was identical: introducing an `IN` clause repository method (`findByPriceBookIdAndProductExternalIdInAndDeletedFalse`), executing a single batch fetch, and mapping the results to an in-memory Map for O(1) loop lookups.
**Action:** When auditing Spring Data JPA services, actively search for `Repository.findBy` or `Repository.save` calls located inside `for` loops or `stream().map()` chains. These are primary candidates for batch optimization.
