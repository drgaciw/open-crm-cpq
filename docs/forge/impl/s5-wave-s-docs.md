---
status: done
id: s5-wave-s-docs
wave: S
pr: S5
---

# s5-wave-s-docs

## Summary

Wave S forge closeout (docs only). Dual-read default still `${APPROVALS_LEGACY_LOCAL_READ:true}`. Product-service validate still retained. WhatsApp #430 not undone. ADR-006 remains **Proposed** (O3 on `main`; other entity leaks remain). Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** Accept ADR-006. Did **not** start price-book list-row delete (**R2** still **HELD**). Did **not** reopen N′′. Did **not** flip YAML/env. No Java, NgRx, Activities, `app.routes` / sidebar / shell.

## Landed (Day-1 already on `main`)

| ID | Result | Tip SHA |
|----|--------|---------|
| S1 | CRM deal + org lists use `app-empty-state` (+ loading `role="status"`; stretch `app-page-header`) — **no routes** | `cfe719d` |
| S2 | quotes-detail More `mat-menu` (Expire/Cancel/Clone/Delete + Approve/Reject); list/get `errorCode` on `quote.service` — H1 helper byte-stable | `7437cde` |
| S3 | product-bundle `bundleHttpErrorMessage` any `errorCode`; list/get service banners | `7159e9d` |
| S4 | simulator HTTP + price-book create-dialog + `PriceBookService` list/get `errorCode` — **no** list-row delete | `04070c7` |

## Residuals / holds noted (this closeout)

| Residual | Disposition |
|----------|-------------|
| **R2** price-book list-row delete | **Still HELD** — do not implement; not named a Wave R track |
| **N′′** | **None** — N complete; no reopen |
| Dual-read flip | Hold **J–S** — YAML untouched; still `true` |
| ADR-006 | Still **Proposed** |

## Docs closeout (this PR)

| Artifact | Change |
|----------|--------|
| `AGENT.md` / `ARCHITECTURE.md` | Waves **A–Q COMPLETE**; Wave **S** this closeout (S1∥S2∥S3∥S4); dual-read hold **J–S** |
| Matrix | Dual-read flip **not authorized in Waves J–S**; Wave S UX note; YAML untouched |
| `31-wave-s-ux-architecture.md` | Status COMPLETE; R2 still held; S1–S5 checkbox |
| `docs/forge/impl/s5-wave-s-docs.md` | This note |

## Holds (unchanged)

- Activities API / `/api/v1/activities/**`
- CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; subscriptions FE; NgRx; Accept ADR-006
- **R2** price-book list-row delete; N′′; WhatsApp #430 undo
