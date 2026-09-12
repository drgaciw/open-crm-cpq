---
status: done
id: u5-wave-u-docs
wave: U
pr: U5
---

# u5-wave-u-docs

## Summary

Wave U forge closeout (docs only). Dual-read default still `${APPROVALS_LEGACY_LOCAL_READ:true}`. Product-service validate still retained. WhatsApp #430 not undone. ADR-006 remains **Proposed** (O3 on `main`; other entity leaks remain). Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** Accept ADR-006. Did **not** start price-book list-row delete (**R2** still **HELD**). Did **not** name Wave T / T1 / T2. Did **not** reopen N/S. Did **not** flip YAML/env. No Java, NgRx, Activities, `app.routes` / sidebar / shell, subscriptions FE.

## Landed (Day-1 already on `main`)

| ID | Result | Tip SHA |
|----|--------|---------|
| U1 | CRM dashboard loading `role="status"` + compact empty `role="status"` + load-error `role="alert"` banner + Retry; stretch `app-page-header` — **no** full `app-empty-state` in cards; **no routes** | `3cec4cf` |
| U2 | lead + contact `error.set` H1 `message [CODE]`; list/detail `role="alert"` banners; no false empty on failed load | `2af5a75` |
| U3 | deal + organization same as U2; S1 `app-empty-state` retained for true empty | `2c7bf0a` |
| U4 | pricing-data-access `rule` / `rule-set` / `pricing-customer` / `pricing-execution` H1 `errorCode` — **no** `price-book.service` edit; **no** rule-sets/pricing-customers component edits | `2168719` |

## Residuals / holds noted (this closeout)

| Residual | Disposition |
|----------|-------------|
| **R2** price-book list-row delete | **Still HELD** — do not implement; not named a Wave R track |
| **Wave T** / T1 / T2 | **Skipped** — do not name or reopen |
| Dual-read flip | Hold **J–U** — YAML untouched; still `true` |
| ADR-006 | Still **Proposed** |

## Docs closeout (this PR)

| Artifact | Change |
|----------|--------|
| `AGENT.md` / `ARCHITECTURE.md` | Waves **A–S COMPLETE**; Wave **U** this closeout (U1∥U2∥U3∥U4); dual-read hold **J–U** |
| Matrix | Dual-read flip **not authorized in Waves J–U**; Wave U UX note; YAML untouched |
| `33-wave-u-ux-architecture.md` | Status COMPLETE; R2 still held; T skipped; U1–U5 checkbox |
| `docs/forge/impl/u5-wave-u-docs.md` | This note |

## Holds (unchanged)

- Activities API / `/api/v1/activities/**`
- CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; subscriptions FE; NgRx; Accept ADR-006
- **R2** price-book list-row delete; named Wave T / T1 / T2; WhatsApp #430 undo
