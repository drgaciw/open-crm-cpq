---
status: done
id: q5-wave-q-docs
wave: Q
pr: Q5
---

# q5-wave-q-docs

## Summary

Wave Q forge closeout (docs only). Dual-read default still `${APPROVALS_LEGACY_LOCAL_READ:true}`. Product-service validate still retained. WhatsApp #430 not undone. ADR-006 remains **Proposed** (O3 on `main`; other entity leaks remain). Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** Accept ADR-006. Did **not** start Wave R2 (price-book list-row delete still **HELD**). Did **not** flip YAML/env.

## Landed (Day-1 already on `main`)

| ID | Result | Tip SHA |
|----|--------|---------|
| Q1 | shared-ui ConfirmDialog a11y (`type="button"`, `cdkFocusInitial`, `aria-describedby`); EmptyState `role="status"`; PageHeader `hasSearchSlot()` false when no search projection; DataTable loading live region + keyboard rowClick — **no shell** | `d2ebf70` |
| Q2 | R1: `quotes-create` + `quote-documents-panel` H1-shaped `errorCode` banners; quotes-list unfiltered empty “New Quote” CTA — **no** `quotes-detail` | `3404bae` |
| Q3 | catalog + rule-sets local `httpErrorMessage` with `errorCode`; 768px density wrap; no validate UI; no shell | `f7eed4c` |
| Q4 | CRM shell mobile `over` sidenav + skip-link; Title Case nav labels; hide inert toolbar icons; approvals-inbox + pricing-customer-detail `errorCode` — **owns chrome only** (no `app.routes` / tsconfig / `test:cpq`) | `0729289` |

## R3 — N residuals closed (this closeout)

| Residual | Disposition |
|----------|-------------|
| **R1** quotes-create + documents `errorCode` | **Landed in Q2** |
| **R2** price-book list-row delete | **Still HELD** — do not implement; not Wave R2 |
| **R3** residual docs | **This PR (Q5)** |

## Docs closeout (this PR)

| Artifact | Change |
|----------|--------|
| `AGENT.md` / `ARCHITECTURE.md` | Waves **A–P COMPLETE**; Wave **Q** this closeout (Q1∥Q2∥Q3∥Q4); dual-read hold **J–Q** |
| Matrix | Dual-read flip **not authorized in Waves J–Q**; Wave Q UX note; YAML untouched |
| `29-wave-q-ux-architecture.md` | Status COMPLETE; R3 note; Q1–Q5 checkbox |
| `docs/forge/impl/q5-wave-q-docs.md` | This note |

## Holds (unchanged)

- Activities API / `/api/v1/activities/**`
- CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; subscriptions FE; NgRx; Accept ADR-006
- **R2** price-book list-row delete; N′′; WhatsApp #430 undo
