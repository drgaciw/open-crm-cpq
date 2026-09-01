# Wave Q architecture — UX/UI polish of existing CPQ/CRM surfaces

**Status:** READY FOR FORGE — orchestrator locked 2026-08-31: **N COMPLETE**; **no N′′**; next wave is **Q UX**  
**Architect:** `opencrm-architect` (Herdr `w8`) — **plan only; do not spawn implementers from this pane**  
**Evidence date:** 2026-08-31  
**Baseline:** Wave P COMPLETE + N review on `main` @ `0b4cd6f` (`e14d832` = N adversarial review; P4 merge `68082c7`)  
**Survey tip:** `origin/main` @ `0b4cd6f`  
**Sources:** tree `rg` + **read templates** (quotes, catalog, sidebar/shell, price-books, pricing-customers, rule-sets, approvals, product-bundles, shared-ui). GitNexus index **`fc7e631`** vs HEAD **`0b4cd6f`** — **stale**; **analyze skipped** per orchestrator; UNKNOWN ≠ unused — confirm with `rg`.  
**N residuals:** **R1 folded into Q2**; **R2 skipped** (list-row delete hold); **R3 in Q5 closeout**.

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Reopen N1–N4 / forge N′′** | **REJECT** | Orchestrator locked. N Day-1 + H1+H2 complete |
| **R1** quotes-create + documents `errorCode` | **IN → Q2** | Same helper as H1; **do not** re-edit `quotes-detail` |
| **R2** price-book list-row delete | **HOLD** | Stretch; skip unless later PM un-hold |
| **R3** residual docs | **IN → Q5** | Closeout only |
| **New portal / extra sidebar route** | **REJECT** | Polish-only; P1 already owns `product-bundles` shell |
| **Subscriptions FE** | **HOLD** | Activate still `ErpAdapter.syncToErp`; ADR-006 Proposed |
| **Accept ADR-006** | **HOLD** | Other entity leaks remain |
| **NgRx** | **HOLD** | Signals + HttpClient |
| **Activities / #238 / #241 / dual-read flip / product validate delete / gateway domain validation / SpEL / Kafka S2B-9 / WhatsApp #430** | **HOLD** | Standing holds |
| **✅ Q1 shared-ui primitives** | **IN** | ConfirmDialog a11y, EmptyState, PageHeader search slot, DataTable loading/keyboard — **no shell** |
| **✅ Q2 quotes polish + R1** | **IN** | create + documents `errorCode`; list empty CTA — **no** `quotes-detail` |
| **✅ Q3 catalog + rule-sets error/density** | **IN** | Local helpers omit `errorCode`; no shell |
| **✅ Q4 CRM chrome + approvals + pricing-customers** | **IN** | Mobile sidenav + nav labels; **owns sidebar/shell/toolbar only** — **not** `app.routes` / tsconfig / `test:cpq` |
| **✅ Q5 docs closeout** | **IN** | R3; dual-read J–Q; no `00-orchestrator-status` |

**Chosen theme:** **FE-only UX consistency** on surfaces already on `main`. **Zero new portal.** At most **one chrome owner (Q4)** for existing nav/shell — no new route.

**Orchestrator rule:** Do **not** update `docs/forge/00-orchestrator-status.md` from implementer PRs.

---

## 1. Problem / evidence (templates, not memory)

### GitNexus freshness

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree (architect): /home/username01/Projects/open-crm-cpq/.worktrees/architect
Indexed commit: fc7e631
HEAD (primary): 0b4cd6f
Status: STALE — analyze skipped; plan from rg + files
Always: -r /home/username01/Projects/open-crm-cpq --file <path>
UNKNOWN ≠ unused
```

### GitHub

| Kind | IDs | Wave Q |
|------|-----|--------|
| Open issues | **#238**, **#241** only | **HOLD** |
| Open PRs | **none** | — |
| #430 | on `main` | **OUT** |

### Dual-read (hold)

```yaml
# backend/services/quote-service/src/main/resources/application.yml
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

