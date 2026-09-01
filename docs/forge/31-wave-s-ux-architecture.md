# Wave S architecture — UX/UI polish (post-Q leftovers)

**Status:** READY FOR FORGE — orchestrator locked 2026-08-31: Waves **A–Q COMPLETE**; **N COMPLETE** (no N′′); **R2 list-row delete remains HELD**  
**Architect:** `opencrm-architect` (Herdr `w8`) — **plan only; do not spawn implementers**  
**Evidence date:** 2026-08-31  
**Baseline:** `origin/main` @ `857e724` (Q closeout `c295e12`; orchestrator status `857e724`)  
**Survey tip:** `origin/main` @ `857e724`  
**Sources:** `rg` + **read templates**. GitNexus index **`fc7e631`** vs HEAD **`857e724`** — **stale**; **analyze skipped**; UNKNOWN ≠ unused; `-r /home/username01/Projects/open-crm-cpq --file`.  
**Letter skip:** Wave **S** (no Wave R) so tracks are never named R1/R2.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Reopen N1–N4 / N′′** | **REJECT** | Locked complete |
| **Price-book list-row delete** | **HOLD** | Former N stretch; do **not** un-hold; do **not** name a track R2 |
| **New portal / `app.routes` / tsconfig / `test:cpq`** | **REJECT** | Polish-only; P1 owns Bundles shell; Q4 owns chrome |
| **Re-litigate Q1 ConfirmDialog / EmptyState / PageHeader / DataTable APIs** | **REJECT** | Landed; keep compatible |
| **Skip-link / mobile sidenav** | **OUT** | Q4 landed (`shell.component.ts` skip-link + `BreakpointObserver`) |
| **Extract shared `httpErrorMessage`** | **REJECT** | Copy H1 locally; no serial gate |
| **Subscriptions FE / Accept ADR-006 / NgRx / Activities / #238 / #241 / dual-read flip / validate delete / gateway domain rules / SpEL / Kafka S2B-9 / WhatsApp #430** | **HOLD** | Standing |
| **✅ S1 CRM deal + org empty-state** | **IN** | Custom `.empty-state` divs; leads/contacts already `app-empty-state` |
| **✅ S2 quotes-detail overflow + list service errorCode** | **IN** | ~12 header lifecycle buttons; Q2 stretch now eligible; **do not change** H1 helper body |
| **✅ S3 product-bundles list/get banners** | **IN** | UI helper exists but **service** `error.set` is message-only; helper only appends two codes |
| **✅ S4 simulator + price-book create (+ list service) errorCode** | **IN** | Simulator HTTP error message-only; create-dialog message-only; `PriceBookService` list errors message-only |
| **✅ S5 docs closeout** | **IN** | Dual-read J–S; R2 still held; no `00-orchestrator-status` |

**Chosen theme:** **FE-only leftover polish** after Q. **Zero new portal. Zero chrome owner.**

**Orchestrator rule:** Implementers must **not** edit `docs/forge/00-orchestrator-status.md`.

---

## 1. Problem / evidence (templates, not the seed list)

Seeds were **verified**; two were **stale**.

### GitNexus / GitHub / dual-read

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree: /home/username01/Projects/open-crm-cpq/.worktrees/architect
Indexed: fc7e631   HEAD: 857e724   STALE — analyze skipped
```

| Item | State |
|------|--------|
| Open issues | **#238**, **#241** only — **HOLD** |
| Open PRs | **none** |
| #430 | on `main` — do not undo |
| Dual-read | `legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |

### S-T1 — CRM lists (confirmed)

| File | Empty UI |
|------|----------|
| `deal-list.component.ts` L60–68 | Custom `<div class="empty-state">` + local CSS; **not** `EmptyStateComponent`; custom `.page-header` (not `app-page-header`); loading spinner **no** `role="status"` |
| `organization-list.component.ts` L52–60 | Same custom empty-state + custom header |
| `lead-list.component.ts` L79–87 | **`app-empty-state`** + `app-page-header` (pattern to copy) |
| `contact-list.component.ts` | **`app-empty-state`** + `app-page-header` |

