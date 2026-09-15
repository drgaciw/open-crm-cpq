# JDK 25 + Angular 22 upgrade — test wave + warning fixes

**Status:** **APPROVED-WITH-GATES** — 2026-09-15
**Architect:** `opencrm-architect` (Grok 4.6; Herdr w8 `arch-pi` — plan only; **do not spawn implementers from this note**)
**Evidence SSOT:** `origin/main` @ `20a58469` (VER-1 #525 ∧ HYG-1 #524 ∧ VAL-1 #523 ∧ upgrade DAG #516–#522)
**Upgrade verdict:** **COMPLETE** ([38-jdk-ng-upgrade-next.md](38-jdk-ng-upgrade-next.md)); this note tests that land and fixes what the runs surface.
**Sources:** read `docs/forge/impl/ver1-tip-verification.md` (green, narrow), `backend/pom.xml` profiles, `scripts/ci/*`, `@Tag("integration")` corpus, `frontend/apps/crm/project.json`, VER-1 FE build/test evidence. GitNexus index stale vs tip — **skip analyze**; UNKNOWN ≠ unused; confirm with `rg` + Maven, not an empty caller set.
**Do not** edit `docs/forge/00-orchestrator-status.md`. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`.

---

## 1. Problem / evidence

### VER-1 gaps (tip `e5d60bbb`, docs-only, green but narrow)

| Gap | Evidence (ver1-tip-verification.md) |
|-----|-------------------------------------|
| ITs/Testcontainers **not exercised** on the tip | "`crm+gateway` used `verify -Punit-tests` (units only; failsafe empty; `jacoco.skip` set by that profile)". Per-module slices ran `-am test` — the `test` phase never reaches integration-tagged tests. 833 tests green, **0** integration classes run locally on 25. GHA `backend-integration` (`scripts/ci/backend-integration.sh` → `mvn -B -U verify`) runs them in CI, but no local Temurin-25 + Docker evidence exists post-merge. |
| `jacoco:check` **not** run on the tip for crm/pricing | pricing `test`: "jacoco:check is bound to `verify`, not `test` — not run on this slice". crm: `jacoco.skip` set by `unit-tests` profile (`backend/pom.xml:213–218`). Coverage floor **0.80** unchanged (`<minimum>0.80</minimum>` in both poms); pricing measured line ≈ **0.844** (639/757) — headroom exists but check never ran on tip. |
| npm install-scripts blocked | `npm ci --legacy-peer-deps` EXIT 0 but install-scripts for **esbuild/nx** were blocked (build still succeeded) — provenance/integrity signals not fully exercised. |
| browserslist advisory | `caniuse-lite` advisory only during production build — not a budget failure (initial 670.95 kB ≤ 750 kb warn). |
| Host toolchain drift | PATH `java` is often host **27.0.0**; every Maven invocation must set `JAVA_HOME=…/temurin-25.0.4+101.0.LTS` explicitly. mise `latest` still resolves 27 (host config, HOLD). |

### Test/IT structure (SSOT)

- Integration tests are surefire-tagged `@Tag("integration")` (crm: 8 classes incl. `LeadToDealLifecycleTest`, `QuoteAcceptedRedisIntegrationTest`); `unit-tests` profile = `excludedGroups=integration` + `jacoco.skip=true` (`backend/pom.xml:211–223`).
- Full `./mvnw -B verify` (no profile) = units + ITs + `jacoco:check`. That is also the CI SSOT: `bash scripts/ci/run-core.sh backend-integration` → `scripts/ci/backend-integration.sh` → `mvn -B -U verify` with `ci_require_cmd docker`.
- Testcontainers deps present in **9** service poms: approval-routing, configuration-engine, contract-asset-management, crm, document-generation, pricebook, pricing, product, quote. **gateway and ai-service have no Testcontainers dep** — gateway verification is unit-only by design (orchestrator's "CRM/gateway/quote" corrected by evidence: gateway gets a plain `verify` slice, no TC).
- FE: Vitest 20/20 projects / 97 spec files / 623 cases green on tip (`--skip-nx-cache`); production build green.

### Warning sources (catalog to be captured, then fixed)

- **BE:** JDK1 recorded non-blocking Java 25 warnings — Maven Jansi native-access, Guava `sun.misc.Unsafe`, Mockito/ByteBuddy dynamic-agent. These are third-party runtime noise. **No in-repo javac warning catalog exists on tip yet** — must capture before fixing.
- **FE:** caniuse-lite advisory (browserslist chain, `package-lock.json:10142`); esbuild/nx install-scripts blocked; no NG8xxx observed in VER-1 build logs (budget pass, no compiler warnings reported).
- **Testcontainers:** Ryuk/reuse warnings expected on first full Docker run — no `testcontainers.reuse` config committed today (rg: zero hits in `backend/services/*/src/test`).

---

## 2. Decision + rejected alternatives

| Topic | Decision | Rejected / why |
|-------|----------|----------------|
| ITs on tip under 25 | **IN** — run the **CI SSOT stage** locally: `newgrp docker` + `JAVA_HOME=temurin-25` + `bash scripts/ci/run-core.sh backend-integration` on the full reactor (`mvn -B -U verify`), split into two disjoint evidence PRs (IT-1 front services, IT-2 back services) so two BE panes run in parallel. Full-`verify` (no `-Punit-tests`) is the only way ITs + `jacoco:check` actually execute. | Re-running `verify -Punit-tests` (VER-1 shape) — repeats the narrow gap. Ad-hoc `mvn test` — never binds failsafe/jacoco:check. |
| Jacoco floor | **0.80 stays**. Pricing currently ≈ 0.844; crm must prove ≥ 0.80 under full `verify` (units + ITs counted). If crm check **fails**, fix is more tests/refactors, never a lower floor. | Lowering `minimum` — forbidden by plan 36 gate. Disabling jacoco via profile flips — forbidden. |
| `-Werror` global | **HOLD — not now.** No evidence the repo treats warnings as errors today (no `-Werror` in parent pom, GHA, or Jenkins; JDK1 ran with warnings and EXIT=0). First **capture** the full warning catalog (WARN-BE); a `-Werror` gate may be proposed later **only** on a proven-clean tree. | Enabling `-Werror` now — would stop the reactor on third-party noise (Jansi/Guava/ByteBuddy) and manufacture failures. |
| Hiding warnings | **REJECT `-nowarn`/`-Xlint:none`/log-level suppression.** Fix real in-repo warnings; document third-party ones with upstream links in the impl note. | `--add-opens` / dynamic-agent flags **only if a test fails** (JDK1 precedent: none needed). |
| `-T1C` parallel reactor | **OUT this wave.** Parallel reactor risks surefire ordering + Testcontainers port/startup races for a pure wall-clock win. CI (`backend-integration.sh`) is serial; stay CI-faithful locally. | `mvn -T1C clean verify` as a new standing gate — perf experiment, not this wave. |
| `clean` vs incremental | Use `clean verify` on the two IT evidence runs (fresh classfiles under 25); slices stay **sliced** (`-pl … -am`) for wall-clock — the union covers the reactor. | One monolithic 9-minute single-worker run — wastes a parallel Herdr DAG. |
| FE warnings | **IN** — fix what is real and in-repo (browserslist DB refresh; unblock install-scripts for esbuild/nx with evidence), **document-skip** third-party NG8xxx/peer advisories only with captured log lines. `npm ci --legacy-peer-deps` stays (Analog peer documented in NG2). | Blanking advisory via config-only hacks without fixing the DB; switching package manager. |
| Browser UAT | **IN this wave** (UAT-1, FE track). NG3/NG4 shipped UI-affecting changes (zoneless CD, Signal Form, CDK focus trap, WebMCP route tools) with **zero browser session** ("no Chrome/Playwright session" — both impl notes). User standing preference = Playwright UAT. Degrade gracefully if CPQ services can't boot locally (documented prior learning) — but attempt real gateway `:8080` first. | Skipping UAT again — third consecutive wave without a browser session is not acceptable for a global-CD upgrade. |
| Testcontainers Ryuk/reuse | **Document in IT notes** (WARN-IT). Do not set `testcontainers.reuse.enabled=true` in committed config (state pollution risk); no committed `~/.testcontainers.properties` changes. | Enabling reuse to silence Ryuk warnings. |
| Holds | Untouched: Activities, #238/#241, R2, NgRx, dual-read flip, Track 2 Spring AI, GLM-5 runtime, subscriptions FE, JDK 27, Boot 4.1, MapStruct 1.6, more Signal Forms / WebMCP, MCP `angular.json` (HOLD). | — |

---

## 3. PR DAG

```text
BE track                         FE track (parallel from day 1)
IT-1  front-services full verify ∥ WARN-FE  FE warning fixes
IT-2  back-services full verify  ∥ UAT-1   Playwright browser UAT (:4200 ↔ :8080)
  │                                  │
  ▼                                  ▼
WARN-BE  javac/Maven warning fixes (after IT-1/IT-2 evidence)
  │
  └─ if code touched → re-run affected IT slices before merge
```

Day-1 parallel: **IT-1 ∥ IT-2 ∥ WARN-FE ∥ UAT-1**. **WARN-BE** starts after IT-1/IT-2 notes land (needs their clean-tip warning catalog as baseline); its acceptance re-runs the affected slices, so no IT re-open unless it changes code.

| ID | Title | Branch | Exclusive files | Owner | Risk | TDD acceptance (red→green) | Herdr agent |
|----|-------|--------|-----------------|-------|------|----------------------------|-------------|
| **IT-1** | `test(be): Temurin-25 Docker ITs — crm, quote, gateway (full verify)` | `test/forge-it1-java25-docker` | `docs/forge/impl/it1-java25-docker-front.md` **only** | BE | **MED** | Red: VER-1 shows 0 IT classes run on tip (`-Punit-tests`, failsafe empty). Green: on a fresh `main`-tip worktree, `JAVA_HOME=temurin-25` + `newgrp docker` + `./mvnw -B clean verify -pl services/crm-service,services/quote-service,services/gateway-service -am` → **BUILD SUCCESS**; impl note records per-class IT execution (crm 8 `@Tag("integration")` classes + quote ITs executed, gateway units), **crm `jacoco:check` ≥ 0.80 PASS** (no unit-tests profile ⇒ no skip), Testcontainers/Ryuk warning catalog (WARN-IT fold), Docker engine version, and `-am` downstream libs evidence. `APPROVALS_LEGACY_LOCAL_READ` untouched. Dual-read YAML untouched. | `be-it1` |
| **IT-2** | `test(be): Temurin-25 Docker ITs — remaining services + jacoco:check` | `test/forge-it2-java25-back` | `docs/forge/impl/it2-java25-docker-back.md` **only** | BE | **MED** | Red: pricing slice never ran `jacoco:check` on tip (bound to `verify`). Green: `newgrp docker` + JAVA_HOME 25 + `./mvnw -B clean verify -pl services/pricing-service,services/product-service,services/pricebook-service,services/configuration-engine,services/approval-routing,services/document-generation,services/contract-asset-management -am` → **BUILD SUCCESS**; note records pricing **`jacoco:check` = "All coverage checks have been met"** (≥ 0.80, expected ≈ 0.844), per-service test counts, and TC warning catalog. **Floor 0.80 not touched.** | `be-it2` |
| **WARN-BE** | `fix(be): Java 25 javac/Maven warning cleanup (real ones)` | `fix/forge-warnbe-java25` | `backend/**` source/pom files **with captured warnings only** + `docs/forge/impl/warnbe-java25-catalog.md` | BE | **MED–HIGH** | Red: capture full warning catalog on tip (`mvn -B clean test` slices, Maven + javac `-Xlint` output where available) — catalog every in-repo javac deprecation/removal warning with file:line. Green: each catalogued in-repo warning either **fixed** (affected slices re-run green; no behavior change; GitNexus detect-changes clean or rg-confirmed) or **documented third-party** (Jansi/Guava/ByteBuddy) with upstream link — no `-nowarn`, no `-Werror`, no `--add-opens` unless a test actually failed. If the catalog has **zero in-repo warnings**, the PR is the docs-only catalog + explicit empty-fix verdict (still a PR). | `be-warn` |
| **WARN-FE** | `fix(fe): browserslist DB + npm install-scripts under Angular 22` | `fix/forge-warnfe-angular22` | `frontend/package-lock.json`; optional `frontend/.npmrc` and/or `frontend/.browserslistrc`; `docs/forge/impl/warnfe-angular22-catalog.md` | FE | **MED** | Red: capture `npm ci --legacy-peer-deps` stderr (esbuild/nx install-script blocked lines) + production-build browserslist advisory. Green: (a) browserslist DB refreshed (`npx update-browserslist-db@latest` or lockfile bump) so the advisory is gone — or a **documented skip** with the advisory line quoted and an upstream "not actionable" reason; (b) install-scripts for esbuild/nx execute on fresh `npm ci` (or documented security-policy skip with evidence); (c) after changes: `NX_DAEMON=false npx nx build crm --configuration=production --skip-nx-cache` SUCCESS **and** `nx run-many -t test --all --parallel=3 --skip-nx-cache` **20/20**. No `frontend/package.json` version changes (no dep bumps beyond the DB refresh). | `fe-warn` |
| **UAT-1** | `test(fe): Playwright browser UAT — CRM Angular 22 against gateway :8080` | `test/forge-uat1-browser` | `docs/forge/impl/uat1-browser-java25-angular22.md` **only** (fixes found get their own PRs) | FE | **MED** | Red: no browser session evidence since NG3/NG4 (both impl notes say so). Green: `nx serve crm` (:4200, proxy `:8080`) with gateway + crm-service + quote-service up locally (JAVA_HOME 25, `newgrp docker` for any container start; Azure Kafka/autoconfig excludes as documented when booting locally); Playwright pass over: catalog list + **catalog create dialog (Signal Form)** incl. validation errors; quotes list filter/status/page + **WebMCP route-tool smoke** if the browser MCP exposes it; one ConfirmDialog (focus lands on confirm via `cdkFocusInitial`, trap holds Tab); console errors **zero or catalogued** with triage. If services cannot boot locally, record boot blockers with logs and degrade to dev-server + mock — documented, not silent. Bugs found → follow-up fix PRs with their own exclusive files (not folded here). | `fe-uat` |
| **FIX-*** | fix PRs spawned by IT-1/IT-2/WARN-BE/WARN-FE/UAT-1 findings | per finding | per finding (named in the finding note) | BE/FE | scoped per finding | Each: red test/log first, green after fix, affected slices re-run. Only if findings exist. | spawned per pane |

**File-conflict check:** IT-1/IT-2/UAT-1 notes are distinct files; WARN-FE touches only `frontend/` build tooling; WARN-BE touches only `backend/` — FE and BE tracks never collide. Merge order: IT-1 + IT-2 + WARN-FE + UAT-1 (parallel) → WARN-BE → FIX-* as needed.

---

## 4. Blast radius

| Change | Surfaces | Risk |
|--------|----------|------|
| IT-1/IT-2/UAT-1/WARN-* evidence notes | docs only | LOW |
| WARN-BE backend sources | Only files with captured warnings; every reactor module compiles against them | MED–HIGH (GitNexus impact per symbol before edit; UNKNOWN ≠ unused) |
| WARN-FE lockfile / `.npmrc` | All 20 FE projects' toolchain resolution | MED (`npm ci` must stay EXIT 0; `--legacy-peer-deps` still expected — Analog peer documented in NG2) |
| UAT-1 | Read-only drive of the running stack | LOW (no code by design) |
| Fix-* PRs | Scoped per finding | scoped |

---

## 5. Forge layout (orchestrator spawns; architect does not)

| Pane | Agent id | Branch | Track |
|------|----------|--------|-------|
| 1 | `be-it1` | `test/forge-it1-java25-docker` | BE |
| 2 | `be-it2` | `test/forge-it2-java25-back` | BE |
| 3 | `be-warn` | `fix/forge-warnbe-java25` | BE (start after IT-1/IT-2 notes) |
| 4 | `fe-warn` | `fix/forge-warnfe-angular22` | FE |
| 5 | `fe-uat` | `test/forge-uat1-browser` | FE (Playwright) |

- **5 worktrees**, each branched from current `origin/main` tip (`20a58469`); **never checkout primary `main`**.
- **Java (BE panes):** `export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS` (verify `"$JAVA_HOME/bin/java" -version` → **25.0.4.1 LTS**; PATH may show 27 — do not trust PATH). Wrap every Maven/Docker command in `newgrp docker <<'EOF'`. Do **not** chmod `docker.sock`.
- **IT-1 ∥ IT-2 Docker concurrency:** Testcontainers uses random host ports — two panes may run concurrently; if the host chokes, serialize be-it2 after be-it1 and say so in the note.
- **FE panes:** real per-worktree `node_modules` (`npm ci --legacy-peer-deps`), `NX_DAEMON=false`, `--skip-nx-cache` for evidence runs. Node 22.22.3+/24/26 host OK.
- **Vitest/UAT:** `NX_DAEMON=false npx nx test <proj> --skip-nx-cache`; Playwright against `nx serve crm` → `http://localhost:4200` (proxy `:8080` gateway). No sidebar/route edits in this wave.
- **Model:** Grok 4.6; fast tier for IT-1/IT-2 evidence capture, mid tier for WARN-BE/WARN-FE/UAT-1.
- **GitNexus:** required (MUST impact-analyze before any WARN-BE source edit; `detect-changes --scope all` before every commit). UNKNOWN ≠ unused.

---

## 6. Out of scope / holds (this wave must not)

- Flip `approvals.legacy-local-read-enabled` / accept ADR-005; dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`
- Activities API / `/api/v1/activities/**`; CI #238 (runner invention); Kafka #241
- **R2** price-book list-row delete; subscriptions FE; contract-asset FE
- NgRx; more Signal Forms; more WebMCP surfaces (catalog-list/app-wide); `@boundary` (not stable in 22.1.6)
- Boot 4.1 / Boot 3 downgrade; JDK 27 (mise `latest` stays host-HOLD); MapStruct 1.6; Azure 7.x BOM
- Track 2 Spring AI; GLM-5 as runtime; WhatsApp #430 undo; product `POST /validate` deletion
- Global `-Werror` gate; `-nowarn`/log-suppression; `--add-opens` unless a test fails; `testcontainers.reuse.enabled` in committed config
- `mvn -T1C clean verify` as a new gate (OUT — CI-faithful serial stays)
- MCP `frontend/angular.json` (HOLD — documented limitation)
- `docs/forge/00-orchestrator-status.md`; `docs/ARCHITECTURE.md`; `AGENT.md` (both SSOTs already correct on tip)
- Harvest-plan language (`docs/plans/price-book-*`, `deal-desk-*`) — Java 21 there is by design of those waves
- Lowering the Jacoco **0.80** floor or any test budget (750 kb warn / 1 mb error stay)

---

## 7. Verdict

**APPROVED-WITH-GATES** — 2026-09-15.

Gates: dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}`; Jacoco floor **0.80 not lowered**; JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27/`latest`); full `verify` (not `-Punit-tests`) is the only accepted IT evidence; no `-Werror`/`-nowarn`; no `-T1C`; browser UAT via Playwright required (third wave in a row without a browser session is rejected); third-party warnings documented, not suppressed; fix-* PRs scope their own exclusive files; GitNexus walk before every WARN-BE source edit.