### Q-T1 — shared-ui primitives (MED a11y / LOW–MED density)

| Surface | Evidence |
|---------|----------|
| `ConfirmDialogComponent` | `confirm-dialog.component.ts`: no `cdkFocusInitial`, no `aria-describedby`, buttons lack `type="button"`; hex colors; `confirmColor` API exists and **callers already pass `warn`** for deletes |
| `EmptyStateComponent` | No `role="status"` / `aria-live`; `min-height: 400px` |
| `PageHeaderComponent` | `hasSearchSlot()` **always `return true`** — empty search padding on detail pages (quotes-detail, catalog-detail, …) |
| `DataTableComponent` | Loading overlay **no** `role="status"`; clickable rows **no** keyboard (`(click)` on `td` only); empty cell “No data available” not `EmptyState` |
| `rg` skip-link | **zero** in `frontend/apps/crm` |

### Q-T2 — quotes (R1 + list) (LOW)

| Surface | Evidence |
|---------|----------|
| `quotes-detail` `httpErrorMessage` | **H1 done** — `errorCode` → `` `${message} [${code}]` `` — **do not re-edit this file** |
| `quotes-create.component.ts` L22–25 | message-only helper — **R1** |
| `quote-documents-panel.component.ts` L25–27 | message-only helper — **R1** |
| `quotes-list` empty | No `actionLabel` when unfiltered (catalog/price-books **have** “New …” CTA). Message: “Quotes will appear here once loaded.” |
| `quotes-detail` header | **12+** lifecycle buttons (`flex-wrap`) — density; **out of Q2 Day-1** (would edit `quotes-detail`; stretch later) |

### Q-T3 — catalog + rule-sets (LOW–MED)

`rg function httpErrorMessage` — **message-only** (no `errorCode`) in:

- `catalog-detail.component.ts`
- `option-create-dialog` / `option-group-create-dialog`
- `config-rules-panel` / `config-rule-create-dialog`
- `rule-set-detail` / `rule-dialog` / `rule-impact-dialog`

catalog-detail `@media (max-width: 768px)` collapses `info-grid` only; nested options/attrs/config-rules stay dense. catalog-list **already** has empty CTA.

### Q-T4 — chrome + approvals + pricing-customers (MED chrome / LOW banners)

| Surface | Evidence |
|---------|----------|
| `shell.component.ts` | `mat-sidenav mode="side" opened` **always** — hamburger toggles but no `over` + closed-by-default on small viewports; `.page-container` padding 24px, **no** `@media` |
| `sidebar.component.ts` | Labels mixed: **Price Books** vs **Pricing customers** vs **Rule sets** vs **Pricing Simulator** vs **Bundles** (P1). No CRM/CPQ grouping. **Do not add/remove routes** |
| `toolbar.component.ts` | Menu `aria-label="Toggle menu"` OK; **Notifications** / **Account** are inert icon buttons |
| `approvals-inbox` L28–30 | message-only `httpErrorMessage`; actions cell 3 buttons/row |
| `pricing-customer-detail` | message-only helper |
| `product-bundles` | P1 already has `app-empty-state` on list/detail — **do not fight**; no Q track except “don’t edit” |

---

## 2. Decisions + rejected

### Track 1 — shared-ui (no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| ConfirmDialog | Additive a11y: `type="button"`, `cdkFocusInitial` on confirm, `aria-describedby` on message; keep `confirmColor` API | Restyle-only; changing caller contracts |
| EmptyState | `role="status"` on root | Rewriting every caller |
| PageHeader | Detect search projection (`contentChild` / `ng-content` presence) so `hasSearchSlot()` is **false** when unused | Always-true leftover |
| DataTable | Loading `role="status" aria-live="polite"`; clickable rows `tabindex="0"` + Enter/Space → `rowClick` | New table library |
| Error helper | **Do not** extract shared `httpErrorMessage` this wave (avoids serial gate). Feature tracks **copy H1 local helper** | Shared-utils Day-1 (merge coupling) |

