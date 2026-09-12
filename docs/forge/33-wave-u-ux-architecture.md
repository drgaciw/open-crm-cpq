# Wave U architecture — UX/UI polish (post-S leftovers)

**Status:** READY FOR FORGE — orchestrator locked: Waves **A–S COMPLETE**; **N COMPLETE**; **S COMPLETE**; **Wave T skipped** (do not name T1/T2)  
**Architect:** `opencrm-architect` (Herdr `w8`) — **plan only; do not spawn implementers**  
**Evidence date:** 2026-09-07  
**Baseline:** `origin/main` @ `64c4948` (S review `a7d5b6d`; S5 `91ffa43`)  
**Survey tip:** `origin/main` @ `64c4948`  
**Sources:** `rg` + **read templates**. GitNexus **skip analyze** (likely stale); UNKNOWN ≠ unused; `-r /home/username01/Projects/open-crm-cpq --file`. Linear/Notion Open CRM hits: **none** (cmano-clone / Aegis **out**).  
**Letter skip:** Wave **U** (no Wave T).

---

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Reopen N / N′′ / S1–S5** | **REJECT** | Locked complete |
| **Named Wave T / tracks T1 T2** | **REJECT** | Orchestrator skipped T |
| **Former T1** `listLineItems` message-only | **SKIP** | `rg`: **no UI caller** (service + spec only) — not justified |
| **Former T2** widen `bookHttpErrorMessage` two known codes | **SKIP** | Would reopen **N1/S4 copies**; not justified |
| **R2** price-book list-row delete | **HOLD** | Do not un-hold |
| **New portal / `app.routes` / tsconfig / `test:cpq`** | **REJECT** | Q4 chrome; P1 Bundles |
| **Wire `LoadingService` into shell** | **REJECT** | Interceptor-only today; global spinner = chrome fight |
| **Extract shared `httpErrorMessage`** | **REJECT** | Copy H1 locally; no serial gate |
| **Activities / #238 / #241 / dual-read flip / validate delete / gateway domain / SpEL / Kafka S2B-9 / WhatsApp / NgRx / subscriptions FE / Accept ADR-006** | **HOLD** | Standing |
| **✅ U1 CRM dashboard loading + compact empty** | **IN** | Custom `.loading` + `.empty-text`; full `app-empty-state` is **too tall** (Q1 `min-height: 400px`) for cards |
| **✅ U2 CRM leads + contacts error UX** | **IN** | Services message-only; lists treat failed load as empty; no `error-banner` |
| **✅ U3 CRM deals + orgs error UX** | **IN** | Same as U2; disjoint libs |
| **✅ U4 pricing-data-access service `errorCode`** | **IN** | `rule` / `rule-set` / `pricing-customer` / `pricing-execution` still `err?.error?.message`; rule-set **list** and pricing-customer **list** bind `service.error()` |
| **✅ U5 docs closeout** | **IN** | Dual-read J–U; R2 held; no `00-orchestrator-status` |

**Chosen theme:** **FE-only leftover polish.** **Zero new portal. Zero chrome owner.**

**Orchestrator rule:** Implementers must **not** edit `docs/forge/00-orchestrator-status.md`.

---

## 1. Problem / evidence (templates, not the seed list)

Seeds **verified**. T1/T2 **not folded**.

### Git / GitHub / dual-read

```text
Repository: open-crm-cpq (/home/username01/Projects/open-crm-cpq)
Worktree: /home/username01/Projects/open-crm-cpq/.worktrees/architect
HEAD: 64c4948
GitNexus: skip analyze (stale expected)
```

