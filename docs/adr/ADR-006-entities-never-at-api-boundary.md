# ADR-006: Entities never at the API boundary

**Status:** Proposed  
**Date:** 2026-08-31  
**Deciders:** (not accepted — documents a forge non-negotiable + remaining leaks)  
**Wave O3:** **on `main`** — subscription leak **gone**; `SubscriptionDto` + MapStruct + paginated `GET /api/v1/subscriptions` (**no FE**). **Do not Accept** this ADR while other controllers still return entities.  
**Does not authorize:** contract-asset / subscriptions **FE**, ERP adapter changes, or deleting `POST /api/v1/configuration/validate`

---

## Context

Open CRM/CPQ convention (`AGENT.md`, `opencrm-architect` skill): **JPA entities are never returned from REST controllers.** Controllers expose DTOs via MapStruct; wrap in `ApiResponse<T>`.

Most CPQ/CRM aggregates already follow this (`QuoteDto`, `ProductDto`, `PriceBookDto`, `CustomerDto`, `ConfigurationValidationResult`, `SubscriptionDto`, …).

**Subscription leak resolved (Wave O3 on `main`):**

```java
// contract-asset-management SubscriptionController
@RequestMapping("/api/v1/subscriptions")
public ApiResponse<Page<SubscriptionDto>> list(...)
public ApiResponse<SubscriptionDto> getById(...)
public ApiResponse<List<SubscriptionDto>> getByCustomer(...)
public ApiResponse<List<SubscriptionDto>> getByStatus(...)
public ApiResponse<SubscriptionDto> activate(...)  // still calls ErpAdapter.syncToErp
```

Gateway already routes `/api/v1/subscriptions/**`. There is **no** subscriptions FE portal (held).

**Remaining entity leaks (tree 2026-08-31) — why Status stays Proposed:**

| Controller | Returns |
|------------|---------|
| `ConfigurationSessionController` | `ApiResponse<ConfigurationSession>` |
| `ApprovalController` (approval-routing) | `ApiResponse<ApprovalRequest>` |
| `DocumentController` (document-generation) | `ApiResponse<DocumentGenerationJob>` |

GitNexus: these controllers are often **UNKNOWN** (HTTP entry). **UNKNOWN ≠ unused.** Confirm with `rg` before treating as unused.

---

## Decision (Proposed)

1. **Normative rule:** public `/api/v1/**` responses MUST be DTOs (or documented primitives/envelopes such as `ApiResponse<List<String>>` on retained product validate). Never `ApiResponse<Entity>`.
2. **Subscription API:** DTO-only + paginated list is **landed** (O3). Forge still **holds** subscriptions FE until a dedicated portal wave (activate → ERP).
3. **Do not** Accept this ADR as “DTO-only API everywhere.” Accept later when remaining entity returns (`ConfigurationSession`, `ApprovalRequest`, `DocumentGenerationJob`) are DTO-wrapped (or exceptions are explicitly listed and time-boxed).
4. **Activate/ERP** stays server-side; FE must not call ERP.

### Rejected

| Option | Why not |
|--------|---------|
| Accept ADR-006 because O3 landed | Other controllers still return entities |
| Ship subscriptions FE against the DTO API this wave | Shell + ERP on activate; Wave P held FE |
| Invent `/api/v1/activities/**` | Separate hold; no schema/controller |

---

## Consequences

- **Positive:** Reviewers have a numbered ADR for entity-at-boundary instead of only AGENT.md; subscription boundary matches convention.
- **Negative:** Runtime still leaks session / approval-routing / document-job entities until dedicated PRs.
- **Residual:** GitNexus **UNKNOWN** on HTTP controllers; confirm with `rg`. `activate` ERP failure mode is independent of DTO work.

## Prerequisites for subscriptions FE (not this ADR’s implementation)

1. ~~`SubscriptionDto` / MapStruct; controller returns DTOs only.~~ **Done (O3).**  
2. ~~Paginated list-all.~~ **Done (O3).**  
3. Explicit contract tests that JSON has no `BaseEntity` leakage beyond DTO fields.  
4. Then a FE portal wave — **not** held as “just add Angular” (ERP on activate remains a product gate).

## References

- `AGENT.md` — DTOs at API boundary  
- `docs/forge/26-wave-o-architecture.md` — O3 DTO+list  
- `docs/forge/28-wave-p-architecture.md` — P4 header hygiene; Accept rejected  
- `backend/services/contract-asset-management/.../SubscriptionController.java`  
- `docs/backlog/ADR-configuration-api-reconciliation.md` — product validate `List<String>` is a **documented** envelope, not an entity leak  