### Track 2 — quotes (R1) (no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| R1 | `quotes-create` + `quote-documents-panel` helpers match H1 (`errorCode` / `error.errorCode` → `message [CODE]`). Specs mock `{ message, errorCode }` | Re-editing `quotes-detail` |
| List | Unfiltered empty: `actionLabel` “New Quote” → `onNewQuote()` | Changing table columns |
| Detail overflow | **Stretch / skip Day-1** — would edit `quotes-detail` | Bundling overflow with R1 |

### Track 3 — catalog + rule-sets (no shell)

| Topic | Decision | Rejected |
|-------|----------|----------|
| errorCode | Copy H1 helper in catalog + rule-sets files listed in §1 (not quotes-detail) | Hitting product `POST /validate` |
| Density | Keep P3 `config-rules-panel` extracted; wrap `header-actions` / nested sections at 768px | Dumping more into catalog-detail mega-file |
| Shell | **None** | New configuration-rules portal |

### Track 4 — CRM chrome + approvals + pricing-customers

| Topic | Decision | Rejected |
|-------|----------|----------|
| Sidenav | `<768px`: `mode="over"`, start **closed**; hamburger opens; `aria` on sidenav. `≥768px`: keep `side`+opened | New layout framework |
| Skip link | First focusable in shell: “Skip to main content” → `#main-content` on `<main>` | — |
| Sidebar | Title Case: **Pricing Customers**, **Rule Sets**, **Pricing Simulator**; keep **Bundles** path/label (P1). Optional `<h3>` CRM vs CPQ groups — no new `routerLink` | Adding subscriptions; removing Bundles; editing `app.routes.ts` / tsconfig / `test:cpq` |
| Toolbar | `aria-disabled` or hide inert Notifications/Account (no fake features) | Building account menu |
| Approvals / pricing-customers | H1-shaped `httpErrorMessage` + specs | List-row delete; NgRx |
| Files Q4 **owns** | `shell.component.ts`, `sidebar.component.ts`, `toolbar.component.ts`, `approvals-inbox*`, `pricing-customer-detail*` (+specs) | `app.routes.ts` |

### Track 5 — docs

R3: N residuals closed (R1 in Q2; R2 still held). Dual-read hold **J–Q**. ADR-006 stays **Proposed**. No `00-orchestrator-status.md`.

---

## 3. PR DAG

Disjoint: **shared-ui** ∥ **quotes** ∥ **catalog+rule-sets** ∥ **shell+approvals+pricing-customers**.

