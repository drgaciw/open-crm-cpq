# ADR-011: Shared Maven modules as the published language (`common`, `security`, `rules-engine`)

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — the libs exist and are versioned with the reactor; the "why" was never written down)*
**Date (decision embedded):** ~2026-08 (foundation wave A) · **Documented:** 2026-09-19
**Deciders:** forge foundation wave (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR — documents the landed shared-library contract. Includes CI governance: shared libraries are verified as the first functional stage (`shared-libraries` in `docs/ci-contract.yaml`), making them the change-propagation gate for the reactor.

---

## Context

Eleven services need identical conventions (response envelope, auditing, soft delete, money math, error taxonomy, user context) and two services need a common rules abstraction. Two failure modes were possible: (a) copy-paste conventions per service → drift; (b) a heavyweight platform framework → upgrade lockstep for every change.

Verified on the tree (2026-09-19):

- `backend/pom.xml` declares exactly three `libs/*` modules, managed at `${project.version}`: **common**, **security**, **rules-engine**.
- `libs/common`: `ApiResponse<T>` (envelope with `traceId`/`PageInfo`), `BaseEntity` (UUID + audit + `@Version` + `is_deleted` soft delete), `BusinessException`/`ResourceNotFoundException`, `Money`/`Percentage` value objects, `ConfigurationValidationResult` + `ConfigurationViolation` (+ `ConfigurationValidityAuthority`), the whole event/outbox published language ([ADR-010](ADR-010-kafka-outbox-events.md)).
- `libs/security`: `UserContext`, `SecurityUtils` (authentication context used via `SecurityUtils.getCurrentUser()` convention).
- `libs/rules-engine`: `RuleEnginePort`/`HybridRuleEnginePort`, `RulesEngineType`, Drools implementation + shared fact model ([ADR-012](ADR-012-rules-engine-port-abstraction.md)).
- Lombok + MapStruct processors configured **once** in the parent `pluginManagement`; services inherit.
- CI: `shared-libraries` stage runs first (Jenkins + GHA), before `backend-unit`/`backend-integration` — a libs break fails the pipeline before any service build.

## Decision

**Cross-service contracts and conventions live in three shared Maven modules — `libs/common` (transport + persistence + event published language), `libs/security` (user context), `libs/rules-engine` (rule port + facts) — versioned with the reactor and compiled against the same Java 25 toolchain. Services depend on libs, never on each other's code; libs stay convention-carriers, not business logic.**

1. `libs/common` is the **only** home for: `ApiResponse`, `BaseEntity`, shared exceptions, `Money`/`Percentage`, `ConfigurationValidationResult`, Kafka event DTOs/outbox. Gateways and services must import, not redeclare.
2. `BaseEntity` is mandatory for new entities (audit, optimistic locking, soft delete, UUID identity); DTOs derived via MapStruct must consciously exclude `deleted`/audit fields ([ADR-006](ADR-006-entities-never-at-api-boundary.md)).
3. No service-to-service Maven dependencies; reuse happens through libs or published contracts (HTTP/Kafka).
4. Lib changes propagate to all consumers in the same reactor build — CI ordering (`shared-libraries` first) is the enforcement mechanism; breaking a lib fails fast.
5. Libs contain no Spring Boot starters that dictate runtime topology beyond what consumers opt into (e.g., rules-engine auto-configuration is opt-in via `RulesEngineAutoConfiguration`).

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Three focused shared modules in-reactor (landed)** | One-place conventions, fast iteration (no artifact repo publishing), CI-gated. Con: all services rebuild on lib change; version skew impossible — which is also a feature. |
| B. Per-service duplication | Guaranteed drift of envelope/error/audit semantics; rejected. |
| C. Separate versioned platform library (artifact repo) | cleaner decoupling but premature: no external consumers, slows forge cadence; revisit at AKS/extraction time. Not chosen. |
| D. Single fat "platform" module | Would force rules/event/persistence deps onto every service; rejected in favor of three narrow modules. |

## Consequences

- **Positive:** `ApiResponse`/`BaseEntity`/event contracts are byte-identical across services; one MapStruct/Lombok toolchain; pipeline fails at `shared-libraries` before service builds; new services inherit conventions by adding three deps.
- **Negative / residual:** Lib changes are reactor-wide (no partial rollout); the "no business logic in libs" line needs review discipline (the event DTO set is the biggest gravity well); Java 25 toolchain is locked to the parent.
- **Boot-4 note:** Azure autoconfig excludes in the parent Surefire config exist to keep lib tests Docker/Azure-free on Boot 4; do not remove them ([ARCHITECTURE.md §16](../ARCHITECTURE.md)).

## Verification evidence (2026-09-19)

- `backend/pom.xml:21-36` (module list), `:104-118` (internal dependencyManagement), `:151-177` (annotation processors).
- File inventory of `backend/libs/{common,security,rules-engine}` (see [ARCHITECTURE.md §5](../ARCHITECTURE.md) evidence table).
- `docs/ci-contract.yaml` `scripts_ci.stages` — `shared-libraries` blocking, runs before `backend-unit`; GHA `backend-integration.needs: [shared-libraries, backend-unit]`.

## References

- [docs/ARCHITECTURE.md §5](../ARCHITECTURE.md) — cross-cutting conventions
- [docs/adr/ADR-010-kafka-outbox-events.md](ADR-010-kafka-outbox-events.md) — event published language in `libs/common`
- [docs/adr/ADR-012-rules-engine-port-abstraction.md](ADR-012-rules-engine-port-abstraction.md) — rules port in `libs/rules-engine`
- [docs/adr/ADR-006-entities-never-at-api-boundary.md](ADR-006-entities-never-at-api-boundary.md) — DTO derivation rules
