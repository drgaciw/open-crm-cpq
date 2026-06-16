## 2026-05-25 - Resolve N+1 Query in ProductAttributeService
**What:** Updated `ProductAttributeService.getAttributeValues` to batch fetch product attributes using a new `findByIdInAndDeletedFalse` method in `ProductAttributeRepository`. Used a Map to store attribute names for O(1) in-memory lookup.
**Why:** The original code executed a database query to find the attribute name for *each* attribute value inside a stream map, causing an N+1 query performance bottleneck. Batch fetching resolves this issue by reducing database calls to O(1).
**Impact:** Execution time for fetching 500 attributes (100 iterations) decreased from 4170ms to 45ms.
**Measurement:** ~99% improvement (~92x speedup).
## 2026-06-16 - Resolve O(N^2) Message Lookup in WhatsApp API
**Learning:** In `get_whatsapp_messages()`, finding replied-to and reacted-to messages involved executing an inner O(N) `next()` search within an outer loop over messages, creating an O(N^2) bottleneck for large threads.
**Action:** Pre-compute a `messages_by_id` dictionary for O(1) lookups to reduce complexity to O(N).
