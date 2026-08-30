---
status: done
id: e4-sentinel-hygiene
---

# e4-sentinel-hygiene

## Summary

Wave E **E4** GitHub hygiene after E1–E3 landed on `main`. No product code in this note.

**Plan:** [`docs/forge/14-wave-e-architecture.md`](../14-wave-e-architecture.md)

## Families closed

| Family | PRs | Rationale |
|--------|-----|-----------|
| CSRF Jules | #402 #404 | Superseded by ADR-004 + E2 (`csrf.disable` on pricing/pricebook); enable-all Cookie CSRF rejected |
| PriceBook N+1 Bolts | #400 #405 #406 #420 #424 #425 #427 #431 #442 #444 #445 #447 #448 #450 #452 #454 #456 | Superseded by forge C6 batch `IN` query |
| WhatsApp SQLi | #414 #416 | Legacy `cpq_beta` / FP or already parameterized — Phase-2 Frappe track |
| CI passwords | #415 | Obsolete vs current `ci.yml` Jenkins cutover |

**Not closed here:** #430 (WhatsApp lookup optimize — different scope); #238 / #241 issues (held).

## Comment templates used

See Wave E architecture §7 / D3-style superseded comments pointing at ADR-004, C6, and tip SHA.
