---
status: done
id: c6-pricebook-n1
---

# c6-pricebook-n1

## Summary

Fixed N+1 in `PriceBookInternalResolutionService.resolveBatch` (GitHub #456 family). Per-product `findByPriceBookIdAndProductExternalIdAndDeletedFalse` calls inside the loop are replaced with a single `IN` batch query and an in-memory map lookup.

## Files

| Path | Change |
|------|--------|
| `backend/services/pricebook-service/.../PriceBookEntryRepository.java` | Add `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` |
| `backend/services/pricebook-service/.../PriceBookInternalResolutionService.java` | Batch-fetch entries once; O(1) map lookups per product id |
| `backend/services/pricebook-service/.../PriceBookInternalResolutionServiceTest.java` | Assert single batch call + never per-id finder; multi-product match/miss |
| `docs/forge/impl/c6-pricebook-n1.md` | This status note |

## Acceptance

- [x] `resolveBatch` uses one entry query for N product external ids (not N queries)
- [x] Unmatched products still return `entryMatched=false` with book currency
- [x] Unit test proves batching via `times(1)` on IN finder and `never()` on per-id finder
- [x] Existing effective/expired price-book tests still pass
- [x] No activities API, S2B-11, S2C-12, or CORS wildcard changes

## Blast radius (grep; GitNexus MCP offline)

- `PriceBookInternalResolutionService` — sole production caller: `InternalPriceBookController`
- `findByPriceBookIdAndProductExternalIdAndDeletedFalse` retained for `PriceBookService` create/update uniqueness checks
- Risk: **LOW**

## Verify

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/21.0.2
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
./mvnw -pl services/pricebook-service -am test \
  -Dtest=PriceBookInternalResolutionServiceTest,PriceBookServiceTest \
  -DfailIfNoTests=false
```

## Notes

- Requires JDK 21 locally (JDK 27 hits known `EndPosTable` compile failure in this environment).
- Prior Jules/Bolt PRs (#400/#405/#425/#445/#456) left `.orig` junk; this worktree ships a clean diff only.
