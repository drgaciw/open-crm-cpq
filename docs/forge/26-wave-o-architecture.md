# Wave O architecture — rule-set admin portal ∥ catalog options+attributes ∥ subscription DTO+list

**Status:** COMPLETE on main 2026-08-31 — O1 `8bdd38f`/`d81d417`, O2 `15f2aef`/`900a24f`, O3 `29f2960`/`14b1a6e`  
**Architect:** `opencrm-architect` (Herdr `w8`)  
**Evidence date:** 2026-08-31  
**Baseline:** Wave N COMPLETE + hygiene H1+H2 on `main` @ `4794d82`  
**Survey tip:** `origin/main` @ `4794d82`  
**Sources:** GitNexus CLI `-r /home/username01/Projects/open-crm-cpq` (indexed commit **`4794d82` MATCHES HEAD**; CLI may still flag stale from untracked `.worktrees/` — **false stale**; **UNKNOWN ≠ unused**; confirm with `rg`); GitHub `drgaciw/open-crm-cpq`; tree `rg`; Wave N leftover list

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
| **Full SpEL / Drools / OpenL** | **REJECT** | H1/J2 dialect enough; DRL is textarea + `validateSyntax`, not a new engine |
| **S2B-9 Kafka** | **HOLD** | Event bus not ready |
| **Bolt #430 WhatsApp** | **OUT** | On `main` — do not reopen/undo |
| **NgRx CPQ store** | **HOLD** | Signals / service state |
| **Contract-asset / subscriptions FE** | **HOLD** | Entity leak until DTO+list (this wave’s O3 is **BE only**) |
| **Product bundles portal** | **DEFER** | `ProductBundleController` **UNKNOWN** (HTTP); zero FE models/clients; **would own shell** — conflict with O1. Nesting on catalog-detail **same-file** as O2 |
| **Configuration-rules portal** | **DEFER** | Would compete for shell or catalog-detail; do not confuse with pricing `/api/v1/rules`. Product validate **retained** |
| **✅ Track 1: Pricing rule-set + rules admin** | **IN** | FE `RuleSetService` / `RuleService` + models ready; **zero** UI; Wave N deferred for shell — N2 landed, shell free. **Owns shell** |
| **✅ Track 2: Catalog nested options + attribute values** | **IN** | BE `ProductOptionController` + `ProductAttributeController` DTOs; gateway routes exist; **zero** FE clients; nest on `catalog-detail` — **no new shell** |
| **✅ Track 3: Subscription DTO + paginated list** | **IN** | ADR-006 Proposed; stop returning `Subscription` entity; paginated `GET /`; **no FE**; activate still ERP-safe server-side |

**PM default:** Pick largest safe LOW–MED disjoint forge. One new portal owns shell.

**Chosen theme:** **CPQ catalog/pricing admin cutover** — ship rule-set+DRL admin against existing FE HTTP, nest product option groups and attribute values on catalog detail, and close the subscription entity leak with DTO+list (no portal). No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read flip / WhatsApp / SpEL / Kafka S2B-9 / contract-asset FE / NgRx / bundles portal.

**Orchestrator rule:** Hygiene N′ COMPLETE is the forge gate. Do **not** update `docs/forge/00-orchestrator-status.md` from implementer PRs.

---

## 1. Problem / evidence

### GitNexus freshness

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree (architect): /home/username01/Projects/open-crm-cpq/.worktrees/architect
Indexed commit: 4794d82
HEAD (primary): 4794d82
Status: MATCHES HEAD — ~17367 nodes / 39031 edges / 657 clusters / 532 flows
CLI caveat: gitnexus status may still report stale due to untracked .worktrees/ — false stale
Always: -r /home/username01/Projects/open-crm-cpq --file <path>
```

### GitHub (`drgaciw/open-crm-cpq`)

| Kind | IDs | Wave O |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** | — |
| #430 WhatsApp | on `main` | **OUT** |

### Shell / security @ `4794d82`

| Surface | State |
|---------|-------|
| Routes + sidebar | dashboard, orgs, contacts, leads, deals, catalog, quotes, approvals, price-books, **pricing-customers**, `pricing/simulator` — **no** rule-sets, bundles, subscriptions, product-options, product-attributes |
| Dual-read | `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |
| Hygiene | H1 quote `httpErrorMessage` appends `[errorCode]`; H2 matrix DELETE quotes + N1 notes; H3 hookTimeout **not** opened |

