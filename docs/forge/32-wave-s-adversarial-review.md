# Wave S adversarial review — completeness + residual hygiene

**Status:** REVIEW COMPLETE — Wave S Day-1 **is 100% complete** (S1–S5 on `main`); residual gaps are **optional LOW** (do **not** reopen S)  
**Architect:** `opencrm-architect` (Herdr `w8:p1`)  
**Evidence date:** 2026-09-07  
**Survey tip:** `origin/main` @ `377e806` (Wave S COMPLETE; S5 `91ffa43`)  
**Do not** edit `docs/forge/00-orchestrator-status.md` from this review  
**Do not** spawn implementers from w8

---

## 0. Verdict

| Track | Complete? | Evidence @ `377e806` |
|-------|-----------|----------------------|
| **S1** deal + org `app-empty-state` | **YES** | `deal-list` / `organization-list` use `app-empty-state` + Create CTA when unfiltered; loading `role="status" aria-live="polite"`; stretch `app-page-header` also landed (`cfe719d`) |
| **S2** quotes More menu + list `errorCode` | **YES** | Visible Back + Calculate/Submit/Send/Accept; More contains Approve/Reject/Expire/Cancel/Clone/Delete; H1 `quotes-detail` helper still reads `errorCode`; `quote.service` list/get `error.set(httpErrorMessage)` (`7437cde`) |
| **S3** bundle list/get `errorCode` | **YES** | `bundleHttpErrorMessage` any code; service list / listByProductId / getById use it (`7159e9d`) |
| **S4** simulator + PB create + list service | **YES** | Simulator HTTP helper; create-dialog helper; `PriceBookService` list/get/getEntries (`04070c7`); **no** list-row delete |
| **S5** docs closeout | **YES** | `s5-wave-s-docs.md`; dual-read hold **J–S**; R2 still held (`91ffa43`) |
| Dual-read YAML | **HELD** (correct) | `${APPROVALS_LEGACY_LOCAL_READ:true}` |
| **R2** list-row delete | **HELD** (correct) | `price-book-list` still **zero** delete |
| Chrome / routes | **Untouched** (correct) | S tracks did not fight Q4/P1 |

**Day-1 authorized acceptance (S1∥S2∥S3∥S4→S5): 100% complete on `main`.**  
**Do not** spawn workers to “finish Wave S.”

---

## 1. Adversarial notes (not impl-note trust)

| Check | Result |
|-------|--------|
| Custom `.empty-state` divs in `crm`/`cpq` libs | **`rg` zero** after S1 |
| S2 H1 helper rewritten | **No** — still `errorCode` → `message [CODE]` |
| S4 list-row delete sneak-in | **No** |
| `00-orchestrator-status` edited by S5 | S5 note says not; current file is orchestrator-owned COMPLETE |
| GitHub | Open issues **#238**, **#241** only; open PRs **none** |
| Vitest re-run this pane | **Not run** (no `frontend/node_modules` here) — impl notes claim S1 27+7 pass |

### Optional leftovers (not missing S tracks)

| ID | Gap | Why not S-incomplete |
|----|-----|----------------------|
| **T1** | `quote.service.ts` `listLineItems` `error.set` still message-only (L186) | S2 scoped **list + getById** only. `rg listLineItems`: **no UI caller** (service + spec) |
| **T2** | `bookHttpErrorMessage` in create/detail still **two known codes** only (N1 shape); S4 copied that | Plan said copy detail helper; S3/S4 **service** paths are H1 any-code |
| **R2** | Price-book list-row delete | **Standing hold** — do not un-hold |

---

## 2. Decisions + rejected

| Topic | Decision | Rejected |
|-------|----------|----------|
| Reopen Wave S | **No** | S6 product portal |
| Residuals | Optional later **Wave T** (or skip). **Default: no workers now** | N′′; un-hold R2; parallel T while nothing else is queued is OK **only if** PM wants T1/T2 |
| Shared `httpErrorMessage` extract | Still **reject** | Serial gate |
| Dual-read | Untouched | Flip YAML |

---

## 3. PR DAG (optional Wave T — only if PM wants leftovers)

Disjoint: **quotes service line-items error** ∥ **price-book helper widen**. No shell.

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **T1** | `fix(fe-cpq): quote listLineItems errorCode` | `quote.service.ts` + spec; `docs/forge/impl/t1-fe-quote-lineitems-error.md` | **LOW** | `listLineItems` uses same H1 helper as list/get; `nx test cpq-quotes`; no `quotes-detail` helper edit |
| **T2** | `fix(fe-cpq): price-book banners any errorCode` | Local `bookHttpErrorMessage` in detail/create/edit/entry → any `errorCode`; specs; `docs/forge/impl/t2-fe-pricebook-any-errorcode.md` | **LOW** | Any code appended; **no** list-row delete; `nx test cpq-price-books` |
| **T3** | docs closeout | Dual-read J–T; R2 still held | **LOW** | No `00-orchestrator-status` |

**Preferred orchestrator action:** **spawn zero T panes.** Wave S is done. Queue T only after an explicit pick.

---

## 4. Blast radius

GitNexus **stale** — `rg` SSOT. T1/T2 additive string formatting. Dual-read flip = **HIGH** — out.

---

## 5. Herdr (only if T is chosen)

| Pane | Agent id | Branch |
|------|----------|--------|
| 1 | `wt-fe-quote-lineitems-error` | `fix/forge-t1-fe-quote-lineitems-error` |
| 2 | `wt-fe-pricebook-any-errorcode` | `fix/forge-t2-fe-pricebook-any-errorcode` |

Architect **does not spawn**. No chrome owner. Vitest: `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`.

---

## 6. Out of scope

Activities; #238; #241; dual-read flip; product validate delete; gateway domain rules; SpEL; Kafka S2B-9; WhatsApp #430; NgRx; subscriptions FE; Accept ADR-006; N′′; **R2 list-row delete**; new portal.

---

## 7. Checklist

- [x] S1–S5 code+docs on `main` @ `377e806`  
- [x] Dual-read still true  
- [x] R2 still held  
- [x] Default: **no workers**  
- [ ] Orchestrator ack (Herdr `w1:p1`)  
