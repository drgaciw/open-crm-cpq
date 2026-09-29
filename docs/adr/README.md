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
| ADR-014 | Jev-driven OpenRouter model selection | **Accepted** (decision); implementation behind a disabled flag; live rollout pending; runtime remains Azure `gpt-4o` | [ADR-014-jev-openrouter-model-selection.md](./ADR-014-jev-openrouter-model-selection.md) |
| ADR-015 | Jev as advisory, shadow-mode decision support in domain services | **Accepted** (decision); code lands flag-off with no-op transport; not runtime behavior | [ADR-015-jev-advisory-decision-support.md](./ADR-015-jev-advisory-decision-support.md) |
| ADR-016 | Identity, authentication and service-to-service trust (Entra ID, gateway BFF, token relay, per-service JWT, `/internal` service tokens, actor integrity) | **Accepted** 2026-09-23 — implemented in rollout order (steps 0–5 done, owner decisions recorded 2026-09-28); going live needs the [runbook](../runbooks/identity-go-live.md) | [ADR-016-identity-authentication-service-trust.md](./ADR-016-identity-authentication-service-trust.md) |
| ADR-017 | Synchronous inter-service communication | **Reserved** — not drafted (see [review §3](../forge/39-adr-gitnexus-review-2026-09-22.md)) | — |
| ADR-018 | Event delivery guarantees, envelope and idempotent consumers (amends ADR-010) | **Accepted** 2026-09-28 (owner decisions delegated) — D1–D10 implemented; the D4 contract step and D8 step 3 are ahead; S2B-9/#241 stay held. See the [event runbook](../runbooks/event-operations.md) | [ADR-018-event-delivery-guarantees-idempotency.md](./ADR-018-event-delivery-guarantees-idempotency.md) |
| ADR-023 | Price ramp schedule model (per-line `RampStep` schedule, ramp metric definitions, whole-month periods, exit-rate renewal base, rate schedule hosted in contract-asset-management) | **Proposed** 2026-09-29 — documentation only. D-02 needs finance sign-off and the D-03 ACV decision in the same review. ADR-017 to ADR-022 stay reserved for other topics | [ADR-023-price-ramp-schedule-model.md](./ADR-023-price-ramp-schedule-model.md) |

**About ADR-007…013 (reverse-engineered):** these were authored 2026-09-19 by a reverse-engineering pass ([docs/ARCHITECTURE.md](../ARCHITECTURE.md)) that documented decisions already embedded and verified in `main` — they record **landed behavior, not new direction**, and every evidence claim cites files on the tree. They are **not** authorizations: NgRx stays held (007), no new schemas (008), no gateway domain logic (009), S2B-9/#241 stay held (010), no business logic in libs (011), SpEL/OpenL/S2B-8/S2B-9 stay held (012), dual-read flip stays gated by ADR-005/J5 (013).

Related (not numbered in this folder — the configuration reconciliation ADR keeps its backlog path):

| Doc | Status | Path |
|-----|--------|------|
| Configuration API reconciliation (S2A-8 / S2B-11) | **Accepted**; hybrid Wave F landed; product validate **retained** | [docs/backlog/ADR-configuration-api-reconciliation.md](../backlog/ADR-configuration-api-reconciliation.md) |
| GitNexus ADR review | Table + 2026-08-31 addendum | [docs/forge/24-adr-gitnexus-review.md](../forge/24-adr-gitnexus-review.md) |
| **ADR implementation verification (GitNexus + Jev)** | Historical 13-ADR evaluation, predating ADR-014; not verification of the new model-selection decision | [ADR-IMPLEMENTATION-VERIFICATION-2026-09-19.md](./ADR-IMPLEMENTATION-VERIFICATION-2026-09-19.md) |
| Jev/OpenRouter selection requirements | **Backlog** — catalog/pricing, qualification, selection, execution, and rollout criteria | [REQ-jev-openrouter-model-selection.md](../backlog/REQ-jev-openrouter-model-selection.md) |
| Price ramping requirements | **Backlog (Draft)** — per-period pricing, governance, contract rate schedule; D-01, D-02, D-04, D-05 and D-08 are drafted in [ADR-023](./ADR-023-price-ramp-schedule-model.md) (Proposed); D-03, D-06 and D-07 remain open | [REQ-price-ramping.md](../backlog/REQ-price-ramping.md) |
| Architecture index | Landed vs target navigation | [docs/ARCHITECTURE.md](../ARCHITECTURE.md) |