| Item | State |
|------|--------|
| Open issues | **#238**, **#241** only — **HOLD** |
| Open PRs | **none** |
| Dual-read | `legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — **do not flip** |
| Linear / Notion | No Open CRM/CPQ issues/pages — **do not import** Aegis/cmano |

### U-T1 — dashboard (confirmed)

`dashboard.component.ts`:

- L26: `<div class="loading"><mat-spinner>` — **no** `role="status"` / `aria-live`
- L91–92 / L123–124: `<p class="empty-text">No recent deals/leads.`
- L244: load `error:` only `loading.set(false)` — **no error UI**
- Custom `.dashboard-header` (not `app-page-header`)
- Does **not** import `EmptyStateComponent`

**Do not** drop full `app-empty-state` into activity cards (Q1 empty-state `min-height: 400px`).

### U-T2 / U-T3 — CRM services + invisible failures (confirmed)

| Service | `error.set` |
|---------|-------------|
| `lead.service.ts` | `err?.error?.message \|\| 'An error occurred'` (no `errorCode`, often no `err.message`) |
| `contact.service.ts` | same |
| `organization.service.ts` | same |
| `deal.service.ts` | same |

`rg` `error-banner` / `service.error()` in `frontend/libs/crm`: **zero**. Lead-list (L74–88): failed load → `!loading && isEmpty()` → **“No leads yet”** false empty. Same pattern S1 already used `app-empty-state` on deals/orgs **without** distinguishing error.

Lead-detail loading spinner: **no** `role="status"`.

### U-T4 — pricing-data-access (confirmed; seed incomplete)

| File | `error.set` | UI reader |
|------|-------------|-----------|
| `price-book.service.ts` | **H1 already** (S4) | price-book-list |
| `rule.service.ts` | message-only | `rule-set-detail` `ruleService.error()` |
| `rule-set.service.ts` | message-only | `rule-set-list` `service.error()` |
| `pricing-customer.service.ts` | message-only | `pricing-customer-list` `service.error()` |
| `pricing-execution.service.ts` | message-only | simulator uses **local** HTTP helper (S4); service signal still stale |

Q3 rule-sets **dialogs** already have local H1 helpers; **list/detail banners** still show data-access strings.

### Already done (do not re-litigate)

Q1 shared-ui APIs; Q4 skip-link/sidenav; S1 list empty-states; S2 quotes More; S3/S4 bundle/sim/PB **service** banners; P1 Bundles routes.

`LoadingService` + `loading.interceptor.ts` exist; **no** component injects `LoadingService` except specs.

---

## 2. Decisions + rejected

### Track 1 — dashboard (no chrome)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Loading | `role="status" aria-live="polite"` on `.loading` | Wiring `LoadingService` |
| Card empty | Keep compact `.empty-text`; add `role="status"` | Full `app-empty-state` (400px cards) |
| Load error | `role="alert"` banner + Retry (forkJoin error path currently silent) | Inventing Activities widgets |
| Header | **Stretch:** `app-page-header` — non-blocker | Mandatory header rewrite |
| Shell | **None** | `app.routes` / sidebar |

### Track 2 — CRM leads + contacts

| Topic | Decision | Rejected |
|-------|----------|----------|
| Services | Copy H1 local helper into `lead.service` / `contact.service` `error.set` (include `err.message` + `errorCode`) | Shared extract |
| Lists | If `error()`, show `role="alert"` banner (Dismiss/Retry); **do not** show empty-state for failed loads | Treating error as empty |
| Details | Same banner if `error()`; loading wrappers `role="status"` | Rewriting to DataTable |
| Shell | **None** | |

### Track 3 — CRM deals + organizations

Same as U2 for `deal.service` / `organization.service` + list/detail. Disjoint from U2. S1 empty-state **kept**; add error branch **before** empty.

### Track 4 — pricing-data-access (no UI lib edits)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Scope | H1 helper in `rule.service.ts`, `rule-set.service.ts`, `pricing-customer.service.ts`, `pricing-execution.service.ts` only | Editing `rule-sets` / `pricing-customers` **components** (Q3/N2) |
| Price-book service | **Do not edit** (S4 done) | T2 / N1 `bookHttpErrorMessage` widen |
| Tests | `nx test cpq-pricing-data-access`; **also** `cpq-rule-sets` + `cpq-pricing-customers` if string assertions break (prefer additive `[CODE]` so old message still matches) | Changing dialog local helpers |

### Track 5 — docs

Dual-read hold **J–U**. R2 still held. T skipped. No `00-orchestrator-status.md`.

---

## 3. PR DAG

Disjoint: **dashboard** ∥ **leads+contacts** ∥ **deals+orgs** ∥ **pricing-data-access**.

```text
U1 dashboard                 U2 leads+contacts              U3 deals+orgs                 U4 pricing-data-access
────────────                 ────────────────               ─────────────                 ─────────────────────
loading live region          H1 error.set + list/detail     same for deals+orgs           rule / rule-set /
compact empty role=status    banners; no false empty        keep S1 empty-state           pricing-customer /
forkJoin error banner                                                                               pricing-execution
        │  no routes                 │  no shell                    │  no shell                   │  no UI lib files
        └────────────────────────────┴──────────────────────────────┴─────────────────────────────┘
                                                          ▼
                                             U5 docs closeout (no 00-orchestrator-status)
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **U1** | `fix(crm): dashboard loading a11y and load-error banner` | `dashboard.component.ts` + spec; `docs/forge/impl/u1-fe-crm-dashboard-ux.md` | **LOW** | Loading `role="status"`; compact empties `role="status"`; load failure `role="alert"` + Retry; **no** full empty-state in cards; `nx test crm-dashboard`; no routes |
| **U2** | `fix(crm): lead and contact errorCode banners` | `lead.service` / `contact.service` + list/detail (+specs); `docs/forge/impl/u2-fe-crm-leads-contacts-error.md` | **LOW–MED** | `error.set` is `message [CODE]`; list shows banner not empty-state on error; `nx test crm-leads` + `crm-contacts` |
| **U3** | `fix(crm): deal and organization errorCode banners` | `deal.service` / `organization.service` + list/detail (+specs); `docs/forge/impl/u3-fe-crm-deals-orgs-error.md` | **LOW–MED** | Same as U2; S1 `app-empty-state` retained for true empty; `nx test crm-deals` + `crm-organizations` |
| **U4** | `fix(fe-cpq): pricing-data-access errorCode` | four services + specs; `docs/forge/impl/u4-fe-pricing-data-access-error.md` | **LOW** | list/get `error.set` H1-shaped; **do not** edit `price-book.service.ts`; **do not** edit rule-sets/pricing-customers **components**; `nx test cpq-pricing-data-access` |
| **U5** | `docs(cpq): Wave U forge closeout` | `docs/forge/impl/u5-wave-u-docs.md` | **LOW** | Dual-read J–U; R2 held; T skipped; **no** `00-orchestrator-status` |

