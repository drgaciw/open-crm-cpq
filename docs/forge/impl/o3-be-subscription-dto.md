---
status: done
id: o3-be-subscription-dto
wave: O
pr: O3
---

# o3-be-subscription-dto

## Summary

Wave O3 (BE only): stop leaking the `Subscription` JPA entity at the HTTP boundary. Added `SubscriptionDto` + MapStruct `SubscriptionMapper` (`componentModel = "spring"`), wired through `SubscriptionService` / `SubscriptionController`. Controllers return DTOs only. New paginated `GET /api/v1/subscriptions` with `ApiResponse.PageInfo` (same shape as `ProductAttributeController.listAttributes`). Lifecycle endpoints (`/{id}`, `/customer/{customerId}`, `/status/{status}`, activate/suspend/cancel/renew) all return `SubscriptionDto`. `activate` still calls `ErpAdapter.syncToErp`, persists ERP fields (`erpSubscriptionId` / `erpSyncStatus`), returns DTO. No FE. No gateway route edits. ADR-006 remains **Proposed** (DTO land is a prerequisite; do not Accept as if FE exists).

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| `SubscriptionController` | **UNKNOWN** (0 callers + riskNote; HTTP entry) | **rg:** gateway `/api/v1/subscriptions/**` (+ `/api/v1/cpq/subscriptions/**` rewrite); FE empty; Kafka `QuoteAcceptedEventConsumer` writes via repository (internal — not broken) |
| `SubscriptionService` | **LOW** (exact, 1) | → `SubscriptionController` only |
| `SubscriptionRepository` | **LOW** (exact, 3 direct) | → service, `QuoteAcceptedEventConsumer`, `RenewalScheduler` |

**WARN:** UNKNOWN ≠ unused. Confirmed gateway + Kafka with `rg`. No HIGH/CRITICAL. Index may report stale from `.worktrees/` — false stale per Wave O arch.

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-o3` (`feat/forge-o3-be-subscription-dto`)  
Impact CLI: `node .gitnexus/run.cjs` from primary repo with `-r /home/username01/Projects/open-crm-cpq --file …`.

## Files

| Path | Change |
|------|--------|
| `…/dto/SubscriptionDto.java` | New response DTO (no `deleted` / Hibernate leak fields) |
| `…/mapper/SubscriptionMapper.java` | MapStruct `toDto` / `toDtoList` |
| `…/service/SubscriptionService.java` | Returns DTOs; `list(Pageable)`; activate still ERP |
| `…/controller/SubscriptionController.java` | DTO-only API; paginated `GET /` |
| `…/controller/SubscriptionControllerTest.java` | MockMvc: no entity JSON; page metadata; lifecycle |
| `…/service/SubscriptionServiceTest.java` | Mockito: list, activate ERP success/fail, lifecycle |
| `docs/forge/impl/o3-be-subscription-dto.md` | This note |

## Acceptance

- [x] No `ApiResponse<Subscription>` / `List<Subscription>` at controller
- [x] Paginated `GET /api/v1/subscriptions` + `PageInfo`
- [x] getById / customer / status / activate / suspend / cancel / renew return DTO
- [x] `activate` → `ErpAdapter.syncToErp`; persist ERP fields; return DTO
- [x] Tests: no entity leak fields in JSON; page metadata; lifecycle; no live ERP
- [x] No FE / portal; no gateway edits; no `00-orchestrator-status.md`; no dual-read flip
- [x] ADR-006 left **Proposed** (note: Accept only after merge + FE readiness, not in this PR)

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend
./mvnw -pl services/contract-asset-management -am test -Dsurefire.failIfNoSpecifiedTests=false
```

Result: Tests run: **18**, Failures: 0, Errors: 0 (JDK 21.0.2). Includes domain lifecycle (8) + controller DTO (5) + service (5).

## Out of scope

- Subscriptions FE / portal
- Gateway route changes
- `docs/forge/00-orchestrator-status.md`
- Dual-read YAML flip
- Accepting ADR-006 as if FE exists
- Activities, #238, #241, WhatsApp, product validate delete, Kafka S2B-9
- ErpAdapter contract changes
