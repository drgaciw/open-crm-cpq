# Architecture decision records

Canonical ADR directory for Open CRM/CPQ. Historical Wave E published ADR-004 under `docs/ADR-004-*.md`; that path is now a **stub**.

| ID | Title | Status | Path |
|----|-------|--------|------|
| ADR-001 | Primary LLM — Claude Sonnet 4.6 | **Superseded** by ADR-002 | [ADR-001-primary-llm-claude-sonnet-4-6.md](./ADR-001-primary-llm-claude-sonnet-4-6.md) |
| ADR-002 | Primary LLM — GLM-5 via Zhipu | **Accepted** (decision); **runtime not landed** (Azure OpenAI gpt-4o) | [ADR-002-primary-llm-glm-5.md](./ADR-002-primary-llm-glm-5.md) |
| ADR-003 | CI LLM integration / Jenkins primary | **Accepted** (matches Jenkins + GHA manual fallback) | [ADR-003-ci-llm-integration.md](./ADR-003-ci-llm-integration.md) |
| ADR-004 | CSRF edge-only (gateway vs STATELESS) | **Accepted**; E2 inventory current | [ADR-004-csrf-edge-boundary.md](./ADR-004-csrf-edge-boundary.md) |
| ADR-005 | Approvals legacy dual-read default `true` | **Proposed** — do **not** flip YAML | [ADR-005-approvals-legacy-dual-read.md](./ADR-005-approvals-legacy-dual-read.md) |
| ADR-006 | Entities never at API boundary | **Proposed** — `Subscription` leak | [ADR-006-entities-never-at-api-boundary.md](./ADR-006-entities-never-at-api-boundary.md) |

Related (not numbered in this folder):

| Doc | Status | Path |
|-----|--------|------|
| Configuration API reconciliation (S2A-8 / S2B-11) | **Accepted**; hybrid Wave F landed; product validate **retained** | [docs/backlog/ADR-configuration-api-reconciliation.md](../backlog/ADR-configuration-api-reconciliation.md) |
| GitNexus ADR review (2026-08-31) | Review table | [docs/forge/24-adr-gitnexus-review.md](../forge/24-adr-gitnexus-review.md) |

**Rules:** Status **Proposed** is documentation-only. Do not Accept dual-read flip or treat GLM-5 as production runtime until implementation PRs land. CSRF `SecurityConfig` GitNexus is often **UNKNOWN** — confirm with `rg`.
