# JDK 25 + Angular 22 upgrade — next steps (closeout)

**Status:** **UPGRADE COMPLETE** — 2026-09-15
**Architect:** `opencrm-architect` (Grok 4.6; Herdr w8 `arch-pi` — consult only; **do not spawn implementers from this note**)
**Evidence SSOT:** `origin/main` @ `e5d60bbb` (VAL-1 #523 merged)
**Plans:** [36-jdk25-backend-upgrade.md](36-jdk25-backend-upgrade.md) + [37-angular22-frontend-upgrade.md](37-angular22-frontend-upgrade.md) (APPROVED-WITH-GATES 2026-09-15)
**Landed DAG:** NG1 #516 (`175c318d`) ∥ JDK1 #517 (`0bd53e5b`), JDK2 #518 (`3993e0ef`), NG2 #519 (`b4ababef`), NG3 #520 (`96e58efb`), NG4 #521 (`c4e0d721`), X1 #522 (`302bcd9c`), VAL-1 #523 (`fbbcd4f1`)
**Validation:** `/tmp/jdk-ng-plan-validation.md` — IMPLEMENTED-WITH-GAPS (VAL-1 only); VAL-1 merged → gap **closed**.
**Do not** edit `docs/forge/00-orchestrator-status.md`.

---

## 1. Completeness (why UPGRADE COMPLETE)

| Gate | Evidence on `origin/main` |
|------|---------------------------|
| Reactor Java 25 / Boot 4.0.6 | `backend/pom.xml` `java.version` 25, compiler plugin 25/25, Boot parent 4.0.6 (`0bd53e5b`); MapStruct **1.5.5.Final** unchanged; Jacoco 0.8.14 with line gate **0.80** on crm + pricing |
| CI + images | GHA 4× `java-version: '25'` + 2× `node-version: '22.22.3'`; Jenkins WAR `2.492.3-lts-jdk21` + `JAVA_HOME=/opt/java/temurin-25` + Node 22.22.3; 7× `eclipse-temurin:25-jre-alpine` (`3993e0ef`); `verify-drift.py` green |
| Angular 22 stack | core/CDK/Material/Aria **22.1.6**, CLI/build **22.1.8**, Nx **23.2.1**, TS **6.0.3**, Analog **2.7.2** + Vite **8.3.0** (`b4ababef`); proxy still `:8080` |
| v22 refactors | explicit `provideZonelessChangeDetection()`; `@Service()` on ProductService + QuoteService; one Signal Form (catalog create `ProductCreateDialogComponent`); confirm-dialog `CdkTrapFocus` + `cdkFocusInitial` + labelledby/describedby; `@boundary` correctly skipped (not stable) (`96e58efb`) |
| WebMCP opt-in | `provideExperimentalWebMcpTools` on quotes-list route only; read tools list/filter/page; `withExperimentalAutoCleanupInjectors()` (`c4e0d721`) |
| MCP | `.cursor/mcp.json` + `frontend/.cursor/mcp.json`; 8 tools advertised via `tools/list` smoke (`175c318d`) |
| Docs SSOT | `ARCHITECTURE.md` Backend Java **25** / Frontend Angular **22** (`302bcd9c`); `AGENT.md` landed table + file-ref table Java **25** (`fbbcd4f1`) |
| Holds intact | Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}`; no NgRx / Activities / #238 / #241 / R2; Spring AI **0.8.1**; Azure **5.8.0**; Drools **10.2.0**; no `00-orchestrator-status.md` edit by #516–#523 |

---

## 2. Ranked next-step DAG

| Rank | ID | Item | Ruling | Why |
|------|----|------|--------|-----|
| 1 | **VER-1** | Post-merge tip verification: Maven reactor slices under `JAVA_HOME=…/temurin-25` + `newgrp docker`; FE `npm ci --legacy-peer-deps` + `nx build crm --configuration=production` + `nx run-many -t test --all` on fresh `node_modules` | **IN** (confidence gate, non-blocking) | JDK1/NG2 gates ran pre-merge in their own worktrees; one tip re-run catches cross-PR merge interaction. No code changes expected; record in `docs/forge/impl/ver1-tip-verification.md`. |
| 2 | **HYG-1** | Stale non-SSOT stack docs: `backend/README.md` (Boot 3.2 / Java 21), `backend/Start-AiService.ps1` (Java 21+), `backend/scripts/build-and-run.sh` (Java 21+), `infra/jenkins/ai-assist/code_review.py` (Java 21 / Angular 21) | **IN** (docs-only) | Misleads agents + the Jenkins AI reviewer's stack string. Same class as VAL-1. **Do not** touch `docs/plans/price-book-*` / `deal-desk-*` harvest language (by design of those waves) or `MODERNIZATION_PLAN.md` (target-state). |
| 3 | **MCP-1** | `frontend/angular.json` for MCP `list_projects` | **HOLD** | Documented limitation (NG1–NG4 impl notes), never a gate. A shim `angular.json` risks `nx migrate` / `ng update` conflicts — tools the next Angular bump depends on. Act only on demonstrated daily agent friction; minimal root-marker per current CLI docs; never fake projects. |
| 4 | **HOST-1** | Pin host mise `java = "latest"` (→27.0.0) away from 27; JAVA_HOME 25 in more local scripts | **HOLD** (host) / IN-in-part via HYG-1 | PATH `java`=27 is harmless: CI, Jenkins, and runtime images are pinned 25; docs use `JAVA_HOME=…/temurin-25`. `~/.config/mise/config.toml` is host state outside repo waves. README toolchain section rides with HYG-1. |
| 5 | **SIG-2** | More Signal Forms | **OUT** (upgrade scope) | Plan 37: one painful surface only; catalog create done; quote-header stays FormBuilder. |
| 6 | **WEBMCP-2** | More WebMCP surfaces | **OUT** (upgrade scope) | Plan 37 XOR locked to quotes-list; expansion = new wave with its own review. |
| 7 | **BOUND-1** | `@boundary` | **HOLD** | Not stable in landed 22.1.6; re-evaluate on next Angular minor marking it stable. |
| 8 | **NGRX-1** | NgRx | **REJECT** (upgrade scope) / **HOLD** (standing) | Signals + HttpClient is landed state; hold unchanged. |
| 9 | **BOOT-41** | Boot 4.1.x | **OUT** | Not authorized; 4.0 EoL ~Dec 2026 is a later wave. No 3.x downgrade. |
| 10 | **AZURE-7** | Azure 7.x BOM | **HOLD** | Pairs with BOOT-41 decision; 5.8 verifier exclusions already in place. |
| 11 | **MAPSTRUCT-16** | MapStruct 1.6.x | **REJECT** | Bump only if JDK 25 javac fails — it did not (14/14 slices green). |
| 12 | **JDK-27** | JDK 27 | **REJECT** | Non-LTS (EoA ~Mar 2027); Boot 4.0.6 supports ≤25. Re-evaluate at Boot 4.1 wave. |
| 13 | **HOLDS** | Dual-read flip / Activities / #238 / #241 / R2 / Track 2 Spring AI / GLM-5 runtime / subscriptions FE | **HOLD** | Standing; untouched by this wave. |
| 14 | **NEXT-WAVE** | Next product forge wave | **Candidate named — PM decides** | Upgrade complete → natural next: **price-book P1 (E0–E2)** per pinned `docs/plans/price-book-harvest-backlog-p1.md` (+ matrix + gap analysis, #513); alternate **deal-desk D1** (`docs/plans/deal-desk-harvest-backlog-d1.md`, #512). Both pre-pinned; no new harvest plan needed. |

---

## 3. IN items — execution detail

### VER-1 — post-merge tip verification (no code PR expected)

- **Branch:** none (fresh `main`-tip verification worktree). If red → `fix/forge-tip-verify-<n>`.
- **Exclusive files:** none (impl note `docs/forge/impl/ver1-tip-verification.md` only).
- **Risk:** LOW.
- **Acceptance:**
  - `JAVA_HOME=$(ls -d ~/.local/share/mise/installs/java/temurin-25* | head -1)`; `"$JAVA_HOME/bin/java" -version` → **25.0.4.x LTS**, never 27.
  - `newgrp docker` + `./mvnw -B -pl libs/common,libs/security,libs/rules-engine -am test`; `./mvnw -B -pl services/crm-service,services/gateway-service -am verify -Punit-tests`; per-module `test` for pricing, product, quote, pricebook, configuration-engine, approval-routing, document-generation, contract-asset-management, ai-service. All green; Jacoco 0.80 checks met.
  - `cd frontend && npm ci --legacy-peer-deps`; `NX_DAEMON=false npx nx build crm --configuration=production` green; `NX_DAEMON=false npx nx run-many -t test --all --parallel=3` → 20/20.
  - `python3 .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py` → OK.
- **Herdr:** 1 pane, `verify-tip`, mid tier.

### HYG-1 — stale stack docs (docs-only PR)

- **Branch:** `docs/forge-hyg1-stack-docs-java25`
- **Exclusive files:** `backend/README.md`, `backend/Start-AiService.ps1`, `backend/scripts/build-and-run.sh`, `infra/jenkins/ai-assist/code_review.py`, `docs/forge/impl/hyg1-stack-docs-java25.md`.
- **Risk:** LOW (docs + one Python stack string; PowerShell floor check passes on 25).
- **Acceptance (red→green):**
  - Red: `rg -n 'Java 21|Boot 3\.2|Angular 21' backend/README.md backend/Start-AiService.ps1 backend/scripts/build-and-run.sh infra/jenkins/ai-assist/code_review.py` → hits.
  - Green: same `rg` → zero; `rg -n 'Java 25|Boot 4\.0\.6|Angular 22'` present in each; `git diff --name-only` shows exactly the exclusive list (harvest plans untouched).
- **Herdr:** 1 pane, `hyg-docs`, fast tier.

**Sequencing:** VER-1 and HYG-1 parallel (disjoint); neither gates the other; neither reopens the upgrade.

---

## 4. Verdict

**UPGRADE COMPLETE** — 2026-09-15. Plans 36 + 37 fully landed and verified on `origin/main` @ `e5d60bbb`; VAL-1 gap closed. Recommended non-gating follow-ons: **VER-1** + **HYG-1**. Standing holds unchanged. Next product wave candidate: **price-book P1 (E0–E2)** — PM decision.
