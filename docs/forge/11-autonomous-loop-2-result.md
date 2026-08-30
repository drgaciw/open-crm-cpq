# Autonomous forge next batch

Batch 0 is on main; B1 FE lifecycle + CPQ Nx scaffold look done. Propose six disjoint Wave A/B leftovers (skip activities API, S2B-11, S2C-12). Proxy/parity/deals/models/staging/ERP-501 touch different surfaces. Pricing execute golden fixtures remain missing on main (WIP in a1-pricing worktree) and can merge in parallel as flex.

- **A2-W1-11** chore(fe): point frontend proxy at API gateway :8080 — Committed proxy targets :8080; nx serve crm can hit /api/v1/deals and /api/v1/products via gateway; README documents :8081 override; CRM_GATEWAY_ENV/parity still say gateway is default.
- **B5-W1-08** docs(crm): refresh parity matrix + encode activities deferral — Parity rows match controllers for convert/search/list envelopes; qualifiedAt noted on LeadDto; activities explicitly Phase-2 deferred with cutover checklist cross-link; no phantom activities gateway route.
- **B1-PR-FE-3** feat(crm-fe): deal pipeline UX — lose dialog, kanban stage actions — Lose uses Material dialog (not window.prompt) on list+detail; kanban exposes Advance/Won/Lost wired to DealService; Vitest covers action wiring; empty error handlers use loading/disable patterns.
- **B2-PR-FE-CPQ-02** feat(fe-cpq): add product, quote, and pricing shared models — DTOs/enums JSDoc-mirrored to Java; catalog vs pricing ProductType kept separate/namespaced; shared-models tests pass; nx build crm green.
- **B3-PR-E** chore(gateway): add bash staging smoke for CRM + ERP 501 — curl GETs for CRM list/pipeline against $GATEWAY_BASE; optional check that /api/v1/erp/costs/bulk returns 501 not 404; documents quote-accept→deal WON manual/idempotency note; Docker-free script.
- **B4-W1-10** test(gateway): ERP 501 HTTP assertions + route-id coverage — CI fails if erp-stub or agent-critical Path predicates removed; ERP reserved paths return HTTP 501 with documented JSON fields (success/service/message); tests remain Docker-free on backend-unit path.

---

## Orchestrator reconciliation (post-run)

| Workflow PR | Status now |
|-------------|------------|
| A2-W1-11 proxy :8080 | **Done** — merged Wave A, on `main`/`origin` |
| Pricing golden fixtures (flex) | **Done** — merged Wave A |
| B5-W1-08 parity docs | In progress — Herdr `wb-parity` |
| B1-PR-FE-3 deal pipeline UX | In progress — `wb-deals` |
| B2-PR-FE-CPQ-02 CPQ models | In progress — `wb-models` |
| B3 staging smoke | In progress — `wb-smoke` |
| B4 ERP 501 tests | In progress — `wb-erp` |
| B6 catalog stub (extra) | In progress — `wb-catalog` |

No duplicate forge panes started for completed Wave A items.
