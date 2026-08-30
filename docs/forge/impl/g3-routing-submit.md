---
status: done
id: g3-routing-submit
---

# g3-routing-submit

## Summary

Wave G3 (S2C-12 precursor): added `POST /api/v1/approvals` create/submit on **approval-routing** for quote BFF orchestration. Maps quote tiers `DEAL_DESK` / `SALES_VP` / `CFO` → routing `approvalTier` + `ApprovalType` (`QUOTE` / `GOVERNANCE`). Kafka `DiscountApprovalEventConsumer` left intact. **No quote-service edits.**

## GitNexus (pre-edit)

| Symbol | Risk | Notes |
|--------|------|-------|
| routing `ApprovalService` | **LOW** (1 direct → controller) | Additive `submit` method |
| routing `ApprovalController` | **UNKNOWN** (HTTP entry) | Confirmed with `rg` |
| `ApprovalType` | **UNKNOWN** (0 edges) | Text: entity + Kafka consumer + tests |
| Index | 9 commits behind | Text confirmation used; G4 still HIGH |

**Wave risk:** MED (architecture). No HIGH/CRITICAL on routing create surface.

## Mapping

| Quote BFF tier | `approvalTier` | `ApprovalType` |
|----------------|----------------|----------------|
| `DEAL_DESK` | `DEAL_DESK` | `QUOTE` |
| `SALES_VP` | `SALES_VP` | `QUOTE` |
| `CFO` | `CFO` | `GOVERNANCE` |

Discount Kafka path still uses `DISCOUNT` + `MANAGER` / `DIRECTOR` / `VP_SALES` tiers — unchanged coexistence.

## Files

| Path | Change |
|------|--------|
| `.../domain/ApprovalType.java` | Add `QUOTE`, `GOVERNANCE` |
| `.../domain/QuoteApprovalTier.java` | New enum (quote BFF tiers) |
| `.../dto/ApprovalSubmitRequest.java` | Create/submit request DTO |
| `.../service/QuoteApprovalTierMapper.java` | Tier → type mapping |
| `.../service/ApprovalService.java` | `submit(...)` creates PENDING row |
| `.../controller/ApprovalController.java` | `POST /api/v1/approvals` → 201 |
| `.../db/migration/V1_2__add_quote_governance_approval_types.sql` | CHECK constraint |
| `.../service/ApprovalServiceTest.java` | Submit unit tests |
| `.../service/QuoteApprovalTierMapperTest.java` | Mapping unit tests |
| `docs/forge/impl/g3-routing-submit.md` | This note |

## Acceptance

- [x] HTTP create returns PENDING approval (`POST /api/v1/approvals`)
- [x] Quote tier → `approvalTier` + `ApprovalType` unit-tested
- [x] Kafka discount create path untouched
- [x] No quote-service edits
- [x] Unit tests green (`approval-routing`)

## Out of scope (G4+)

- Quote `ApprovalService` BFF client / single-writer cutover
- Dual-read pending merge
- Agent URL changes under `/api/v1/quotes/**/approvals*`
