# ADR-006: Entities never at the API boundary

**Status:** Proposed  
**Date:** 2026-08-31  
**Deciders:** (not accepted — documents a forge non-negotiable + current leak)  
**Does not authorize:** contract-asset / subscriptions **FE**, ERP adapter changes, or deleting `POST /api/v1/configuration/validate`

---

## Context

Open CRM/CPQ convention (`AGENT.md`, `opencrm-architect` skill): **JPA entities are never returned from REST controllers.** Controllers expose DTOs via MapStruct; wrap in `ApiResponse<T>`.

Most CPQ/CRM aggregates already follow this (`QuoteDto`, `ProductDto`, `PriceBookDto`, `CustomerDto`, `ConfigurationValidationResult`, …).

**Current leak (tree 2026-08-31):**

```java
// contract-asset-management SubscriptionController
@RequestMapping("/api/v1/subscriptions")
public ApiResponse<Subscription> getById(...)
public ApiResponse<List<Subscription>> getByCustomer(...)
public ApiResponse<List<Subscription>> getByStatus(...)
public ApiResponse<Subscription> activate(...)  // also calls ErpAdapter.syncToErp
```

`Subscription` extends `BaseEntity` (audit, soft-delete, version). There is **no** `SubscriptionDto` / MapStruct mapper. There is **no** list-all / pagination (only `/{id}`, `/customer/{id}`, `/status/{status}`). Gateway already routes `/api/v1/subscriptions/**`.

GitNexus: `SubscriptionController` **UNKNOWN** (0 upstream, HTTP entry). **UNKNOWN ≠ unused.** `rg` FE: no subscription portal. Kafka `QuoteAcceptedEventConsumer` writes internally.

Forge holds **contract-asset FE** until DTO + list exist. Wave N architecture deferred both FE and a Day-1 DTO PR (ERP on activate + breaking JSON shape).

---

## Decision (Proposed)

1. **Normative rule:** public `/api/v1/**` responses MUST be DTOs (or documented primitives/envelopes such as `ApiResponse<List<String>>` on retained product validate). Never `ApiResponse<Entity>`.
2. **Known exception until a dedicated PR:** `SubscriptionController` entity leak — track here; fix with `SubscriptionDto` + MapStruct + stop returning `Subscription`; add paginated list **before** any portal FE.
3. **Do not** Accept this ADR as “leak is gone.” Accept later when the subscription API is DTO-only (or when exceptions are explicitly listed and time-boxed).
4. **Activate/ERP** stays server-side; FE must not call ERP. Hybrid DTO wrap without list-all is insufficient for a portal.

### Rejected

| Option | Why not |
|--------|---------|
| Ship subscriptions FE against the entity JSON | Violates non-negotiable; couples UI to JPA/`BaseEntity` |
| Silent Java DTO in this docs review | Out of scope (user: docs unless HIGH tiny correction); ERP/list still missing |
| Invent `/api/v1/activities/**` | Separate hold; no schema/controller |

---

## Consequences

- **Positive:** Reviewers have a numbered ADR for entity-at-boundary instead of only AGENT.md.
- **Negative:** Runtime still leaks `Subscription` until a Java PR.
- **Residual:** GitNexus **UNKNOWN** on the controller; confirm with `rg` before treating as unused. `activate` ERP failure mode is independent of DTO work.

## Prerequisites for subscriptions FE (not this ADR’s implementation)

1. `SubscriptionDto` / create-update requests as needed; MapStruct; controller returns DTOs only.  
2. Paginated list-all (or documented query that a portal can use).  
3. Explicit contract tests that JSON has no `BaseEntity` leakage beyond DTO fields.  
4. Then a FE portal wave — **not** held as “just add Angular.”

## References

- `AGENT.md` — DTOs at API boundary  
- `docs/forge/23-wave-n-architecture.md` — contract-asset FE / DTO deferred  
- `backend/services/contract-asset-management/.../SubscriptionController.java`  
- `docs/backlog/ADR-configuration-api-reconciliation.md` — product validate `List<String>` is a **documented** envelope, not an entity leak  
