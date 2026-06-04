## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-06-04 - ConfigurationRuleService Optimization
**Learning:** O(N*M) loops over maps built from a batched result set can be optimized to O(N) by iterating over the batched result set directly when the map values only serve to be searched against an already-existing Set.
**Action:** When validating a collection against rules fetched in batch, iterate the rules directly instead of re-grouping them by source ID and looping.
