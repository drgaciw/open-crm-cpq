---
status: done
id: p4-wave-p-docs
wave: P
pr: P4
---

# p4-wave-p-docs

## Summary

Wave P forge closeout (docs only). Dual-read default still `${APPROVALS_LEGACY_LOCAL_READ:true}`. Product-service validate still retained. WhatsApp #430 not undone. ADR-006 remains **Proposed** (O3 on `main`; other entity leaks remain). Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** Accept ADR-006. Did **not** start Wave Q.

## Landed (Day-1 already on `main`)

| ID | Result |
|----|--------|
| P1 | `@frontend/cpq-product-bundles` list/detail/create; items add/remove; delete ConfirmDialog; **owns shell** (routes/sidebar/tsconfig/`test:cpq`) on `/api/v1/product-bundles` |
| P2 | Rule-sets Impact dialog → `GET /api/v1/rules/{id}/impact`; no shell; no `pricing-data-access` edit |
| P3 | Catalog extracted `config-rules-panel` → `/api/v1/configuration/rules` (+ `/product/{id}`); no validate UI; no shell |

## Docs closeout (this PR)

| Artifact | Change |
|----------|--------|
| ADR-006 | Header: O3 **on `main`**; subscription leak **gone**; Status stays **Proposed** (`ConfigurationSession`, `ApprovalRequest`, `DocumentGenerationJob`) |
| `docs/adr/README.md` | Drop “O3 forging … until O3 is on main”; still Proposed |
| `AGENT.md` / `ARCHITECTURE.md` | Wave **O COMPLETE**; Wave **P** this closeout (P1 shell ∥ P2 impact ∥ P3 config-rules) |
| Matrix | §7a product-options FE (O2), product-bundles list/items FE (P1), subscriptions DTO list (O3, **no FE**); §3 config-rules FE nest (P3); dual-read hold **J–P** |
| `28-wave-p-architecture.md` | Status COMPLETE; Day-1+closeout checkbox |

## Holds (unchanged)

- Activities API / `/api/v1/activities/**`
- CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; subscriptions FE; NgRx; Accept ADR-006
