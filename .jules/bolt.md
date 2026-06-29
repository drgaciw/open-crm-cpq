## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).

## 2026-06-29 - Resolve N+1 Query in PriceBookInternalResolutionService
**Learning:** Batching DB lookups with `IN` clauses combined with O(1) Map lookups is a highly reliable pattern to eliminate N+1 bottlenecks in Spring Data JPA when looping over lists.
**Action:** When iterating over a collection to fetch associated entities one-by-one, always refactor to pre-fetch via a batch `IN` query and map the results in memory.
