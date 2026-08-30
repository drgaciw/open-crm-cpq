# Forge orchestrator status

**Authority:** PM full autonomous forge (delegated)  
**Session state:** **Wave D EXECUTING** — security forge  
**Architect:** `/opencrm-architect` + `.cursor/agents/opencrm-architect.agent.md`  
**Plan:** `docs/forge/13-wave-d-architecture.md`  
**Herdr:** `forge-wave-d` — `wd-cors` ∥ `wd-secopen` → `wd-docs`  
**Workflow:** `forge-wave-d`

## Goal

Remove `security.open` bypass and gateway `*.azurewebsites.net` CORS/CSRF wildcards (canonical GitHub **#451** / **#458** families).

## Wave D progress

| ID | Work | Branch / worktree | Status |
|----|------|-------------------|--------|
| **D1** | Gateway CORS/CSRF explicit allow-list | `fix/forge-d1-gateway-cors-csrf` | **Pending** — not merged; wildcards still on tip |
| **D2** | Remove `security.open` permitAll bypass | `fix/forge-d2-remove-security-open` | **Pending** — not merged; flag still in 9 `SecurityConfig`s |
| **D3** | Security closeout docs + verification runbook | `docs/forge-d3-security-closeout` | **Partial** — `docs/forge/impl/d3-security-closeout.md` drafted; flip to done after D1+D2 + clean `rg` |
| **D4** (optional) | Comment/close duplicate Sentinel PRs | — | After D1/D2 merge |

## Completed waves (on main)

A (pricing fixtures, proxy), B (CRM/CPQ polish), C (quotes/data-access/shared-ui/sim/tests/N+1)

## Still held beyond D

Activities API, S2B-11, S2C-12, CI runners (#238), agent Kafka (#241)

## Closeout pointer

Post-merge verification commands and issue-family tables: [`docs/forge/impl/d3-security-closeout.md`](./impl/d3-security-closeout.md)
