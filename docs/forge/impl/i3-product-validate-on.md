---
status: done
id: i3-product-validate-on
---

# i3-product-validate-on

## Summary

Wave I3 / Bolt #412 salvage: `ConfigurationRuleService.validateConfiguration`
iterates the batched `allRules` list once (flat O(rules)) instead of
`groupingBy` source product + nested loops over selected IDs. REQUIRES /
EXCLUDES membership checks and default message text (via `sourceProductId`)
unchanged. Endpoint remains `ApiResponse<List<String>>`. No gateway /
configuration-engine edits.

## Changes

| Area | Detail |
|------|--------|
| `ConfigurationRuleService.validateConfiguration` | Remove `requiresRulesByProduct` / `excludesRulesByProduct`; single pass over `allRules`; default strings use `rule.getSourceProductId()` |
| Imports | Drop unused `Map` / `Collectors` |
| Tests | Existing F4 parity fixtures (`ConfigurationValidateParityFixtureTest`) — no contract change |

## Acceptance

- [x] Flat rules iteration (no groupingBy + nested product loop)
- [x] REQUIRES / EXCLUDES semantics preserved
- [x] Default messages use `sourceProductId` (Jules #412)
- [x] Endpoint retained `List<String>`
- [x] No gateway / CE edits
- [x] JDK 21 parity / unit fixtures green

## Risk note

GitNexus: product `validateConfiguration` (`--file product-service`) upstream
**LOW** (1 direct → `ConfigurationRuleController.validate`). Class-level
`ConfigurationRuleService` **UNKNOWN** (0 edges) — **warned**; confirmed via
`rg` (controller + parity fixtures). Index was 42 commits behind at edit time;
method risk remains LOW for this micro-opt.
