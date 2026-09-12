---
status: done
id: v4-wave-v-docs
wave: V
pr: V4
---

# v4-wave-v-docs

## Summary

Wave V forge closeout (docs only). Dual-read default still `${APPROVALS_LEGACY_LOCAL_READ:true}`. Product-service validate still retained. WhatsApp #430 not undone. ADR-006 remains **Proposed** (O3 on `main`; other entity leaks remain). Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** Accept ADR-006. Did **not** start price-book list-row delete (**R2** still **HELD**). Did **not** name Wave T / T1 / T2. Did **not** reopen N/S/U. Did **not** flip YAML/env. No Java, NgRx, Activities, `app.routes` / sidebar / shell, subscriptions FE. Did **not** `@use` Tailwind; did **not** reopen CE (#480).

## Landed (Day-1 already on `main`)

| ID | Result | Tip SHA |
|----|--------|---------|
| V1 | Tailwind CSS entry `apps/crm/src/tailwind.css` + Sass `@import` removed; initial `maximumWarning` **750kb** (error still 1mb) — **no** `app.routes` / sidebar / shell | `32ccdd8` (#483) |
| V2 | catalog `product.service` list/getById/search H1 `error.set` `message [CODE]` — list/detail banners unchanged structurally | `56b9c77` (#485) |
| V3 | deal-kanban load-error `role="alert"` + Retry; loading `role="status"`; empty columns only when load succeeded | `2235cc9` (#484) |

## Residuals / holds noted (this closeout)

| Residual | Disposition |
|----------|-------------|
| **R2** price-book list-row delete | **Still HELD** — do not implement; not named a Wave R track |
| **Wave T** / T1 / T2 | **Skipped** — do not name or reopen |
| Dual-read flip | Hold **J–V** — YAML untouched; still `true` |
| ADR-006 | Still **Proposed** |
| CE dual-run / #480 | Do not reopen |

## Docs closeout (this PR)

| Artifact | Change |
|----------|--------|
| `AGENT.md` / `ARCHITECTURE.md` | Waves **A–U COMPLETE**; Wave **V** this closeout (V1∥V2∥V3); dual-read hold **J–V** |
| Matrix | Dual-read flip **not authorized in Waves J–V**; Wave V FE polish note; YAML untouched |
| `34-wave-v-architecture.md` | Status COMPLETE; R2 still held; T skipped; V1–V4 checkbox |
| `docs/forge/impl/v4-wave-v-docs.md` | This note |

## Holds (unchanged)

- Activities API / `/api/v1/activities/**`
- CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; subscriptions FE; NgRx; Accept ADR-006
- **R2** price-book list-row delete; named Wave T / T1 / T2; WhatsApp #430 undo
- `@use` Tailwind; CE Java / dual-run reopen (#480)
