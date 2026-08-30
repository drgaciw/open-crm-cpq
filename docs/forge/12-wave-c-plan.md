# Wave C plan — Safe CPQ/CRM polish

**Status:** EXECUTING  
**Sources:** GitHub issues (`gh`), forge backlog, post–Wave A/B `main` @ `24c468f`  
**GitNexus MCP:** offline this session — use CLI index + grep for blast radius  
**Model:** Grok 4.5 / Herdr max parallel  

## GitHub scan (relevant open issues)

| Issue | Theme | Wave C action |
|-------|--------|----------------|
| #456 / #454 / #452 / #450 | PriceBook N+1 in `PriceBookInternalResolutionService` | **C6** — fix once (dedupe Bolt dupes) |
| #458 / #457 / #455 / #453 / #449 | CORS/CSRF wildcards (HIGH) | **Defer** — security wave, not this polish |
| #451 | Auth bypass CRITICAL | **Defer** — dedicated security wave |
| #238 | CI runners never acquire | **Defer** — infra |
| #241 | Migrate remaining simulated CPQ agents | **Defer** — agent stack |

## Wave C work items (6 parallel)

| ID | Title | Acceptance |
|----|-------|------------|
| **C1** | Quotes list/detail stub (`libs/cpq/quotes`) | Routes + shared-ui list; mock data OK; Vitest smoke |
| **C2** | CPQ data-access HTTP services | Catalog/quotes/pricing clients via gateway `/api`; unit tests with HttpClientTesting |
| **C3** | Shared-ui adoption on CRM leads + contacts lists | Use page header/search/empty/status badge where missing; no behavior regressions |
| **C4** | Pricing simulator stub UI | Minimal simulator page in `libs/cpq/pricing-simulator`; wire route optional |
| **C5** | FE test hardening for Wave B surfaces | `nx` tests green for deals/catalog/shared-models (fix failures only) |
| **C6** | Fix PriceBook N+1 (`PriceBookInternalResolutionService`) | Batch/join fetch; unit/IT evidence; closes intent of gh #456 family |

## Hold

- Activities API, S2B-11, S2C-12  
- CORS/CSRF wildcard cluster  
- Auth bypass CRITICAL  

## Goal (for `/goal` / orchestrator loop)

```text
Execute Open CRM/CPQ Wave C safe polish: (1) quotes list stub, (2) CPQ data-access HTTP clients, (3) shared-ui on CRM leads/contacts lists, (4) pricing simulator stub, (5) fix Wave B FE test failures, (6) PriceBookInternalResolutionService N+1. Use worktree-isolated Grok agents. Do not implement activities API, S2B-11, S2C-12, or CORS wildcard rewrites. Evidence: docs/forge/impl/c*.md status done, local commits, relevant nx/mvn unit tests green, then merge to main and push.
```

## Herdr layout

Workspace `forge-wave-c` — 6 tabs, Grok 4.5 `--always-approve`, one worktree each.
