## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-07-05 - Resolve N+1 Query in PriceBookInternalResolutionService
**Learning:** Resolving N+1 database queries when batch processing requests requires fetching all elements at once with an IN clause, mapping results to avoid looping via multiple `findBy...` DB lookups.
**Action:** Always inspect loops containing `Repository.findBy...` methods during performance analysis. Replace per-iteration queries with a single batch `IN` query and in-memory `Map` lookups. Update corresponding tests to expect collection types instead of `Optional`.
