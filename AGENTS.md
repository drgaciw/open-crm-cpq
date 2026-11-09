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

GitNexus block above is **code-intelligence only**. For what to build vs hold, read:

- [AGENT.md](AGENT.md) — landed vs target; dual-read / NgRx holds and Activities status
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — index
- [docs/adr/README.md](docs/adr/README.md) — ADRs (GLM-5 is **not** runtime; ADR-006 **Accepted**; dual-read ADR-005 still **Proposed**)

## AutoJev decision skills (optional, advisory)

Six third-party skills from <https://autojev.ai/jev-skills> are vendored by
`scripts/sync-autojev-skills.sh` into `.agents/skills/` (source of truth) and mirrored to
`.claude/skills/`. They call the AutoJev MCP server (`.mcp.json` → `https://autojev.ai/mcp`), which
needs `AUTOJEV_API_KEY` in the environment that launches the agent host — never in a project file.
Without the key the `autojev_*` tools are absent; carry on without them, do not invent results.

| Skill | Use at this decision boundary |
| --- | --- |
| `autojev` | Umbrella router; `autojev_decide` only when no preset tool fits |
| `autojev-task-router` | Ambiguous or risky next step: proceed / deep review / split / block |
| `autojev-model-router` | Real quality/cost/latency tradeoff between models you can actually invoke |
| `autojev-tool-guard` | Immediately before a consequential, hard-to-reverse tool call |
| `autojev-research-guard` | Whether gathered evidence supports one precise claim |
| `autojev-completion-review` | Before reporting a multi-step task complete |

- Verdicts are **decision support, not authorization**. They never replace the GitNexus impact /
  `detect_changes` requirements above, user confirmations, or the holds in [AGENT.md](AGENT.md).
- Every call sends a task summary to an external service: no secrets, credentials, or customer/CRM
  data in the payload — compact task state only.
- This is separate from the Jev *model* used by `jev-worker/`, `frontend/e2e/uat-jev-local.mjs`, and the
  advisory decision support in domain services
  ([ADR-015](docs/adr/ADR-015-jev-advisory-decision-support.md)).

When the `autojev_*` tools are connected, these project-specific moments are **mandatory**, not optional.

`autojev-tool-guard` — call before:

- Anything aimed at the dev server on `:4200` or at the GitNexus **MCP**: both point at the *other*
  checkout (`C:\Users\dgorn\cpq-crm\open-crm-cpq`), not this tree. State which checkout the action hits.
- Starting, restarting, or killing any backend service, dev server, Docker container or browser. Services
  reaped for memory are **never** restarted unprompted; check free RAM first.
- Editing `frontend/proxy.conf.json` (it must keep targeting the gateway on `:8080`).
- Touching anything under a hold in [AGENT.md](AGENT.md) / `docs/ARCHITECTURE.md` §17 — including
  `approvals.legacy-local-read-enabled` and turning on any `opencrm.jev.*.enabled` flag.
- `mvn` builds of a service whose jar is currently running from `target/` (Windows locks the jar).

`autojev-completion-review` — always list these under known gaps when they apply, never omit them:

- `*IntegrationTest` classes need Docker; a Docker-less run did not execute them.
- `scripts/ci/frontend.sh` builds, lints and tests every Nx project (`--all`), but there is no
  frontend typecheck target and `.github/workflows/pr-check.yml` runs no frontend stage (DRG-527).
  Frontend does run in the full `ci.yml` matrix and the primary Jenkins Frontend stage. `scripts/ci/backend-unit.sh` runs
  every backend module's unit tests and fails if one starts a Testcontainer; tag Docker tests `integration`.
- CPQ services are often down during CRM UAT — empty lists or 503 fallbacks are expected then, and prove
  nothing about the feature.
- A GitNexus result with `partial: true`, `truncated: true` or `risk: UNKNOWN` is not a clean check.
- Browser UAT / chaos scripts are not wired into CI; "tests pass" does not include them.

## Learned User Preferences

- Prefer browser automation (Playwright, Cursor browser; Chrome DevTools when Google Chrome is available) for UAT and chaos testing across pages, fields, and workflows with mock data; fix failures with a TDD loop.
- When given bug reports, verify each issue in the repo before changing code; leave false positives untouched.
- When restarting local apps, kill all related dev servers first, then bring the stack back up cleanly.
- Keep committed `frontend/proxy.conf.json` targeting the gateway (`:8080`), not a temporary CRM-direct workaround.
- When the user delegates CPQ product or architecture choices (e.g. "you are the cpq application" / "I delegate this decision"), decide and record the choice in the plan rather than blocking on clarifying questions.

## Learned Workspace Facts

- Local default ports: Angular CRM UI `:4200`, API gateway `:8080`, CRM service `:8081`; the UI proxies `/api` to the gateway.
- Spring Cloud Gateway 5 / Boot 4 registers routes and filters under `spring.cloud.gateway.server.webflux.*`; the old `spring.cloud.gateway.routes` root yields empty routes and API 404s through the proxy.
- Gateway actuator config belongs under `management.endpoint.gateway` (e.g. `access: read-only`), not under `server.webflux`.
- Frontend unit tests use Vitest (`@nx/vitest:test`, `vitest/globals`, Analog `setupTestBed`); `TestBed` + `vi.fn()` is the intended pattern, not Jasmine spies.
- Chrome DevTools MCP needs Google Chrome stable; hosts with only Chromium should rely on Playwright and the Cursor browser for UI automation.
- CPQ services often need Azure Kafka/autoconfig excludes to boot locally and may be down during CRM-only UAT (list APIs empty or failing is expected without them).
- Sibling-service reuse gap analyses and harvest backlogs live under `docs/plans/`; `docs/plans/00-harvest-index.md` is the agentic index for that work.

## Cursor Cloud specific instructions

The image Java is 21. This repo needs Temurin 25 at `/usr/lib/jvm/temurin-25-jdk-amd64` (`/etc/profile.d/opencrm-dev.sh` and `/usr/local/bin/java`). There is no systemd: start `dockerd` (fuse-overlayfs, iptables-legacy) before `docker compose`.

Boot stack for CRM work:

- Postgres 16 and Redis 7 from `backend/` (`POSTGRES_USER`/`POSTGRES_PASSWORD` = `postgres`/`postgres`, which matches the `dev` profile password default). `backend/.env.example` uses different credentials and is not loaded by Compose unless you pass it.
- `crm-service` `:8081` and `gateway-service` `:8080` with `--spring.profiles.active=dev`.
- `npx nx serve crm --host=0.0.0.0 --port=4200` from `frontend/`. Redirect stdin from `/dev/null`. A TTY makes Nx wait on the analytics prompt (`frontend/nx.json` has no `analytics` flag).

Gateway mutations need `Origin: http://localhost:4200` and the `XSRF-TOKEN` cookie from `GET /api/v1/csrf` (`X-XSRF-TOKEN`). Quote, pricing, and the other CPQ services are not part of this stack; the account detail quotes panel returns 500 until quote-service is up.

`mvn -DskipTests install` fails the JaCoCo check. Install local snapshots with `./mvnw -B -DskipTests -Djacoco.skip=true install -pl services/crm-service,services/gateway-service -am` from `backend/` before a single-module test such as `LeadServiceTest`.
