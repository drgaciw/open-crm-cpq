# ADR: Configuration API Reconciliation (Rule CRUD vs Session Validate vs Agent Validate)

**Status:** Accepted  
**Date:** 2026-05-18  
**Accepted:** 2026-08-30 (Wave E / E3 — freeze open-question defaults only)  
**Backlog PR:** S2A-8 (Stack 2A — Shared Contracts and Published Language)  
**Depends on:** S2A-7 (`CPQ_API_CONTRACT_MATRIX.md`)  
**Implementation PR:** S2B-11 (configuration API compatibility layer) — **deferred**; Accept does **not** schedule or land the gateway validate shim, response-shape rewrite, or product-service validate removal  
**Deciders:** Platform architecture / CPQ microservice backlog owners  
**Accept authority:** Wave E architecture (`docs/forge/14-wave-e-architecture.md`)

---

## Context

Open CRM CPQ exposes configuration capabilities through two overlapping API surfaces that evolved independently:

| Surface | Base path | Current owner | Purpose |
|---------|-----------|---------------|---------|
| **Agent-stable configuration API** | `/api/v1/configuration/*` | `product-service` (`ConfigurationRuleController`) | Rule CRUD and stateless validation expected by agent PRDs |
| **Session-scoped configuration engine** | `/api/v1/configurations/*` | `configuration-engine` (`ConfigurationSessionController`) | Stateful configuration sessions, incremental selections, session validate |

### Agent contract expectations

Multiple agent PRDs treat `POST /api/v1/configuration/validate` as the canonical real-time validation entry point:

- **cpq-agent-015 (Compatibility Rules):** submits configuration payloads to `POST /api/v1/configuration/validate`; expects structured pass/fail with violations and remediation guidance.
- **cpq-agent-016 (Validation Rules):** delegates compatibility checking via the same path and merges results into broader preflight scopes (`CONFIGURATION_ONLY`, `QUOTE_COMPLETE`, `FULL_PREFLIGHT`).
- **cpq-agent-002 (Option & Feature Configuration):** calls validate when configuration is complete (PRD text also references `POST /configuration/validate` without the `/api/v1` prefix — normalized to gateway-stable `/api/v1/configuration/validate` per S2A-7).

Rule management agents expect rule CRUD on the same prefix:

- `POST /api/v1/configuration/rules`
- `GET /api/v1/configuration/rules`
- `GET /api/v1/configuration/rules/{id}`
- `GET /api/v1/configuration/rules/product/{productId}`
- `DELETE /api/v1/configuration/rules/{id}`

### Current implementation state

**product-service** (`ConfigurationRuleController`, port 8083):

- Owns rule persistence (`ConfigurationRule` entity, `ConfigurationRuleRepository`).
- Implements `POST /api/v1/configuration/validate` inline: accepts `List<Long> selectedProductIds`, evaluates REQUIRES/EXCLUDES rules from the local database, returns `List<String>` error messages.
- Does **not** evaluate RECOMMENDS, DEFAULT, or VALIDATES rule types in the current validator.
- Does **not** manage configuration sessions or option-level selections.

**configuration-engine**:

- Owns `ConfigurationSession` aggregate (selections map, validity flag, lifecycle: IN_PROGRESS → COMPLETED/CANCELLED).
- Exposes `POST /api/v1/configurations/{id}/validate` returning the updated session with `valid` and `validationErrors`.
- Uses `ConstraintSolver` for constraint evaluation (currently placeholder logic; S2B-7/S2B-8 will wire REQUIRES/EXCLUDES/RECOMMENDS/DEFAULT/VALIDATES and graph conflict checks).
- Does **not** expose rule CRUD endpoints.

### Problem

Without an explicit boundary:

1. **Duplicate validation logic** will diverge (product-service REQUIRES/EXCLUDES vs configuration-engine `ConstraintSolver`).
2. **Agent paths break** if validation moves exclusively to session-scoped URLs agents do not call.
3. **Ownership is ambiguous** — agents list `product-service` as primary service, but DDD direction assigns constraint solving to `configuration-engine`.

This ADR reconciles ownership while preserving agent-stable URLs via the gateway (S2A-7 matrix, S2B-11 implementation).

---

## Decision

### 1. Rule CRUD owner: `product-service`