**Rules:** Status **Proposed** is documentation-only. Do **not** Accept dual-read flip (ADR-005). ADR-006 is **Accepted** (W1–W3 entity leaks gone) — still **do not** ship subscriptions FE from this ADR. ADR-014 replaces the fixed GLM-5 direction; Jev/OpenRouter model selection is implemented behind a disabled flag, while Azure OpenAI `gpt-4o` remains configured in `ai-service`. ADR-015 un-holds **only** advisory + shadow Jev code in domain services (every flag `false`, no-op transport); it does not un-hold ADR-014 selection or activation, and enabling a flag beyond local/offline needs its shadow → canary gates. ADR-016 (accepted 2026-09-23) and ADR-018 (accepted 2026-09-28) are implemented. What remains is environment work (the [identity go-live runbook](../runbooks/identity-go-live.md) and the [event operations runbook](../runbooks/event-operations.md)) and two staged ADR-018 steps, the D4 contract step and D8 step 3. The outbox relay stays off by default (`opencrm.outbox.relay.enabled=false`) until an environment has decided its backlog. ADR-018 replaces ADR-010's delivery, versioning and consumer rules. CSRF `SecurityConfig` GitNexus is often **UNKNOWN** — confirm with `rg`.

## Code verification (reverse-engineering pass, 2026-09-19)

The original ADR-001–013 records were reviewed against the working tree on 2026-09-19 (evidence in each file; summary in [ARCHITECTURE.md §16](../ARCHITECTURE.md)). ADR-014 was accepted afterward; its implementation is now behind a disabled flag with live qualification outstanding:

| ADR | Claim | Code evidence (2026-09-19) | Match |
|-----|-------|---------------------------|-------|
| ADR-001 | Superseded by ADR-002 | No Anthropic starter in tree | ✅ |
| ADR-002 | Superseded by ADR-014; GLM-5 never landed | `ai-service/application.yml` — `spring.ai.azure.openai`, `deployment-name: gpt-4o` | Historical direction superseded |
| ADR-014 | Jev selects qualifying OpenRouter models at lowest total cost | Catalog, Jev evaluation, selector and gated execution implemented; offline tests | Live qualification pending |
| ADR-003 | Jenkins primary, GHA `workflow_dispatch` only | `docs/ci-contract.yaml` (`execution.primary: jenkins`, GHA `push_branches: []`) | ✅ |
| ADR-004 | Gateway CSRF ON; domain `csrf.disable()` + STATELESS | gateway `SecurityConfig` (`CookieServerCsrfTokenRepository`); crm `SecurityConfig:34-35,44-45` | ✅ |
| ADR-005 | Flag default still `true`; Proposed | `quote-service/application.yml:89`; `ApprovalService` `@Value` + 7 branches | ✅ |
| ADR-006 | No entity leaks at `/api/v1/**` | grep: zero `ApiResponse<{Subscription,ConfigurationSession,ApprovalRequest,DocumentGenerationJob}>` (non-Dto) in controllers | ✅ |
| backlog ADR | Product validate retained; gateway → CE evaluate | gateway route order (`configuration-validate` before `product-service`), `ConfigurationValidationResult` in `libs/common` | ✅ |

**Follow-up implementation:** AI now uses `8090`; four standalone service defaults are aligned to isolated `opencrm` schemas; canonical approval history retains completed decisions. Existing-data cutovers remain separate. See [implementation evidence and rollout gates](../adr-gap-implementation.md).
