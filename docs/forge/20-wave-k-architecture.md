# Wave K architecture — quote lifecycle+lines FE ∥ approval inbox ∥ catalog update ∥ last JsonNode landmine

**Status:** APPROVED FOR FORGE — PM authorized 2026-08-30 (architect pick after Wave J COMPLETE; orchestrator review)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** Wave J COMPLETE on `main` @ `6f66b9c` (J1–J5 merged; J6 closeout)  
**Survey tip:** `main` @ `6f66b9c`  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (index **stale ~64 commits** @ `c5075a8` → tip — **WARN**; re-analyze before symbol edits); GitHub open issues/PRs; tree `rg`; Wave J leftover list

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable |
| **CI runners (#238)** | **HOLD** | Infra/billing — only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Stack B2 / S2A-2 — out of forge wave |
| **Delete product-service validate** | **HOLD** | Dual-run parity not calendar-ready |
| **Gateway domain validation** | **REJECT** | ADR + forge DO NOT TOUCH — route+adapt only |
| **Flip `approvals.legacy-local-read-enabled` → false** | **HOLD** | Still **HIGH** cutover; J5 checklist only; default remains `${…:true}` |
| **Full SpEL / Drools / OpenL** | **REJECT** | Over-scope; H1/J2 dialect enough |
| **S2B-9 Kafka / event invalidation** | **HOLD** | Event bus not ready |
| **Bolt #430 WhatsApp** | **OUT** | **Closed** 2026-08-30 (`closed_at`); was held — do not reopen |
| **CE RECOMMENDS / DEFAULT** | **SKIP** | Already in `ProductRuleEvaluationCore` (soft RECOMMENDS + DEFAULT expand); no SpEL |
| **Document-generation / contract-asset FE** | **DEFER** | BE `QuoteDocumentController` + `document-generation` Kafka path exist; competes with K2 on quote detail; Wave L |
| **NgRx CPQ global store** | **DEFER** | Signals / service state sufficient for inbox + detail |
| **✅ Track 1: Last backend `body(JsonNode.class)` landmine** | **IN** | Only remaining hits: quote `ProductServiceClient.getProductById/Sku`; **no production callers** (`rg`); Jackson 3 bind failure if revived |
| **✅ Track 2: FE quote lifecycle + line-item richness** | **IN** | HTTP `send`/`accept`/`approve`/`reject`/`updateLineItem`/`removeLineItem` wired; detail has Calculate/Submit + add-only; no Send/Accept; lines not listed/editable |
| **✅ Track 3: FE catalog product update** | **IN** | `ProductService.update` + `ProductUpdateRequest` exist; `catalog-detail` read-only |
| **✅ Track 4: FE approval inbox** | **IN** | Quote BFF `GET /api/v1/quotes/approvals/pending` + approve/reject; **zero** FE surfaces; largest J-deferred cutover slice |

**PM authorization used:** Architect picks largest safe forge from GitNexus+GitHub after Wave J COMPLETE. Prefer disjoint parallel tracks; overall risk LOW–MED.

**Chosen theme:** **CPQ portal cutover** — finish quote status matrix + line CRUD UI, ship approval inbox against existing BFF, enable catalog edit, and delete the last RestClient `JsonNode` landmine. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read flip / WhatsApp / SpEL / Kafka S2B-9 / docgen FE.

**Orchestrator rule:** Wave J COMPLETE is the forge gate. This file is the authorized Wave K plan. Do **not** update `docs/forge/00-orchestrator-status.md` from this PR (orchestrator owns that).

---

## 1. Problem / evidence

### K-T1 — Last RestClient `body(JsonNode.class)` (LOW hygiene / MED if called)

Post-J1, backend inventory:

```text
rg 'body\(JsonNode' backend — ONLY:
  ProductServiceClient.java:44  getProductById  → .body(JsonNode.class)
  ProductServiceClient.java:52  getProductBySku → .body(JsonNode.class)
PricingServiceClient — fixed J1 (String + Jackson 2); javadoc forbids JsonNode bind
```

```text
rg getProductById|getProductBySku backend — definitions only in ProductServiceClient.java
  (no QuoteService / controller callers)
productRestClient field used solely by those two methods
validateConfiguration — gateway typed path; used by QuoteService.assertConfigurationPreflight
```

**GitNexus** (`-r /home/username01/Projects/open-crm-cpq`, index **~64 behind** — **UNKNOWN ≠ unused**):

| Symbol | Risk | Note |
|--------|------|------|
| `ProductServiceClient` (quote) | **UNKNOWN** (0 upstream + riskNote) | Text: `QuoteService` injects client for **validate only**; getters unused |
| `getProductById` | **UNKNOWN** (0) | Confirm `rg` — safe to delete after text confirm |
| `validateConfiguration` | **UNKNOWN** graph; **text:** `QuoteService` + tests | **Do not** break validate path when removing getters |

### K-T2 — Quote FE lifecycle incomplete; lines add-only (LOW–MED UX)

| Surface | State @ `6f66b9c` |
|---------|-------------------|
| `quote.service.ts` | `submit`, `approve`, `reject`, `send`, `accept`, `calculate`, `addLineItem`, `updateLineItem`, `removeLineItem` — all HttpClient |
| `quotes-detail` | Calculate + Submit only; **no** Approve/Reject/Send/Accept buttons |
| Status gates (BE) | `submit`: DRAFT→IN_REVIEW; `approve`: IN_REVIEW\|PENDING_APPROVAL→APPROVED; `reject`: →REJECTED; `send`: APPROVED→SENT; `accept`: SENT→ACCEPTED |
| Line UI | Count in header; **Add** form (`productExternalId`, `listPrice`, `quantity`) when DRAFT/REJECTED; **no list/edit/remove** of `quote.lineItems` |

**GitNexus:** `QuoteService.send` upstream **LOW** (→ `QuoteController`); FE outside Java graph.

### K-T3 — Catalog detail still read-only (LOW UX)

| Surface | State |
|---------|-------|
| `product.service.ts` | `update(id, ProductUpdateRequest)` exists |
| `catalog-detail` | Read-only cards; Back only — **no Edit/Save** |
| Models | `ProductUpdateRequest`: name, description, productType, category, status, listPrice, currency, … |

Disjoint from quotes lib → safe parallel with K2.

### K-T4 — Approval inbox missing entirely (MED product / LOW–MED UX)

BE BFF (quote-service) already:

```text
GET  /api/v1/quotes/approvals/pending
POST /api/v1/quotes/approvals/{id}/approve
POST /api/v1/quotes/approvals/{id}/reject
POST /api/v1/quotes/{quoteId}/approvals   (submit — optional for inbox Day-1)
```

Gateway routes `/api/v1/quotes/**` → quote-service (covers pending path).  
FE: **no** `approvals` lib, **no** sidebar link, **no** models for `ApprovalRequestDto`.  
`apiResponseInterceptor` unwraps `ApiResponse<T>` — FE services should type `ApprovalRequestDto[]` directly.

**GitNexus:** `ApprovalController` (quote) **UNKNOWN** (0 edges — HTTP entry); routing `ApprovalService.approve` processes exist. Disambiguate quote vs `approval-routing` controllers by `--file`.

### GitHub inventory

| Kind | IDs | Wave K |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** (`list`/`search` empty) | — |
| #430 WhatsApp | **closed** 2026-08-30 | **OUT** (do not reopen) |

### GitNexus staleness WARN

```text
Indexed commit: c5075a8
Current commit: 6f66b9c
Status: stale (~64 commits behind)
```

Re-run `node .gitnexus/run.cjs analyze` (or `analyze --index-only`) on tip before symbol edits. Treat **UNKNOWN** as unresolved; confirm with `rg`.

---

## 2. Decisions + rejected alternatives

### Track 1 — JsonNode landmine cleanup

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | **Delete** unused `getProductById` / `getProductBySku`; remove `JsonNode` import; drop `productRestClient` field/ctor param from `ProductServiceClient` | Keep typed getters “for later” (dead code + landmine) |
| RestClient bean | Remove `productRestClient` `@Bean` from `RestClientConfig` if unused after client change; leave `services.product.url` in YAML harmless or trim if nothing references | Big-bang product client rewrite |
| Validate path | **Untouched** `validateConfiguration` + gateway RestClient | Couple with product-service validate deletion |
| Bind rule | After K1: **zero** `body(JsonNode.class)` under `backend/` | String+Jackson 2 “type” unused getters |

### Track 2 — Quote detail lifecycle + lines

| Topic | Decision | Rejected |
|-------|----------|----------|
| Actions | Status-gated buttons: **Approve** / **Reject** (IN_REVIEW \| PENDING_APPROVAL); **Send** (APPROVED); **Accept** (SENT); keep Calculate/Submit | Full wizard; expire/cancel/clone Day-1 |
| Reject UX | Optional reason prompt (query param already on `reject`) | Silent reject only |
| Lines | Render `quote.lineItems` table/list; **Remove** + inline **Edit** (qty/listPrice/productName via `updateLineItem`); enrich Add with optional `productName` / `notes` | Discount engine UI; NgRx |
| Scope file | Single PR on `quotes-detail` (+ specs); use existing `QuoteService` methods | Split lifecycle vs lines across parallel PRs (same-file conflict) |
| Documents | **Out** of K2 | Document generate/download panel (Wave L) |

### Track 3 — Catalog update

| Topic | Decision | Rejected |
|-------|----------|----------|
| UX | Edit mode / form on `catalog-detail` matching `ProductUpdateRequest` (name, description, productType, category, status, listPrice, currency at minimum) | Full config-rules admin |
| Save | `ProductService.update`; stay on detail; error banner | Delete product Day-1 |

### Track 4 — Approval inbox

| Topic | Decision | Rejected |
|-------|----------|----------|
| Home | New Nx lib `frontend/libs/cpq/approvals` (`@frontend/cpq-approvals`) mirroring catalog/quotes patterns | Stuff inbox into quotes-list |
| API | Call quote BFF `/api/v1/quotes/approvals/pending` (+ approve/reject); **not** raw approval-routing (dual-read lives in quote BFF) | Bypass BFF; flip legacy default |
| Models | Add `ApprovalRequestDto` (+ tier/status unions) under `shared/models` or lib-local | Invent Activities |
| Shell | Route `approvals` in `app.routes.ts` + sidebar item | NgRx store |
| Day-1 | List pending + Approve/Reject actions + link to quote detail | Full history / submit-from-inbox / SLA |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **quote BE client** ∥ **FE quotes detail** ∥ **FE catalog detail** ∥ **FE approvals lib + shell**.

```text
Track 1 (quote BE)     Track 2 (FE quotes)              Track 3 (FE catalog)     Track 4 (FE approvals)
─────────────────      ───────────────────              ───────────────────      ────────────────────
K1 remove JsonNode        K2 lifecycle Send/Accept/        K3 catalog-detail         K4 approvals lib +
   getters + dead            Approve/Reject + line            update form               models + route +
   productRestClient         list/edit/remove                                           sidebar
        │                         │                              │                         │
        └─────────────────────────┴──────────────────────────────┴─────────────────────────┘
                                                         ▼
                                          K5 docs/matrix forge closeout + orchestrator COMPLETE
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **K1** | `refactor(quote): remove unused ProductServiceClient JsonNode getters` | `ProductServiceClient`; `RestClientConfig` `productRestClient` bean if orphaned; **update `ProductServiceClientTest` ctor** (still passes product RestClient today); `docs/forge/impl/k1-product-client-jsonnode.md` | **LOW** | `rg 'body\(JsonNode' backend` → **empty**; `validateConfiguration` tests green; no production getter callers |
| **K2** | `feat(fe-cpq): quote detail send/accept/approve/reject + line edit/remove` | `quotes-detail` (+ spec); maybe tiny `quote.service` helpers only if needed; `docs/forge/impl/k2-fe-quote-lifecycle-lines.md` | **LOW–MED** | Buttons respect BE status gates; Accept/Send/Approve/Reject call existing HTTP; line list + update/remove; Vitest green |
| **K3** | `feat(fe-cpq): catalog product update on detail` | `catalog-detail` (+ spec); `docs/forge/impl/k3-fe-catalog-update.md` | **LOW** | Edit/Save via `ProductService.update`; no quotes lib edits |
| **K4** | `feat(fe-cpq): approval inbox against quote BFF` | New `libs/cpq/approvals`; `tsconfig.base.json` path; `app.routes.ts`; `sidebar.component.ts`; shared models; Vitest; `docs/forge/impl/k4-fe-approval-inbox.md` | **MED** | Pending list loads; approve/reject succeed; nav works; uses `/api/v1/quotes/approvals/**` not routing-direct |
| **K5** | `docs(cpq): Wave K forge closeout` | Matrix touch-ups; orchestrator note for owner; `docs/forge/impl/k5-wave-k-docs.md` | **LOW** | Docs match runtime; holds listed; **no** orchestrator status edit in architect-only PR if orchestrator owns `00-*` |

### Parallelism rules

**Day-1 max safe parallel (4 panes):** **K1 ∥ K2 ∥ K3 ∥ K4**

| Pair | Conflict? | Rule |
|------|-----------|------|
| K1 vs K2/K3/K4 | BE vs FE | Parallel |
| K2 vs K3 | Different FE libs | Parallel |
| K2 vs K4 | quotes vs new lib; K4 may touch `app.routes`/`sidebar` only | Parallel — **K2 must not** edit shell/routes |
| K3 vs K4 | catalog vs approvals; K4 owns shell | Parallel |
| K4 shared-models | If K2 needs no model churn | Prefer approval DTOs in K4 only; avoid dual-edit `quote.model.ts` |
| K5 | Docs | After Day-1 notes |

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, reopens WhatsApp #430, or starts #238/#241.

---

## 4. Blast radius / GitNexus

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| K1 `ProductServiceClient` getters | None (`rg`) | **UNKNOWN** → text-confirmed unused | **LOW** |
| K1 `validateConfiguration` | `QuoteService` submit preflight | **UNKNOWN** graph; **text critical** — do not regress | **MED** if broken |
| K2 FE `QuotesDetailComponent` | Shell quotes routes | FE outside Java graph | **LOW–MED** status UX |
| K2 BE `QuoteService.send/accept` | `QuoteController` | send **LOW** | FE-only; no BE change expected |
| K3 `CatalogDetailComponent` | Catalog routes | FE | **LOW** |
| K4 new approvals lib + shell | `app.routes`, sidebar | New surface | **MED** (BFF contract / dual ApprovalController names) |
| Dual-read default | — | Flip = **HIGH** — **not in wave** | — |

**Explicit HIGH / WARN callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default (**HIGH**).  
2. **Index ~64 commits behind** — re-analyze before symbol edits; **UNKNOWN ≠ unused**.  
3. Disambiguate `ApprovalController` / `ApprovalService` (**quote** vs **approval-routing**) and `QuoteService` (FE vs BE) with `--file` / `--uid`.  
4. K1 must not break `validateConfiguration` (text callers in `QuoteService`).  
5. Accept on quote publishes CRM/Kafka events in BE — FE only triggers existing endpoint; no new event code in K.

---

## 5. Herdr forge layout (4 Day-1 panes + closeout)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wk-product-client-jsonnode` | `feat/forge-k1-product-client-jsonnode` | K1 |
| 2 | `wk-fe-quote-lifecycle-lines` | `feat/forge-k2-fe-quote-lifecycle-lines` | K2 |
| 3 | `wk-fe-catalog-update` | `feat/forge-k3-fe-catalog-update` | K3 |
| 4 | `wk-fe-approval-inbox` | `feat/forge-k4-fe-approval-inbox` | K4 |
| 5 (closeout) | `wk-docs` | `docs/forge-k5-wave-k-closeout` | K5 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Re-analyze GitNexus on tip; `impact` with `-d upstream -r /home/username01/Projects/open-crm-cpq --file <path>` before symbol edits; warn HIGH/CRITICAL/UNKNOWN; confirm UNKNOWN with `rg`.  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read default flip, WhatsApp, full SpEL, Kafka S2B-9, or document-generation FE.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- CI runners (#238), agent Kafka / Stack B2 (#241)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation in gateway  
- Full SpEL/Drools/OpenL expression engines  
- S2B-9 Kafka / Redis shared invalidation bus  
- Flipping `approvals.legacy-local-read-enabled` default to `false`  
- Reopening Bolt #430 WhatsApp  
- Document-generation / contract-asset portal FE  
- NgRx CPQ global store  
- CE dialect work (RECOMMENDS/DEFAULT/VALIDATES `in:` already landed)  
- FULL_PREFLIGHT / live ERP adapters  

---

## 7. Goal text (paste into `/goal` when forging — only after Wave J COMPLETE)

```text
Implement Open CRM/CPQ Wave K per docs/forge/20-wave-k-architecture.md in four parallel tracks: (1) quote-service — delete unused ProductServiceClient.getProductById/getProductBySku that still use body(JsonNode.class); drop dead productRestClient wiring; leave validateConfiguration untouched; zero body(JsonNode.class) remain under backend/; (2) FE quotes-detail — status-gated Approve/Reject/Send/Accept using existing QuoteService HTTP; list line items with edit/remove via updateLineItem/removeLineItem; enrich add form optionally; no document-generation panel; (3) FE catalog-detail — Edit/Save via ProductService.update matching ProductUpdateRequest; (4) new FE cpq-approvals lib — pending inbox against GET /api/v1/quotes/approvals/pending + approve/reject; wire app.routes + sidebar; do not call approval-routing directly; do not flip legacy-local-read default. Day-1 parallelize K1∥K2∥K3∥K4; K5 docs closeout after. Use GitNexus impact before symbol edits; re-analyze (index stale ~64 behind); warn HIGH/CRITICAL/UNKNOWN; disambiguate ApprovalController/QuoteService by file_path/uid. Do not invent Activities API, start #238/#241, flip dual-read default, reopen WhatsApp #430, embed validation in gateway, full SpEL, Kafka S2B-9, or docgen FE. Evidence: docs/forge/impl/k*.md done, rg body(JsonNode) empty, quote lifecycle+lines green, catalog update green, approval inbox green.
```

---

## 8. Architect sign-off checklist

- [x] Wave J COMPLETE on forging tip (`6f66b9c`; J1–J5 + J6)  
- [x] GitHub: open issues **only #238, #241** (held); open PRs **none**; #430 **closed**  
- [x] Evidence: last `body(JsonNode.class)` on unused product getters; FE missing send/accept/approve/reject + line edit; catalog detail read-only; no approval FE; CE RECOMMENDS/DEFAULT already present  
- [x] GitNexus: `ProductServiceClient` / getters **UNKNOWN** (text-confirmed unused); `QuoteService.send` **LOW**; ApprovalController **UNKNOWN** — **warned**; index **~64 behind**  
- [x] Dual-read default flip **held**; product validate delete **held**; gateway domain rules **rejected**; docgen FE **deferred**  
- [x] PR DAG max parallelism with disjoint files documented (**4** Day-1 panes: K1∥K2∥K3∥K4 → K5)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp, full SpEL, Kafka S2B-9, dual-read flip, docgen FE, NgRx  
- [x] Overall wave risk **LOW–MED** (K4 MED; no HIGH in-scope work)  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [x] Orchestrator → Wave K FORGING (orchestrator owns `00-orchestrator-status.md`)  
- [ ] Day-1 + closeout merged; K5 COMPLETE  

**PM WARNING:** Re-analyze before quote BE edits (**UNKNOWN** / stale index ≠ unused/safe). Do **not** flip approval legacy dual-read default (**HIGH**). K1 deletes unused JsonNode getters only — preserve `validateConfiguration`. K4 must use quote BFF paths, not approval-routing direct.

**WAVE K — APPROVED FOR FORGE.**