**Canonical paths (unchanged):**

| Method | Path | Owner |
|--------|------|-------|
| POST | `/api/v1/configuration/rules` | product-service |
| GET | `/api/v1/configuration/rules` | product-service |
| GET | `/api/v1/configuration/rules/{id}` | product-service |
| GET | `/api/v1/configuration/rules/product/{productId}` | product-service |
| DELETE | `/api/v1/configuration/rules/{id}` | product-service |

**Rationale:** Configuration rules are catalog metadata tied to products, bundles, and option definitions — the same bounded context as product lifecycle, attributes, and option groups. Rule persistence, soft-delete, and admin CRUD remain in `ConfigurationRuleService` / `ConfigurationRuleRepository`.

**product-service does not own constraint evaluation long-term.** The inline `validateConfiguration()` in `ConfigurationRuleService` is a transitional implementation; S2B-11 may retain it temporarily behind the gateway shim but must not be the sole evaluator once `configuration-engine` rule consumption (S2B-8) is complete.

### 2. Session-scoped validation owner: `configuration-engine`

**Canonical path:**

| Method | Path | Owner |
|--------|------|-------|
| POST | `/api/v1/configurations/{id}/validate` | configuration-engine |

**Rationale:** Validation against incremental option selections, session validity, and commit-to-snapshot flows belong to the `ConfigurationSession` aggregate. `ConfigurationSessionService.validate()` delegates to `ConstraintSolver`, which is the long-term home for REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, and VALIDATES evaluation plus graph conflict detection (S2B-8).

**Rule source:** `configuration-engine` reads rules from `product-service` (REST projection, cached compile, or event-driven invalidation per S2B-9) — it does not persist rule definitions.

### 3. Agent-stable validate path: gateway/BFF preserves `POST /api/v1/configuration/validate`

**External contract (stability tier: agent-critical, per S2A-7):**

| Method | Path | External owner | Internal delegate |
|--------|------|----------------|-------------------|
| POST | `/api/v1/configuration/validate` | gateway-service (route + adapter) | configuration-engine |

**Decision:** `POST /api/v1/configuration/validate` **remains the agent-facing URL**. It is **not** removed or renamed. Implementation is delivered in **S2B-11**, not S2A-8.

Gateway behavior (S2B-11 — **implementation deferred**; defaults frozen at Accept):

1. Accept agent PRD payload (product/option/configuration state — structured DTO, not raw `List<Long>` long-term).
2. **Prefer:** map payload to configuration-engine **`POST /internal/v1/configurations/evaluate`** (stateless). Ephemeral-session-only agent path is rejected as the long-term default (session validate remains available for UI session flows).
3. **Translate** configuration-engine result to agent PRD shape: `{ valid, violations: [{ ruleId, ruleType, description, remediation }] }` using a shared DTO in `backend/libs/common`.
4. **Do not** embed domain validation logic in the gateway — routing, auth, correlation IDs, and response adaptation only.

**No breaking removal:** Existing `product-service` routes under `/api/v1/configuration/*` must not be deleted without a gateway shim or documented deprecation window. Retain product-service `POST /api/v1/configuration/validate` (current `List<String>` error shape per contract matrix) until contract-test parity — **no removal date** until S2B-11 lands. During S2B-11, product-service continues to serve rule CRUD directly; validate may be proxied or dual-run under contract tests until parity is proven.

### 4. Evaluation vs persistence split

| Concern | Owner |
|---------|-------|
| Rule definition persistence (CRUD, soft-delete, product linkage) | product-service |
| Constraint evaluation (REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, VALIDATES, graph checks) | configuration-engine |
| Agent-stable URL preservation | gateway-service |
| Session lifecycle (selections, complete, cancel, immutable snapshot) | configuration-engine |
| Quote preflight orchestration calling validate | quote-service (S2D-3a) via gateway/client — no inline compatibility logic |

---

## Consequences

### Positive

- **Clear bounded contexts:** catalog rules vs runtime configuration vs edge routing.
- **Agent PRDs stay stable:** no changes required to cpq-agent-002, cpq-agent-015, or cpq-agent-016 validate call sites.
- **Single evaluation engine:** avoids duplicating REQUIRES/EXCLUDES (and future VALIDATES) in product-service and configuration-engine.
- **Session and stateless paths coexist:** UI/session flows use `/api/v1/configurations/{id}/validate`; agents and quote preflight use `/api/v1/configuration/validate` through the gateway.
- **Aligns with backlog merge gates:** S2A-8 documents intent; S2B-11 implements; S2D-3a consumes via client.

