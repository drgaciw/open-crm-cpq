# Wave M architecture — quote header edit ∥ price-book entry CRUD ∥ clone copies line items

**Status:** COMPLETE on main (M1–M3 merged; M4 closeout) — PM authorized 2026-08-30 (architect pick after Wave L COMPLETE; Herdr Grok 4.5 implementers; merge gate M3 before M1)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** Wave L COMPLETE on `main` @ `a00c4b0` (L1–L3 merged; L4 closeout)  
**Survey tip:** `origin/main` @ `a00c4b0`  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (re-analyzed 2026-08-30 ~20:13; **indexed commit `a00c4b0` matches HEAD**; CLI may still flag stale because of untracked `.worktrees/` — treat HEAD symbols as current); GitHub `drgaciw/open-crm-cpq` open issues/PRs; tree `rg`; Wave L leftover list; orchestrator review

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable |
| **CI runners (#238)** | **HOLD** | Infra/billing — only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Stack B2 / S2A-2 — out of forge wave |
| **Delete product-service validate** | **HOLD** | Dual-run parity not calendar-ready |
| **Gateway domain validation** | **REJECT** | ADR + forge DO NOT TOUCH — route+adapt only |
| **Flip `approvals.legacy-local-read-enabled` → false** | **HOLD** | Still **HIGH** cutover; J5 checklist only; default remains `${APPROVALS_LEGACY_LOCAL_READ:true}` |
| **Full SpEL / Drools / OpenL** | **REJECT** | Over-scope; H1/J2 dialect enough |
| **S2B-9 Kafka / event invalidation** | **HOLD** | Event bus not ready |
| **Bolt #430 WhatsApp** | **OUT** | Already on `main` — do not reopen/undo |
| **NgRx CPQ global store** | **HOLD** | Signals / service state sufficient |
| **Contract-asset / subscriptions portal FE** | **DEFER** | Verified: `SubscriptionController` exists + gateway `/api/v1/subscriptions/**`, but **leaks `Subscription` entity** (no DTO/MapStruct), **no list-all/pagination** (only `/{id}`, `/customer/{id}`, `/status/{status}`), `activate` syncs ERP, Kafka `QuoteAcceptedEventConsumer` is the write path. Needs DTO+list **before** FE. Not LOW–MED Day-1 |
| **Quote soft-delete UI** | **OUT** | `QuoteService.delete` HTTP exists; not an L leftover; ACCEPTED-gated data-loss UX — later |
| **Price-book header update/delete** | **STRETCH** | HTTP exists; L leftover was **entries**; M2 may add book edit as non-blocker |
| **Pricing rule-set admin FE** | **DEFER** | `RuleSetService` HTTP ready; new lib + shell — larger than leftovers |
| **✅ Track 1: Quote header `update` form** | **IN** | L leftover: FE `QuoteService.update` + `QuoteUpdateRequest` exist; `quotes-detail` is read-only header; BE `PUT /{id}` DRAFT\|REJECTED |
| **✅ Track 2: Price-book entry create/update/delete** | **IN** | L leftover: `PriceBookService.createEntry`/`updateEntry`/`deleteEntry` exist; L3 list/detail/create **book** only — entries **read** |
| **✅ Track 3: BE clone copies line items** | **IN** | L leftover: `QuoteService.clone` copies header only; L1 UX honestly says lines are not copied |

**PM authorization used:** Architect picks largest safe forge from GitNexus+GitHub after Wave L COMPLETE. Prefer disjoint parallel tracks; overall risk LOW–MED.

**Chosen theme:** **CPQ portal write-back** — wire quote header edit and price-book entry CRUD against existing HTTP, and make clone persist line items so the L1 honesty copy can tell the truth. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read flip / WhatsApp / SpEL / Kafka S2B-9 / contract-asset FE / NgRx.

**Orchestrator rule:** Wave L COMPLETE is the forge gate. This file is the authorized Wave M plan. Do **not** update `docs/forge/00-orchestrator-status.md` from this PR (orchestrator owns that).

---

## 1. Problem / evidence

### GitNexus staleness WARN

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree (architect): /home/username01/Projects/open-crm-cpq/.worktrees/architect
Indexed commit: a00c4b0
Current commit: a00c4b0
Re-analyzed: 2026-08-30 ~20:13 (analyze --index-only)
```

HEAD symbols are current. CLI `status` may still print stale because of untracked `.worktrees/`. Treat **UNKNOWN** as unresolved; confirm with `rg`. Disambiguate with `--file` / `--uid` (`QuoteService` FE vs BE; `update` / `clone` / `cancel` across quote vs configuration vs subscription). Re-run impact in each worktree before symbol edits.

### GitHub inventory (`drgaciw/open-crm-cpq`)

| Kind | IDs | Wave M |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** | — |
| #430 WhatsApp | on `main` | **OUT** (do not reopen/undo) |

### M-T1 — Quote header cannot be edited (MED product / LOW–MED UX)

| Surface | State @ `a00c4b0` |
|---------|-------------------|
| BE `QuoteController.update` | `PUT /api/v1/quotes/{id}` — DRAFT\|REJECTED (`EDITABLE_STATUSES`); MapStruct `updateEntity` **IGNORE** nulls |
| BE `QuoteUpdateRequest` | name, description, dealExternalId, subscriptionType, currency, region, durationInMonths, startDate, endDate, notes — **not** customerExternalId / dealType / status / totals |
| FE `QuoteService.update` | `http.put` exists — **zero component callers** (`rg`); **no** `quote.service.spec` coverage |
| FE `quotes-detail` | Header is an info-grid; `canEditLines` already DRAFT\|REJECTED; `editForm` is **line-item** edit, not header |
| FE `quotes-create` | Pattern for `QuoteCreateRequest` — reuse field set minus customer (immutable on update) |

```text
rg 'quoteService.update|QuoteService.update' frontend — only quote.service.ts definition
quotes-detail editForm → updateLineItem only
```

**GitNexus** (`-r /home/username01/Projects/open-crm-cpq --file <path>`, index **HEAD `a00c4b0`** — **UNKNOWN ≠ unused**):

| Symbol | Risk | Note |
|--------|------|------|
| BE `QuoteService.update` | **LOW** (→ controller) | Epistemic **lower-bound**: 2 unresolved `update` receivers dropped — `rg` confirms Java caller is `QuoteController` only |
| FE `QuoteService.update` | **UNKNOWN** (0 upstream) + same 2-receiver boundary | `rg`: unused; M1 is the first UI caller |
| `QuotesDetailComponent` | **LOW** (4) | routes / barrel — confirm `rg`; Wave L callers not in stale index |

### M-T2 — Price-book entries are read-only (MED product / LOW–MED UX)

| Surface | State |
|---------|-------|
| BE `PriceBookController` | `POST/PUT/DELETE .../{id}/entries[/{entryId}]` |
| BE `PriceBookService.createEntry`/`updateEntry`/`deleteEntry` | Soft-delete; duplicate product → `DUPLICATE_PRICE_BOOK_ENTRY`; **published books immutable** (`ensureNotPublished`: `effectiveDate != null && !effectiveDate.isAfter(today)` → `PRICE_BOOK_PUBLISHED_IMMUTABLE`) |
| FE `PriceBookService` | `createEntry` / `updateEntry` / `deleteEntry` + models exist; Vitest in `price-book.service.spec.ts` |
| FE `cpq-price-books` | L3 list/detail/create **book**; detail entries DataTable **read-only**; **zero** `createEntry`/`updateEntry`/`deleteEntry` in `libs/cpq/price-books` |
| Shared-ui `DataTable` | `rowAction` output exists but **no** actions column type — **do not** expand shared-ui in M2 |

**GitNexus:**

| Symbol | Risk | Note |
|--------|------|------|
| `PriceBookService.createEntry` | **LOW** (→ controller) | FE-only consumption |
| `PriceBookService.updateEntry` | **LOW** (→ controller) | FE-only |
| `SubscriptionController` (deferred track) | **UNKNOWN** (0 + riskNote) | HTTP entry; FE absent — **not in wave** |

Prefix remains **`/api/v1/price-books/**` only** — never `/api/v1/pricing/price-books`.

### M-T3 — Clone drops line items (MED product / LOW BE blast)

```java
// QuoteService.clone — header builder only; no QuoteLineItem copy; returns quoteMapper.toDto(saved)
```

| Surface | State |
|---------|-------|
| BE `clone` | New DRAFT, new `quoteNumber`, name + `" (Copy)"`; **no** lines; totals stay builder defaults |
| FE L1 | ConfirmDialog + `.clone-note`: *"Line items are not copied"* — tests assert that copy (`quotes-detail.component.spec.ts`) |
| `Quote.lineItems` | `@OneToMany` cascade ALL + `@SQLRestriction("is_deleted = false")` |
| Totals helpers | `QuoteLineItemService.recalculateQuoteTotals` sums `extendedAmount` / `discountAmount` (live path). `Quote.recalculateTotals()` is a **different** 10% tax stub — **do not call** |

**GitNexus (fresh index):** BE `QuoteService.clone` **LOW exact** (→ `QuoteController.cloneQuote`, process `CloneQuote`). FE `cloneQuote` — confirm with `rg` (`quotes-detail` is the UI caller from Wave L).

No `QuoteServiceCloneTest` today (tests exist for calculate + submit preflight only).

### Contract-asset leftover (verified DEFER)

`SubscriptionController` `@RequestMapping("/api/v1/subscriptions")` returns `ApiResponse<Subscription>` **entity**. `activate` calls `ErpAdapter.syncToErp`. Gateway already routes `/api/v1/subscriptions/**`. FE `rg` has **no** subscription portal lib. Entity-at-boundary violates forge non-negotiable; list-all missing. **Wave N+** after DTO+MapStruct+paginated list.

---

## 2. Decisions + rejected alternatives

### Track 1 — Quote header edit (single quotes-detail PR)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | **One PR** owns `quotes-detail` (+ spec) + header edit child + clone **copy** update (same-file conflict with M3 FE) | Parallel header vs clone-copy panes |
| Child shape | Prefer `quote-header-edit` under `libs/cpq/quotes` (dialog **or** inline panel) matching `QuoteUpdateRequest`; Edit visible when `canEditLines` (DRAFT\|REJECTED) | Separate Nx lib; NgRx |
| API | Existing `QuoteService.update` → `PUT /api/v1/quotes/{id}` | New endpoint; PATCH |
| Fields | `QuoteUpdateRequest` only. **Do not** send customerExternalId / dealType / status / money fields (mapper ignores) | Full create-form clone including customer |
| After save | Refresh `getById`; error banner on `QUOTE_NOT_EDITABLE` | Optimistic header-only patch without reload |
| Clone UX | **M1 owns strings/tests** in `quotes-detail`: clone copies **header + line items**; waterfall/ARR not copied — run Calculate. Assumes M3 semantics | Leave L1 "not copied" lie; M3 editing the same template |
| Quote `delete` UI | **Out** of M1 | Soft-delete ConfirmDialog Day-1 |

### Track 2 — Price-book entry CRUD

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | `libs/cpq/price-books` only (detail + dialog(s) + specs). **Do not** edit shell / `app.routes` / sidebar (already L3) | New lib; shared-ui DataTable rewrite |
| Day-1 | Create + update + delete **entries** via existing `PriceBookService`; ConfirmDialog on delete; reload `getEntries` | Book update/delete as **blocker** |
| Published | Disable Add/Edit/Delete when `effectiveDate` is present and `<= today` (mirror `ensureNotPublished`). Surface `PRICE_BOOK_PUBLISHED_IMMUTABLE` / `DUPLICATE_PRICE_BOOK_ENTRY` in banner | Allow writes and rely on 400 only |
| Dialog | Child `price-book-entry-dialog` mirroring `price-book-create-dialog`; fields: productExternalId, listPrice, optional floorPrice, optional currency | Inline table editing; catalog product picker (no product search required) |
| API | `/api/v1/price-books/{id}/entries/**` only | `/api/v1/pricing/price-books` |
| Stretch (non-blocker) | Book metadata update / soft-delete on same lib | Required for M2 merge |

### Track 3 — BE clone copies line items

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | `quote-service` Java + unit test **only**. No FE files (M1 owns clone copy) | FE in this PR (merge fight on `quotes-detail`) |
| Copy set | Non-deleted lines via `findByQuoteIdAndDeletedFalseOrderBySortOrderAsc`. New entities (no id/uuid/audit). Copy commercial snapshot: product\* , quantity, UoM, list/unit/net/extended, discount rate/amount, billingFrequency, sortOrder, notes, metadata | Call `addLineItem` in a loop (drops discounts; resets unitPrice from listPrice) |
| Skip | `pricingExplanationCode`, `pricingWaterfallJson` on lines; header waterfall / ARR / TCV / ACV / acceptedAt / approval\* (already omitted). Cloned quote stays **DRAFT** with new number | Copy priced waterfall as if it were a new quote |
| Totals | After persist, roll up **the same way as** `QuoteLineItemService.recalculateQuoteTotals` (`extendedAmount` sum, `discountAmount` sum, `totalAmount = subtotal - totalDiscount`). **Do not** call `Quote.recalculateTotals()` (10% tax stub) | Invoke constitutional tax helper |
| DTO | `toDto` must include copied `lineItems` (attach to collection or reload before map) | Return header-only DTO |
| Tests | New `QuoteServiceCloneTest` (Mockito like submit/calculate tests): lines copied with new ids; empty source → empty clone; deleted lines omitted | Controller-only test |
| Events | **No** Kafka/Redis on clone | Publish QuoteAccepted or clone topic |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **FE quotes-detail** ∥ **FE price-books lib** ∥ **BE quote-service clone**.

```text
Track 1 (FE quotes)                 Track 2 (FE price-books)           Track 3 (BE quote)
─────────────────────               ──────────────────────             ─────────────────
M1 header edit + clone copy           M2 entry create/update/delete      M3 QuoteService.clone
   (quotes-detail owner)                 (price-books lib only)             copies line items
        │                                    │                                 │
        └────────────────────────────────────┴─────────────────────────────────┘
                                               ▼
                                M4 docs/matrix forge closeout + orchestrator COMPLETE
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **M1** | `feat(fe-cpq): quote header update form + clone copy` | `quote-header-edit` (+spec); `quotes-detail` (+spec); `quote.service.spec` PUT coverage; `docs/forge/impl/m1-fe-quote-header-edit.md` | **MED** | Edit+Save on DRAFT\|REJECTED via `QuoteService.update`; hidden/disabled otherwise; `QUOTE_NOT_EDITABLE` banner; clone dialog/note say lines **are** copied and Calculate is still required; Vitest green; no BE Java; no shell |
| **M2** | `feat(fe-cpq): price-book entry create/update/delete` | `price-book-detail` (+spec); `price-book-entry-dialog` (+spec); `docs/forge/impl/m2-fe-price-book-entries.md` | **MED** | Create/update/delete entries on unpublished books; published books read-only; errors for duplicate product / published immutable; prefix `/api/v1/price-books`; no shell; no quotes |
| **M3** | `feat(quote): clone copies line items` | `QuoteService.clone`; `QuoteServiceCloneTest`; `docs/forge/impl/m3-be-quote-clone-lines.md` | **MED** (semantics) / GitNexus **LOW** | Clone DRAFT has N lines matching source commercial fields; new ids; totals rolled up; waterfall not copied; empty/deleted-line cases; unit tests green; **no FE** |
| **M4** | `docs(cpq): Wave M forge closeout` | Matrix notes (quote PUT FE, entry writes, clone lines); holds listed; `docs/forge/impl/m4-wave-m-docs.md` | **LOW** | Docs match runtime; **no** orchestrator status edit |

### Parallelism rules

**Day-1 max safe parallel (3 panes):** **M1 ∥ M2 ∥ M3**

**Orchestrator merge gate:** land **M3 before or atomically with M1** so the clone copy is never a lie on `main`. Parallel implementation is allowed; do not merge M1 alone while clone still omits lines.

| Pair | Conflict? | Rule |
|------|-----------|------|
| M1 vs M2 | quotes vs price-books | Parallel |
| M1 vs M3 | M1 owns all `quotes-detail` / clone **copy**; M3 **must not** edit FE | Parallel |
| M2 vs M3 | FE lib vs quote-service Java | Parallel |
| M1 clone copy vs M3 | Semantic coupling, **not** a file fight | Both required before Wave M COMPLETE; M1 copy describes M3 behavior |
| M4 | Docs | After Day-1 notes |

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, reopens WhatsApp #430, starts #238/#241, builds contract-asset/subscriptions FE, or introduces NgRx.

---

## 4. Blast radius / GitNexus

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| M1 FE header form | First UI → existing PUT | FE `update` **UNKNOWN**; BE `update` **LOW** + 2 unresolved — `rg` controller only | **MED** UX |
| M1 clone copy | `quotes-detail` tests | FE `cloneQuote` **UNKNOWN** (stale; L1 caller exists) | **LOW** copy-only |
| M2 entry writes | New UI → existing pricebook HTTP | `createEntry`/`updateEntry` **LOW** | **MED** (published + duplicate gates) |
| M3 `QuoteService.clone` | Controller + FE clone button (no FE edit in M3) | **LOW** (1 caller) | **MED** product semantics |
| Dual-read default | — | Flip = **HIGH** — **not in wave** | — |
| `SubscriptionController` | HTTP / gateway | **UNKNOWN** — **not in wave** | — |

**Explicit HIGH / WARN callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default (**HIGH**). Still `${APPROVALS_LEGACY_LOCAL_READ:true}` in `quote-service` `application.yml`.  
2. **Index matches HEAD `a00c4b0`** after re-analyze — still **UNKNOWN ≠ unused**; confirm with `rg`. CLI stale flag from untracked `.worktrees/` is not a dirty graph.  
3. Disambiguate `QuoteService` (FE vs BE) and `update` / `clone` / `cancel` with `--file` / `--uid`.  
4. BE `update` GitNexus **lower-bound** (2 dropped receivers) — confirmed via `rg`, not an all-clear from the zero FE callers.  
5. Do **not** call `Quote.recalculateTotals()` from clone (10% tax stub ≠ live line-item rollup).  
6. Contract-asset controller **UNKNOWN** + entity leak — do not ship FE against it this wave.  
7. #430 WhatsApp already on main — do not undo.

---

## 5. Herdr forge layout (Grok 4.5 panes as IMPLEMENTERS)

Orchestrator will prompt **Herdr** windows; do **not** dual-write with extra subagents on the same trees.

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wm-fe-quote-header-edit` | `feat/forge-m1-fe-quote-header-edit` | M1 |
| 2 | `wm-fe-price-book-entries` | `feat/forge-m2-fe-price-book-entries` | M2 |
| 3 | `wm-be-quote-clone-lines` | `feat/forge-m3-be-quote-clone-lines` | M3 |
| 4 (closeout) | `wm-docs` | `docs/forge-m4-wave-m-closeout` | M4 |

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
- Contract-asset / subscriptions portal FE (DTO + list + ERP-safe API is a **prerequisite**, not this wave)  
- NgRx CPQ global store  
- Quote soft-delete UI  
- Pricing rule-set / rule admin portal; expanding `pricing-ui` waterfall stub  
- Shared-ui `DataTable` actions-column redesign  
- FULL_PREFLIGHT / live ERP adapters  
- Calling document-generation or contract-asset microservices from the browser  

---

## 7. Goal text (paste into `/goal` when forging — only after Wave L COMPLETE)

```text
Implement Open CRM/CPQ Wave M per docs/forge/22-wave-m-architecture.md in three parallel tracks: (1) FE quotes — quote-header-edit on quotes-detail for DRAFT|REJECTED using existing QuoteService.update PUT /api/v1/quotes/{id} and QuoteUpdateRequest (do not send customerExternalId/dealType/status/totals); reload getById; error banner; update clone ConfirmDialog + clone-note to say header and line items are copied and Calculate is still required for waterfall; add quote.service.spec PUT coverage; no BE Java; no shell; (2) FE price-books — entry create/update/delete via PriceBookService on /api/v1/price-books/{id}/entries; disable writes when published (effectiveDate <= today); ConfirmDialog delete; banners for PRICE_BOOK_PUBLISHED_IMMUTABLE and DUPLICATE_PRICE_BOOK_ENTRY; do not use /api/v1/pricing/price-books; no shell; (3) BE quote-service — QuoteService.clone copies non-deleted line items (new ids, commercial snapshot, skip pricingExplanationCode/waterfall); roll up totals like QuoteLineItemService not Quote.recalculateTotals; include lines on DTO; QuoteServiceCloneTest; no FE. Day-1 parallelize M1∥M2∥M3; M4 docs closeout after. Use GitNexus impact before symbol edits; index matches HEAD a00c4b0 (re-analyzed); warn HIGH/CRITICAL/UNKNOWN; disambiguate QuoteService/update/clone by file_path/uid. Do not invent Activities API, start #238/#241, flip dual-read default, reopen WhatsApp #430, embed validation in gateway, full SpEL, Kafka S2B-9, contract-asset FE, or NgRx. Evidence: docs/forge/impl/m*.md done, header update green, entry CRUD green, clone copies lines green.
```

---

## 8. Architect sign-off checklist

- [x] Wave L COMPLETE on forging tip (`a00c4b0`; L1–L4)  
- [x] GitHub: open issues **only #238, #241** (held); open PRs **none**; #430 **on main** (do not undo)  
- [x] Evidence: quote PUT HTTP-only; price-book entries HTTP-only (L3 read); BE clone omits lines; subscription controller entity leak + no list-all  
- [x] GitNexus: BE `update` **LOW lower-bound** (2 dropped receivers; `rg` controller only); BE `clone` **LOW exact**; `createEntry`/`updateEntry` **LOW**; FE `update`/`cloneQuote` confirm with `rg`; `SubscriptionController` **UNKNOWN** — **warned**; index **HEAD `a00c4b0`**  
- [x] Dual-read default flip **held**; product validate delete **held**; gateway domain rules **rejected**; contract-asset FE **deferred** (verified leftover)  
- [x] PR DAG max parallelism with disjoint files documented (**3** Day-1 panes: M1∥M2∥M3 → M4)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp, full SpEL, Kafka S2B-9, dual-read flip, contract-asset FE, NgRx  
- [x] Overall wave risk **LOW–MED** (M1/M2/M3 MED product; no HIGH in-scope work)  
- [x] Herdr forge layout with Grok 4.5 implementer panes documented  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [x] Orchestrator → Wave M FORGING (orchestrator owns `00-orchestrator-status.md`)  
- [x] Day-1 + closeout merged; M4 COMPLETE  

**PM WARNING:** Impact before Java/FE symbol edits (index **HEAD `a00c4b0`**). Do **not** flip approval legacy dual-read default (**HIGH**). M3 must **not** edit `quotes-detail`. M1 must **not** claim clone copies waterfall. Merge **M3 before or with M1**. M2 must use `/api/v1/price-books/**` only and honor published immutability. Do **not** return `Subscription` entities from a new FE client this wave.

**WAVE M — APPROVED FOR FORGE.**