### O-T1 — Rule-sets HTTP ready; portal missing (MED product)

| Surface | State |
|---------|-------|
| FE `RuleSetService` | `/api/v1/rule-sets` list/get/create/update/delete/**activate**/**deprecate** + signals |
| FE `RuleService` | `/api/v1/rules` **no bare list** — `listByRuleSet`; CRUD; `POST …/validate`; `GET …/impact` |
| Models | `RuleSetDto` / Create / Update; `RuleDto` / Create; `RuleType` / `RuleStatus` in `shared/models` |
| Gateway | `/api/v1/rule-sets/**` + `/api/v1/rules/**` on pricing-service |
| UI | **zero** (`rg` RuleSetService: service + spec + barrel only) |

**GitNexus:** `RuleSetService` **LOW exact** (1 direct → barrel, 16 total). `RuleService` **LOW exact** (same). `rg`: no UI outside pricing-data-access.

### O-T2 — Options + attributes HTTP ready; catalog-detail product-only (MED product)

| Surface | State |
|---------|-------|
| BE `ProductOptionController` | `/api/v1/product-options/groups` create/get; `GET …/groups/product/{productId}`; `POST …/groups/{id}/options`; delete group. DTO `ProductOptionGroupDto`. `OptionGroupCreateRequest`: productId, name, selectionType, min/maxSelections |
| BE `ProductAttributeController` | `/api/v1/product-attributes` list/create/get/delete; `GET/POST …/products/{productId}/values` (`AttributeValueSetRequest`: attributeId, value) |
| Gateway | `product-options/**`, `product-attributes/**` on product-service |
| FE | **empty** clients/models (catalog `ProductDto.bundle?: boolean` only) |

**GitNexus:** both controllers **UNKNOWN** (0, HTTP). `rg` FE empty. Nest on catalog-detail — **do not** open a second shell.

### O-T3 — Subscription entity at API boundary (MED API hygiene)

`SubscriptionController` returns `ApiResponse<Subscription>` / `List<Subscription>`. No DTO, no paginated list-all, `activate` → `ErpAdapter.syncToErp`. Gateway `/api/v1/subscriptions/**`. FE `rg` `/api/v1/subscriptions` **empty**. Kafka `QuoteAcceptedEventConsumer` writes **internally**.

**GitNexus:** `SubscriptionController` **UNKNOWN**. ADR-006 **Proposed**. This wave: **Java DTO+list only** — **no FE**.

### Deferred leftovers

| Candidate | Evidence |
|-----------|----------|
| Bundles portal | Controller **UNKNOWN**; paginated list + items; CE `handleBundleStructureChanged`; **shell conflict** with O1 |
| Config-rules portal | `/api/v1/configuration/rules`; validate **retained**; would fight O1 shell or O2 catalog-detail |

---

## 2. Decisions + rejected alternatives

### Track 1 — Rule-set + rules admin (new lib, **owns shell**)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Home | New Nx lib `frontend/libs/cpq/rule-sets` (`@frontend/cpq-rule-sets`, project `cpq-rule-sets`) mirroring price-books | Stuff into `pricing-ui`; `/pricing/rule-sets` nested under simulator; NgRx |
| Day-1 | List (optional status filter) + detail + create set; **Activate/Deprecate** ConfirmDialog; nested **rules** table via `RuleService.listByRuleSet`; create/edit rule (DRL `content` textarea); `validateSyntax` before/after save | Full Drools/OpenL/SpEL; impact-analysis UI as **blocker** |
| Stretch (non-blocker) | `analyzeImpact` panel; rule-set metadata update/delete | Required for merge |
| Shell | Route `rule-sets` + sidebar **Rule sets**; O1 **owns** `app.routes.ts`, `sidebar.component.ts`, `tsconfig.base.json` path, `frontend/package.json` `test:cpq` | O2/O3 editing shell |
| API | `/api/v1/rule-sets/**` and `/api/v1/rules/**` only | `/api/v1/pricing/rule-sets`; product `/api/v1/configuration/rules` |

