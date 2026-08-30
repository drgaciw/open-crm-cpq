---
status: done
id: h3-jpa-projections
---

# h3-jpa-projections

## Summary

Replaced raw `Object[]` aggregate results on `LeadRepository.countGroupByStatus` and `DealRepository.findPipelineSummary` with Spring Data JPA interface projections (`LeadStatusCountProjection`, `PipelineSummaryProjection`). Updated `LeadService` / `DealService` mapping and unit mocks. Salvaged Bolt #437; skipped `.jules/bolt.md` / `pr_body.txt`.

## GitNexus

| Symbol | Risk | Callers (confirmed) |
|--------|------|---------------------|
| `findPipelineSummary` | **LOW** | `DealService.getPipelineSummary` → `DealController` |
| `countGroupByStatus` | **LOW** | `LeadService.getStatusCounts` → `LeadController` |
| Controller HTTP entrypoints | **UNKNOWN** graph | FE dashboard / lead status + IT; envelopes unchanged |

Index was 28 commits behind at edit time; text `rg` confirmed HTTP/FE consumers. No HIGH/CRITICAL.

## Acceptance

- [x] No `Object[]` on those two queries
- [x] Projection aliases match interface getters
- [x] Unit tests map via projections (JDK 21 record test fixtures)
- [x] HTTP pipeline/status response shapes unchanged
