---
status: done
id: b2-parity-docs
---

# b2-parity-docs

## Summary

Wave B5 / W1-08: Refresh `docs/CRM_API_PARITY.md` for Batch 0 List+PageInfo search reality and encode activities API deferral (docs only). Cross-link cutover checklist. No Java/FE app code.

## Files

| Path | Change |
|------|--------|
| `docs/CRM_API_PARITY.md` | B0-1 List+PageInfo contract table; convert/`qualifiedAt` notes; **Activities (deferred)** section; gateway freeze + legacy `cpq_beta` link |
| `docs/CRM_CUTOVER_CHECKLIST.md` | Pre-cutover activities deferral + PageInfo smoke item; Phase 2 activities decision cross-link |
| `docs/forge/impl/b2-parity-docs.md` | This status note |

## Acceptance (W1-08)

- [x] Parity rows match controllers (35 routes; no activities row)
- [x] Convert/search/list envelopes accurate (`data: T[]` + `PageInfo`; FE `.content`)
- [x] `qualifiedAt` noted as BE-present / FE-absent drift
- [x] Activities deferred with cutover checklist + gateway env cross-links
- [x] Docs only — no `backend/` or `frontend/` app edits

## Notes

- GitNexus MCP unavailable; verified callers via `rg` on controllers, gateway YAML, FE services, and `cpq_beta/crm/api/activities.py`.
- Batch 0 search unify already landed (`LeadController`/`DealController` return `getContent()` + `PageInfo`); this wave documents that reality.
