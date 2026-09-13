---
status: done
id: w1-be-config-session-dto
wave: W
pr: W1
---

# w1-be-config-session-dto

## Summary

Wave W1 (BE only): stop leaking the `ConfigurationSession` JPA entity at the HTTP boundary. Added `ConfigurationSessionDto` + MapStruct `ConfigurationSessionMapper` (`componentModel = "spring"`), wired through `ConfigurationSessionService` / `ConfigurationSessionController`. Controllers return DTOs only. Session `POST /{id}/validate` retained and returns DTO. product-service `POST /api/v1/configuration/validate` **untouched**. No FE. No gateway edits. ADR-006 remains **Proposed** (Accept only in W4 after W1–W3 on `main`).

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| `ConfigurationSessionController` | **UNKNOWN** (0 callers + riskNote; HTTP entry) | **rg:** gateway `Path=/api/v1/configurations/**`; FE `frontend/**/*.ts` empty for configurations |
| `ConfigurationSessionService` (class) | **UNKNOWN** | **rg:** `ConfigurationSessionController` only |
| `create` / `validate` / `complete` | **LOW** | → controller methods |
| `getById` | **HIGH** (internal service callers + controller) | Mitigated: private `findEntityById` for entity; public `getById` returns DTO (O3 pattern) |

**WARN:** UNKNOWN ≠ unused. Confirmed gateway + no FE with `rg`. `getById` HIGH is expected (shared entity lookup); not a product/FE blast — same-module only. Index 3 commits behind — analyze skipped per Wave W plan.

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-w1` (`feat/forge-w1-be-config-session-dto`)  
Impact CLI: `node .gitnexus/run.cjs impact … --direction upstream --repo /home/username01/Projects/open-crm-cpq`

## Files

| Path | Change |
|------|--------|
| `…/dto/ConfigurationSessionDto.java` | New response DTO (no `deleted`) |
| `…/mapper/ConfigurationSessionMapper.java` | MapStruct `toDto` / `toDtoList` |
| `…/session/ConfigurationSessionService.java` | Returns DTOs; private `findEntityById` |
| `…/session/ConfigurationSessionController.java` | DTO-only API; session validate stays |
| `…/session/ConfigurationSessionControllerTest.java` | MockMvc: no `deleted`; no entity in signatures |
| `…/session/ConfigurationSessionServiceTest.java` | Mockito: DTO returns + lifecycle |
| `docs/forge/impl/w1-be-config-session-dto.md` | This note |

## Acceptance

- [x] No `ConfigurationSession` in controller signatures
- [x] MockMvc JSON has no `deleted` / `isDeleted`
- [x] Session `POST /{id}/validate` stays; returns DTO
- [x] product-service `POST /api/v1/configuration/validate` untouched
- [x] MapStruct `componentModel = "spring"` `toDto` / `toDtoList`
- [x] DTO fields: id, uuid, productId, userId, status, selections, valid, validationErrors, completedAt, expiresAt, createdAt, updatedAt, createdBy, updatedBy, version
- [x] No FE / gateway / quote-service / approval-routing / document-generation
- [x] No dual-read YAML; no `00-orchestrator-status.md`; ADR-006 left **Proposed**
- [x] Boot parent remains **4.0.6**

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
./mvnw -B -pl services/configuration-engine -am test
EOF
```

Result (2026-09-13): **BUILD SUCCESS**. configuration-engine Tests run: **76**, Failures: 0, Errors: 0 (incl. Controller DTO 6 + Session Service 8). JDK 21.0.2 via `newgrp docker`.

### detect-changes (pre-commit)

`node .gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq` from worktree: **3 files / 28 symbols / 34 processes / risk CRITICAL** (graph inflated — all session HTTP methods + service return-type change). Wave W plan **MED**; **rg** confirms gateway route only, **no FE**. Same O3/W2/W3 DTO pattern. No `partial`/`truncated`.

## Out of scope

- FE / portal / gateway / quote-service
- approval-routing (W2) / document-generation (W3)
- Accept ADR-006 (W4 only)
- Dual-read flip; Activities; #238; #241; R2; NgRx; subscriptions FE
- Delete product `POST /configuration/validate`
