## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).

## 2026-06-28 - Resolve N+1 Query in PriceBookInternalResolutionService
**Learning:** `resolveBatch` looped over `productExternalIds`, executing a single lookup per ID, causing an N+1 query. While fixing this with a Spring Data JPA `IN` query, I learned that ensuring unit tests continue passing requires matching the mocked `findBy...In` structures tightly to the production code.
**Action:** When identifying per-item queries in loops, replace them with a single bulk query (e.g., `findByExternalIdIn`) and index the results in a `LinkedHashMap` for fast, order-preserving in-memory mapping to resolve N+1 overhead efficiently.