### S-T2 — quotes-detail density (confirmed; H1 helper already correct)

`quotes-detail.component.ts` `header-actions` (L89–236): **12** buttons — Back, Calculate, Submit, Approve, Reject, Send, Accept, Expire, Cancel quote, Clone, Delete. CSS `flex-wrap` only (L510–515). Q2 **skipped** this stretch.

H1 helper L39–50 **already** appends `[errorCode]` — **do not rewrite**. Additional `mat-button`s exist on error banner and line-item rows (not the overflow target).

`quote.service.ts` list/get `error.set(err?.error?.message \|\| err?.message)` — **message-only**. `quotes-list` banner binds `quoteService.error()` — list load failures omit codes.

### S-T3 — product-bundles (seed half-true)

| Surface | Reality |
|---------|---------|
| `bundle-http-error.ts` | **Exists.** Appends code **only** for `BUNDLE_MAX_ITEMS_EXCEEDED` / `BUNDLE_MIN_ITEMS_REQUIRED` — **not** H1 “any errorCode” |
| Dialogs/detail | Use `bundleHttpErrorMessage` (specs cover those two codes) |
| `product-bundle.service.ts` L59–61, 88–90, 104–106 | `error.set(err?.error?.message \|\| err?.message)` — **no code** on list/get banners |

Do **not** fight P1 routes/shell.

### S-T4 — simulator + price-books (seed stale on “no helper”)

| Surface | Reality |
|---------|---------|
| `price-book-detail` / edit-dialog / entry-dialog | **Have** `bookHttpErrorMessage` with `errorCode` (N1) |
| `price-book-create-dialog.component.ts` L229–233 | **message-only** |
| `price-book.service.ts` (pricing-data-access) list/get | **message-only** `error.set` |
| `simulator-page.component.ts` | `app-empty-state` for idle + failed (L137–148) **exists**. HTTP `error` handler L376–380 **message-only**. `pricingResult.errorMessage` path has no code (body field, OK) |
| Skip-link | **Already in shell** (Q4) — **out** |

### Q1 / Q4 — do not touch

ConfirmDialog / EmptyState / PageHeader / DataTable APIs; shell skip-link + mobile sidenav; sidebar labels; Bundles route.

---

## 2. Decisions + rejected

### Track 1 — CRM deal + organization empty-state (no chrome)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Empty | Replace custom `.empty-state` with `app-empty-state` (lead/contact pattern): icon, title, message, actionLabel Create … | Rewriting tables to `app-data-table` |
| Loading | `role="status" aria-live="polite"` on spinner wrappers | — |
| Header | **Stretch:** `app-page-header` like leads — **non-blocker**; Day-1 may keep custom header if empty-state lands | Mandatory header rewrite |
| Shell | **None** | `app.routes` / sidebar |

### Track 2 — quotes overflow + list errorCode (no chrome)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Overflow | Keep **Back** + **status-gated primary** (Calculate/Submit/Send/Accept as today when enabled). Collapse **Expire, Cancel quote, Clone, Delete** (and disabled Approve/Reject when not applicable **or** all secondary) into `mat-menu` “More”. Preserve ConfirmDialog delete **warn** + `canDelete`. **Do not edit** `httpErrorMessage` in this file | Changing lifecycle gates; hard delete; regressing H1 helper |
| List service | `quote.service.ts` `error.set` → copy H1 (`message [CODE]` when `errorCode` present). List banner unchanged structurally | Extracting shared helper |
| Files | `quotes-detail` template/menu + specs; `quote.service.ts` + spec | `quotes-create` / documents (Q2 done) |

### Track 3 — product-bundles banners (no chrome)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Helper | Widen `bundleHttpErrorMessage` to **any** `errorCode` (H1 shape), keep MAX/MIN tests | Shared-utils extract |
| Service | list / listByProductId / getById `error.set(bundleHttpErrorMessage(err, …))` | New bundle routes |
| Shell | **None** — do not edit `app.routes` / `test:cpq` / tsconfig |

### Track 4 — simulator + price-book create/list errors

