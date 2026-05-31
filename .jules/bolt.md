## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-05-31 - Resolve N+1 Query in PriceBookInternalResolutionService
**Learning:** Resolving multiple product price book entries iteratively creates an N+1 query problem, especially noticeable when dealing with large quotes. Batch fetching these entries using an `IN` query drastically improves performance. Using `Collectors.toMap` with a merge function safely avoids `IllegalStateException` on duplicates.
**Action:** Always batch fetch dependent entities like `PriceBookEntry` when iterating over collections (e.g., list of product IDs in quotes or configuration resolution). Use a fast in-memory map for lookups instead of database calls in loops.