```text
Q1 shared-ui                 Q2 quotes (R1)              Q3 catalog+rule-sets         Q4 chrome+approvals+PC
──────────────────           ────────────────            ────────────────────         ──────────────────────
ConfirmDialog a11y            create+documents            local errorCode               shell mobile sidenav
EmptyState/PageHeader         errorCode like H1           catalog+rule-sets             skip-link; sidebar labels
DataTable kbd/loading         list empty CTA              density wrap                  approvals+PC errorCode
        │  no routes                  │  no quotes-detail         │  no shell                   │  no app.routes
        └─────────────────────────────┴───────────────────────────┴─────────────────────────────┘
                                                      ▼
                                         Q5 docs closeout (R3; no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **Q1** | `fix(fe-ui): confirm/empty/header/table a11y` | `frontend/libs/shared/ui` ConfirmDialog, EmptyState, PageHeader, DataTable (+specs); `docs/forge/impl/q1-fe-shared-ui-a11y.md` | **MED** (many importers) | Dialog focus + `type="button"`; empty `role="status"`; header no empty search slot; table loading live region + keyboard row; `nx test shared-ui`; **no** routes |
| **Q2** | `fix(fe-cpq): quote create/documents errorCode + list empty CTA` | `quotes-create`, `quote-documents-panel`, `quotes-list` (+specs); `docs/forge/impl/q2-fe-quotes-ux.md` | **LOW** | R1 banners `message [CODE]`; list empty has New Quote when unfiltered; **quotes-detail not in diff**; `nx test cpq-quotes` |
| **Q3** | `fix(fe-cpq): catalog and rule-sets errorCode + density` | catalog helpers/dialogs/panel + rule-sets helpers/dialogs (+specs); `docs/forge/impl/q3-fe-catalog-rulesets-ux.md` | **LOW–MED** | Helpers match H1; 768px wrap; no `/api/v1/rules` from catalog; no validate UI; `nx test cpq-catalog` + `cpq-rule-sets` |
| **Q4** | `fix(crm): mobile sidenav + nav labels; CPQ banner errorCode` | `shell` / `sidebar` / `toolbar`; approvals-inbox; pricing-customer-detail (+specs); `docs/forge/impl/q4-fe-shell-approvals-customers-ux.md` | **MED** (chrome) | Mobile `over` sidenav; skip-link; Title Case labels; Bundles unchanged; **no** `app.routes`/`test:cpq`/tsconfig; H1-shaped banners; `nx test crm` app **or** component specs for shell if present + `cpq-approvals` + `cpq-pricing-customers` |
| **Q5** | `docs(cpq): Wave Q forge closeout` | R3 note; dual-read J–Q; `docs/forge/impl/q5-wave-q-docs.md` | **LOW** | Docs match; R2 still held; ADR-006 Proposed; **no** orchestrator status |

**Day-1 max safe parallel:** **Q1 ∥ Q2 ∥ Q3 ∥ Q4** then **Q5**.

| Pair | Rule |
|------|------|
| Q1 vs others | Parallel; Q1 **additive** a11y — do not rename ConfirmDialog inputs |
| Q2 vs Q3 | Parallel (quotes vs catalog/rule-sets) |
| Q4 vs Q1 | Parallel; Q4 must not edit `shared-ui` |
| Q4 vs P1 | **Do not** change `product-bundles` route, tsconfig alias, or `test:cpq` |
| Merge | No serial gate except Q4 exclusive chrome files |

---

## 4. Blast radius / GitNexus

Index **stale** (`fc7e631` vs `0b4cd6f`). Treat impact as **lower bound**. **`rg` is SSOT.**

| Change | `rg` / note | Wave risk |
|--------|-------------|-----------|
| Q1 ConfirmDialog / PageHeader / DataTable / EmptyState | Imported across CRM lists + all CPQ portals | **MED** — keep API compatible |
| Q2 quotes-create / documents / list | Local helpers; **not** quotes-detail | **LOW** |
| Q3 catalog + rule-sets helpers | Local; HTTP controllers **UNKNOWN** if GitNexus used — no Java | **LOW–MED** |
| Q4 ShellComponent / SidebarComponent | `rg` shell = layout only; HTTP N/A | **MED** (every page) |
| Dual-read YAML | Flip = **HIGH** — **not in wave** | — |
| `quotes-detail` `httpErrorMessage` | **Do not edit** (H1) | — |

**WARN:**

1. Do **not** flip dual-read (**HIGH**).  
2. UNKNOWN HTTP controllers ≠ unused — Q is FE-only.  
3. Disambiguate FE vs BE `QuoteService` / `delete` with `--file`.  
4. Do not re-edit `quotes-detail`.  
5. Do not add routes (P1 Bundles stays).  
6. #430 — do not undo.

---

## 5. Herdr forge layout (Grok 4.5 IMPLEMENTERS — orchestrator spawns)

Architect **w8 does not spawn these**.

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wq-fe-shared-ui` | `fix/forge-q1-fe-shared-ui-a11y` | Q1 |
| 2 | `wq-fe-quotes` | `fix/forge-q2-fe-quotes-ux` | Q2 |
| 3 | `wq-fe-catalog-rulesets` | `fix/forge-q3-fe-catalog-rulesets-ux` | Q3 |
| 4 | `wq-fe-shell-approvals` | `fix/forge-q4-fe-shell-approvals-customers-ux` | Q4 |
| 5 (closeout) | `wq-docs` | `docs/forge-q5-wave-q-closeout` | Q5 |

