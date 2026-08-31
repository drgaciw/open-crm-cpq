# Wave L architecture — quote documents+terminal lifecycle ∥ catalog delete ∥ price-books portal

**Status:** APPROVED FOR FORGE — PM authorized 2026-08-30 (architect pick after Wave K COMPLETE; Herdr Grok 4.5 implementers)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** Wave K COMPLETE on `main` @ `3d8ea3d` (K1–K4 merged; K5 closeout + matrix notes)  
**Survey tip:** `main` @ `3d8ea3d`  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (index **stale ~76 commits** @ `c5075a8` → tip — **WARN**; re-analyze before symbol edits); GitHub open issues/PRs; tree `rg`; Wave K leftover list

---

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
| **Bolt #430 WhatsApp** | **OUT** | Already merged on main — do not reopen/undo |
| **Contract-asset / subscriptions portal FE** | **DEFER** | BE `SubscriptionController` exists; Kafka accept→asset path; larger vertical than Day-1; Wave M+ |
| **NgRx CPQ global store** | **DEFER** | Signals / service state sufficient |
| **Quote header `update` edit form** | **DEFER** | Competes with L1 on `quotes-detail`; HTTP already wired — later polish |
| **✅ Track 1: Quote documents FE + expire/cancel/clone** | **IN** | K deferred: BE `QuoteDocumentController` + real PDF; zero FE. HTTP `expire`/`cancel`/`cloneQuote` wired; detail has no buttons. Same-file conflict → **one** quotes PR |
| **✅ Track 2: Catalog product delete** | **IN** | K deferred: `ProductService.delete` exists; detail has Edit/Save only |
| **✅ Track 3: Price-books portal FE** | **IN** | Largest adjacent safe surface: `PriceBookService` + models exist; **zero** routes/UI/sidebar (simulator only under pricing) |

**PM authorization used:** Architect picks largest safe forge from GitNexus+GitHub after Wave K COMPLETE. Prefer disjoint parallel tracks; overall risk LOW–MED.

**Chosen theme:** **CPQ portal terminal cutover** — ship quote PDF generate/list/download + expire/cancel/clone against existing BE, enable catalog soft-delete, and open a price-books browse/create surface on ready data-access. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read flip / WhatsApp / SpEL / Kafka S2B-9 / contract-asset FE / NgRx.

**Orchestrator rule:** Wave K COMPLETE is the forge gate. This file is the authorized Wave L plan. Do **not** update `docs/forge/00-orchestrator-status.md` from this PR (orchestrator owns that).

---

## 1. Problem / evidence

### L-T1 — Quote documents absent; terminal lifecycle HTTP-only (MED product / LOW–MED UX)

| Surface | State @ `3d8ea3d` |
|---------|-------------------|
| BE `QuoteDocumentController` | `POST .../documents/generate?type=`, `GET .../documents`, `GET .../documents/{id}/download` (raw PDF bytes) |
| BE `QuoteDocumentService` | Thymeleaf `quote-template` → OpenHTML→PDF; persists `QuoteDocument` blob; types `PROPOSAL` \| `ORDER_FORM` \| `SOW` |
| FE models | **No** `QuoteDocumentDto` / `DocumentType` |
| FE `QuoteService` | **No** document methods; has `expire` / `cancel` / `cloneQuote` HttpClient |
| FE `quotes-detail` | Calculate/Submit/Approve/Reject/Send/Accept + line CRUD; **no** Expire/Cancel/Clone; **no** documents panel |
| BE status gates | `expire`: SENT→EXPIRED; `cancel`: any except ACCEPTED→CANCELLED; `clone`: copy header → new DRAFT (**does not copy line items** — confirm in UI copy) |

```text
rg QuoteDocument|documents/generate frontend — empty (README notes "No document-generation panel")
rg 'expire\(|cancel\(|cloneQuote' frontend/libs/cpq/quotes — service + service.spec only (no detail handlers)
```

**GitNexus** (`-r /home/username01/Projects/open-crm-cpq`, index **~76 behind** — **UNKNOWN ≠ unused**):

| Symbol | Risk | Note |
|--------|------|------|
| `QuoteDocumentController` | **UNKNOWN** (0 upstream + riskNote) | HTTP entry — text confirms FE absent |
| `QuoteService.expire` | **LOW** (→ controller) | FE-only consumption |
| `QuoteService.cancel` | **LOW** | FE-only |
| `QuoteService.clone` | **LOW** | FE-only; clone omits lines (BE) |

