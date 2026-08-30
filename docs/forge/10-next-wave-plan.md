# Next forge wave plan (ready on MCP reconnect)

**Status:** Wave A + Wave B MERGED to main  
**Chosen wave (PM):** Finish WIP, then Batch 2 leftovers  
**Model:** Grok 4.5 via Herdr  
**Autonomy:** Full after reconnect (prior PM delegation + this choice)

## Blockers (this session)

| Integration | State |
|-------------|--------|
| Linear MCP | Not connected (`search_tool` empty) |
| Notion MCP | Not connected |
| GitNexus MCP | Not connected (CLI index can be refreshed locally) |

**Resume condition:** MCP servers available → re-query Linear/Notion backlog → reconcile with this plan → `/goal` loop + Herdr forge.

## Recommended execution order

### Wave A — Finish WIP (2 panes, parallel)

| ID | Branch (already on origin) | Task | Acceptance |
|----|----------------------------|------|------------|
| A1 | `test/forge-b1-pricing-execute-fixtures` | Finish pricing execute golden fixtures | Docker-free unit/fixture tests for `POST /api/v1/pricing/execute`; status `docs/forge/impl/b1-pricing-fixtures.md`; merge to main |
| A2 | `chore/forge-b2-proxy-gateway-8080` | Finish FE proxy → gateway `:8080` | `proxy.conf.json` + README override for `:8081`; status `docs/forge/impl/b2-proxy-gateway.md`; merge to main |

### Wave B — Batch 2 leftovers (6 panes, parallel after A merges or conflict-free)

| ID | Task | Notes |
|----|------|-------|
| B1 | Deal pipeline UX (PR-FE-3) | win/lose/advance wiring + Vitest |
| B2 | CPQ shared TS models (PR-FE-CPQ-02) | product/quote/pricing models on `libs/cpq` / shared |
| B3 | Staging smoke scripts | CRM gateway + quote-accept idempotency notes |
| B4 | Gateway ERP 501 contract tests | Expand Docker-free unit coverage |
| B5 | CRM parity docs + activities deferral | Docs only |
| B6 | (flex) Pricing fixture follow-up OR CPQ catalog list stub | Only if A1 done early |

### Hold (do not start without ADR)

- Activities API implementation  
- S2B-11 configuration validate compatibility  
- S2C-12 quote → approval-routing BFF  

## Orchestration recipe (on resume)

1. **Linear:** list open issues / current sprint for Open CRM CPQ; map to Wave A/B.  
2. **Notion:** pull any roadmap / cutover page status; note conflicts with forge backlog.  
3. **GitNexus:** `gitnexus_query` / `gitnexus_impact` before edits; refresh index if stale.  
4. **Write/update** this plan + `docs/forge/00-orchestrator-status.md`.  
5. **`/goal`** (or workflow `forge-autonomous-loop`) with objective covering Wave A then B.  
6. **Herdr:** recreate `forge-impl` workspace; 2 then 6 Grok 4.5 panes with worktrees; `--always-approve`.  
7. Merge locally → push when green.

## Goal text (paste into `/goal` on resume)

```text
Finish Open CRM/CPQ forge Wave A (pricing golden fixtures + proxy:8080 WIP branches), merge to main, then complete Wave B in parallel: deal pipeline UX, CPQ shared models, staging smokes, gateway ERP 501 tests, CRM parity/activities deferral docs. Use GitNexus impact before symbol edits. Do not implement activities API, S2B-11, or S2C-12. Prefer worktree-isolated changes; push only after local verify. Evidence: merged commits, docs/forge/impl/*.md status done, and relevant unit tests green.
```

## Local backlog sources (until MCP returns)

- `docs/forge/07-wave-backlog.md`  
- `docs/forge/09-workflow-wave-plan.md`  
- `docs/CRM_CUTOVER_CHECKLIST.md`  
- `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`  