### Track 2 — Catalog nested options + attribute values (existing lib, no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | `libs/cpq/catalog` + `shared/models` DTOs + small HTTP helpers (catalog lib or `product.service` siblings). **One PR** owns `catalog-detail` (options **and** attribute values — same-file like L1) | Parallel options vs attributes panes; new shell portal |
| Options Day-1 | Load `GET /api/v1/product-options/groups/product/{id}`; create group dialog (`OptionGroupCreateRequest`); add option; delete group ConfirmDialog | Full option-group editor as separate Nx lib |
| Attributes Day-1 | `GET …/product-attributes/products/{id}/values`; set value via `AttributeValueSetRequest`; list attribute definitions as picker (`GET /api/v1/product-attributes`) | Global attribute-definition admin portal (shell) |
| API | `/api/v1/product-options/**` + `/api/v1/product-attributes/**` | `/api/v1/cpq/…` from browser; configuration/validate |
| Out | Bundles; configuration rules; product validate | — |

### Track 3 — Subscription DTO + paginated list (Java, no FE)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | `contract-asset-management`: `SubscriptionDto` + MapStruct; controller returns DTOs only; `GET /api/v1/subscriptions` paginated; keep getById/customer/status/activate/suspend/cancel/renew | Subscriptions FE; changing ERP adapter contract |
| Activate | Still calls `ErpAdapter.syncToErp`; persist erp fields; **return DTO** | Swallow ERP; FE calling ERP |
| Tests | Controller/service unit tests: no entity in JSON; page metadata | Live ERP |
| JAVA_HOME | `/home/username01/.local/share/mise/installs/java/21.0.2` | — |
| ADR-006 | Remains **Proposed** until this lands; then note in O4 — do not Accept “leak gone” until merged | Accept ADR-006 in the Java PR as if FE exists |

**Update 2026-10-16 (DRG-543, docs DRG-608):** Wave O kept `POST /{id}/renew` as a DTO lifecycle route. That route now returns 410 Gone (`SUBSCRIPTION_RENEW_DEPRECATED`, `Deprecation: true`, successor `Link` to `POST /api/v1/quotes/continuations`). `Subscription.renew` and `SubscriptionService.renew` were removed. Priced renewal is an accepted quote continuation (`AcceptedQuoteSubscriptionService`). The scope row above is the wave decision and is left as written.

---

## 3. PR DAG (max safe parallelism)

Disjoint: **FE rule-sets lib + shell** ∥ **FE catalog-detail** ∥ **BE contract-asset**.