**Chrome owner:** **Q4** (`shell` + `sidebar` + `toolbar` only). **Zero new** `app.routes` / tsconfig path / `test:cpq`.  
**Model:** Grok 4.5, worktree-isolated. Overlay isolation is **not** merge SSOT.  
**Vitest:** `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`  
**Java:** none expected. If touched: `JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2`  
**Do not** implement holds in §6. Do not edit `docs/forge/00-orchestrator-status.md`.

---

## 6. Out of scope

- Activities API / `/api/v1/activities/**`  
- CI #238, agent Kafka #241  
- Product-service `POST /configuration/validate` deletion  
- Gateway domain validation  
- Dual-read YAML/env flip  
- Full SpEL; Kafka S2B-9  
- WhatsApp #430 undo  
- NgRx CPQ store  
- Subscriptions / contract-asset FE  
- Accept ADR-006  
- N′′ / R2 price-book list-row delete  
- New portal or extra sidebar route  
- Re-editing `quotes-detail` `httpErrorMessage`  
- Product-bundles route/lib fight with P1  
- Fake account/notifications features  

---

## 7. Goal text (orchestrator `/goal` after this file is on the forging tip)

```text
Implement Open CRM/CPQ Wave Q UX per docs/forge/29-wave-q-ux-architecture.md in four parallel FE tracks: (1) shared-ui — ConfirmDialog type=button + cdkFocusInitial + aria-describedby; EmptyState role=status; PageHeader hasSearchSlot false when no search projection; DataTable loading role=status and keyboard rowClick; nx test shared-ui; (2) quotes — R1 httpErrorMessage on quotes-create and quote-documents-panel matching H1 quotes-detail (errorCode → message [CODE]); quotes-list unfiltered empty actionLabel New Quote; do not edit quotes-detail; nx test cpq-quotes; (3) catalog + rule-sets — copy H1 errorCode helper into message-only httpErrorMessage functions; 768px wrap; no product validate UI; nx test cpq-catalog and cpq-rule-sets; (4) CRM shell mobile sidenav mode=over when <768px + skip-link; sidebar Title Case (Pricing Customers, Rule Sets, Pricing Simulator); keep Bundles; no app.routes/tsconfig/test:cpq; approvals-inbox and pricing-customer-detail errorCode banners; nx test cpq-approvals and cpq-pricing-customers. Day-1 Q1∥Q2∥Q3∥Q4; Q5 docs after (R3; dual-read J–Q; R2 still held). No Java. Do not flip dual-read default, invent Activities, start #238/#241, delete product validate, reopen WhatsApp #430, NgRx, subscriptions FE, Accept ADR-006, or N′′. Do not edit docs/forge/00-orchestrator-status.md. Evidence: docs/forge/impl/q*.md done, vitest green.
```

---

## 8. Architect sign-off checklist

- [x] N COMPLETE; N′′ **not** authorized; R1 in Q2; R2 held; R3 → Q5  
- [x] GitHub: **#238**, **#241** hold; #430 on main  
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`  
- [x] Evidence from **read templates** + `rg`  
- [x] GitNexus **stale** (`fc7e631` / `0b4cd6f`) — analyze skipped; `rg` used  
- [x] **Zero new portal**; Q4 chrome owner **without** `app.routes` / `test:cpq` / tsconfig  
- [x] P1 Bundles route **untouched**  
- [x] `quotes-detail` **not** in Q2 Day-1  
- [x] PR DAG 4 Day-1 panes → Q5; overall **LOW–MED**; no HIGH in-scope  
- [x] Holds listed  
- [x] Architect w8 **does not spawn** implementers  
- [ ] Orchestrator → Wave Q FORGING (`00-*`)  
- [ ] Q1–Q5 merged  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). Do **not** reopen N. Do **not** add a portal. Q4 must not fight P1 Bundles.

**WAVE Q — APPROVED FOR FORGE.**
