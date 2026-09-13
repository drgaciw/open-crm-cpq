---
status: done
id: w2-be-approval-request-dto
wave: W
pr: W2
---

# w2-be-approval-request-dto

## Summary

Wave W2 (BE only): stop leaking the approval-routing `ApprovalRequest` JPA entity at the HTTP boundary. Added `com.opencrm.approval.dto.ApprovalRequestDto` + MapStruct `ApprovalRequestMapper` (`componentModel = "spring"`), wired through `ApprovalService` / routing `ApprovalController` (`/api/v1/approvals`). Controllers return DTOs only. Wire JSON keeps every field quote-service `RoutingApprovalDto` reads (`id`, `uuid`, `quoteId` as **String**, `approvalType`, `status`, `requesterId`, `approverId`, `approvalTier`, `reason`, `rejectionReason`, `submittedAt`, `approvedAt`, `rejectedAt`, `createdAt`); extras OK (`ignoreUnknown`); **`deleted` omitted**. No quote-service BFF edits. No FE. Dual-read YAML untouched. ADR-006 remains **Proposed**.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| routing `ApprovalController` | **UNKNOWN** (0 callers + riskNote; HTTP entry) | **rg:** quote `ApprovalRoutingClient` → `/api/v1/approvals/**`; FE inbox uses `/api/v1/quotes/approvals/**` only |
| `ApprovalService.submit` / `approve` | **LOW** | → routing `ApprovalController` only |
| `ApprovalService.getById` | **LOW** | → controller + approve/reject |

**WARN:** UNKNOWN ≠ unused. Confirmed quote client + no FE direct routing with `rg`. Disambiguated two `ApprovalController` / two `ApprovalRequest` with `--file`. No HIGH/CRITICAL. Analyze skipped (stale per Wave W).

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-w2` (`feat/forge-w2-be-approval-request-dto`)  
Impact CLI: `node .gitnexus/run.cjs impact … --direction upstream --repo /home/username01/Projects/open-crm-cpq --file …`.

## Files

| Path | Change |
|------|--------|
| `…/dto/ApprovalRequestDto.java` | New response DTO (no `deleted`) |
| `…/mapper/ApprovalRequestMapper.java` | MapStruct `toDto` / `toDtoList` |
| `…/service/ApprovalService.java` | Returns DTOs |
| `…/controller/ApprovalController.java` | DTO-only API |
| `…/service/ApprovalServiceTest.java` | Mockito: DTO returns + mapper mock |
| `…/controller/ApprovalControllerTest.java` | MockMvc: no entity / no `deleted`; wire fields |
| `docs/forge/impl/w2-be-approval-request-dto.md` | This note |

## Acceptance

- [x] No `ApiResponse<ApprovalRequest>` / `List<ApprovalRequest>` at routing controller
- [x] submit / getById / pending / pending/tier / approve / reject return DTO
- [x] JSON includes `RoutingApprovalDto` fields; `quoteId` remains String; no `deleted`
- [x] MapStruct `componentModel = "spring"`
- [x] No quote-service / FE edits; no dual-read YAML flip; no ADR-006 Accept
- [x] `ApprovalRoutingClientTest` fixtures remain valid (missing `deleted` fine)
- [x] Tests: MockMvc no entity leak; service state machine green
- [x] No `docs/forge/00-orchestrator-status.md`; Boot 4.0.6 untouched

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
./mvnw -B -pl services/approval-routing -am test
EOF
```

Result: **BUILD SUCCESS**. approval-routing Tests run: **20**, Failures: 0, Errors: 0 (controller DTO 6 + service 10 + tier mapper 4). Reactor also green for common (86) + security (13). JDK 21.0.2 under `newgrp docker`.

## Out of scope

- Quote-service BFF `ApprovalController` / FE inbox
- configuration-engine (W1) / document-generation (W3)
- Dual-read YAML flip; ADR-006 Accept (W4)
- Activities, #238, #241, R2, named T1/T2, subscriptions FE, NgRx
- Boot downgrade; `00-orchestrator-status.md`
