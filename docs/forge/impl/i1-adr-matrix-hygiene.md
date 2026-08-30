---
status: done
id: i1-adr-matrix-hygiene
---

# i1-adr-matrix-hygiene

## Summary

Wave I **I1**: Reconcile ADR + residual contract-matrix rows so **S2B-11 hybrid is documented as LANDED (Waves F–H)**, not still “implementation deferred”. product-service validate **retained** with **no removal date**. **Docs only** — no Java/TS.

**Plan:** [`docs/forge/18-wave-i-architecture.md`](../18-wave-i-architecture.md) (I1 / Track 1)

## Intent

| Item | Outcome |
|------|---------|
| ADR header Implementation PR | Kept (already noted Wave F hybrid landed) |
| ADR gateway behavior | Retitled from deferred → **landed Wave F hybrid** |
| ADR Accept freeze section | Preserved as **historical**; Accept-time “runtime today” reworded |
| ADR Post-F/G/H section | **Added** — hybrid LANDED; product validate retained |
| Matrix §3 intro + open-questions | “may delegate” → **does** delegate gateway → CE evaluate; response-shape dual paths clarified |
| product validate | **Retained**, **no removal date** |
| GRAPHITE backlog pointer | Skipped (file absent in tree) |

## Files

| Path | Change |
|------|--------|
| `docs/backlog/ADR-configuration-api-reconciliation.md` | Gateway/migration/AC-8/related-docs + Post-implementation status (Waves F–H) |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | Residual §3 intro + open-questions table rows |
| `docs/forge/impl/i1-adr-matrix-hygiene.md` | This status note (`status: done`) |

## Out of scope (explicit)

- Java / TypeScript / runtime edits
- Deleting or calendar-deprecating product-service validate
- Embedding domain validation in gateway
- Flipping `approvals.legacy-local-read-enabled` default
- I2–I4 implementation panes; I5 wave closeout

## Acceptance

- [x] No reader can conclude gateway validate shim is still deferred as **current** runtime
- [x] S2B-11 hybrid documented **LANDED** (Waves F–H)
- [x] product-service validate retention explicit with **no removal date**
- [x] Historical E3 Accept freeze preserved (not rewritten as if Accept shipped code)
- [x] Matrix residual “may delegate in S2B-11” fixed
- [x] **No** Java/TS runtime edits
- [x] This forge status note `status: done`

## Verify

```bash
# Docs-only: no Java compile required
rg -n 'implementation deferred|may delegate|Runtime today' docs/backlog/ || true
# Expect: only historical/Accept-time references (or none claiming current deferred)

rg -n 'LANDED|landed Wave F|no removal date' \
  docs/backlog/ADR-configuration-api-reconciliation.md \
  docs/backlog/CPQ_API_CONTRACT_MATRIX.md

# Confirm no Java/TS touched in this worktree commit
git diff --name-only origin/main...HEAD | rg '\.(java|ts|tsx)$' || echo 'OK: no Java/TS files'
```
