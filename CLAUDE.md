<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **open-crm-cpq** (28744 symbols, 61640 relationships, 815 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact before editing.** Use `impact({target: "symbolName", direction: "upstream"})` or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .`; report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- MUST warn on HIGH/CRITICAL `risk` pre-edit; never use `riskSharedAxes` to waive a HIGH/CRITICAL `risk` warning. Compare File/symbol: MCP File omits axes; Graph-RAG expands File.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- **MUST use `query({search_query: "concept"})` for concepts/flows, `context({name: "symbolName"})` for a named symbol, or `impact` for blast radius, on read-only callers, dependencies, imports, or execution flow.** Graph first; text search only for empty/`UNKNOWN`/literals.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/open-crm-cpq/context` | Codebase overview, check index freshness |
| `gitnexus://repo/open-crm-cpq/clusters` | All functional areas |
| `gitnexus://repo/open-crm-cpq/processes` | All execution flows |
| `gitnexus://repo/open-crm-cpq/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

## Project steering (not generated)

The GitNexus block above is **code-intelligence only**. Steering lives in [AGENTS.md](AGENTS.md) —
read its "Project steering", "Learned User Preferences" and "Learned Workspace Facts" sections, which
are authoritative and kept current. Do not duplicate them here.

- [AGENT.md](AGENT.md) — landed vs target state, and the explicit **holds** (Activities API, NgRx,
  dual-read flip, …). Treat `MODERNIZATION_PLAN.md` as target-state, never as this week's work.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/adr/README.md](docs/adr/README.md)

## Commands

Backend — Maven, **Java 25 / Spring Boot 4.0.6**, 14 modules under `backend/`:

```bash
cd backend
mvn -B verify                                    # full reactor
mvn -pl services/crm-service -am verify          # one service + its lib deps
mvn -Dtest=LeadServiceTest -pl services/crm-service test           # single class
mvn -Dtest=LeadServiceTest#methodName -pl services/crm-service test # single method
```

`*IntegrationTest` classes extend `BaseIntegrationTest` and start a **PostgreSQL Testcontainer** —
they need Docker. CI splits these into separate `backend-unit` and `backend-integration` jobs.

Frontend — Nx + Angular 22, Vitest:

```bash
cd frontend
npm ci --legacy-peer-deps      # the flag is mandatory; npm + package-lock.json only
npm run test:crm               # crm-* + shared libs
npm run test:cpq               # cpq-* libs
npx nx serve crm               # :4200, proxies /api -> gateway :8080
npx nx test crm-leads -- -t "name"   # single spec
```

Never add a `pnpm-lock.yaml` or `yarn.lock` under `frontend/` — it breaks the Nx project graph.
`nx.json` has **no `plugins` array**, so no targets are inferred; declare every target explicitly in
`project.json`.

Browser UAT / chaos (`frontend/e2e/`, plain `.mjs`, driven by Playwright):

```bash
node e2e/uat-crm-smoke.mjs     # deterministic smoke
node e2e/uat-jev-local.mjs     # LLM-driven (Jev) browser agent; needs OPENROUTER_API_KEY
node e2e/chaos-crm.mjs         # network/fault injection
```

These need the full stack running and are **not** wired into CI.

CI: **Jenkins is primary** (`Jenkinsfile`); `.github/workflows/ci.yml` is the `workflow_dispatch`
fallback and also runs nightly on `main`; `pr-check.yml` gates pull requests and `main-verify.yml`
re-runs the unit stage after every merge to `main` — see ADR-003 sections 7–8. The frontend stage
(`scripts/ci/frontend.sh`) builds, lints and tests every Nx project; it runs in the full `ci.yml`
matrix and the primary Jenkins Frontend stage. `.github/workflows/pr-check.yml` omits frontend.
