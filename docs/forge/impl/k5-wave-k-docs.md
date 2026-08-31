---
status: done
id: k5-wave-k-docs
---

# k5-wave-k-docs

## Summary

Wave K forge closeout. Dual-read default still `true` (`${APPROVALS_LEGACY_LOCAL_READ:true}`). Product-service validate still retained. No YAML flip. Orchestrator owns `docs/forge/00-orchestrator-status.md` (updated separately by owner).

## Landed

| ID | Result |
|----|--------|
| K1 | Quote `ProductServiceClient` unused JsonNode getters removed; `productRestClient` bean/ctor dropped; `validateConfiguration` untouched; `rg 'body\(JsonNode' backend` empty |
| K2 | FE quote detail status-gated Approve/Reject/Send/Accept + line list/edit/remove; Calculate/Submit kept; no docgen |
| K3 | FE catalog-detail Edit/Save via `ProductService.update` (`ProductUpdateRequest` fields) |
| K4 | FE `@frontend/cpq-approvals` inbox vs quote BFF (`/api/v1/quotes/approvals/**`); route + sidebar; no approval-routing direct; dual-read default untouched |
| K5 | Matrix touch-ups + this closeout |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; docgen FE; NgRx CPQ store
- WhatsApp #430 (closed — do not reopen)

## Matrix / ops

- §6 + §8: dual-read default remains true through Wave K; cutover still `docs/forge/impl/j5-dual-read-readiness.md`
- §9 internal clients: quote `ProductServiceClient` validate-only after K1
- FE portal uses quote BFF pending/approve/reject (K4); agents unchanged
