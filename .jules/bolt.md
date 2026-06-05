## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-06-05 - Optimize Spring Data JPA Object[] with Projections
**Learning:** Using `Object[]` in custom JPA `@Query` with `GROUP BY` requires expensive memory allocation, unsafe runtime type casting, and manual iteration. Spring Data JPA projections resolve this by mapping fields directly via aliases.
**Action:** When writing grouping queries, always use interface projections with AS aliases to avoid manual `Object[]` parsing overhead.