```text
O1 FE rule-sets portal (SHELL)     O2 FE catalog options+attrs      O3 BE subscription DTO+list
──────────────────────────────     ────────────────────────────     ──────────────────────────
new lib + RuleSet/Rule HTTP          catalog-detail panels +           SubscriptionDto+MapStruct
activate/deprecate + DRL textarea      models/clients                    GET page; no entity JSON
        │                                    │                                 │
        │  Day-1 CODE parallel               │  no shell                       │  Java only
        │  MERGE: O1 exclusive shell         │                                 │
        └────────────────────────────────────┴─────────────────────────────────┘
                                               ▼
                                O4 docs/matrix closeout (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **O1** | `feat(fe-cpq): rule-sets admin portal` | New `libs/cpq/rule-sets`; shell files listed above; Vitest; `docs/forge/impl/o1-fe-rule-sets.md` | **MED** | List/detail/create; activate/deprecate ConfirmDialog; nested rules CRUD + validateSyntax; `/api/v1/rule-sets` + `/api/v1/rules`; never configuration/rules; `nx test cpq-rule-sets` |
| **O2** | `feat(fe-cpq): catalog option groups + attribute values` | `catalog-detail` (+spec); models; HTTP helpers (+spec); `docs/forge/impl/o2-fe-catalog-options-attrs.md` | **MED** | Groups load/create/add/delete; attribute values load/set; no shell; no quotes/rule-sets; `nx test cpq-catalog` + `shared-models` if touched |
| **O3** | `feat(contract): subscription DTO + paginated list` | contract-asset DTO/mapper/controller/service/tests; `docs/forge/impl/o3-be-subscription-dto.md` | **MED** (JSON break / UNKNOWN HTTP) | No `ApiResponse<Subscription>`; paginated GET `/`; lifecycle methods return DTO; activate still ERP; tests green; **no FE** |
| **O4** | `docs(cpq): Wave O forge closeout` | Matrix notes (rule-sets FE, options/attrs FE, subscription DTO); holds; `docs/forge/impl/o4-wave-o-docs.md` | **LOW** | Docs match runtime; **no** orchestrator status edit |

### Parallelism / merge

**Day-1 max safe parallel (3 panes):** **O1 ∥ O2 ∥ O3**

| Pair | Rule |
|------|------|
| O1 vs O2 | Parallel; O1 owns shell; O2 must not edit routes/sidebar/`test:cpq` |
| O1 vs O3 | Parallel (FE vs Java) |
| O2 vs O3 | Parallel |
| Merge | **No serial gate** except O1 exclusive shell. O3 JSON break: land anytime; FE must not be started against entities |

---

## 4. Blast radius / GitNexus

| Change | GitNexus / text | Wave risk |
|--------|-----------------|-----------|
| O1 new UI → existing rule HTTP | `RuleSetService` / `RuleService` **LOW exact**; `rg` zero UI today | **MED** (activate/deprecate + DRL) |
| O2 new FE → option/attribute HTTP | Controllers **UNKNOWN**; `rg` FE empty | **MED** (catalog-detail size) |
| O3 entity → DTO | `SubscriptionController` **UNKNOWN**; Kafka consumer **not** HTTP | **MED** (breaking JSON for any unknown client) |
| Dual-read YAML | Flip = **HIGH** — **not in wave** | — |
| Bundles / config-rules | **UNKNOWN** HTTP — **not in wave** | — |

**WARN:**

1. Do **not** flip dual-read default (**HIGH**).  
2. Index **`4794d82` MATCHES HEAD**; CLI `.worktrees/` stale is **false**. **UNKNOWN ≠ unused**.  
3. Disambiguate `RuleSetService` FE vs BE; `RuleService` vs product `ConfigurationRule*`; `delete`/`update` with `--file`.  
4. O3 must not ship subscriptions FE.  
5. O1 must not call product `/api/v1/configuration/**`.  
6. Gateway stays route-only. Product validate **retained**.  
7. #430 — do not undo.

---

## 5. Herdr forge layout (Grok 4.5 IMPLEMENTERS)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wo-fe-rule-sets` | `feat/forge-o1-fe-rule-sets` | O1 |
| 2 | `wo-fe-catalog-options-attrs` | `feat/forge-o2-fe-catalog-options-attrs` | O2 |
| 3 | `wo-be-subscription-dto` | `feat/forge-o3-be-subscription-dto` | O3 |
| 4 (closeout) | `wo-docs` | `docs/forge-o4-wave-o-closeout` | O4 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated. Overlay isolation is **not** merge SSOT.  
**Mandatory:** `impact -d upstream -r /home/username01/Projects/open-crm-cpq --file <path>` before symbol edits; warn HIGH/CRITICAL/UNKNOWN; `rg` confirm.  
**Java (O3 only):** `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2`  
**Vitest:** `cd frontend && NX_DAEMON=false npx nx test cpq-rule-sets --skip-nx-cache` (O1); `nx test cpq-catalog` (O2); `shared-models` if O2 adds types.  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read flip, WhatsApp, SpEL, Kafka S2B-9, contract-asset FE, bundles portal, NgRx. Do not edit `docs/forge/00-orchestrator-status.md`.

---

## 6. Out of scope (this wave)

- Activities API / `/api/v1/activities/**`  
- CI #238, agent Kafka #241  
- Deleting product-service `POST /api/v1/configuration/validate`  
- Gateway domain validation  
- Full SpEL/Drools/OpenL engines  
- Kafka S2B-9  
- Dual-read YAML/env flip  
- WhatsApp #430 undo  
- Subscriptions **FE**  
- NgRx  
- Product-bundles **portal** (shell)  
- Configuration-rules admin portal  
- Expanding `pricing-ui` stub  
- H3 TestBed hookTimeout (optional leftover, not this wave)

---

## 7. Goal text (paste into `/goal` after hygiene N′ COMPLETE)

```text
Implement Open CRM/CPQ Wave O per docs/forge/26-wave-o-architecture.md in three parallel tracks: (1) new FE lib @frontend/cpq-rule-sets — list/detail/create rule sets via RuleSetService on /api/v1/rule-sets; Activate/Deprecate ConfirmDialog; nested rules via RuleService.listByRuleSet on /api/v1/rules (no bare list); DRL textarea create/edit + validateSyntax; O1 owns app.routes + sidebar Rule sets + tsconfig path + test:cpq; never /api/v1/configuration/rules or /api/v1/pricing/rule-sets; no NgRx; (2) FE catalog-detail — option groups GET /api/v1/product-options/groups/product/{id} + create group + add option + delete group; attribute values GET/POST /api/v1/product-attributes/products/{id}/values with definition picker GET /api/v1/product-attributes; add shared-models DTOs; no shell; (3) BE contract-asset — SubscriptionDto + MapStruct; controller never returns Subscription entity; paginated GET /api/v1/subscriptions; activate still ErpAdapter; no FE. Day-1 O1∥O2∥O3; O4 docs after. JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 for O3. Vitest NX_DAEMON=false nx test cpq-rule-sets, cpq-catalog. GitNexus impact -d upstream -r /home/username01/Projects/open-crm-cpq --file <path>; index 4794d82 matches HEAD; UNKNOWN ≠ unused. Do not flip dual-read default, invent Activities, start #238/#241, delete product validate, embed gateway validation, reopen WhatsApp #430, SpEL, Kafka S2B-9, contract-asset FE, bundles portal, or NgRx. Do not edit docs/forge/00-orchestrator-status.md. Evidence: docs/forge/impl/o*.md done, rule-sets portal green, catalog options/attrs green, subscription DTO tests green.
```

---

## 8. Architect sign-off checklist

- [x] Hygiene N′ COMPLETE on forging tip (`4794d82`; H1+H2; H3 not opened)  
- [x] GitHub: open issues **only #238, #241**; open PRs **none**; #430 on main  
- [x] Evidence: RuleSet/Rule HTTP no UI; options/attributes HTTP no FE; Subscription entity leak; dual-read still true  
- [x] GitNexus: index **`4794d82` MATCHES HEAD**; `RuleSetService`/`RuleService` **LOW**; option/attribute/bundle/subscription controllers **UNKNOWN** — **warned**; `rg` confirmed  
- [x] Dual-read flip **held**; product validate **retained**; gateway domain rules **rejected**; contract-asset FE **held**; bundles portal **deferred** (shell)  
- [x] PR DAG **3** Day-1 panes O1∥O2∥O3 → O4; **one** shell owner (**O1**)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp, SpEL, Kafka S2B-9, dual-read flip, contract-asset FE, NgRx  
- [x] Overall wave risk **LOW–MED** (O1/O2/O3 MED; no HIGH in-scope)  
- [x] Herdr Grok 4.5 implementer panes documented  
- [x] PM authorized pick from GitNexus+GitHub  
- [ ] Orchestrator → Wave O FORGING (orchestrator owns `00-*`)  
- [ ] Day-1 + closeout merged; O4 COMPLETE  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). O1 owns shell. O3 is **not** a subscriptions portal. Product validate **retained**. Gateway route+adapt only. UNKNOWN HTTP controllers — `rg` before treating unused.

**WAVE O — APPROVED FOR FORGE.**
