# Forge wave plan

Wave 1 hardens CRM cutover (convert LeadDto navigation, FE page unwrap, then BE Lead/Deal List+PageInfo; quote-accepted idempotency + Redis IT) while running gateway unit + drift CI and CPQ matrix/skill docs in parallel; gateway HTTP/ERP 501 tests and proxy→:8080 unlock FE CPQ Nx scaffold+models. Defer activities, S2B-11, full CPQ UI, and high-blast gateway YAML until later waves.

## Ordered PRs
- **W1-01** [crm-frontend] fix(crm-fe): lead convert uses convertedDealId + LeadDto — After convert, UI navigates to /deals/{convertedDealId}; types/docs match LeadDto; LeadToDealLifecycleTest contract unchanged; Vitest covers happy path
- **W1-02** [crm-frontend] fix(crm-fe): unwrap SpringPage/content for search and org contacts — Search and org-detail contacts populate entity arrays; list/typeahead no longer set page objects into T[] signals; specs cover List+PageInfo and nested Page
- **W1-03** [crm-api-parity] fix(crm-api): unify Lead/Deal search to List+PageInfo — All four resource search endpoints return data as array with pagination sibling; Lead/Deal tests assert $.data array like Contact
- **W1-04** [quote-deal-e2e] fix(crm): quote-accepted idempotency poison on missing deal — Missing deal does not leave crm:quote-accepted:processed:* set; later create+replay applies WON; existing duplicate-skip test still passes
- **W1-05** [gateway-ci] ci: run gateway-service unit tests on fast backend-unit path — bash scripts/ci/run-core.sh backend-unit executes CpqGatewayRoutesTest; remains Docker-free; verify-drift.py still OK
- **W1-06** [gateway-ci] ci: run contract drift verify in toolchain (blocking, non-AI) — run-core.sh toolchain fails on GHA/Jenkins needs drift; ADR-003 unchanged; no ai-assist on GHA
- **W1-07** [cpq-contracts] docs(cpq): refresh matrix for pricebook + align agent skill URLs — Matrix last-verified updated; §9 includes /api/v1/price-books and port 8089; skills stop citing /api/v1/pricing/price-books; extra ERP paths marked out-of-scope or stubbed
- **W1-08** [crm-api-parity] docs(crm): refresh parity matrix + encode activities deferral — Parity rows match controllers; convert/search/list envelopes accurate; qualifiedAt noted on LeadDto; activities deferred with cutover checklist cross-link
- **W1-09** [quote-deal-e2e] test(crm): Redis Testcontainers IT for QuoteAccepted → WON — mvn -pl services/crm-service -Pintegration-tests covers Redis E2E; DealStage.WON asserted; second message no-op; CI backend-integration green with Docker
- **W1-10** [gateway-ci] test(gateway): agent-contract route coverage + ERP 501 HTTP assertions — CI fails if erp-stub or agent-critical Path predicates removed; ERP reserved paths return HTTP 501 with documented JSON fields; route-id constants cover default yml owners
- **W1-11** [crm-frontend] chore(fe): point frontend proxy at API gateway :8080 — nx serve crm can call /api/v1/deals and /api/v1/products through same proxy when gateway is up; README documents :8081 override
- **W1-12** [fe-cpq-ui] chore(fe-cpq): scaffold Nx cpq libs, shared models, and aliases — npx nx show projects lists cpq-* projects; @frontend/cpq-* aliases resolve; product/quote/pricing models exported with separate ProductType enums; nx build crm still green

## Parallel batches
- Batch 1: ["W1-01","W1-04","W1-05","W1-06","W1-07"]
- Batch 2: ["W1-02","W1-09","W1-10","W1-11"]
- Batch 3: ["W1-03","W1-12"]
- Batch 4: ["W1-08"]
