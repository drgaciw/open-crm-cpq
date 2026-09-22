# Architecture decision records

Canonical ADR directory for Open CRM/CPQ. Historical Wave E published ADR-004 under `docs/ADR-004-*.md`; that path is now a **stub**.

## Numbered ADRs

| ID | Title | Status | Path |
|----|-------|--------|------|
| ADR-001 | Primary LLM — Claude Sonnet 4.6 | **Superseded** by ADR-002 | [ADR-001-primary-llm-claude-sonnet-4-6.md](./ADR-001-primary-llm-claude-sonnet-4-6.md) |
| ADR-002 | Primary LLM — GLM-5 via Zhipu | **Superseded** by ADR-014; never implemented | [ADR-002-primary-llm-glm-5.md](./ADR-002-primary-llm-glm-5.md) |
| ADR-003 | CI LLM integration / Jenkins primary | **Accepted** (matches Jenkins + GHA manual fallback) | [ADR-003-ci-llm-integration.md](./ADR-003-ci-llm-integration.md) |
| ADR-004 | CSRF edge-only (gateway vs STATELESS) | **Accepted**; E2 inventory current | [ADR-004-csrf-edge-boundary.md](./ADR-004-csrf-edge-boundary.md) |
| ADR-005 | Approvals legacy dual-read default `true` | **Proposed** — do **not** flip YAML | [ADR-005-approvals-legacy-dual-read.md](./ADR-005-approvals-legacy-dual-read.md) |
| ADR-006 | Entities never at API boundary | **Accepted** — O3 + W1–W3 **on `main`** (subscription / configuration-session / approval-routing / document-job DTOs; **no** subscriptions FE) | [ADR-006-entities-never-at-api-boundary.md](./ADR-006-entities-never-at-api-boundary.md) |
| ADR-007 | FE state — signals + HttpClient, no state library | **Accepted** (reverse-engineered) | [ADR-007-frontend-signals-no-state-library.md](./ADR-007-frontend-signals-no-state-library.md) |
| ADR-008 | One PostgreSQL DB, schema-per-service | **Accepted** (reverse-engineered) | [ADR-008-postgres-schema-per-service.md](./ADR-008-postgres-schema-per-service.md) |
| ADR-009 | Gateway edge-only — routing/aliasing/resilience, no domain logic | **Accepted** (reverse-engineered) | [ADR-009-gateway-edge-only.md](./ADR-009-gateway-edge-only.md) |
| ADR-010 | Kafka integration — transactional outbox + versioned `v1` events | **Accepted** (reverse-engineered) | [ADR-010-kafka-outbox-events.md](./ADR-010-kafka-outbox-events.md) |
| ADR-011 | Shared Maven modules as published language | **Accepted** (reverse-engineered) | [ADR-011-shared-maven-libs-published-language.md](./ADR-011-shared-maven-libs-published-language.md) |
| ADR-012 | Rules engine port — Drools landed, OpenL/SpEL deferred, CE shared core | **Accepted** (reverse-engineered) | [ADR-012-rules-engine-port-abstraction.md](./ADR-012-rules-engine-port-abstraction.md) |
| ADR-013 | Quote-service approvals BFF — single-writer routing | **Accepted** (reverse-engineered) | [ADR-013-quote-bff-approvals-single-writer.md](./ADR-013-quote-bff-approvals-single-writer.md) |
| ADR-014 | Jev-driven OpenRouter model selection | **Accepted** (decision); implementation in backlog; runtime remains Azure `gpt-4o` | [ADR-014-jev-openrouter-model-selection.md](./ADR-014-jev-openrouter-model-selection.md) |
| ADR-015 | Jev as advisory, shadow-mode decision support in domain services | **Accepted** (decision); code lands flag-off with no-op transport; not runtime behavior | [ADR-015-jev-advisory-decision-support.md](./ADR-015-jev-advisory-decision-support.md) |
| ADR-016 | Identity, authentication and service-to-service trust (Entra ID, gateway BFF, token relay, per-service JWT, `/internal` service tokens, actor integrity) | **Proposed** — documentation only; changes no `SecurityConfig` | [ADR-016-identity-authentication-service-trust.md](./ADR-016-identity-authentication-service-trust.md) |
| ADR-017 | Synchronous inter-service communication | **Reserved** — not drafted (see [review §3](../forge/39-adr-gitnexus-review-2026-09-22.md)) | — |
| ADR-018 | Event delivery guarantees, envelope and idempotent consumers (amends ADR-010) | **Proposed** — documentation only; S2B-9/#241 stay held | [ADR-018-event-delivery-guarantees-idempotency.md](./ADR-018-event-delivery-guarantees-idempotency.md) |