**Download caveat:** `download` returns `ResponseEntity<byte[]>` (not `ApiResponse`). FE must use `responseType: 'blob'`. `apiResponseInterceptor` passes non-envelope bodies through; still prefer explicit blob + object-URL download (see `USAGE_EXAMPLE.md` export pattern).

### L-T2 — Catalog detail cannot delete (LOW UX)

| Surface | State |
|---------|-------|
| `product.service.ts` | `delete(id)` → `DELETE /api/v1/products/{id}` |
| BE `ProductController.delete` / `ProductService.delete` | Soft-delete present |
| `catalog-detail` | Edit/Save + Back — **no Delete** |

**GitNexus:** `ProductService.delete` **LOW** upstream (controller) with index boundary note (5 unresolved call sites) — **confirm with `rg`**; FE change only expected. Soft-delete + `CacheEvict` — do not invent hard delete.

### L-T3 — Price-books HTTP ready; portal UI missing (MED product / LOW–MED UX)

| Surface | State |
|---------|-------|
| `PriceBookService` | list/get/create/update/delete + entry CRUD — signal-state |
| Models | `PriceBookDto` / Create / Update / Entry* in `shared/models` |
| Shell | Routes: catalog, quotes, approvals, `pricing/simulator` only — **no** `price-books` |
| `pricing-ui` lib | Stub README only (waterfall/gauge — **out**; do not expand stub in L3) |

Disjoint from quotes/catalog → safe parallel with L1/L2. L3 owns `app.routes.ts` + sidebar (same pattern as K4).

### GitHub inventory

| Kind | IDs | Wave L |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** | — |
| #430 WhatsApp | merged on main | **OUT** (do not reopen/undo) |

### GitNexus staleness WARN

```text
Indexed commit: c5075a8
Current commit: 3d8ea3d
Status: stale (~76 commits behind)
```

Re-run `node .gitnexus/run.cjs analyze` (or `analyze --index-only`) on tip before symbol edits. Treat **UNKNOWN** as unresolved; confirm with `rg`.

---

## 2. Decisions + rejected alternatives

### Track 1 — Quote documents + expire/cancel/clone (single quotes PR)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope bundling | **One PR** owns `quotes-detail` + `QuoteService` document methods + models — documents panel **and** Expire/Cancel/Clone (same-file conflict like K2) | Parallel docs vs terminal panes (merge fights on 800+ LOC detail) |
| Panel shape | Prefer child `quote-documents-panel` component under `libs/cpq/quotes` embedded on detail; list + Generate (type select default PROPOSAL) + Download | Separate Nx lib; contract-asset Kafka UI |
| API | Quote-service paths `/api/v1/quotes/{id}/documents/**` only — **not** document-generation microservice direct | Call document-generation service from FE |
| Download | `HttpClient.get(..., { responseType: 'blob' })` + save-as via object URL / anchor; filename from DTO when listing | Assume JSON envelope on download |
| Expire | Status-gated: **SENT** only; ConfirmDialog | Silent expire; auto-expire jobs |
| Cancel | ConfirmDialog; enabled when status ≠ ACCEPTED (and ideally ≠ CANCELLED); match BE | Cancel ACCEPTED; soft-delete quote Day-1 |
| Clone | Always available (or hide only when loading); navigate to cloned quote id; UI note that **lines are not copied** (BE today) | Invent line-copy in FE; change BE clone in same PR |
| Quote `update` / soft-delete quote | **Out** of L1 | Header edit form |

### Track 2 — Catalog delete

| Topic | Decision | Rejected |
|-------|----------|----------|
| UX | Delete on `catalog-detail` via ConfirmDialog → `ProductService.delete` → navigate to list | Bulk delete; hard delete |
| Scope | `catalog-detail` (+ spec) only; do not touch quotes / shell | Expand to option/bundle delete admin |

### Track 3 — Price-books portal

| Topic | Decision | Rejected |
|-------|----------|----------|
| Home | New Nx lib `frontend/libs/cpq/price-books` (`@frontend/cpq-price-books`) mirroring catalog/approvals patterns **or** feature folder under pricing — prefer **dedicated lib** like approvals | Stuff into `pricing-ui` stub; NgRx |
| Day-1 | List (+ optional region filter) + detail (metadata + **entries list**) + Create book dialog/form via existing `PriceBookService` | Full entry CRUD write + activate workflows Day-1 (entries **read** OK; entry create/edit **optional stretch**, not blocker) |
| Shell | Route `price-books` + sidebar item; L3 **owns** shell touches | L1/L2 editing routes |
| API | `/api/v1/price-books/**` only (pricebook-service) — never `/api/v1/pricing/price-books` | Wrong gateway prefix |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **FE quotes (detail+docs)** ∥ **FE catalog detail** ∥ **FE price-books lib + shell**.

