# Wave N architecture — price-book header write-back ∥ pricing-customers portal ∥ quote soft-delete

**Status:** COMPLETE on main (N1–N3 merged; N4 closeout) — PM authorized 2026-08-31 (architect pick after Wave M COMPLETE; Herdr Grok 4.5 implementers; N2 owned shell)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-31  
**Baseline:** Wave M COMPLETE on `main` @ `e09a4a3` (M1–M3 merged; M4 closeout; merge gate M3 before M1)  
**Survey tip:** `origin/main` @ `e09a4a3`  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (primary **re-analyzed 2026-08-31**; indexed commit **`e09a4a3` MATCHES HEAD `e09a4a3`** — HEAD symbols current. CLI may still flag stale because of untracked `.worktrees/` — **false stale**; **UNKNOWN ≠ unused**; confirm with `rg`); GitHub `drgaciw/open-crm-cpq`; tree `rg`; Wave M leftovers + HTTP-ready gaps

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable |
| **CI runners (#238)** | **HOLD** | Infra/billing — only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Stack B2 / S2A-2 — out of forge wave |
| **Delete product-service validate** | **HOLD** | Dual-run parity not calendar-ready |
| **Gateway domain validation** | **REJECT** | ADR + forge DO NOT TOUCH — route+adapt only |
| **Flip `approvals.legacy-local-read-enabled` → false** | **HOLD** | Still **HIGH**; default remains `${APPROVALS_LEGACY_LOCAL_READ:true}` |
| **Full SpEL / Drools / OpenL** | **REJECT** | Over-scope; H1/J2 dialect enough |
| **S2B-9 Kafka / event invalidation** | **HOLD** | Event bus not ready |
| **Bolt #430 WhatsApp** | **OUT** | Already on `main` — do not reopen/undo |
| **NgRx CPQ global store** | **HOLD** | Signals / service state sufficient |
| **Contract-asset / subscriptions FE** | **HOLD** | Controller still returns `ApiResponse<Subscription>` **entity**; no DTO; no list-all; `activate` hits ERP. DTO+list is a prerequisite — **not** a Day-1 LOW–MED disjoint FE track |
| **Contract-asset BE DTO+list** | **DEFER** | Would be Java + breaking JSON shape + still need paginated list + ERP-safe activate. Not needed for the three FE tracks below |
| **Rule-set + rules admin portal** | **DEFER** | HTTP ready (`RuleSetService` / `RuleService`); **shell conflict** with pricing-customers; activate/deprecate + DRL validate/impact is a larger dialect surface than customers |
| **Product bundles portal** | **DEFER** | BE `ProductBundleController` + DTO at `/api/v1/product-bundles`; GitNexus **UNKNOWN** (HTTP); **zero** FE models/clients — bigger than (2) |
| **✅ Track 1: Price-book HEADER update/delete** | **IN** | M leftover: FE `PriceBookService.update`/`delete` exist; M2 wired **entry** CRUD only; detail header is Back-only; published books immutable (`ensureNotPublished`). **No shell** |
| **✅ Track 2: Pricing-customers portal** | **IN** | HTTP-ready gap: `PricingCustomerService` + models on `/api/v1/pricing-customers`; GitNexus FE class **LOW** (barrel); **zero** UI/routes. **Owns shell** (the one new portal this wave) |
| **✅ Track 3: Quote soft-delete UI** | **IN** | FE `QuoteService.delete` exists; detail has no Delete; BE blocks ACCEPTED (`QUOTE_ACCEPTED_NO_DELETE`). **No shell** |

**PM authorization used:** Architect picks largest safe forge from GitNexus+GitHub after Wave M COMPLETE. Prefer disjoint parallel tracks; overall risk LOW–MED. At most **one** new portal owns `app.routes` + sidebar.

**Chosen theme:** **CPQ portal admin cutover** — finish price-book header write-back on the existing lib, open a pricing-customers browse/CRUD surface on ready data-access, and enable quote soft-delete against existing HTTP. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read flip / WhatsApp / SpEL / Kafka S2B-9 / contract-asset FE / NgRx / rule-set admin / bundles.

**Orchestrator rule:** Wave M COMPLETE is the forge gate. This file is the authorized Wave N plan. Do **not** update `docs/forge/00-orchestrator-status.md` from this PR (orchestrator owns that).

---

## 1. Problem / evidence

### GitNexus freshness (primary re-analyze)

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree (architect): /home/username01/Projects/open-crm-cpq/.worktrees/architect  (docs/forge-architect, merge-base e09a4a3)
Re-analyzed: 2026-08-31
Indexed commit: e09a4a3
HEAD (primary): e09a4a3
Status: MATCHES HEAD — symbols current
CLI caveat: gitnexus status may still report stale due to untracked .worktrees/ — false stale
```

Treat **UNKNOWN** as unresolved; confirm with `rg`. Disambiguate with `--file` / `--uid` (`QuoteService` FE vs BE; `PriceBookService` FE vs BE; `delete` / `update` across many services; `RuleSetService` FE vs BE).

### GitHub inventory (`drgaciw/open-crm-cpq`)

| Kind | IDs | Wave N |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** | — |
| #430 WhatsApp | on `main` | **OUT** (do not reopen/undo) |

### Shell / security snapshot @ `e09a4a3`

| Surface | State |
|---------|-------|
| Routes | dashboard, organizations, contacts, leads, deals, catalog, quotes, approvals, price-books, `pricing/simulator` — **no** rules, pricing-customers, bundles, subscriptions |
| Sidebar | Matches routes above |
| `security.open` | **empty** (`rg` zero) |
| Dual-read | `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |

### N-T1 — Price-book header cannot be edited or deleted (MED product / LOW–MED UX)

| Surface | State @ `e09a4a3` |
|---------|-------------------|
| BE `PUT/DELETE /api/v1/price-books/{id}` | `ensureNotPublished`: `effectiveDate != null && !effectiveDate.isAfter(today)` → `PRICE_BOOK_PUBLISHED_IMMUTABLE`; region uniqueness; date range |
| BE `PriceBookUpdateRequest` | name, region, currency, isDefault, effectiveDate, expirationDate |
| FE `PriceBookService.update`/`delete` | HTTP exists; Vitest on service spec |
| FE `price-book-detail` | M2: **entry** Add/Edit/Delete + `isPriceBookPublished` / `canEditEntries`. Header actions: **Back only** — no book Edit/Save/Delete |
| `rg` | `priceBookService.update(` / `.delete(` — **zero** UI callers |

**GitNexus** (`-r /home/username01/Projects/open-crm-cpq --file <path>`, HEAD **`e09a4a3` current**):

| Symbol | Risk | Note |
|--------|------|------|
| FE `PriceBookService.update` | **UNKNOWN** (0) + **lower-bound** 2 dropped receivers | `rg`: no UI callers — N1 is first |
| FE `PriceBookService.delete` | **UNKNOWN** (0) + **lower-bound** 5 dropped | `rg`: no UI callers |
| BE `PriceBookService.update` | **LOW** (→ controller) + 2 dropped | `rg`: `PriceBookController` |

### N-T2 — Pricing customers HTTP ready; portal UI missing (MED product / LOW–MED UX)

| Surface | State |
|---------|-------|
| BE `CustomerController` | `/api/v1/pricing-customers` list (optional `segment`), get, create, update, soft-delete |
| Gateway | Path `/api/v1/pricing-customers/**` on pricing-service (asserted in `CpqGatewayRoutesTest`) |
| FE `PricingCustomerService` | list / listBySegment / getById / create / update / delete + signals |
| Models | `PricingCustomerDto` / Create / Update in `shared/models` |
| UI / routes / sidebar | **zero** |
| Not CRM | Distinct from `/api/v1` CRM contacts/orgs — label **Pricing customers** |

**GitNexus:** FE `PricingCustomerService` class **LOW exact** (barrel / data-access graph). New UI is additive.

### N-T3 — Quote cannot be deleted from the portal (LOW–MED UX)

| Surface | State |
|---------|-------|
| BE `QuoteService.delete` | Soft-delete; **ACCEPTED** → `QUOTE_ACCEPTED_NO_DELETE` |
| FE `QuoteService.delete` | `http.delete` exists — **no** `quote.service.spec` delete case; **zero** component callers |
| FE `quotes-detail` | Header edit (M1) + lifecycle + lines + documents; **no Delete** |

**GitNexus:**

| Symbol | Risk | Note |
|--------|------|------|
| FE `QuoteService.delete` | **UNKNOWN** (0) + **lower-bound** 5 dropped | `rg`: service definition only |
| BE `QuoteService.delete` | **LOW** (→ `QuoteController`) + 5 dropped | Module label `Bundle` is graph noise — `rg` controller only |

Pattern: Wave L2 catalog delete (`ConfirmDialog` → soft-delete HTTP → list).

### Deferred leftovers (verified, not IN)

| Candidate | Evidence |
|-----------|----------|
| Rule-sets | FE `RuleSetService` **LOW** (barrel); `activate`/`deprecate`; `RuleService` validate/impact; **zero UI**; would also own shell |
| Bundles | `ProductBundleController` **UNKNOWN** (HTTP); FE `rg` empty |
| Subscriptions | Still `ApiResponse<Subscription>` entity; no list-all; ERP on activate |

---

## 2. Decisions + rejected alternatives

### Track 1 — Price-book header update/delete (existing lib, no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | `libs/cpq/price-books` only — `price-book-detail` (+ spec) + header edit dialog; **do not** edit `app.routes` / sidebar / quotes | New lib; shared-ui rewrite |
| Update | Dialog or inline form matching `PriceBookUpdateRequest`; enabled when **not** `isPriceBookPublished` (reuse helper). Reload `getById` after save | Edit published books; PATCH |
| Delete | ConfirmDialog → `PriceBookService.delete` → navigate `/price-books`; disabled when published; banner `PRICE_BOOK_PUBLISHED_IMMUTABLE` | List-page bulk delete; hard delete |
| Entries | **Do not** regress M2 entry CRUD | Rewrite entry table |
| Stretch | List-row delete (non-blocker) | Required for merge |

### Track 2 — Pricing-customers portal (owns shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Home | New Nx lib `frontend/libs/cpq/pricing-customers` (`@frontend/cpq-pricing-customers`, project `cpq-pricing-customers`) mirroring price-books/approvals | Stuff into `pricing-ui` stub; nest under simulator; NgRx |
| Day-1 | List (optional segment filter) + detail + create dialog + update + ConfirmDialog delete via existing `PricingCustomerService` | Read-only list only (HTTP already has writes) |
| Shell | Route `pricing-customers` + sidebar **Pricing customers**; N2 **owns** `app.routes.ts`, `sidebar.component.ts`, `tsconfig.base.json` path, `frontend/package.json` `test:cpq` | N1/N3 editing shell; path `/customers` (CRM collision) |
| API | `/api/v1/pricing-customers/**` only (pricing-service) | `/api/v1/customers`; CRM contact APIs |
| Out | Rule-set admin, simulator rewrite, live ERP | Same PR |

### Track 3 — Quote soft-delete (quotes-detail, no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| UX | Delete on `quotes-detail` via ConfirmDialog (warn) → `QuoteService.delete` → `/quotes` | Silent delete; hard delete |
| Gate | Disabled when status === `ACCEPTED` (match BE). Other statuses including CANCELLED/EXPIRED/DRAFT allowed | Also block SENT/APPROVED beyond BE |
| Errors | Banner `QUOTE_ACCEPTED_NO_DELETE`; stay on detail | Toast-only |
| Scope | `quotes-detail` (+ spec) + `quote.service.spec` DELETE coverage. **No** BE Java. **No** shell. Do not rewrite M1 header edit / clone copy | Bundle with header-edit rewrite |
| Spec | Confirm → DELETE → navigate; dismiss skips; ACCEPTED has no enabled Delete | — |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **FE price-books lib** ∥ **FE pricing-customers lib + shell** ∥ **FE quotes-detail**.

```text
Track 1 (FE price-books)            Track 2 (FE pricing-customers)      Track 3 (FE quotes)
────────────────────────            ────────────────────────────        ─────────────────
N1 book header update/delete          N2 new lib + list/detail/CRUD       N3 quote Delete
   (no shell)                            + route + sidebar                  (no shell)
        │                                    │                                 │
        │  Day-1 CODE parallel (3 panes)     │                                 │
        │         MERGE: no serial gate      │  N2 owns shell files            │
        └────────────────────────────────────┴─────────────────────────────────┘
                                               ▼
                                N4 docs/matrix forge closeout + orchestrator COMPLETE
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **N1** | `feat(fe-cpq): price-book header update/delete` | `price-book-detail` (+spec); `price-book-edit-dialog` (+spec); `docs/forge/impl/n1-fe-price-book-header.md` | **MED** | Unpublished: Edit+Save via `PriceBookService.update`; Delete ConfirmDialog → list; published: writes disabled + `PRICE_BOOK_PUBLISHED_IMMUTABLE` banner; entry CRUD unchanged; Vitest `cpq-price-books`; no shell; no Java |
| **N2** | `feat(fe-cpq): pricing-customers list/detail/CRUD portal` | New `libs/cpq/pricing-customers`; `tsconfig.base.json`; `app.routes.ts`; `sidebar.component.ts`; `frontend/package.json` `test:cpq`; Vitest; `docs/forge/impl/n2-fe-pricing-customers.md` | **MED** | List/detail/create/update/delete via `PricingCustomerService` on `/api/v1/pricing-customers`; nav works; not CRM `/customers`; `nx test cpq-pricing-customers` green |
| **N3** | `feat(fe-cpq): quote soft-delete on detail` | `quotes-detail` (+spec); `quote.service.spec`; `docs/forge/impl/n3-fe-quote-delete.md` | **LOW–MED** | ConfirmDialog → `QuoteService.delete` → list; ACCEPTED disabled; `QUOTE_ACCEPTED_NO_DELETE` banner; Vitest `cpq-quotes`; no BE Java; no shell |
| **N4** | `docs(cpq): Wave N forge closeout` | Matrix notes (price-book header FE, pricing-customers FE, quote DELETE FE); holds listed; `docs/forge/impl/n4-wave-n-docs.md` | **LOW** | Docs match runtime; **no** orchestrator status edit |

### Parallelism rules

**Day-1 max safe parallel (3 panes):** **N1 ∥ N2 ∥ N3**

| Pair | Conflict? | Rule |
|------|-----------|------|
| N1 vs N2 | price-books lib vs new lib; N2 owns shell | Parallel |
| N1 vs N3 | price-books vs quotes | Parallel |
| N2 vs N3 | N2 owns shell; N3 must **not** edit routes/sidebar/`test:cpq` | Parallel |
| N1 vs N3 merge | none | Any order |
| N4 | Docs | After Day-1 notes |

**Merge gate:** none beyond **N2 exclusive ownership** of `app.routes.ts`, `sidebar.component.ts`, `frontend/tsconfig.base.json` path, `frontend/package.json` `test:cpq`. Do **not** merge two panes that both add a sidebar item.

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, reopens WhatsApp #430, starts #238/#241, builds contract-asset/subscriptions FE, rule-set admin, bundles, or NgRx.

---

## 4. Blast radius / GitNexus

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| N1 FE book update/delete | First UI → existing PUT/DELETE | FE **UNKNOWN** + dropped receivers; BE update **LOW** | **MED** (published gate) |
| N2 new portal + shell | New lib → pricing-service HTTP | FE class **LOW**; FE outside Java graph | **MED** (new nav) |
| N3 FE quote delete | First UI → existing DELETE | FE **UNKNOWN** + 5 dropped; BE **LOW** | **LOW–MED** (soft-delete UX) |
| Dual-read default | — | Flip = **HIGH** — **not in wave** | — |
| `ProductBundleController` | HTTP | **UNKNOWN** — **not in wave** | — |
| `SubscriptionController` | HTTP / gateway | Entity leak — **not in wave** | — |

**Explicit HIGH / WARN callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default (**HIGH**).  
2. Primary index **`e09a4a3` MATCHES HEAD**. CLI may still flag stale from untracked `.worktrees/` — **false stale**. **UNKNOWN ≠ unused**.  
3. Disambiguate `QuoteService` / `PriceBookService` / `delete` / `update` with `--file` / `--uid`.  
4. FE `update`/`delete` **lower-bound** dropped receivers — `rg` confirms no UI callers today.  
5. Quote delete is **soft-delete** and blocked only for ACCEPTED — ConfirmDialog required; do not claim hard delete.  
6. Price-book header writes must reuse `isPriceBookPublished` (`effectiveDate` present and `<= today` ISO date).  
7. Pricing-customers are **not** CRM contacts.  
8. #430 WhatsApp on main — do not undo.

---

## 5. Herdr forge layout (Grok 4.5 panes as IMPLEMENTERS)

Orchestrator will prompt **Herdr** windows; do **not** dual-write with extra subagents on the same trees.

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wn-fe-price-book-header` | `feat/forge-n1-fe-price-book-header` | N1 |
| 2 | `wn-fe-pricing-customers` | `feat/forge-n2-fe-pricing-customers` | N2 |
| 3 | `wn-fe-quote-delete` | `feat/forge-n3-fe-quote-delete` | N3 |
| 4 (closeout) | `wn-docs` | `docs/forge-n4-wave-n-closeout` | N4 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** `impact` with `-d upstream -r /home/username01/Projects/open-crm-cpq --file <path>` before symbol edits; warn HIGH/CRITICAL/UNKNOWN; confirm UNKNOWN with `rg`. Primary index already matches `e09a4a3`; re-analyze if tip moves. Do not trust CLI stale if caused only by untracked `.worktrees/`.  
**Java:** **none planned.** If a pane accidentally edits Java: `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2` and Maven from that JDK.  
**Vitest (frontend/):** `NX_DAEMON=false npx nx test cpq-price-books --skip-nx-cache` (N1); `NX_DAEMON=false npx nx test cpq-pricing-customers --skip-nx-cache` (N2); `NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache` (N3).  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read default flip, WhatsApp, full SpEL, Kafka S2B-9, contract-asset FE, rule-set admin, bundles, or NgRx.

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
- Contract-asset / subscriptions portal FE **and** BE DTO+list  
- NgRx CPQ global store  
- Rule-set / rule admin portal (activate/deprecate/DRL validate)  
- Product-bundles portal (no FE models yet)  
- Expanding `pricing-ui` waterfall stub  
- Quote header / clone / document regressions  
- Price-book entry CRUD regressions (M2)  
- FULL_PREFLIGHT / live ERP adapters  

---

## 7. Goal text (paste into `/goal` when forging — only after Wave M COMPLETE)

```text
Implement Open CRM/CPQ Wave N per docs/forge/23-wave-n-architecture.md in three parallel tracks: (1) FE price-books — header Edit/Save via existing PriceBookService.update (PriceBookUpdateRequest) and ConfirmDialog Delete via PriceBookService.delete then navigate to /price-books; reuse isPriceBookPublished / canEditEntries; disable writes when published (effectiveDate present and <= today); banner PRICE_BOOK_PUBLISHED_IMMUTABLE; do not regress M2 entry CRUD; no shell; no Java; (2) new FE lib @frontend/cpq-pricing-customers — list/detail/create/update/delete via existing PricingCustomerService on /api/v1/pricing-customers only (optional segment filter); wire app.routes + sidebar (N2 owns shell); add tsconfig path + test:cpq project; never /api/v1/customers or CRM contacts; (3) FE quotes-detail — ConfirmDialog soft-delete via QuoteService.delete then /quotes; disable when ACCEPTED; banner QUOTE_ACCEPTED_NO_DELETE; quote.service.spec DELETE coverage; no BE Java; no shell. Day-1 parallelize N1∥N2∥N3; N4 docs closeout after. Use GitNexus impact before symbol edits (primary index e09a4a3 matches HEAD; CLI stale from untracked .worktrees/ is false); warn HIGH/CRITICAL/UNKNOWN; disambiguate QuoteService/PriceBookService/delete/update by file_path/uid. Vitest: NX_DAEMON=false nx test cpq-price-books, cpq-pricing-customers, cpq-quotes. No Maven unless Java is touched (then JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2). Do not invent Activities API, start #238/#241, flip dual-read default, reopen WhatsApp #430, embed validation in gateway, full SpEL, Kafka S2B-9, contract-asset FE, rule-set admin, bundles, or NgRx. Evidence: docs/forge/impl/n*.md done, price-book header write green, pricing-customers portal green, quote delete green.
```

---

## 8. Architect sign-off checklist

- [x] Wave M COMPLETE on forging tip (`e09a4a3`; M1–M4)  
- [x] GitHub: open issues **only #238, #241** (held); open PRs **none**; #430 **on main** (do not undo)  
- [x] Evidence: price-book header HTTP-only; pricing-customers HTTP-only (zero UI); quote delete HTTP-only; dual-read still true; `security.open` empty  
- [x] GitNexus: primary re-analyzed **2026-08-31**, indexed **`e09a4a3` MATCHES HEAD**; FE book `update`/`delete` **UNKNOWN**; FE quote `delete` **UNKNOWN**; `PricingCustomerService` **LOW**; BE quote `delete` **LOW**; `ProductBundleController` **UNKNOWN** — **warned**; CLI stale from `.worktrees/` is **false**  
- [x] Dual-read default flip **held**; product validate delete **held**; gateway domain rules **rejected**; contract-asset FE **held**; rule-sets **deferred** (shell); bundles **deferred**  
- [x] PR DAG max parallelism with disjoint files documented (**3** Day-1 panes: N1∥N2∥N3 → N4); **one** shell owner (N2)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp, full SpEL, Kafka S2B-9, dual-read flip, contract-asset FE, NgRx  
- [x] Overall wave risk **LOW–MED** (N1/N2 MED; N3 LOW–MED; no HIGH in-scope work)  
- [x] Herdr forge layout with Grok 4.5 implementer panes documented  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [x] Orchestrator → Wave N FORGING (orchestrator owns `00-orchestrator-status.md`)  
- [x] Day-1 + closeout merged; N4 COMPLETE  

**PM WARNING:** Primary GitNexus **matches `e09a4a3`**; do not treat CLI stale from `.worktrees/` as behind. Do **not** flip approval legacy dual-read default (**HIGH**). N2 **owns** shell — N1/N3 must not edit routes/sidebar. N1 must not regress entry CRUD. N3 must not claim hard-delete or allow ACCEPTED delete. Pricing-customers path is **`/api/v1/pricing-customers`**, not CRM customers. No Java planned.

**WAVE N — APPROVED FOR FORGE.**