### Negative

- **Gateway adapter complexity:** request/response mapping between agent DTO and configuration-engine session/evaluator models (S2B-11 scope).
- **Transitional duplication:** until S2B-11 lands, product-service still runs inline validation — contract tests must detect drift.
- **Cross-service latency:** validate path adds a network hop (gateway → configuration-engine → rule projection from product-service).
- **Rule freshness:** configuration-engine must handle stale compiled rules when product-service publishes rule changes (S2B-9).

### Migration notes

| Phase | PR | Action |
|-------|-----|--------|
| Document | S2A-8 | This ADR; no compile changes |
| Matrix | S2A-7 | Register routes, owners, stability tiers in `CPQ_API_CONTRACT_MATRIX.md` |
| Implement | S2B-7 | ConfigurationSession, selections, validation result model |
| Implement | S2B-8 | Full rule-type evaluation in `ConstraintSolver`; consume rules from product-service |
| Implement | S2B-11 | **Deferred** (not Wave E). When scheduled: gateway route for `POST /api/v1/configuration/validate`; contract tests; harden rule CRUD on product-service; prefer `POST /internal/v1/configurations/evaluate` + `libs/common` DTO per frozen defaults |
| Consume | S2D-3a | Quote preflight calls validate via gateway/client — not inline logic |
| Retire | Post-S2B-11 | product-service `POST /api/v1/configuration/validate` may become internal-only or removed **only after** gateway shim proves parity in contract tests — **no removal date** at Accept |

**Response shape migration:** Current product-service validate returns `ApiResponse<List<String>>`. Agent PRDs expect structured violations. S2B-11 defines the canonical response DTO; gateway adapts for backward compatibility if needed.

---

## Service boundary table

| Capability | product-service | configuration-engine | gateway-service | quote-service |
|------------|:---------------:|:--------------------:|:---------------:|:-------------:|
| Rule CRUD (`/api/v1/configuration/rules*`) | **Owner** | — | Route (pass-through) | — |
| Rule persistence / soft-delete | **Owner** | — | — | — |
| Configuration session CRUD | — | **Owner** | Route | — |
| Session validate (`/api/v1/configurations/{id}/validate`) | — | **Owner** | Route | — |
| Constraint evaluation (REQUIRES…VALIDATES) | Transitional inline only | **Owner** | — | — |
| Agent validate (`POST /api/v1/configuration/validate`) | Legacy impl (deprecate) | **Evaluator** | **Contract owner** (route + adapt) | Client (preflight) |
| Product / option / attribute catalog APIs | **Owner** | Consumer (read) | Route | Client |
| Configuration committed snapshot / events | — | **Owner** (S2B-7+) | — | Consumer (S2D-2) |

---

## Sequence diagram: agent validate through gateway

```mermaid
sequenceDiagram
    autonumber
    participant Agent as CPQ Agent<br/>(015/016/002)
    participant GW as gateway-service
    participant CE as configuration-engine
    participant PS as product-service

    Agent->>GW: POST /api/v1/configuration/validate<br/>(configuration payload)
    GW->>GW: Auth, correlation ID,<br/>payload normalization

    alt Session ID present in payload
        GW->>CE: POST /api/v1/configurations/{id}/validate
        CE->>PS: GET rules (product scope)<br/>[cache or projection]
        PS-->>CE: ConfigurationRule[] 
        CE->>CE: ConstraintSolver.evaluate()
        CE-->>GW: ConfigurationSession<br/>(valid, validationErrors)
    else Stateless agent payload
        GW->>CE: POST /internal/v1/configurations/evaluate<br/>(S2B-11 — preferred stateless path)
        CE->>PS: GET /api/v1/configuration/rules/product/{productId}<br/>(sync REST first; cache = S2B-9)
        PS-->>CE: ConfigurationRule[]
        CE->>CE: ConstraintSolver.evaluate()
        CE-->>GW: ValidationResult (shared libs/common DTO)
    end

    GW->>GW: Map to agent PRD shape<br/>{ valid, violations[] }
    GW-->>Agent: 200 ApiResponse<br/>(structured pass/fail)

    Note over Agent,PS: Rule CRUD remains direct:<br/>POST/GET/DELETE /api/v1/configuration/rules* → product-service
```