**About ADR-007…013 (reverse-engineered):** these were authored 2026-09-19 by a reverse-engineering pass ([docs/ARCHITECTURE.md](../ARCHITECTURE.md)) that documented decisions already embedded and verified in `main` — they record **landed behavior, not new direction**, and every evidence claim cites files on the tree. They are **not** authorizations: NgRx stays held (007), no new schemas (008), no gateway domain logic (009), S2B-9/#241 stay held (010), no business logic in libs (011), SpEL/OpenL/S2B-8/S2B-9 stay held (012), dual-read flip stays gated by ADR-005/J5 (013).

Related (not numbered in this folder — the configuration reconciliation ADR keeps its backlog path):

| Doc | Status | Path |
|-----|--------|------|
| Configuration API reconciliation (S2A-8 / S2B-11) | **Accepted**; hybrid Wave F landed; product validate **retained** | [docs/backlog/ADR-configuration-api-reconciliation.md](../backlog/ADR-configuration-api-reconciliation.md) |
| GitNexus ADR review | Table + 2026-08-31 addendum | [docs/forge/24-adr-gitnexus-review.md](../forge/24-adr-gitnexus-review.md) |
| **ADR implementation verification (GitNexus + Jev)** | Historical 13-ADR evaluation, predating ADR-014; not verification of the new model-selection decision | [ADR-IMPLEMENTATION-VERIFICATION-2026-09-19.md](./ADR-IMPLEMENTATION-VERIFICATION-2026-09-19.md) |
| Jev/OpenRouter selection requirements | **Backlog** — catalog/pricing, qualification, selection, execution, and rollout criteria | [REQ-jev-openrouter-model-selection.md](../backlog/REQ-jev-openrouter-model-selection.md) |
| Architecture index | Landed vs target navigation | [docs/ARCHITECTURE.md](../ARCHITECTURE.md) |

**Rules:** Status **Proposed** is documentation-only. Do **not** Accept dual-read flip (ADR-005). ADR-006 is **Accepted** (W1–W3 entity leaks gone) — still **do not** ship subscriptions FE from this ADR. ADR-014 replaces the fixed GLM-5 direction; Jev/OpenRouter model selection is backlog, while Azure OpenAI `gpt-4o` remains configured in `ai-service`. ADR-015 un-holds **only** advisory + shadow Jev code in domain services (every flag `false`, no-op transport); it does not un-hold ADR-014 selection, and enabling a flag beyond local/offline needs its shadow → canary gates. ADR-016 and ADR-018 are **Proposed**, which is documentation-only. They do not authorize security, outbox, consumer or Redis-channel changes. ADR-018 records that ADR-010's delivery claims are not met on HEAD, so treat ADR-010 as direction only until ADR-018 is decided. CSRF `SecurityConfig` GitNexus is often **UNKNOWN** — confirm with `rg`.

## Code verification (reverse-engineering pass, 2026-09-19)

The original ADR-001–013 records were reviewed against the working tree on 2026-09-19 (evidence in each file; summary in [ARCHITECTURE.md §16](../ARCHITECTURE.md)). ADR-014 was accepted afterward and is not implemented:

| ADR | Claim | Code evidence (2026-09-19) | Match |
|-----|-------|---------------------------|-------|
| ADR-001 | Superseded by ADR-002 | No Anthropic starter in tree | ✅ |
| ADR-002 | Superseded by ADR-014; GLM-5 never landed | `ai-service/application.yml` — `spring.ai.azure.openai`, `deployment-name: gpt-4o` | Historical direction superseded |
| ADR-014 | Jev selects qualifying OpenRouter models at lowest total cost | Requirements recorded; backend selector/execution not implemented | Backlog |
| ADR-003 | Jenkins primary, GHA `workflow_dispatch` only | `docs/ci-contract.yaml` (`execution.primary: jenkins`, GHA `push_branches: []`) | ✅ |
| ADR-004 | Gateway CSRF ON; domain `csrf.disable()` + STATELESS | gateway `SecurityConfig` (`CookieServerCsrfTokenRepository`); crm `SecurityConfig:34-35,44-45` | ✅ |
| ADR-005 | Flag default still `true`; Proposed | `quote-service/application.yml:89`; `ApprovalService` `@Value` + 7 branches | ✅ |
| ADR-006 | No entity leaks at `/api/v1/**` | grep: zero `ApiResponse<{Subscription,ConfigurationSession,ApprovalRequest,DocumentGenerationJob}>` (non-Dto) in controllers | ✅ |
| backlog ADR | Product validate retained; gateway → CE evaluate | gateway route order (`configuration-validate` before `product-service`), `ConfigurationValidationResult` in `libs/common` | ✅ |

**New drift found during verification (recorded, not authorized to fix):** ai-service `server.port: 8086` collides with document-generation `:8086`; document-generation defaults to DB `cpq_document` instead of shared `opencrm` — see [ARCHITECTURE.md §16](../ARCHITECTURE.md).
