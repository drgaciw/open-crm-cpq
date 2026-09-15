# Angular 22 frontend upgrade — core, CDK, CLI MCP, WebMCP opt-in

**Status:** **APPROVED-WITH-GATES** — 2026-09-15  
**Architect:** `opencrm-architect` (Grok 4.6; Cursor `w8` quota-blocked — plan only; **do not spawn implementers**)  
**Evidence date:** 2026-09-15  
**Baseline:** `origin/main` @ `adb928b` (tdd-be-clean #515; Angular **~21.1** + CDK/Material **21.1.3**; Nx **22.4.5**)  
**Sister plan:** [36-jdk25-backend-upgrade.md](36-jdk25-backend-upgrade.md) (BE; disjoint exclusive files)  
**Sources:** `rg` + read `frontend/package.json`, `app.config.ts`, CDK/Material usage, `angular.dev/reference/versions`. GitNexus **skip analyze**. UNKNOWN ≠ unused; `-r /home/username01/Projects/open-crm-cpq`. Linear/Notion Open CRM: **none**.  
**Do not** edit `docs/forge/00-orchestrator-status.md`.  
**Closeout:** **X1 is this worker (FE)** after JDK2 **and** NG4 are green on `main`. Exclusive: `docs/ARCHITECTURE.md` landed-vs-target rows (Java **25** / Angular **22**). BE **must not** edit that file.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Stay Angular 21.1** | **REJECT** | Authorized: Angular **22** |
| **NgRx 19+** | **HOLD** | Signals + HttpClient. Do **not** introduce |
| **New portal / customer-portal / admin-console** | **HOLD** | Zero new apps. Shell already owns routes |
| **Subscriptions FE** | **HOLD** | ADR-006 does not authorize |
| **WebMCP app-wide / NgRx tools** | **REJECT** | Experimental — **one** low-risk list surface |
| **Rewrite all reactive forms → Signal Forms** | **REJECT** | Only where reactive forms are **painful** (one dialog/form) |
| **`@boundary` if still developer-preview** | **SKIP unless stable** in the landed 22.x (`search_documentation`). Blog: preview **Q3 2026** |
| **FE editing `ci.yml` / Jenkins Dockerfile / `ci-contract.yaml`** | **REJECT** | **JDK2** owns CI Node **22.22.3** + Java 25 so files stay exclusive |
| **ARCHITECTURE.md in NG1–NG4** | **REJECT** | **X1 only**, after both upgrades green |
| **✅ NG1 Angular CLI MCP** | **IN** | `.cursor/mcp.json`; tools listed below |
| **✅ NG2 Angular 22 + CDK/Material 22 + Nx 23 + TS 6 + Analog/Vite** | **IN** | Exclusive: `frontend/package.json` |
| **✅ NG3 v22 refactors** | **IN** | zoneless/OnPush remaining, `@Service`, `modernize`, Signal Forms (one surface), CDK/Aria where we already use CDK |
| **✅ NG4 WebMCP opt-in** | **IN** | catalog-list **or** quotes-list only |
| **✅ X1 docs closeout** | **IN** | FE-owned `ARCHITECTURE.md` rows |

**Chosen theme:** Angular **22** (latest 22.x patch at implement time) in lockstep with **CDK/Material 22**, Nx **≥23.1** (22.1 needs **≥23.2**), TypeScript **6.0.x**, Node **22.22.3** (CI via JDK2). MCP first so later PRs can call `search_documentation` / `modernize`.

---

## 1. Problem / evidence (read files)

### Git / dual-read / FE state

| Item | State |
|------|--------|
| Dual-read | **true** — FE does not touch quote-service YAML |
| State | Signals + HttpClient. **NgRx held** |
| Vitest | Analog + `@nx/vitest`; `NX_DAEMON=false npx nx test <proj> --skip-nx-cache` |
| Proxy | `frontend/proxy.conf.json` → gateway `:8080` — **do not** retarget CRM-direct |
| ADR-006 | **Accepted** — no subscriptions FE |
| Track 2 Spring AI | **HOLD** — no FE AI-authoring portal |

### Landed package versions (`frontend/package.json`)

| Package | Landed |
|---------|--------|
| `@angular/{core,common,compiler,forms,platform-browser,router,compiler-cli,cli,build}` | `~21.1.0` |
| `@angular/animations`, `@angular/cdk`, `@angular/material` | `^21.1.3` |
| `@nx/*`, `nx` | **22.4.5** |
| `@analogjs/{vite-plugin-angular,vitest-angular}` | `~2.1.2` |
| `typescript` | `~5.9.2` |
| `vite` | `^7.0.0` |
| `vitest` | `^4.0.8` |
| `rxjs` | `~7.8.0` |
| `zone.js` | **not a direct dep** (peer of `@angular/core` in lockfile only) |
| `engines` | **unset** |

Nx matrix (nx.dev, 2026-08): Angular **~21.1.0** → Nx **≥22.4** (landed). Angular **~22.0.0** → Nx **≥23.1**. Angular **~22.1.0** → Nx **≥23.2**. **NG2 must bump Nx to latest 23.x that supports the chosen 22.x.**

### Official Angular 22 toolchain (`angular.dev/reference/versions`, 2026-09-15)

| Angular | Node.js | TypeScript | RxJS |
|---------|---------|------------|------|
| **22.0.x** | **`^22.22.3 \|\| ^24.15.0 \|\| ^26.0.0`** | **`>=6.0.0 <6.1.0`** | `^6.5.3 \|\| ^7.4.0` |
| 21.1.x (landed) | `^20.19.0 \|\| ^22.12.0 \|\| ^24.0.0` | `>=5.9.0 <6.0.0` | same |

CI today: GHA `node-version: '22.12.0'` — **illegal for Angular 22**. Host `node` is **v26.7.0** (allowed). **JDK2** pins CI to **22.22.3**; NG2 assumes that pin and **must not** edit `ci.yml`.

### App bootstrap (zoneless-ish, not explicit)

`frontend/apps/crm/src/app/app.config.ts`: `provideBrowserGlobalErrorListeners`, router, HttpClient interceptors, `provideAnimationsAsync`. **No** `provideZonelessChangeDetection()`. **No** `zone.js` in app `polyfills` / `project.json`. New v21+ apps are zoneless by omission; v22 best practice is an **explicit** zoneless provider (confirm via MCP `get_best_practices` / `search_documentation`).

OnPush: **already set** on CRM/CPQ/shared-ui components surveyed. Remainder: `frontend/libs/shared/utils/src/lib/shared-utils/shared-utils.ts` (`SharedUtils` — **no** `changeDetection`). v22 default is OnPush for **new** components; existing explicit OnPush can stay (migration keeps the property).

### CDK / Material usage (lockstep 22)

Direct `@angular/cdk` import: **only** `BreakpointObserver` from `@angular/cdk/layout` (`shell.component.ts` + spec).

Indirect CDK via Material:

| Surface | API |
|---------|-----|
| `ConfirmDialogComponent` | `MatDialog`, **`cdkFocusInitial`** on confirm (spec asserts attribute) |
| Lists / details / rule-sets / catalog / quotes / bundles / price-books | `MatDialog` (overlay + focus trap) |
| Shared DataTable | CSS “loading overlay” — **not** CDK Overlay |

**Do not** rewrite Material dialogs to Aria wholesale. **Do** bump `@angular/cdk` + `@angular/material` with core, then use **new 22** Focus / overlay / a11y / Aria **on surfaces that already use CDK** (shell breakpoints; confirm-dialog focus; dialog a11y). Aria (`@angular/aria`) is **stable** in v22 — adopt for the confirm-dialog / focus path **if** Material 22 still composes CDK the same way; otherwise stay on Material dialog + `cdkFocusInitial`.

### Reactive forms (painful vs leave)

`FormBuilder` / `ReactiveFormsModule` on: organization/lead/deal/contact **forms**, lose-reason dialogs, plus CPQ create/edit dialogs (rule-sets, bundles, price-books, catalog create, quote header). **NG3 converts one painful surface** — **catalog create dialog** (`ProductCreateDialogComponent`) **or** **quote-header-edit** if catalog create is already simple. **Not** all CRM forms. **Not** NgRx.

### MCP (missing)

`rg mcp` in `*.json`: **zero**. No `.cursor/mcp.json`. Angular CLI MCP is **not** configured.

### GitNexus

`java.version` / npm package names are **not** symbols → **UNKNOWN**. Treat Angular 22 pin as **HIGH** (workspace-wide `frontend/package.json` + every lib). Confirm with `nx test` / `nx build`, not an empty caller set.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "@angular/core" \
  --direction upstream --repo /home/username01/Projects/open-crm-cpq
```

Expect UNKNOWN. **HIGH by policy.**

---

## 2. Decisions + rejected

| Topic | Decision | Rejected |
|-------|----------|----------|
| Angular version | Latest **22.x** patch at implement time (22.0 + later minors OK). Use `ng update` / Nx migrate, not hand-edits of 20+ package names | Angular 23; staying on 21.1 |
| CDK / Material | **Same 22.x** as `@angular/core`. Add `@angular/aria` only if NG3 uses a stable Aria primitive on an existing CDK surface | CDK 21 leftover; Material-only bump |
| Nx | Bump **22.4.5 → latest 23.x** that supports chosen Angular 22.x (`nx migrate`) | Staying on Nx 22.4.5 (does **not** support Angular 22) |
| TypeScript | **6.0.x** (`>=6.0.0 <6.1.0`) | TS 5.9 |
| Analog / Vite / Vitest | Analog **latest** that declares Angular 22 + Nx 23; Vite **8** if Analog/Nx require it (Analog docs: Nx 22 + Angular 22 → Vite ^8). Keep Vitest + Analog `setupTestBed` — **not** Jasmine | Switching test runner |
| Node | CI **22.22.3** via **JDK2**. Local: 22.22.3+ or 24.15+ or 26.x (host 26.7.0 OK) | GHA 22.12.0; FE editing `ci.yml` |
| MCP | Repo `.cursor/mcp.json` running `npx -y @angular/cli mcp` (CLI version = workspace 22). Optional copy: `frontend/.cursor/mcp.json` **or** `frontend/.vscode/mcp.json` if agents cwd into `frontend/`. `list_projects` reads `angular.json` — Nx has **project.json only**; if the tool returns empty, add a **minimal** `frontend/angular.json` **or** pass workspace path per current CLI docs (`search_documentation`). Do not fake projects | Enabling experimental MCP flags that duplicate stable v22.1 tools |
| MCP tools (must work) | `get_best_practices`, `onpush_zoneless_migration`, `devserver.start`, `devserver.stop`, `devserver.wait_for_build`, `run_target`, `search_documentation`, `list_projects`. v22.1: several are **stable default** — do **not** pass obsolete `--experimental-tool` unless `ng mcp` says so | Assuming v20 `-E modernize` flags without checking 22.x |
| `modernize` | Run via MCP `run_target` / schematic **after** NG2. Inputs → `input()`, outputs → `output()`, inject(), control flow already mostly landed | Mega-rewrite in NG2 |
| Zoneless / OnPush | NG3: `provideZonelessChangeDetection()` (or current v22 name from docs) in `app.config.ts`; run `onpush_zoneless_migration` on remainder (`SharedUtils` + any Eager). Keep existing OnPush | Reintroducing `zone.js`; NgZone apps |
| `@Service` | NG3: convert **HttpClient data-access** singletons that are `@Injectable({ providedIn: 'root' })` — start with `ProductService` + `QuoteService` (WebMCP hosts). Leave constructor-injection / abstract `BaseCrudService` as `@Injectable` | Mass-converting every service in one PR |
| Signal Forms | **One** painful reactive form (`@angular/forms/signals` — **stable** in 22). Prefer catalog create dialog | All CRM forms; mixing NgRx |
| `@boundary` | NG3 **only if** the landed 22.x marks it **stable**. Else document skip. Preview is not a gate | Shipping preview in production lists |
| WebMCP | NG4 **opt-in**. `provideExperimentalWebMcpTools` **or** the shipped name (`provideExperimentalWebMcpTool` / `provideWebMcpTools` — **confirm with MCP `search_documentation`**, do not invent). Register **read** tools on **catalog-list or quotes-list** (filter/search/page). Optional `provideExperimentalWebMcpForms` **only** if that list already has a Signal Form from NG3. **Not** app-wide, **not** approvals, **not** NgRx | Default-on for all routes |
| NgRx | **Forbidden** | — |
| Closeout | **X1 FE** updates ARCHITECTURE.md Backend row Java **25** and Frontend row Angular **22** after JDK2 ∧ NG4 | Both workers editing ARCHITECTURE |

### MCP json (NG1 deliverable)

`.cursor/mcp.json` (repo root):

```json
{
  "mcpServers": {
    "angular-cli": {
      "command": "npx",
      "args": ["-y", "@angular/cli", "mcp"],
      "cwd": "frontend"
    }
  }
}
```

If the host ignores `cwd`, duplicate under `frontend/.cursor/mcp.json` with `cwd` omitted. After NG2, CLI 22 is on `frontend/node_modules` — prefer `frontend/node_modules/.bin/ng mcp` **or** keep `npx @angular/cli@22 mcp` so agents do not pick a global 21. Record the exact argv in `docs/forge/impl/ng1-angular-cli-mcp.md`.

Acceptance: agent can invoke the eight tools (smoke: `list_projects` + `get_best_practices`). `devserver.*` / `run_target` may require a running workspace — document, do not fail NG1 if sandbox blocks `ng serve`.

---

## 3. PR DAG

```text
NG1  Angular CLI MCP json                         JDK1 (BE, parallel)
        exclusive: .cursor/mcp.json (+ frontend copy)
        │
        ▼
NG2  Angular 22 + CDK/Material 22 + Nx 23 + TS 6
        exclusive: frontend/package.json (+ lock, nx.json, tsconfigs as migrate requires)
        depends: NG1 (MCP for search_documentation / migrate guidance)
        assumes: JDK2 Node 22.22.3 on CI (rebase if JDK2 not yet main — do not edit ci.yml)
        │
        ▼
NG3  v22 refactors: zoneless, @Service, modernize, one Signal Form, CDK/Aria on existing CDK
        │
        ▼
NG4  WebMCP opt-in on catalog-list XOR quotes-list
        │
        └──────────────►  X1 FE closeout (ARCHITECTURE.md) after JDK2 ∧ NG4
```

**Day-1 parallel:** **NG1 ∥ JDK1**. NG2 after NG1. NG3 after NG2. NG4 after NG3 (needs 22 APIs). X1 after JDK2 **and** NG4.

| ID | Title | Branch | Exclusive files | Owner | Risk | Acceptance tests |
|----|-------|--------|-----------------|-------|------|------------------|
| **NG1** | `chore(ai): Angular CLI MCP server for agents` | `feat/forge-ng1-angular-cli-mcp` | `.cursor/mcp.json`; optional `frontend/.cursor/mcp.json` or `frontend/.vscode/mcp.json`. **Not** `package.json` (may add a comment in impl only) | FE | **LOW** | File committed; `npx -y @angular/cli mcp` starts; tools listed in plan are advertised (`ng mcp` help or MCP list). No app runtime change. Impl: `docs/forge/impl/ng1-angular-cli-mcp.md` |
| **NG2** | `build(fe): Angular 22 + CDK/Material 22 + Nx 23` | `feat/forge-ng2-angular22-core` | `frontend/package.json` **and** lockfile; `frontend/nx.json`; tsconfig(s) / `project.json` **as `nx migrate` / `ng update` emit**. **Not** `ci.yml`, Jenkins Dockerfile, `ci-contract.yaml`, `ARCHITECTURE.md` | FE | **HIGH** | `@angular/core` **22.x**; `@angular/cdk` + `@angular/material` **same 22.x**; `nx` **23.x**; `typescript` **6.0.x**; Analog/Vite peer-compatible. `cd frontend && npm ci --legacy-peer-deps` (or drop flag if unused). `NX_DAEMON=false npx nx build crm --configuration=production`. `NX_DAEMON=false npx nx run-many -t test --all --parallel=3`. `npm run test:cpq` + `npm run test:crm`. Proxy still `:8080`. No NgRx. Dual-read untouched. Impl: `docs/forge/impl/ng2-angular22-core.md` |
| **NG3** | `refactor(fe): v22 zoneless, @Service, signal form, CDK a11y` | `feat/forge-ng3-v22-refactors` | FE sources under `frontend/apps/crm`, `frontend/libs/**` as listed in impl; **not** `package.json` unless a new `@angular/aria` dep is required (then **only** that dep line + lock — prefer adding Aria in NG2 if NG3 needs it: **add `@angular/aria` in NG2** to keep exclusive files clean) | FE | **MED** | `provideZonelessChangeDetection()` (or v22 name) in `app.config.ts`. `onpush_zoneless_migration` remainder done or documented empty. `ProductService` + `QuoteService` use `@Service()` (or documented skip if API needs `@Injectable`). `modernize` schematic run; no `*ngIf`/`*ngFor` regressions. **One** Signal Form on catalog create **or** quote-header-edit. Confirm-dialog keeps **focus** (`cdkFocusInitial` or Aria equivalent) + overlay a11y. `@boundary` only if stable. Vitest for touched projects. **No NgRx.** Impl: `docs/forge/impl/ng3-v22-refactors.md` |
| **NG4** | `feat(fe): experimental WebMCP on one CPQ list` | `feat/forge-ng4-webmcp-optin` | `frontend/apps/crm/src/app/app.config.ts` **and** **either** `frontend/libs/cpq/catalog/**` **or** `frontend/libs/cpq/quotes/src/lib/quotes-list/**` — **not both** | FE | **LOW–MED** | Experimental provider present. Tools: list/filter/page **read** for that surface only. No writes that bypass UI validation. No approvals, no NgRx, no new portal, no subscriptions. Unit test: provider registered / tool execute hits the existing service signals. `nx test` that project. Impl: `docs/forge/impl/ng4-webmcp-optin.md` |
| **X1** | `docs(arch): land Java 25 and Angular 22 in ARCHITECTURE.md` | `docs/forge-x1-upgrade-closeout` | **`docs/ARCHITECTURE.md`** (Backend Java **25**, Frontend Angular **22**). Same PR may update **AGENT.md** landed table **only** so the two SSOT tables do not drift. **Not** `00-orchestrator-status.md`. **Not** `backend/pom.xml` / `frontend/package.json` | **FE** | **LOW** | JDK2 **and** NG4 on `main`. Dual-read still true. JAVA_HOME example in §8 uses mise **25**, `newgrp docker` retained. Holds unchanged. Impl: `docs/forge/impl/x1-upgrade-closeout.md` |

| Pair | Rule |
|------|------|
| NG1 vs JDK1 | Parallel |
| NG2 vs JDK2 | NG2 **does not** edit CI files; rebase onto JDK2 for Node 22.22.3 on Jenkins |
| NG3 vs NG2 | Serial |
| NG4 vs NG3 | Serial (WebMCP APIs exist only on 22) |
| Catalog vs quotes for NG4 | **XOR** — pick one in the impl note; default **quotes-list** if catalog create already owns NG3 Signal Form |
| X1 vs ARCHITECTURE | Only X1; after both greens |
| NgRx / dual-read / Activities | **Forbidden** |

---

## 4. Blast radius

| Change | Surfaces | Risk |
|--------|----------|------|
| Angular 22 + Nx 23 + TS 6 | Entire `frontend/` workspace (`apps/crm` + all `libs/crm/*` + `libs/cpq/*` + `libs/shared/*`) | **HIGH** |
| CDK/Material 22 | Shell `BreakpointObserver`; every `MatDialog`; confirm-dialog `cdkFocusInitial`; Material modules | **HIGH** |
| Analog/Vite 8 | Vitest `TestBed` + `vi.fn()` — keep this pattern | **MED** |
| Zoneless provider | Change detection globally | **MED** |
| `@Service` on Product/Quote | WebMCP + list HTTP | **LOW–MED** |
| One Signal Form | One dialog | **MED** |
| WebMCP experimental | **One** list; browser agents | **LOW–MED** (opt-in) |
| MCP json | Agents only | **LOW** |
| ARCHITECTURE.md | Steering table | **LOW** (X1) |

**WARN:** GitNexus UNKNOWN on package names. `rg` + `nx test` are SSOT. Do not flip dual-read. Do not add NgRx. Do not ship subscriptions FE.

---

## 5. Herdr layout (orchestrator spawns — architect does not)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `ng-fe-mcp` | `feat/forge-ng1-angular-cli-mcp` | NG1 |
| 2 | `ng-fe-core` | `feat/forge-ng2-angular22-core` | NG2 |
| 3 | `ng-fe-modernize` | `feat/forge-ng3-v22-refactors` | NG3 |
| 4 | `ng-fe-webmcp` | `feat/forge-ng4-webmcp-optin` | NG4 |
| 5 | `x1-docs` | `docs/forge-x1-upgrade-closeout` | X1 (after JDK2 ∧ NG4) |

**Chrome owner:** none (no new routes). NG4 must **not** add sidebar entries.  
**Model:** Grok 4.6 or Cursor `--model auto` when `w8` recovers. Worktree-isolated.  
**Node:** 22.22.3+ / 24.15+ / 26.x. Host 26.7.0 is legal.  
**Vitest:** `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`.  
**MCP during NG2+:** call `get_best_practices`, `search_documentation`, `onpush_zoneless_migration`, `run_target` before guessing APIs (`provideExperimentalWebMcpTools` spelling, zoneless provider name, `@boundary` stability).  
**Java / ITs:** none on FE panes.

Browser verification (user rule): NG3/NG4 touch UI — after `nx serve crm`, exercise catalog list + quotes list (filter, dialog focus, create if Signal Form). If no browser tools, say so and rely on Vitest + `nx build`.

---

## 6. Out of scope

- Activities API / `/api/v1/activities/**`
- CI #238; Kafka #241
- Dual-read YAML flip; Accept ADR-005
- **NgRx**; customer-portal / admin-console
- Subscriptions / contract-asset FE
- Full SpEL / OpenL; Kafka S2B-9; gateway domain validation
- Delete product validate; WhatsApp #430 undo; **R2** list-row delete
- Named Wave T
- GLM-5 runtime; Track 2 Spring AI FE
- WebMCP on approvals, rule-sets admin, or app-wide
- Signal Forms for every CRM form
- `@boundary` while developer-preview
- `backend/pom.xml` / CI Java (**JDK\***)
- `docs/forge/00-orchestrator-status.md`
- Editing `docs/ARCHITECTURE.md` before X1

---

## 7. Goal text

```text
Implement Open CRM/CPQ Angular 22 upgrade per docs/forge/37-angular22-frontend-upgrade.md. NG1: .cursor/mcp.json (npx -y @angular/cli mcp, cwd frontend) with tools get_best_practices, onpush_zoneless_migration, devserver.start/stop/wait_for_build, run_target, search_documentation, list_projects. NG2: Angular 22 + CDK/Material 22 lockstep + Nx 23.x + TypeScript 6.0.x + Analog/Vite as required; exclusive frontend/package.json; do not edit ci.yml (JDK2 pins Node 22.22.3). NG3: explicit zoneless provider; modernize schematic; @Service on ProductService+QuoteService; one Signal Form where reactive forms hurt; CDK/Aria/focus only on existing CDK surfaces (shell BreakpointObserver, confirm-dialog cdkFocusInitial, MatDialog overlay); @boundary only if stable. NG4: experimental WebMCP (shipped provideExperimentalWebMcpTools or documented alias) opt-in on catalog-list XOR quotes-list — not NgRx, not a new portal. X1 FE: docs/ARCHITECTURE.md landed Java 25 / Angular 22 after JDK2 and NG4 on main. Do not flip dual-read, introduce NgRx, subscriptions FE, Activities, #238/#241, R2, Track 2 Spring AI, or edit 00-orchestrator-status.md.
```

---

## 8. Checklist

- [ ] MCP json committed; eight tools reachable
- [ ] Angular/CDK/Material **22.x**; Nx **23.x**; TS **6.0.x**
- [ ] CI Node **22.22.3** comes from **JDK2**, not this worker’s `ci.yml` edit
- [ ] Zoneless explicit; OnPush remainder documented
- [ ] One Signal Form; `@Service` on Product/Quote
- [ ] WebMCP **one** list
- [ ] No NgRx; no new portal; dual-read true
- [ ] X1 ARCHITECTURE.md only after JDK2 ∧ NG4
- [ ] No 00-orchestrator-status

---

## 9. Architect verdict

**APPROVED-WITH-GATES** — 2026-09-15.

Gates: dual-read **true**; ADR-006; no Activities; no #238/#241; no R2; Track 2 Spring AI **HOLD**; GLM-5 not runtime; **no NgRx**; GitNexus `-r /home/username01/Projects/open-crm-cpq` (Angular packages **UNKNOWN → HIGH**); CI Node 22.22.3 owned by **JDK2**; exclusive files as DAG; **X1 FE** (not BE, not both) owns `docs/ARCHITECTURE.md` Java 25 / Angular 22 rows after both greens.
