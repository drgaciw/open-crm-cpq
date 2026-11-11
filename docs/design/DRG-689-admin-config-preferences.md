# DRG-689: Admin configuration and user preferences (design note)

**Status:** Proposed (draft design note), 2026-11-11. It authorizes no code, schema, Flyway migration, gateway route, feature flag, topic, registry entry or deploy. It does not take an ADR number and does not edit `docs/adr/README.md`. Every recommendation below is Proposed. Adoption needs the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Stories:** [DRG-689 ADMIN-BE-01](https://linear.app/drgamtd-workspace/issue/DRG-689/admin-be-01-backend-admin-configuration-and-user-preferences) (this note is its "ADR or short design note required before extract" step) · [DRG-690 ADMIN-FE-01](https://linear.app/drgamtd-workspace/issue/DRG-690/admin-fe-01-frontend-admin-console-and-user-preferences-ux) (blocked by DRG-689) · parent epic [DRG-688](https://linear.app/drgamtd-workspace/issue/DRG-688).

**Base read:** `main` at `d3256ae4b4960f58a2c060883bc980c8f27c2be1`.

## 1. Context and scope

DRG-689 asks for backend APIs and a clear owner so that:
- administrators can manage tenant/org configuration;
- users can manage their own preferences.

**Administrator configuration (tenant scoped), per the story:**
- runtime-safe capability toggles, distinct from BC-18 model promotion;
- default locale, timezone and base-currency display;
- notification defaults;
- session/idle timeout within ADR-016 bounds;
- links to approval/quote policy defaults owned by BC-04/BC-18 (not editable here);
- an audit of admin changes.

**User preferences (principal scoped), per the story:**
- locale, timezone, density/theme and default landing route;
- notification opt-ins;
- saved list filters and table column preferences;
- per-user defaults that never override admin-enforced policy.

**Out of scope (story):**
- BC-18 model versioning and sandbox promotion;
- Entra app registration and OIDC setup;
- a full RBAC redesign (BC-19-03): roles are consumed, not reinvented.

**What exists on `main` today:**
- No admin-config or preferences code exists under `backend/services/**`.
- No admin, config or preferences ADR exists.
- Legacy reference material is in `cpq_beta/` (`crm_view_settings`, `components/Settings/`). It is reference only and not a source of truth.

## 2. Terms

| Term | Meaning in this note | Not to be confused with |
|---|---|---|
| Tenant configuration | Admin-editable settings scoped to one tenant | Spring `opencrm.*` properties, which stay ops-owned and deploy-time |
| User preference | Settings owned by one principal (`sub`) within one tenant | Admin-enforced policy |
| Capability toggle | A tenant-level, runtime on/off for a product capability that is safe to expose to admins | `opencrm.jev.*`, dual-read, `opencrm.assets.*` and other held flags, which this note never exposes |
| Saved view | Exactly as defined by [ADR-031 D-06](../adr/ADR-031-extensible-field-registry-and-saved-views.md) (Accepted), owned by `crm-service` under `/api/v1/saved-views/**` (ADR-031 D-07) | A preference blob. This note does **not** redefine saved views |

## 3. Ownership options

| Option | Shape | Fit with ADR-008 / ADR-009 | Notes |
|---|---|---|---|
| A | New `admin-service` with its own schema | ADR-008 says it "does not authorize new schemas", so this needs an ADR | Cleanest boundary. Adds a service, a gateway route set, a schema and CI. |
| B | Module inside `crm-service` (schema `crm`) | Fits as is | CRM already owns accounts, saved views (ADR-031) and the human-facing records. Risk: CRM grows into a catch-all. |
| C | Module inside another existing service (e.g. `configuration-engine`) | Fits | Name clash: "configuration" there means product configuration sessions. |
| D | Split: tenant config in one service, user preferences in another | Mixed | Two owners for one admin UX (DRG-690). |

**Recommended (Proposed): B for the first slice**, with an explicit module boundary (package plus its own tables) so it can be extracted later. Promote to A only through an ADR if scale or ownership demands it. `Owner decision: ___ (drg, time)`

## 4. Data model sketch (no DDL)

- **`tenant_config`**
  - Fields: `tenant_id`, `key` (namespaced, e.g. `locale.default`), typed `value`, `version`, `updated_by`, `updated_at`.
  - Keys come from a server-side allow-list. Unknown keys are rejected.
- **`user_preference`**
  - Fields: `tenant_id`, `principal_id` (`sub`), `namespace` (e.g. `ui`, `notifications`), `payload` (JSON), `schema_version`, `updated_at`.
  - The payload has a hard size cap (OQ7).
- **Precedence**
  - A tenant key marked *enforced* wins over the user value.
  - A tenant key marked *default* is used when the user has no value.
  - The API returns the effective value plus its source (`tenant-enforced`, `tenant-default` or `user`), so the UI can disable enforced fields (DRG-690 AC).
- **List filters and columns**
  - The preference stores at most a pointer to a default saved view per entity (e.g. `ui.defaultView.deals = <saved_view id>`).
  - Filters, sort and columns themselves live in ADR-031 D-06 `saved_view`.

## 5. API sketch

| Method and path | Caller | Behaviour |
|---|---|---|
| `GET /api/v1/admin/config` | admin role (OQ2) | effective tenant config |
| `PUT /api/v1/admin/config` | admin role | partial update. Optimistic concurrency via `version` / `If-Match`. Validation errors per key. Audited (§7) |
| `GET /api/v1/me/preferences` | any authenticated human | own preferences merged with tenant defaults/enforced values, with sources |
| `PUT /api/v1/me/preferences` | same principal only | validated against `schema_version`. Oversized payload → 413/400. Cannot set enforced keys |

- **Refusals:**
  - 401 without a caller;
  - 403 for non-admins on `/admin/**`;
  - 403 for service tokens on `/me/**`;
  - no path parameter ever selects another user.
- **Gateway:** routes are added as gateway predicates only (ADR-009 edge-only), with dev and JWT security parity.

## 6. Authorization

- ADR-016 app roles today are `CPQ.User`, `DealDesk`, `SalesVP`, `CFO`, `Admin` and `Internal.Invoke`. ADR-016 states "Admin and CPQ.User decide nothing" for approvals.
- **Proposed:** gate tenant config with `Admin` (`ROLE_ADMIN`), or with a dedicated role following the [DRG-691 account-merge pattern](./DRG-691-account-merge-authorization.md). See OQ2.
- `/me/**` is self-only. The principal is the JWT `sub`; the tenant comes from the token (OQ3).
- The backend enforces every rule. DRG-690's UI hides the admin nav only from a server-derived capability and fails closed. Frontend visibility is not a security boundary.

## 7. Audit

- Every admin write records actor, time, key, before, after and request id.
- **Proposed:** a local append-only table in the owning service for the first slice. Alignment with BC-20 / [ADR-033](../adr/ADR-033-crm-record-archive-purge-suppression.md) retention comes later (OQ6).
- User preference changes are not audited beyond `updated_at`, unless the owner asks for it.

## 8. Overlaps and boundaries

- **Saved views:** [ADR-031](../adr/ADR-031-extensible-field-registry-and-saved-views.md) D-06/D-07 own saved filters, sort and columns. This note only references them.
- **Currency defaults:** display defaults only. Base-currency semantics stay with [ADR-032](../adr/ADR-032-multi-currency-pipeline-reporting.md).
- **Session/idle timeout:** a tenant value is clamped to [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md) bounds and never extends them.
- **Approval/quote policy (BC-04/BC-18):** shown as links or read-only, never written here.
- **Tenant identity:** [ADR-036](../adr/ADR-036-account-tenant-ownership.md) is Proposed. Until it is accepted, use the ADR-016 token tenant (OQ3).

## 9. Events

- None are needed for the first slice.
- A possible `TenantConfigChanged` event (for caches in other services) is listed only. Any new event or registry entry is owner-gated, per ADR-018 and ADR-026 practice.

## 10. Rollout and holds

- No flags are flipped, and no held flag is exposed as a capability toggle.
- DRG-690 (FE) starts only after the BE slice exists. The frontend CI must also be green first (#954).
- Tests for the later build: unit tests, plus Testcontainers ITs for persistence and authorization (story AC).

## 11. Open questions

| ID | Question | Recommended (Proposed) |
|---|---|---|
| OQ1 | Owner: a module in an existing service, or a new service (new schema needs an ADR-008 amendment)? | Module in `crm-service` |
| OQ2 | Which role administers tenant config: existing `Admin` or a new dedicated role? | `Admin` for config. Revisit per key |
| OQ3 | Tenant key: the ADR-016 token tenant now, or ADR-036 account-tenant (Proposed)? | ADR-016 token tenant |
| OQ4 | Saved filters/columns: ADR-031 `saved_view` only, with the preference holding a default-view pointer? | Yes |
| OQ5 | Are runtime capability toggles in the first slice, and which ones? | Out of the first slice. An allow-list ADR/decision comes later |
| OQ6 | Audit: a local table, or the BC-20 audit path? | Local table first |
| OQ7 | Preference payload cap and versioning? | e.g. 16 KiB per namespace, integer `schema_version`, reject unknown versions |
| OQ8 | Notification defaults: no notification service was found on `main`. Store the defaults only? | Store only. No delivery |

`Owner decision: ___ (drg, time)` for each row.

## 12. Proposed follow-up stories (not filed)

1. BE slice 1: `/me/preferences`, with self-only authz, validation and size cap.
2. BE slice 2: `/admin/config`, with role gate, optimistic concurrency and audit.
3. Gateway routes and the contract-matrix rows.
4. DRG-690 FE, after slices 1 and 2.
