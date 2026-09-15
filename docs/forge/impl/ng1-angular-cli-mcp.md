---
status: done
id: ng1-angular-cli-mcp
wave: NG
pr: NG1
---

# ng1-angular-cli-mcp

## Summary

NG1 only: configure the Angular CLI MCP server for agents. No app runtime change. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. No NgRx, Activities API, #238/#241, R2, subscriptions FE, GLM-5 runtime, `frontend/package.json`, CI, `docs/ARCHITECTURE.md`, or `docs/forge/00-orchestrator-status.md`.

## GitNexus (pre-edit)

| Target | Risk | Notes |
|--------|------|-------|
| `mcpServers` | **UNKNOWN** (not a symbol) | `impact` error: target not found |
| `angular-cli` | **UNKNOWN** (not a symbol) | same |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/ng1`  
Index: `04909db` (2026-09-13), behind HEAD `e387cf0`

UNKNOWN ≠ unused. `rg` confirmed **no** existing `mcp.json` / `mcpServers` in committed JSON. New Cursor MCP json + this impl note only → treat as **LOW**. Did not skip the walk; did not treat empty callers as proof of unused application code.

## Files

| Path | Change |
|------|--------|
| `.cursor/mcp.json` | Cursor stdio MCP: `npx -y @angular/cli mcp`, `cwd` `frontend` |
| `frontend/.cursor/mcp.json` | Same argv, **no** `cwd` — hosts that open `frontend/` as the workspace (or ignore root `cwd`) |
| `docs/forge/impl/ng1-angular-cli-mcp.md` | This note |

Did **not** add `frontend/.vscode/mcp.json` (repo `.gitignore` ignores `.vscode/`). Did **not** add `frontend/angular.json` (NG2 exclusive surface / Nx migrate). Did **not** edit `frontend/package.json`, lockfile, `nx.json`, CI, `ARCHITECTURE.md`, or `backend/**`.

## MCP argv (exact)

Root (Cursor / Grok opening the monorepo):

```text
command: npx
args:    ["-y", "@angular/cli", "mcp"]
cwd:     frontend
type:    stdio
```

Shell equivalent:

```bash
cd frontend
npx -y @angular/cli mcp
```

`ng mcp --help` (CLI 22.1.8 via `npx`) exposes only `--read-only` and `--local-only`. **No** `--experimental-tool` / `-E` flags. The eight required tools are **stable defaults**.

After NG2 lands CLI 22 on `frontend/node_modules`, agents may switch to `frontend/node_modules/.bin/ng mcp` **or** keep `npx -y @angular/cli@22 mcp` so a global 21 is not picked. NG1 keeps the unpinned `npx -y @angular/cli mcp` from the architect snippet.

## Tools advertised (MCP `tools/list`)

Smoke: JSON-RPC `initialize` + `tools/list` against `npx -y @angular/cli mcp` from `frontend/` (this worktree has **no** `frontend/node_modules`; npx resolved **@angular/cli 22.1.8**, `serverInfo.name` = `angular-cli-server`).

| Required (plan / ticket) | Advertised name | Present |
|--------------------------|-----------------|---------|
| `get_best_practices` | `get_best_practices` | yes |
| `onpush_zoneless_migration` | `onpush_zoneless_migration` | yes |
| `devserver.start` | `devserver_start` | yes |
| `devserver.stop` | `devserver_stop` | yes |
| `wait_for_build` / `devserver.wait_for_build` | `devserver_wait_for_build` | yes |
| `run_target` | `run_target` | yes |
| `search_documentation` | `search_documentation` | yes |
| `list_projects` | `list_projects` | yes |

Also advertised (not required): `ai_tutor`.

`ng mcp` help does **not** print tool names; MCP `tools/list` is the SSOT. Dotted names in [angular.dev/ai/mcp](https://angular.dev/ai/mcp) map to underscored MCP tool names on 22.1.8.

## `list_projects` / `devserver.*` / `run_target`

`list_projects` `inputSchema` is `{}` (no workspace-path argument). Smoke `tools/call` from `frontend/`:

```text
No Angular workspace found. An `angular.json` file, which marks the root of a
workspace, could not be located in the current directory or any of its parent
directories.
```

Nx workspace uses `project.json` only. **Do not fake projects** in NG1. A minimal `frontend/angular.json` is an NG2 decision (`search_documentation` / `nx migrate`), not this PR.

`devserver.start` / `devserver.stop` / `devserver.wait_for_build` / `run_target` are advertised. Invoking them may require a running workspace / `node_modules`; NG1 acceptance is **advertised**, not a live `ng serve`.

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}` — YAML/env untouched
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime; Track 2 Spring AI FE
- NG2 Angular 22 + Nx 23 (`frontend/package.json`); NG3/NG4; X1 `ARCHITECTURE.md`
- `docs/forge/00-orchestrator-status.md`

## Acceptance

- [x] `.cursor/mcp.json` (+ optional `frontend/.cursor/mcp.json`) committed
- [x] `npx -y @angular/cli mcp` starts (stdio MCP `initialize` OK; CLI **22.1.8**)
- [x] Eight required tools advertised (`tools/list`)
- [x] No app runtime change
- [x] `NX_DAEMON=false` not required (did not run nx)
