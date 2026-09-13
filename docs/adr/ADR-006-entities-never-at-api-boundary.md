# ADR-006: Entities never at the API boundary

**Status:** Accepted  
**Date:** 2026-08-31 (Proposed); **Accepted** 2026-09-13 (Wave W4 — W1+W2+W3 on `main`)  
**Deciders:** forge Wave W closeout after remaining three entity leaks DTO-wrapped  
**Wave O3:** **on `main`** — subscription leak **gone**; `SubscriptionDto` + MapStruct + paginated `GET /api/v1/subscriptions` (**no FE**).  
**Wave W1–W3:** **on `main`** — `ConfigurationSessionDto` (#503 / `e6d3b23`), approval-routing `ApprovalRequestDto` (#501 / `29cac5b`), `DocumentGenerationJobDto` (#502 / `0a3d9a4`).  
**Does not authorize:** contract-asset / subscriptions **FE**, ERP adapter changes, deleting `POST /api/v1/configuration/validate`, dual-read flip, or Accept ADR-005

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

**Remaining entity leaks resolved (Wave W1∥W2∥W3 on `main`):**

| Controller | Was | Now (Accepted) |
|------------|-----|----------------|
| `ConfigurationSessionController` | `ApiResponse<ConfigurationSession>` | `ConfigurationSessionDto` (W1) |
| `ApprovalController` (approval-routing) | `ApiResponse<ApprovalRequest>` | `ApprovalRequestDto` (W2) |
| `DocumentController` (document-generation) | `ApiResponse<DocumentGenerationJob>` | `DocumentGenerationJobDto` (W3) |

Quote-service BFF `ApprovalController` was already DTO-only before Wave W. Product `POST /api/v1/configuration/validate` returning `ApiResponse<List<String>>` is a **documented** envelope, not an entity leak — **retained**.

GitNexus: HTTP controllers are often **UNKNOWN**. **UNKNOWN ≠ unused.** Confirm with `rg` before treating as unused.

---

## Decision (Accepted)

1. **Normative rule:** public `/api/v1/**` responses MUST be DTOs (or documented primitives/envelopes such as `ApiResponse<List<String>>` on retained product validate). Never `ApiResponse<Entity>`.
2. **Subscription API:** DTO-only + paginated list is **landed** (O3). Forge still **holds** subscriptions FE until a dedicated portal wave (activate → ERP).
3. **Configuration session / approval-routing / document-generation job APIs:** DTO-only is **landed** (W1–W3). Omit soft-delete `deleted` from response DTOs.
4. **Activate/ERP** stays server-side; FE must not call ERP.

### Rejected

| Option | Why not |
|--------|---------|
| Accept ADR-006 because O3 landed alone | Other controllers still returned entities until W1–W3 |
| Ship subscriptions FE against the DTO API in Wave O/W | Shell + ERP on activate; still held |
| Invent `/api/v1/activities/**` | Separate hold; no schema/controller |
| Flip dual-read / Accept ADR-005 in the same closeout | Independent **HIGH** gate; still Proposed |

---

## Consequences

- **Positive:** Reviewers have a numbered **Accepted** ADR for entity-at-boundary; subscription, configuration session, approval-routing, and document-job APIs match convention.
- **Negative:** New controllers must continue the DTO + MapStruct pattern; regressions that re-expose entities fail review against this ADR.
- **Residual:** GitNexus **UNKNOWN** on HTTP controllers; confirm with `rg`. `activate` ERP failure mode is independent of DTO work. Subscriptions FE still held.

## Prerequisites for subscriptions FE (not this ADR’s implementation)

1. ~~`SubscriptionDto` / MapStruct; controller returns DTOs only.~~ **Done (O3).**  
2. ~~Paginated list-all.~~ **Done (O3).**  
3. Explicit contract tests that JSON has no `BaseEntity` leakage beyond DTO fields.  
4. Then a FE portal wave — **not** held as “just add Angular” (ERP on activate remains a product gate).

## References

- `AGENT.md` — DTOs at API boundary  
- `docs/forge/26-wave-o-architecture.md` — O3 DTO+list  
- `docs/forge/35-wave-w-architecture.md` — W1–W3 DTO wraps; W4 Accept  
- `docs/forge/impl/w4-wave-w-docs.md` — closeout  
- `backend/services/contract-asset-management/.../SubscriptionController.java`  
- `backend/services/configuration-engine/.../ConfigurationSessionController.java`  
- `backend/services/approval-routing/.../ApprovalController.java`  
- `backend/services/document-generation/.../DocumentController.java`  
- `docs/backlog/ADR-configuration-api-reconciliation.md` — product validate `List<String>` is a **documented** envelope, not an entity leak  
