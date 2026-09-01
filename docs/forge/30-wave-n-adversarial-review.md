# Wave N adversarial review — completeness + residual hygiene

**Status:** REVIEW COMPLETE — Wave N Day-1 **is 100% complete**; N′ H1+H2 **complete**; residual gaps are **optional LOW** (do not reopen N as incomplete)  
**Architect:** `opencrm-architect` (Herdr `w8:p1`)  
**Evidence date:** 2026-08-31  
**Survey tip:** `origin/main` @ `c2e7b3f` (Wave P COMPLETE)  
**Plan this file authorizes (optional):** N′′ residual only if orchestrator chooses **not** to fold into Wave Q UX  
**Do not** edit `docs/forge/00-orchestrator-status.md` from implementer PRs

---

## 0. Verdict

| Scope | Complete? | Evidence |
|-------|-----------|----------|
| **N1** price-book header Edit/Save + ConfirmDialog delete | **YES** | `price-book-detail` `openEditBook` / `confirmDeleteBook`; `isPriceBookPublished`; banner `PRICE_BOOK_PUBLISHED_IMMUTABLE`; Vitest notes 52 passed |
| **N2** pricing-customers portal + shell | **YES** | lib `@frontend/cpq-pricing-customers`; `app.routes` `pricing-customers`; sidebar; `PricingCustomerService.delete` on detail; API prefix `/api/v1/pricing-customers` |
| **N3** quote soft-delete | **YES** | `canDelete` (!ACCEPTED); ConfirmDialog → `QuoteService.delete` → `/quotes`; `quote.service.spec` DELETE |
| **N4** docs closeout | **YES** | `docs/forge/impl/n4-wave-n-docs.md` |
| **H1** quote banner `message [errorCode]` | **YES** | `quotes-detail` `httpErrorMessage` reads `errorCode`; spec asserts `Cannot delete an accepted quote [QUOTE_ACCEPTED_NO_DELETE]` (`3158620`) |
| **H2** matrix DELETE quotes + N1 PUT/DELETE + dual-read J–N | **YES** | matrix §6 DELETE `/api/v1/quotes/{id}`; §4b N1 header PUT/DELETE; dual-read hold now **J–P** (later waves extended, still true) |
| **H3** TestBed hookTimeout | **NOT OPENED** (optional; hygiene plan said non-blocker) | **No** `hookTimeout` in `cpq-price-books` / `cpq-pricing-customers` / `cpq-quotes` source |
| Dual-read YAML | **HELD** (correct) | `legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` |
| N1 **stretch** list-row delete | **NO** | Explicit non-blocker in `23-wave-n-architecture.md`; `price-book-list` has **zero** delete |

**Day-1 authorized acceptance (N1∥N2∥N3→N4 + H1+H2): 100% complete on `main`.**  
**Including stretch + sibling error helpers + live vitest re-run: not 100%.** Those are residual LOW polish, not missing N tracks.

**Do not** spawn workers to “finish Wave N.” It is finished. Residual work below is **N′′ optional** or **Wave Q**.

---

## 1. Problem / evidence (adversarial, not impl-note trust)

### Git / GitHub @ `c2e7b3f`

| Item | State |
|------|--------|
| N closeout | `5f126ca` |
| H1 | `3158620` / merge `2061638` |
| H2 | `9e1fc3e` / merge `31d5cf9` |
| Open issues | **#238**, **#241** only — **HOLD** |
| Open PRs | **none** |
| #430 | on `main` — do not undo |

### N1 — header writes (pass)

- Published gate reused: `isPriceBookPublished` (`effectiveDate` present and `<=` today).  
- Edit dialog matches `PriceBookUpdateRequest`.  
- Delete ConfirmDialog; published writes disabled; banner includes `[PRICE_BOOK_PUBLISHED_IMMUTABLE]`.  
- **Gap (stretch only):** no list-row delete on `price-book-list`.

### N2 — portal (pass)

- Route + sidebar **Pricing customers**; `test:cpq` includes project.  
- Detail ConfirmDialog delete. Never CRM `/api/v1/customers` (api-contract spec).

### N3 + H1 — quote delete (pass after hygiene)

- Original N3 banner test mocked `error.message` already containing the code (false complete). **H1 closed this.**  
- Live helper: `e?.errorCode \|\| e?.error?.errorCode` → `` `${message} [${code}]` ``.

### Sibling leak (N3 scope was detail-only — residual)

Same quotes lib still **omits** `errorCode`:

- `quotes-create.component.ts` `httpErrorMessage` — message only  
- `quote-documents-panel.component.ts` `httpErrorMessage` — message only  

Not an N3 acceptance miss; it is the same defect class H1 fixed on detail.

### Tests

- Impl notes: N1 52, N2 26, N3/H1 71.  
- Historical flake (architect session): N1/N2 TestBed **hookTimeout 10s** on some TestBed specs — Nx labeled flaky; **not reproduced here** (architect/primary **no** `frontend/node_modules`).  
- H3 correctly **not** opened as a blocker.

### Holds still held (not gaps)

Activities; #238; #241; product validate delete; gateway domain validation; dual-read flip; SpEL; Kafka S2B-9; WhatsApp; NgRx. Rule-sets / bundles / subscription DTO were **N defers** later shipped in O/P — **not** N incompleteness.

