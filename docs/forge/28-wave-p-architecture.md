# Wave P architecture — product-bundles portal ∥ rule impact ∥ catalog configuration-rules nest

**Status:** COMPLETE on main 2026-08-31 — P1 `c6db558`/`21c032c`, P2 `ddf7e5a`/`cd45c17`, P3 `d9757ac`/`03766a4` (P4 this closeout)  
**Architect:** `opencrm-architect` (Herdr `w8`)  
**Evidence date:** 2026-08-31  
**Baseline:** Wave O COMPLETE on `main` @ `fc7e631` (O1 rule-sets portal owns shell; O2 catalog options+attrs; O3 `SubscriptionDto` + paginated `GET /`; O4 closeout)  
**Survey tip:** `origin/main` @ `fc7e631`  
**Sources:** GitNexus CLI `-r /home/username01/Projects/open-crm-cpq` (indexed commit **`fc7e631` MATCHES HEAD**; CLI may still flag stale from untracked `.worktrees/` — **false stale**; **UNKNOWN ≠ unused**; confirm with `rg`); GitHub `drgaciw/open-crm-cpq`; tree `rg`; Wave O leftover list

**DAG-changing question (asked; recommended plan written anyway):** spend the **one** new-portal shell slot on **product bundles** (catalog completeness, DTO API ready, no ERP) vs **subscriptions list/detail** (O3 DTO+list landed; activate still `ErpAdapter.syncToErp`). **Recommendation: bundles own shell.** Subscriptions FE **held** this wave (see §0). Swap only if PM overrides before Day-1.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; do not invent `/api/v1/activities/**` |
| **CI runners (#238)** | **HOLD** | Only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Stack B2 / S2A-2 |
| **Delete product-service validate** | **HOLD** | Dual-run not calendar-ready; **retain** `POST /api/v1/configuration/validate` |
| **Gateway domain validation** | **REJECT** | Route + adapt only |
| **Flip `approvals.legacy-local-read-enabled`** | **HOLD** | **HIGH**; YAML stays `${APPROVALS_LEGACY_LOCAL_READ:true}` |
| **Full SpEL / Drools / OpenL** | **REJECT** | Config-rule `conditionExpression` is a stored string, not a new engine |
| **S2B-9 Kafka** | **HOLD** | Event bus not ready |
| **Bolt #430 WhatsApp** | **OUT** | On `main` — do not reopen/undo |
| **NgRx CPQ store** | **HOLD** | Signals / service HTTP |
| **Accept ADR-006** | **REJECT this wave** | O3 **is on `main`** (header is stale). Subscription API is DTO-only, but `ConfigurationSessionController`, `ApprovalController`, `DocumentController` still return **entities**. Keep **Proposed**; P4 header hygiene only |
| **Subscriptions FE** | **HOLD** | Would consume the only shell slot. `POST /{id}/activate` → `ErpAdapter.syncToErp` (rate-limited; Kafka `QuoteAcceptedEventConsumer` also syncs). Browse-only without activate is LOW–MED **if** it owned shell — **not** recommended vs bundles. Next wave after activate is gated **or** PM swaps P1 |
| **✅ Track 1: Product bundles portal** | **IN** | `ProductBundleController` DTO + paginated list + items; gateway `/api/v1/product-bundles/**`; **zero** FE lib (`ProductDto.bundle?: boolean` only). **Owns shell** |
| **✅ Track 2: Rule-sets `analyzeImpact`** | **IN** | FE `RuleService.analyzeImpact` + `RuleImpactDto` exist; O1 stretch skipped. Existing lib; **no new shell** |
| **✅ Track 3: Configuration-rules nest on catalog** | **IN** | `/api/v1/configuration/rules` ≠ pricing `/api/v1/rules`. Product validate **retained**. Extract panel on catalog-detail — **no new shell** |
| **✅ Track 4: Docs closeout** | **IN** | ADR-006 header (O3 on main, still Proposed); matrix product-options + subscriptions DTO list + this wave’s FE; AGENT/ARCHITECTURE Wave O COMPLETE |

**PM default:** Largest safe LOW–MED disjoint forge. **At most one** new portal owns `app.routes.ts` + `sidebar.component.ts` + tsconfig path + `test:cpq`.

**Chosen theme:** **CPQ catalog admin cutover** — ship product-bundles portal against existing DTO HTTP, add O1 leftover rule-impact UI, nest product configuration-rules on catalog-detail. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read flip / WhatsApp / SpEL / Kafka S2B-9 / subscriptions FE / NgRx / Accept ADR-006.

**Orchestrator rule:** Wave O COMPLETE is the forge gate. Do **not** update `docs/forge/00-orchestrator-status.md` from implementer PRs.

---

## 1. Problem / evidence

### GitNexus freshness

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree (architect): /home/username01/Projects/open-crm-cpq/.worktrees/architect
Indexed commit: fc7e631
HEAD (primary): fc7e631
Status: MATCHES HEAD
CLI caveat: gitnexus status may still report stale due to untracked .worktrees/ — false stale
Always: -r /home/username01/Projects/open-crm-cpq --file <path>
UNKNOWN ≠ unused
```

### GitHub (`drgaciw/open-crm-cpq`)

| Kind | IDs | Wave P |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** | — |
| #430 WhatsApp | on `main` | **OUT** |

### Shell / security @ `fc7e631`

| Surface | State |
|---------|-------|
| Routes + sidebar | dashboard, orgs, contacts, leads, deals, catalog, quotes, approvals, price-books, pricing-customers, **rule-sets**, `pricing/simulator` — **no** bundles, subscriptions, configuration-rules, product-options **standalone** |
| Dual-read | `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |
| O1 leftover | `analyzeImpact` stretch **skipped** (`docs/forge/impl/o1-fe-rule-sets.md`) |
| catalog-detail | **1056** lines after O2 options+attrs — P3 must **extract a panel**, not dump more into the mega-file |

### P-T1 — Bundles HTTP ready; portal missing (MED product)

| Surface | State |
|---------|-------|
| BE `ProductBundleController` | `/api/v1/product-bundles` POST create, GET page, GET `/{id}`, GET `/product/{productId}`, POST `/{id}/items`, DELETE `/{bundleId}/items/{itemId}`, DELETE `/{id}` |
| DTOs | `ProductBundleDto` (id, productId, name, description, `DiscountType` PERCENTAGE/FIXED_AMOUNT/NONE, discountValue, min/maxItems, status, `items[]`); `ProductBundleCreateRequest`; `BundleItemCreateRequest` (productId, quantity≥1, optional, sortOrder); `BundleItemDto` |
| Gateway | `Path=/api/v1/product-bundles/**` (+ CPQ alias) on product-service |
| Kafka | `ProductBundleService.addItem` / `removeItem` publish `BUNDLE_STRUCTURE_CHANGED`; CE `handleBundleStructureChanged` invalidates — **do not edit Java** |
| FE | `rg` **no** portal. `ProductDto.bundle` / create/update `bundle?: boolean` only. Agents document HTTP (`cpq-agent-016` etc.) |

**GitNexus:** `ProductBundleController` **UNKNOWN** (0, HTTP). `ProductBundleService` **UNKNOWN** (0; Spring). `rg`: controller + `ProductBundleLifecycleEventTest` + CE consumer. Query `product bundles` → create/toDto + `handleBundleStructureChanged`.

### P-T2 — `analyzeImpact` HTTP ready; O1 UI missing (LOW)

| Surface | State |
|---------|-------|
| FE `RuleService.analyzeImpact(id)` | `GET /api/v1/rules/{id}/impact` → `RuleImpactDto` (`pricing.model.ts` mirrors BE) |
| BE `RuleController.impact` | `ApiResponse<RuleImpactDto>` — ruleId, name, type, status, priority, ruleSetId/status, activeRuleSet, total/active rules in set, pricing executions (all-time + last 30d) |
| UI | **zero** — service + `rule.service.spec.ts` only (`rg analyzeImpact`) |

**GitNexus:** `analyzeImpact` **UNKNOWN** (0 UI callers) — `rg` confirms unused UI. FE `RuleService` class **LOW exact** (barrel + rule-sets/price-books/… import the data-access barrel). **Do not edit** `pricing-data-access`; only rule-sets UI.

### P-T3 — Configuration rules HTTP ready; catalog has no admin (MED product)

| Surface | State |
|---------|-------|
| BE `ConfigurationRuleController` | `/api/v1/configuration/rules` POST, GET page, GET `/{id}`, GET `/product/{productId}`, DELETE `/{id}`; **also** `POST /validate` → `List<String>` **retained** |
| DTO | `ConfigurationRuleDto`; `ConfigRuleCreateRequest` (name, description, `ConfigRuleType` REQUIRES/EXCLUDES/RECOMMENDS/DEFAULT/VALIDATES, sourceProductId, targetProductId, conditionExpression, errorMessage, priority) |
| Gateway | `/api/v1/configuration/**` on product-service **after** validate rewrite to CE evaluate |
| FE | **empty** clients. O1 api-contract spec **forbids** `/api/v1/configuration/rules` on rule-sets lib |

**GitNexus:** `ConfigurationRuleController` **UNKNOWN** (HTTP). `ConfigurationRuleService` **UNKNOWN**. `rg`: controller + `ConfigurationValidateParityFixtureTest` + CE evaluator test mention.

### P-T4 — Docs drift after O3 merge (LOW)

| Artifact | Drift |
|----------|--------|
| ADR-006 header | “**FORGING** (not on `main`)” — **false**; O3 merged `14b1a6e` / closeout `fc7e631` |
| `docs/adr/README.md` | Still “O3 forging … until O3 is on `main`” |
| `AGENT.md` / `ARCHITECTURE.md` | Still “Wave O FORGING” |
| Matrix §7a | Attributes have O2 notes; **no** product-options path rows; **no** `/api/v1/subscriptions` DTO list row (`rg` subscriptions **zero** in matrix) |

### Dual-read (hold)

```yaml
# backend/services/quote-service/src/main/resources/application.yml
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

---

## 2. Decisions + rejected alternatives

### Track 1 — Product bundles portal (new lib, **owns shell**)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Home | New Nx lib `frontend/libs/cpq/product-bundles` (`@frontend/cpq-product-bundles`, project `cpq-product-bundles`) mirroring price-books / rule-sets | Nest on catalog-detail (1056 lines + would fight P3); `/catalog/bundles` child routes (edits `catalog.routes.ts`); NgRx |
| Models | Colocate DTOs in the new lib (not `shared/models` barrel — avoids P3 conflict) | Editing `product.model.ts` `bundle?: boolean` |
| Day-1 | Paginated list `GET /api/v1/product-bundles`; detail `GET /{id}` + items table; create dialog (`productId`, name, optional description/discountType/discountValue/minItems/maxItems); add item dialog; remove item + delete bundle **ConfirmDialog**; surface `BUNDLE_MAX_ITEMS_EXCEEDED` / `BUNDLE_MIN_ITEMS_REQUIRED` in banner | Bundle price simulation; changing Kafka/outbox; CE consumer |
| Optional filter | List may filter via `GET …/product/{productId}` (productId query) — non-blocker | Required for merge |
| Shell | Route `product-bundles` + sidebar **Bundles**; P1 **owns** `app.routes.ts`, `sidebar.component.ts`, `frontend/tsconfig.base.json` path, `frontend/package.json` `test:cpq` | P2/P3 editing shell |
| API | `/api/v1/product-bundles/**` only (gateway already routes) | `/api/v1/cpq/product-bundles` from browser; product validate |

### Track 2 — Rule impact on existing rule-sets lib (no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | `libs/cpq/rule-sets` only. Per-rule **Impact** on detail nested table → dialog or extracted panel showing `RuleImpactDto` via existing `RuleService.analyzeImpact` | New shell; editing `pricing-data-access`; calling product `/api/v1/configuration/rules` |
| HTTP | `GET /api/v1/rules/{id}/impact` only | Recalculate/execute pricing |
| File size | `rule-set-detail.component.ts` is **649** lines — extract `rule-impact-dialog` (or panel), host a button | Stuff 100+ lines into detail template |

### Track 3 — Configuration-rules nest on catalog (existing lib, no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | New `configuration-rule.service.ts` + **extracted** `config-rules-panel` + create dialog in `libs/cpq/catalog`. Host `<cpq-config-rules-panel>` on catalog-detail (minimal insert). Models **in catalog lib** (not `shared/models` index) | New shell portal; same-file dump into 1056-line detail; parallel pane vs P1 on `catalog.routes.ts` |
| Day-1 | `GET /api/v1/configuration/rules/product/{productId}`; create (`ConfigRuleCreateRequest`, sourceProductId = current product); delete ConfirmDialog; ruleType select; optional targetProductId + conditionExpression **textarea** (store only — **no** SpEL eval) | Global rules list portal; validate button; deleting product `POST /validate` |
| API | `/api/v1/configuration/rules` (+ `/product/{id}`) only | `/api/v1/rules` (pricing); `/api/v1/pricing/rule-sets`; CE session `/api/v1/configurations` |

### Track 4 — Docs closeout

| Topic | Decision | Rejected |
|-------|----------|----------|
| ADR-006 | Header: O3 **on `main`**; subscription leak **gone**; **Status remains Proposed** because other controllers still return entities (`ConfigurationSession`, `ApprovalRequest`, `DocumentGenerationJob`). Catalog/README same | **Accept** “DTO-only API” this wave |
| Matrix | §7a: product-options FE rows (O2); product-bundles list/items FE (P1); new subscriptions DTO list row (O3, no FE); §3 config-rules FE nest (P3); dual-read hold **J–P** | Inventing Activities paths |
| Steering | `AGENT.md` + `ARCHITECTURE.md`: Wave **O COMPLETE**, Wave **P** this plan | Editing `00-orchestrator-status.md` |

---

## 3. PR DAG (max safe parallelism)

Disjoint: **FE product-bundles lib + shell** ∥ **FE rule-sets impact** ∥ **FE catalog config-rules panel**.

```text
P1 FE product-bundles portal (SHELL)   P2 FE rule-sets analyzeImpact    P3 FE catalog config-rules
──────────────────────────────────     ────────────────────────────     ──────────────────────────
new lib + list/detail/create             rule-impact dialog on            extracted panel + HTTP
items add/remove + delete bundle         existing rule-set-detail         GET product/{id} + create/delete
        │  owns app.routes/sidebar              │  no shell                      │  no shell
        │  tsconfig path + test:cpq             │  do not edit data-access        │  do not edit app.routes
        │  models in new lib                    │                                │  models in catalog lib
        └───────────────────────────────────────┴────────────────────────────────┘
                                                 ▼
                                  P4 docs/matrix/ADR-006 header (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **P1** | `feat(fe-cpq): product-bundles admin portal` | New `libs/cpq/product-bundles`; shell files listed above; Vitest; `docs/forge/impl/p1-fe-product-bundles.md` | **MED** | List/detail/create; add/remove items; delete ConfirmDialog; `/api/v1/product-bundles` only; never configuration/rules or subscriptions; `nx test cpq-product-bundles` |
| **P2** | `feat(fe-cpq): rule-set analyzeImpact panel` | `rule-sets` impact dialog/panel + spec; `docs/forge/impl/p2-fe-rule-impact.md` | **LOW** | Impact button → `GET /api/v1/rules/{id}/impact`; shows RuleImpactDto fields; no shell; no `pricing-data-access` edit; `nx test cpq-rule-sets` |
| **P3** | `feat(fe-cpq): catalog nested configuration rules` | catalog panel + service + dialog + catalog-detail host; `docs/forge/impl/p3-fe-catalog-config-rules.md` | **MED** | Product-scoped list/create/delete; never `/api/v1/rules`; **no** validate UI; **no** shell; `nx test cpq-catalog` |
| **P4** | `docs(cpq): Wave P forge closeout` | ADR-006 header + adr README; matrix; AGENT/ARCHITECTURE Wave O COMPLETE; `docs/forge/impl/p4-wave-p-docs.md` | **LOW** | Docs match runtime; ADR-006 still **Proposed**; **no** orchestrator status |

### Parallelism / merge

**Day-1 max safe parallel (3 panes):** **P1 ∥ P2 ∥ P3**

| Pair | Rule |
|------|------|
| P1 vs P2 | Parallel; P1 owns shell; P2 must not edit routes/sidebar/`test:cpq`/tsconfig |
| P1 vs P3 | Parallel; P3 must not edit `app.routes.ts` / sidebar / `test:cpq` / tsconfig / product-bundles lib |
| P2 vs P3 | Parallel (rule-sets vs catalog) |
| shared-models | **Neither** P1 nor P3 edits `frontend/libs/shared/models/src/index.ts` |
| Merge | **No serial gate** except P1 exclusive shell. Overlay isolation is **not** merge SSOT |
| P4 | After P1+P2+P3 on `main` (or after last of the three merges) |

---

## 4. Blast radius / GitNexus

| Change | GitNexus / text | Wave risk |
|--------|-----------------|-----------|
| P1 new UI → bundle HTTP | Controllers/service **UNKNOWN**; `rg` controller + lifecycle test + CE consumer | **MED** (item add/remove publishes Kafka; min/max BusinessException) |
| P2 new UI → existing impact HTTP | `analyzeImpact` **UNKNOWN** (no UI); `RuleService` FE **LOW** barrel | **LOW** |
| P3 new UI → configuration-rules HTTP | Controller/service **UNKNOWN**; `rg` controller + parity test | **MED** (do not hit validate; do not confuse with pricing rules) |
| Dual-read YAML | Flip = **HIGH** — **not in wave** | — |
| Subscriptions / Accept ADR-006 | **UNKNOWN** HTTP — **not in wave** (docs header only) | — |

**WARN:**

1. Do **not** flip dual-read default (**HIGH**).  
2. Index **`fc7e631` MATCHES HEAD**; CLI `.worktrees/` stale is **false**. **UNKNOWN ≠ unused**.  
3. Disambiguate: pricing `/api/v1/rules` vs product `/api/v1/configuration/rules`; `ProductDto.bundle` boolean vs `ProductBundleDto`; FE vs BE `RuleService`; `delete` with `--file`.  
4. P1 must not call `/api/v1/configuration/**` or `/api/v1/subscriptions/**`.  
5. P3 must **not** delete or UI-replace product `POST /validate`. Gateway stays route-only.  
6. P2 must not edit `RuleService` HTTP (already correct).  
7. #430 — do not undo.  
8. Kafka `BUNDLE_STRUCTURE_CHANGED` is existing BE — FE mutations will fire it; **no Java** in this wave.

---

## 5. Herdr forge layout (Grok 4.5 IMPLEMENTERS)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wp-fe-bundles` | `feat/forge-p1-fe-product-bundles` | P1 |
| 2 | `wp-fe-rule-impact` | `feat/forge-p2-fe-rule-impact` | P2 |
| 3 | `wp-fe-config-rules` | `feat/forge-p3-fe-catalog-config-rules` | P3 |
| 4 (closeout) | `wp-docs` | `docs/forge-p4-wave-p-closeout` | P4 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated. Overlay isolation is **not** merge SSOT.  
**Mandatory:** `impact -d upstream -r /home/username01/Projects/open-crm-cpq --file <path>` before symbol edits; warn HIGH/CRITICAL/UNKNOWN; `rg` confirm.  
**Java:** none this wave. If a pane adds Java anyway: `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2`  
**Vitest:** `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`  
- P1: `cpq-product-bundles`  
- P2: `cpq-rule-sets`  
- P3: `cpq-catalog`  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read flip, WhatsApp, SpEL, Kafka S2B-9, subscriptions FE, NgRx, Accept ADR-006. Do not edit `docs/forge/00-orchestrator-status.md`.

---

## 6. Out of scope (this wave)

- Activities API / `/api/v1/activities/**`  
- CI #238, agent Kafka #241  
- Deleting product-service `POST /api/v1/configuration/validate`  
- Gateway domain validation  
- Full SpEL/Drools/OpenL engines  
- Kafka S2B-9 (and **no** Java edits to bundle outbox / CE consumer)  
- Dual-read YAML/env flip  
- WhatsApp #430 undo  
- Subscriptions **FE** (list/detail/activate) — **held** (shell + ERP)  
- Activate/suspend/cancel/renew UI  
- NgRx  
- Accept ADR-006  
- Configuration-rules **standalone** portal (shell)  
- Expanding `pricing-ui` stub  
- DTO-wrapping `ConfigurationSession` / `ApprovalRequest` / `DocumentGenerationJob`

---

## 7. Goal text (paste into `/goal` after Wave O COMPLETE)

```text
Implement Open CRM/CPQ Wave P per docs/forge/28-wave-p-architecture.md in three parallel tracks: (1) new FE lib @frontend/cpq-product-bundles — paginated list/detail/create via GET/POST /api/v1/product-bundles; add item POST /{id}/items; remove item DELETE /{bundleId}/items/{itemId}; delete bundle ConfirmDialog; P1 owns app.routes + sidebar Bundles + tsconfig path + test:cpq; models colocated in the new lib; never /api/v1/configuration/rules or /api/v1/subscriptions; no NgRx; (2) FE rule-sets — extract impact dialog/panel on rule-set-detail; RuleService.analyzeImpact GET /api/v1/rules/{id}/impact; do not edit pricing-data-access; no shell; (3) FE catalog — extracted config-rules-panel on catalog-detail; GET /api/v1/configuration/rules/product/{productId} + create + delete; never pricing /api/v1/rules; no validate UI; no shell; no shared-models index.ts. Day-1 P1∥P2∥P3; P4 docs after (ADR-006 header O3 on main still Proposed; matrix product-options + bundles FE + subscriptions DTO list; AGENT/ARCHITECTURE Wave O COMPLETE). Vitest NX_DAEMON=false nx test cpq-product-bundles, cpq-rule-sets, cpq-catalog. GitNexus impact -d upstream -r /home/username01/Projects/open-crm-cpq --file <path>; index fc7e631 matches HEAD; UNKNOWN ≠ unused. Do not flip dual-read default, invent Activities, start #238/#241, delete product validate, embed gateway validation, reopen WhatsApp #430, SpEL, Kafka S2B-9, subscriptions FE, Accept ADR-006, or NgRx. Do not edit docs/forge/00-orchestrator-status.md. Evidence: docs/forge/impl/p*.md done, bundles portal green, rule impact green, catalog config-rules green.
```

---

## 8. Architect sign-off checklist

- [x] Wave O COMPLETE on forging tip (`fc7e631`; O1∥O2∥O3∥O4)  
- [x] GitHub: open issues **only #238, #241**; open PRs **none**; #430 on main  
- [x] Evidence: bundles HTTP no UI; analyzeImpact HTTP no UI; configuration-rules HTTP no FE; dual-read still true; ADR-006 header stale  
- [x] GitNexus: index **`fc7e631` MATCHES HEAD**; bundle/config-rule controllers **UNKNOWN** — **warned**; `analyzeImpact` **UNKNOWN** (no UI) — `rg` confirmed; FE `RuleService` **LOW**  
- [x] Dual-read flip **held**; product validate **retained**; gateway domain rules **rejected**; subscriptions FE **held**; ADR-006 stays **Proposed**  
- [x] PR DAG **3** Day-1 panes P1∥P2∥P3 → P4; **one** shell owner (**P1**)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp, SpEL, Kafka S2B-9, dual-read flip, subscriptions FE, NgRx, Accept ADR-006  
- [x] Overall wave risk **LOW–MED** (P1/P3 MED; P2/P4 LOW; no HIGH in-scope)  
- [x] Herdr Grok 4.5 implementer panes documented  
- [x] PM authorized pick from GitNexus+GitHub  
- [ ] Orchestrator → Wave P FORGING (orchestrator owns `00-*`)  
- [x] Day-1 + closeout merged; P4 COMPLETE  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). P1 owns shell. Do **not** start subscriptions FE this wave (ERP on activate). Product validate **retained**. Gateway route+adapt only. UNKNOWN HTTP controllers — `rg` before treating unused. ADR-006 stays **Proposed** (other entity leaks remain).

**WAVE P — APPROVED FOR FORGE.**