| Topic | Decision | Rejected |
|-------|----------|----------|
| Simulator | HTTP `error` handler: H1-shaped local helper; empty-state **message** shows `message [CODE]`; keep existing empty-states | Redesigning form layout |
| Create dialog | Copy `bookHttpErrorMessage` from detail into create-dialog | List-row delete |
| List service | `PriceBookService` (data-access) `error.set` H1-shaped so list banners get codes | Editing Q1 DataTable |

### Track 5 — docs

Dual-read hold **J–S**. Note R2 still held. No `00-orchestrator-status.md`.

---

## 3. PR DAG

Disjoint: **CRM deals+orgs** ∥ **quotes** ∥ **product-bundles** ∥ **simulator + price-books/data-access**.

```text
S1 CRM empty-state          S2 quotes overflow            S3 bundles errorCode         S4 sim + PB create
──────────────────          ──────────────────            ──────────────────           ──────────────────
deal-list + org-list         header More menu              widen bundle helper          simulator HTTP helper
app-empty-state              quote.service error.set       service list/get banners     create-dialog + PB service
        │  no routes                 │  do not rewrite H1          │  no shell                  │  no list-row delete
        └────────────────────────────┴─────────────────────────────┴────────────────────────────┘
                                                       ▼
                                          S5 docs closeout (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **S1** | `fix(crm): deal and org lists use app-empty-state` | `deal-list`, `organization-list` (+specs); `docs/forge/impl/s1-fe-crm-empty-state.md` | **LOW** | Empty uses `app-empty-state` with Create CTA when unfiltered; loading `role="status"`; `nx test crm-deals` + `crm-organizations` (or Nx project names in `project.json`); no routes |
| **S2** | `fix(fe-cpq): quote detail More menu; list errorCode` | `quotes-detail` template/menu + specs; `quote.service.ts` + spec; `docs/forge/impl/s2-fe-quotes-overflow.md` | **MED** | ≤ visible primary actions + More; Expire/Cancel/Clone/Delete reachable; delete ConfirmDialog + ACCEPTED gate unchanged; H1 helper **byte-stable or equivalent tests still pass**; list `error.set` is `message [CODE]`; `nx test cpq-quotes` |
| **S3** | `fix(fe-cpq): product-bundle list/get errorCode` | `bundle-http-error.ts` + spec; `product-bundle.service.ts` + spec; `docs/forge/impl/s3-fe-bundles-errorcode.md` | **LOW** | Any `errorCode` appended; service banners use helper; no `app.routes`; `nx test cpq-product-bundles` |
| **S4** | `fix(fe-cpq): simulator and price-book create errorCode` | `simulator-page`; `price-book-create-dialog`; `price-book.service.ts` (data-access) (+specs); `docs/forge/impl/s4-fe-simulator-pricebook-error.md` | **LOW** | HTTP failures `message [CODE]`; create-dialog same; **no** list-row delete; `nx test cpq-pricing-simulator` + `cpq-price-books` + `cpq-pricing-data-access` |
| **S5** | `docs(cpq): Wave S forge closeout` | `docs/forge/impl/s5-wave-s-docs.md` | **LOW** | Dual-read J–S; R2 still held; **no** `00-orchestrator-status` |

**Day-1 max parallel:** **S1 ∥ S2 ∥ S3 ∥ S4** → **S5**.

| Pair | Rule |
|------|------|
| S1 vs others | Parallel (CRM vs CPQ) |
| S2 vs S3 | Parallel (quotes vs bundles) |
| S4 vs S2 | Parallel; S4 must **not** edit quotes |
| S4 vs list-row delete | **Forbidden** |
| Chrome / routes | **Nobody** edits `app.routes.ts`, `sidebar.component.ts`, `shell.component.ts`, tsconfig, `test:cpq` |

---

## 4. Blast radius

GitNexus **stale** — do not treat zeros as unused. **`rg` SSOT.**

| Change | `rg` | Risk |
|--------|------|------|
| S1 deal-list / org-list | List components only | **LOW** |
| S2 quotes-detail + QuoteService | Detail + list banner via `error()` | **MED** (lifecycle still same methods) |
| S3 ProductBundleService + helper | Bundles lib; HTTP controller **UNKNOWN** — no Java | **LOW** |
| S4 simulator + PriceBookService + create-dialog | Data-access list callers = price-book-list banner | **LOW** |
| Dual-read YAML | Flip = **HIGH** — not in wave | — |
| ConfirmDialog / EmptyState APIs | **Do not change** (Q1) | — |

**WARN:** Do not flip dual-read. Do not un-hold list-row delete. Disambiguate FE vs BE `QuoteService` / `delete` with `--file`. P1 Bundles **routes** untouched. Q4 chrome untouched.

---

## 5. Herdr layout (orchestrator spawns — architect does not)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `ws-fe-crm-empty` | `fix/forge-s1-fe-crm-empty-state` | S1 |
| 2 | `ws-fe-quotes-overflow` | `fix/forge-s2-fe-quotes-overflow` | S2 |
| 3 | `ws-fe-bundles-error` | `fix/forge-s3-fe-bundles-errorcode` | S3 |
| 4 | `ws-fe-sim-pricebook-error` | `fix/forge-s4-fe-simulator-pricebook-error` | S4 |
| 5 | `ws-docs` | `docs/forge-s5-wave-s-closeout` | S5 |

**Chrome owner:** **none.**  
**Model:** Grok 4.5, worktree-isolated. Overlay ≠ merge SSOT.  
**Vitest:** `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`  
**Java:** none. `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2` only if accidentally touched.

---

## 6. Out of scope

- Activities / `/api/v1/activities/**`  
- CI #238; Kafka #241  
- Product validate deletion; gateway domain validation  
- Dual-read YAML flip  
- Full SpEL; Kafka S2B-9  
- WhatsApp #430 undo  
- NgRx; subscriptions FE; Accept ADR-006  
- N′′; **price-book list-row delete**  
- New portal; `app.routes` / sidebar / shell / tsconfig / `test:cpq`  
- Q1 primitive API changes  
- Shared `httpErrorMessage` extract  

---

## 7. Goal text

```text
Implement Open CRM/CPQ Wave S UX per docs/forge/31-wave-s-ux-architecture.md in four parallel FE tracks: (1) deal-list and organization-list replace custom empty-state divs with app-empty-state (lead/contact pattern) and loading role=status; no routes; nx test crm-deals and crm-organizations; (2) quotes-detail collapse Expire/Cancel/Clone/Delete into More mat-menu; keep Back and status-gated primaries; do not change quotes-detail httpErrorMessage (H1); quote.service error.set copy H1 message [CODE]; nx test cpq-quotes; (3) widen bundleHttpErrorMessage to any errorCode like H1; product-bundle.service list/get error.set uses it; no app.routes; nx test cpq-product-bundles; (4) simulator HTTP error H1-shaped; price-book-create-dialog and PriceBookService list errors H1-shaped; no list-row delete; nx test cpq-pricing-simulator, cpq-price-books, cpq-pricing-data-access. Day-1 S1∥S2∥S3∥S4; S5 docs after. No Java. Do not flip dual-read, invent Activities, start #238/#241, reopen N, un-hold list-row delete, NgRx, subscriptions FE, or edit 00-orchestrator-status.md. Evidence: docs/forge/impl/s*.md done, vitest green.
```

---

## 8. Checklist

- [x] Rebased/even with `origin/main` @ `857e724`  
- [x] Seeds verified (price-books helper **exists** on detail; bundles helper **exists** but narrow; skip-link **already** Q4)  
- [x] N complete; no N′′; list-row delete **held**; tracks named **S1–S5** only  
- [x] Dual-read still true  
- [x] Zero new portal; **no** chrome owner  
- [x] GitNexus stale — analyze skipped; `rg` used  
- [x] Architect **does not spawn** implementers  
- [ ] Orchestrator → Wave S FORGING (`00-*`)  
- [ ] S1–S5 merged  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). Do **not** un-hold price-book list-row delete. Do **not** rewrite H1 `quotes-detail` helper.

**WAVE S — APPROVED FOR FORGE.**