**Day-1 max parallel:** **U1 ∥ U2 ∥ U3 ∥ U4** → **U5**.

| Pair | Rule |
|------|------|
| U1 vs U2 | Parallel; dashboard may **read** `LeadService.error()` after U2 — **do not** edit lead.service in U1 |
| U2 vs U3 | Parallel (different CRM libs) |
| U4 vs Q3/N2 UI | U4 **data-access only** |
| Chrome / routes | **Nobody** edits `app.routes.ts`, `sidebar`, `shell`, tsconfig, `test:cpq` |
| R2 / T1 / T2 | **Forbidden** |

---

## 4. Blast radius

GitNexus **skip analyze**. **`rg` SSOT.**

| Change | `rg` | Risk |
|--------|------|------|
| U1 dashboard | Single component | **LOW** |
| U2/U3 CRM services | Lists/details in same lib | **LOW–MED** (false-empty fix) |
| U4 data-access | rule-set-list + pricing-customer-list bind `error()` | **LOW** (additive `[CODE]`) |
| Dual-read YAML | Flip = **HIGH** — out | — |
| Q1 EmptyState min-height | **Do not change** | — |

**WARN:** Do not flip dual-read. Do not un-hold R2. Do not name T1/T2. Disambiguate FE vs BE services with `--file`. P1 Bundles / Q4 chrome untouched.

---

## 5. Herdr layout (orchestrator spawns — architect does not)

| Pane | Agent id | Branch | PR |
|------|----------|--------|-----|
| 1 | `wu-fe-dashboard` | `fix/forge-u1-fe-crm-dashboard-ux` | U1 |
| 2 | `wu-fe-crm-leads-contacts` | `fix/forge-u2-fe-crm-leads-contacts-error` | U2 |
| 3 | `wu-fe-crm-deals-orgs` | `fix/forge-u3-fe-crm-deals-orgs-error` | U3 |
| 4 | `wu-fe-pricing-da` | `fix/forge-u4-fe-pricing-data-access-error` | U4 |
| 5 | `wu-docs` | `docs/forge-u5-wave-u-closeout` | U5 |

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
- Full SpEL; Kafka S2B-9; WhatsApp #430 undo  
- NgRx; subscriptions FE; Accept ADR-006  
- N′′; **R2** list-row delete  
- **Named Wave T** / T1 / T2  
- `listLineItems` helper; widening N1 `bookHttpErrorMessage`  
- New portal; `app.routes` / sidebar / shell / tsconfig / `test:cpq`  
- Q1 primitive API changes; `LoadingService` global spinner  
- Linear/Notion cmano-clone / Project Aegis tickets  

---

## 7. Goal text

```text
Implement Open CRM/CPQ Wave U UX per docs/forge/33-wave-u-ux-architecture.md in four parallel FE tracks: (1) dashboard — loading role=status; compact empty-text role=status not full app-empty-state; forkJoin failure role=alert + Retry; nx test crm-dashboard; (2) lead.service + contact.service H1 error.set; list/detail role=alert on error; do not show empty-state for failed loads; nx test crm-leads and crm-contacts; (3) same for deal.service + organization.service; keep S1 app-empty-state for true empty; nx test crm-deals and crm-organizations; (4) H1 error.set on rule.service, rule-set.service, pricing-customer.service, pricing-execution.service only; do not edit price-book.service or rule-sets/pricing-customers components; nx test cpq-pricing-data-access. Day-1 U1∥U2∥U3∥U4; U5 docs after. No Java. Do not flip dual-read, name T1/T2, un-hold R2, reopen N/S, invent Activities, start #238/#241, NgRx, subscriptions FE, wire LoadingService into shell, or edit 00-orchestrator-status.md. Evidence: docs/forge/impl/u*.md done, vitest green.
```

---

## 8. Checklist

- [x] Even with `origin/main` @ `64c4948`  
- [x] Seeds verified; T1/T2 **not** folded; dashboard EmptyState **rejected** (density)  
- [x] N/S complete; no N′′; R2 held; tracks named **U1–U5** only  
- [x] Dual-read still true  
- [x] Zero new portal; **no** chrome owner  
- [x] GitNexus analyze skipped; `rg` used  
- [x] Architect **does not spawn** implementers  
- [ ] Orchestrator → Wave U FORGING (`00-*`)  
- [ ] U1–U5 merged  

**PM WARNING:** Do **not** flip dual-read (**HIGH**). Do **not** un-hold R2. Do **not** name T1/T2. Do **not** put 400px empty-state on dashboard cards.

**WAVE U — APPROVED FOR FORGE.**