---

## Acceptance criteria checklist (S2A-8)

Matches backlog amendment specifications in `GRAPHITE_CPQ_MICROSERVICE_BACKLOG.md`.

- [x] **AC-1:** ADR names canonical owner for **rule CRUD** → `product-service` (`/api/v1/configuration/rules*`).
- [x] **AC-2:** ADR names canonical owner for **session validate** → `configuration-engine` (`POST /api/v1/configurations/{id}/validate`).
- [x] **AC-3:** ADR names canonical owner for **agent validate** → external contract on `gateway-service`; evaluation delegated to `configuration-engine`.
- [x] **AC-4:** Explicit decision documented: **`POST /api/v1/configuration/validate` remains the agent contract path** (not replaced by session URL in agent PRDs).
- [x] **AC-5:** Explicit decision documented: **configuration-engine owns constraint evaluation**; **product-service owns rule persistence**.
- [x] **AC-6:** No breaking removal of product-service `/api/v1/configuration/*` routes without gateway shim — documented in migration notes.
- [x] **AC-7:** Service boundary table and validate-flow sequence diagram included.
- [x] **AC-8:** S2B-11 implementation scope referenced; Accept / S2A-8 introduce **no compile changes** (documentation only). S2B-11 code remains **deferred**.

**Verification (S2A-8 / E3 Accept):** ADR review + Status Accepted + frozen open-question defaults + **no** Java/product/gateway code changes.

---

## Related documents

| Document | Relevance |
|----------|-----------|
| `docs/backlog/GRAPHITE_CPQ_MICROSERVICE_BACKLOG.md` | S2A-8, S2B-11 acceptance criteria |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | S2A-7 route registry (still documents current `List<String>` validate shape until S2B-11) |
| `docs/forge/14-wave-e-architecture.md` | Wave E Accept authority; frozen open-question defaults |
| `docs/forge/impl/e3-adr-accept.md` | Forge E3 status note |
| `agents/compatibility-rules.md` | cpq-agent-015 validate + rule CRUD paths |
| `agents/validation-rules.md` | cpq-agent-016 validate delegation |
| `agents/option-feature-configuration.md` | cpq-agent-002 incremental + final validate |
| `backend/services/product-service/.../ConfigurationRuleController.java` | Current rule CRUD + validate (unchanged by Accept) |
| `backend/services/configuration-engine/.../ConfigurationSessionController.java` | Session validate |
| `backend/services/configuration-engine/.../ConstraintSolver.java` | Target evaluation engine |

---

## Resolved open questions (Wave E freeze for S2B-11)

Open questions below were **frozen at Accept** (2026-08-30) per `docs/forge/14-wave-e-architecture.md`. They constrain a **future** S2B-11 implementation wave. **S2B-11 implementation is deferred** — this Accept changes documentation only; **do not** land gateway validate shim, shared DTO types, configuration-engine evaluate endpoint, or product-service validate removal in the Accept PR.

| # | Question | Frozen default | Rejected |
|---|----------|----------------|----------|
| 1 | Stateless evaluator shape | Prefer **`POST /internal/v1/configurations/evaluate`** (stateless) on configuration-engine for agent payloads | Ephemeral-session-only as the long-term agent path |
| 2 | Response DTO canonical type | Shared DTO in **`backend/libs/common`** | Gateway-local DTOs as the long-term home |
| 3 | Rule projection mechanism | **Sync REST** rule fetch first (`GET /api/v1/configuration/rules/product/{productId}` and related) | Compiled cache / event invalidation as day-1 (that remains **S2B-9**) |
| 4 | product-service validate deprecation | **Retain** product-service `POST /api/v1/configuration/validate` until contract-test parity — **no removal date** until S2B-11 lands | Setting a calendar removal date at Accept |

**Runtime today (unchanged by Accept):** product-service validate remains authoritative; contract matrix still lists body `List<Long>` / response `List<String>` errors. Agent-facing `{ valid, violations[] }` and gateway shim land only when S2B-11 is scheduled.