```text
Track 1 (FE quotes)              Track 2 (FE catalog)        Track 3 (FE price-books)
─────────────────────            ───────────────────         ───────────────────────
L1 documents panel +               L2 catalog-detail           L3 price-books lib +
   expire/cancel/clone +              Delete + ConfirmDialog      models reuse + route +
   QuoteService doc HTTP                                          sidebar
        │                               │                            │
        └───────────────────────────────┴────────────────────────────┘
                                              ▼
                               L4 docs/matrix forge closeout + orchestrator COMPLETE
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **L1** | `feat(fe-cpq): quote documents panel + expire/cancel/clone` | `quote.service` (+spec); `shared/models` quote document types; `quote-documents-panel` (+spec); `quotes-detail` (+spec); `docs/forge/impl/l1-fe-quote-docs-terminal.md` | **MED** | Generate/list/download work; Expire/Cancel gated + confirmed; Clone navigates to new DRAFT; Vitest green; no BE Java changes required |
| **L2** | `feat(fe-cpq): catalog product delete on detail` | `catalog-detail` (+spec); `docs/forge/impl/l2-fe-catalog-delete.md` | **LOW** | ConfirmDialog → `ProductService.delete` → list; error banner; no quotes/shell edits |
| **L3** | `feat(fe-cpq): price-books list/detail/create portal` | New `libs/cpq/price-books`; `tsconfig.base.json` path; `app.routes.ts`; `sidebar.component.ts`; Vitest; `docs/forge/impl/l3-fe-price-books.md` | **MED** | List/detail load via `PriceBookService`; create book; entries visible on detail; nav works; correct `/api/v1/price-books` prefix |
| **L4** | `docs(cpq): Wave L forge closeout` | Matrix touch-ups (documents + price-books FE notes); holds listed; `docs/forge/impl/l4-wave-l-docs.md` | **LOW** | Docs match runtime; **no** orchestrator status edit in architect-only PR if orchestrator owns `00-*` |

### Parallelism rules

**Day-1 max safe parallel (3 panes):** **L1 ∥ L2 ∥ L3**

| Pair | Conflict? | Rule |
|------|-----------|------|
| L1 vs L2 | quotes vs catalog | Parallel |
| L1 vs L3 | L3 owns shell; L1 must **not** edit `app.routes` / sidebar | Parallel |
| L2 vs L3 | catalog vs price-books; L3 owns shell | Parallel |
| L1 shared-models | Document types only under quote models; avoid dual-edit pricing models | L3 reuses existing price-book models — **no** model file fights expected |
| L4 | Docs | After Day-1 notes |

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, reopens WhatsApp #430, starts #238/#241, or builds contract-asset/subscriptions FE.

---

## 4. Blast radius / GitNexus

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| L1 FE documents | New FE → quote BE HTTP | Controller **UNKNOWN** (HTTP) | **MED** UX / blob download |
| L1 FE expire/cancel/clone | Existing BE | Methods **LOW** | **LOW–MED** status UX |
| L1 BE clone semantics | Lines **not** copied | Text in `QuoteService.clone` | Document in UI; **no** BE change in L |
| L2 `ProductService.delete` FE | Soft-delete + cache | **LOW** + unresolved-call boundary — confirm `rg` | **LOW** |
| L3 price-books lib + shell | New surface | FE outside Java graph | **MED** (new nav / contract paths) |
| Dual-read default | — | Flip = **HIGH** — **not in wave** | — |

**Explicit HIGH / WARN callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default (**HIGH**).  
2. **Index ~76 commits behind** — re-analyze before symbol edits; **UNKNOWN ≠ unused**.  
3. Disambiguate `QuoteService` (FE vs BE) and any `cancel`/`clone` names (quote vs configuration session vs subscription) with `--file` / `--uid`.  
4. Document download is **raw PDF**, not `ApiResponse` — blob handling required.  
5. BE `clone` does **not** copy line items — FE must not claim otherwise; fixing clone-lines is **out of scope** (optional follow-up BE).  
6. Accept path still publishes CRM/Kafka events — L1 only triggers existing expire/cancel/clone/document endpoints; no new event code.

---

## 5. Herdr forge layout (Grok 4.5 panes as IMPLEMENTERS)

Orchestrator will prompt **Herdr** windows; do **not** dual-write with extra subagents on the same trees.

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wl-fe-quote-docs-terminal` | `feat/forge-l1-fe-quote-docs-terminal` | L1 |
| 2 | `wl-fe-catalog-delete` | `feat/forge-l2-fe-catalog-delete` | L2 |
| 3 | `wl-fe-price-books` | `feat/forge-l3-fe-price-books` | L3 |
| 4 (closeout) | `wl-docs` | `docs/forge-l4-wave-l-closeout` | L4 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Re-analyze GitNexus on tip; `impact` with `-d upstream -r /home/username01/Projects/open-crm-cpq --file <path>` before symbol edits; warn HIGH/CRITICAL/UNKNOWN; confirm UNKNOWN with `rg`.  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read default flip, WhatsApp, full SpEL, Kafka S2B-9, contract-asset FE, or NgRx.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- CI runners (#238), agent Kafka / Stack B2 (#241)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation in gateway  
- Full SpEL/Drools/OpenL expression engines  
- S2B-9 Kafka / Redis shared invalidation bus  
- Flipping `approvals.legacy-local-read-enabled` default to `false`  
- Reopening / undoing WhatsApp #430  
- Contract-asset / subscriptions portal FE  
- NgRx CPQ global store  
- BE changes to `QuoteService.clone` (line-item copy)  
- Quote header `update` edit form / quote soft-delete UI  
- Expanding `pricing-ui` waterfall stub; pricing rule-set admin  
- FULL_PREFLIGHT / live ERP adapters  
- Calling document-generation microservice from the browser  

---

## 7. Goal text (paste into `/goal` when forging — only after Wave K COMPLETE)

```text
Implement Open CRM/CPQ Wave L per docs/forge/21-wave-l-architecture.md in three parallel tracks: (1) FE quotes — add QuoteDocumentDto/DocumentType models; QuoteService generateDocuments/listDocuments/downloadDocument (blob) against /api/v1/quotes/{id}/documents/**; quote-documents-panel on quotes-detail (list/generate/download); status-gated Expire (SENT) + Cancel (not ACCEPTED) with ConfirmDialog; Clone navigating to new DRAFT and honest UX that BE clone does not copy line items; no BE Java changes; (2) FE catalog-detail — Delete via ConfirmDialog + ProductService.delete then navigate to list; (3) new FE cpq-price-books lib — list/detail/create via existing PriceBookService on /api/v1/price-books; show entries on detail; wire app.routes + sidebar; do not use /api/v1/pricing/price-books. Day-1 parallelize L1∥L2∥L3; L4 docs closeout after. Use GitNexus impact before symbol edits; re-analyze (index stale ~76 behind); warn HIGH/CRITICAL/UNKNOWN; disambiguate QuoteService/cancel/clone by file_path/uid. Do not invent Activities API, start #238/#241, flip dual-read default, reopen WhatsApp #430, embed validation in gateway, full SpEL, Kafka S2B-9, contract-asset FE, or NgRx. Evidence: docs/forge/impl/l*.md done, documents generate/download green, expire/cancel/clone green, catalog delete green, price-books portal green.
```

---

## 8. Architect sign-off checklist

- [x] Wave K COMPLETE on forging tip (`3d8ea3d`; K1–K4 + K5)  
- [x] GitHub: open issues **only #238, #241** (held); open PRs **none**; #430 **merged** (do not undo)  
- [x] Evidence: BE documents+PDF present, FE absent; expire/cancel/clone HTTP-only; catalog delete HTTP-only; price-books data-access without UI; dual-read default still true  
- [x] GitNexus: `QuoteDocumentController` **UNKNOWN** (HTTP); expire/cancel/clone **LOW**; `ProductService.delete` **LOW** + boundary note — **warned**; index **~76 behind**  
- [x] Dual-read default flip **held**; product validate delete **held**; gateway domain rules **rejected**; contract-asset FE **deferred**  
- [x] PR DAG max parallelism with disjoint files documented (**3** Day-1 panes: L1∥L2∥L3 → L4)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp, full SpEL, Kafka S2B-9, dual-read flip, contract-asset FE, NgRx, BE clone-lines  
- [x] Overall wave risk **LOW–MED** (L1/L3 MED; no HIGH in-scope work)  
- [x] Herdr forge layout with Grok 4.5 implementer panes documented  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [x] Orchestrator → Wave L FORGING (orchestrator owns `00-orchestrator-status.md`)  
- [ ] Day-1 + closeout merged; L4 COMPLETE  

**PM WARNING:** Re-analyze before any Java touches (none planned in L — FE-only; still WARN on stale index). Do **not** flip approval legacy dual-read default (**HIGH**). L1 download must use **blob**, not ApiResponse unwrap assumptions. L1 must not claim clone copies lines. L3 must use `/api/v1/price-books/**` only.

**WAVE L — APPROVED FOR FORGE.**