---

## 2. Decisions + rejected

| Topic | Decision | Rejected |
|-------|----------|----------|
| Reopen Wave N | **No** — Day-1 complete | New N5/N6 product portal |
| Residual | Optional **N′′** two FE tracks **or** fold into **Wave Q UX** (preferred — Q already targets quotes banners + price-books density) | Parallel N′′ **and** Q on `quotes` / `price-books` (file conflict) |
| List-row delete | Optional R2; ConfirmDialog + same published gate as detail | Hard delete; published delete |
| H3 | Re-run vitest only; bump timeout **iff** flake reproduces | Blind hookTimeout PR |
| Dual-read | Untouched | Flip YAML |
| Shell | **None** for N′′ | Touching `app.routes` / sidebar |

**DAG-changing question:** fold residuals into Wave Q vs tiny N′′ first. **Recommend: Wave Q absorbs them.** Orchestrator may still run N′′ if Q is delayed.

---

## 3. PR DAG (optional N′′ — only if not folding into Q)

Disjoint: **quotes sibling errorCode** ∥ **price-book list-row delete**. No shell.

```text
R1 FE quotes errorCode siblings          R2 FE price-book list-row delete
──────────────────────────────          ──────────────────────────────
quotes-create + documents-panel           price-book-list ConfirmDialog
httpErrorMessage match H1                 unpublished only; same published gate
        │                                        │
        └──────────── Day-1 parallel ────────────┘
                           ▼
              R3 docs note + vitest re-run (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **R1** | `fix(fe-cpq): quote create/documents errorCode in banners` | `quotes-create`, `quote-documents-panel` (+specs); `docs/forge/impl/r1-quote-errorcode-siblings.md` | **LOW** | Banner `message [CODE]` from `errorCode` like H1; `nx test cpq-quotes`; no shell |
| **R2** | `feat(fe-cpq): price-book list-row delete` | `price-book-list` (+spec); `docs/forge/impl/r2-price-book-list-delete.md` | **LOW–MED** | ConfirmDialog delete unpublished only; published disabled; `PRICE_BOOK_PUBLISHED_IMMUTABLE`; navigate/reload list; no detail regression; `nx test cpq-price-books` |
| **R3** | `docs(cpq): N residual closeout` | Short impl note; **no** matrix churn unless R2 adds a FE note | **LOW** | Notes residual closed; dual-read still true; **no** `00-orchestrator-status` |

**Vitest:** `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`  
**Java:** none. Dual-read YAML **untouched**.

---

## 4. Blast radius

| Change | Risk |
|--------|------|
| R1 local helpers in quotes lib | **LOW** — same pattern as H1 (`httpErrorMessage` MEDIUM only if editing quotes-detail; **do not** re-edit detail) |
| R2 first list-row delete UI | **LOW–MED** — FE `PriceBookService.delete` already used by N1 detail; `rg` confirm list has no caller |
| Dual-read / shell / catalog / rule-sets / bundles | **Out** |

GitNexus: confirm `delete` / `httpErrorMessage` with `--file`; UNKNOWN ≠ unused.

---

## 5. Herdr layout (only if N′′ is chosen)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `wnp-fe-quote-errorcode` | `fix/forge-n-quote-errorcode-siblings` | R1 |
| 2 | `wnp-fe-price-book-list-delete` | `feat/forge-n-price-book-list-delete` | R2 |

Grok 4.5. No shell owner. Do **not** overlap Wave Q implementers on `frontend/libs/cpq/quotes` or `price-books`.

**Preferred orchestrator action:** **do not start these panes.** Fold R1 into Wave Q quotes track; skip R2 unless PM wants N stretch. Continue Wave Q UX architecture (`docs/forge/29-wave-q-ux-architecture.md` if not yet written).

---

## 6. Out of scope

Activities; #238; #241; dual-read flip; product validate delete; gateway domain validation; SpEL; Kafka S2B-9; WhatsApp #430; NgRx; Accept ADR-006; subscriptions FE; reopening N1/N2/N3 files except R1/R2 listed; catalog/rule-sets/bundles (O/P).

---

## 7. Goal text (paste only if forging N′′)

```text
Optional Open CRM/CPQ N residual (not a reopen of Wave N) per docs/forge/30-wave-n-adversarial-review.md: (1) quotes-create + quote-documents-panel httpErrorMessage append [errorCode] like H1 quotes-detail; specs mock { message, errorCode }; nx test cpq-quotes; (2) price-book-list ConfirmDialog delete for unpublished books only; reuse isPriceBookPublished; banner PRICE_BOOK_PUBLISHED_IMMUTABLE; nx test cpq-price-books. No shell. No Java. Do not flip dual-read. Do not edit 00-orchestrator-status.md. Do not overlap Wave Q on the same libs.
```

---

## 8. Checklist

- [x] N1–N4 code+docs on `main`  
- [x] H1+H2 on `main`; H3 not required  
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`  
- [x] Residual R1/R2 optional; default fold into Q  
- [x] Orchestrator notified (Herdr `w1:p1`) — do not edit `00-*` from this review PR  
