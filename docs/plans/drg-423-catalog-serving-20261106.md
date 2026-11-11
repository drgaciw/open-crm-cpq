# DRG-423 catalog serving — contract and validation

Date: 2026-11-06. Lane: product-service only, branch `feat/parallel-story-wave-20261106`.

Sources: [Linear DRG-423](https://linear.app/drgamtd-workspace/issue/DRG-423/bc-01-06-catalog-serving-api-open-host-service), [Notion BC-01-06](https://app.notion.com/p/3e3f7cb4e4df8108b972caf17cbc02a8). Acceptance: typed read-only GetProduct/ListSellableProducts/ResolveBundle; ETag/version headers for client caching; actual GetProduct p95 under 100ms.

## Existing implementation and bounded change

ProductController already returns ProductDto at `/api/v1/products/{id}` and `/sku/{sku}`. ProductBundleController returns ProductBundleDto including active composition items at `/api/v1/product-bundles/{id}`. ProductService has existing nondeleted DTO reads and product-cache invalidation on mutations. Reuse these services and unchanged JSON/Java signatures.

New product-local CatalogReadController adds:

- `GET /api/v1/products/sellable`: paginated existing ProductDto shape; forces ACTIVE status and repository's nondeleted filter, stable id ordering by default. No caller-supplied alternate status or hidden filtering policy.
- `GET /api/v1/product-bundles/{id}/resolve`: explicit read adapter over existing getById composition DTO. Nested composition/three-level expansion is DRG-420; published catalog releases are DRG-421. Neither is invented or claimed here.

New CatalogCacheAdvice applies only to successful 2xx ApiResponse responses from product, bundle and this read controller on GET/HEAD. SHA-256 covers response data and pagination; volatile envelope timestamp is excluded. The validator is weak (`W/"digest"`) because a fresh envelope timestamp can differ while DTO semantics are unchanged. `X-Catalog-Version` repeats the representation digest: it is a cache version, **not** a published catalog release or version-selecting snapshot API.

Use `Cache-Control: private, no-cache` and vary by Authorization/Cookie/Accept in addition to existing Vary values. No shared or freshness-window cache is introduced. Each conditional request still executes the normal authenticated read before Spring's conditional-request handling evaluates weak/strong tags and multiple tags. The existing-resource `*` case is handled explicitly after that successful GET/HEAD read (the local Spring ServletWebRequest limits its wildcard handling to unsafe methods). Missing/deleted resources and other failures cannot become 304. Matching If-None-Match returns 304 without body. Old validators return the updated DTO and new digest. No write or error receives these cache validators. Paginated digest input is normalized to DTO content, sort and PageInfo rather than framework PageImpl internals; the existing response shape stays unchanged.

Product-options/attributes APIs, default evaluation, shared DTOs/libs, gateway/proxy/security/CI and schemas are outside this lane. Existing 36-file November5 work remains unchanged.

## Graph and tests

Fresh local GitNexus query/context inspected ProductController, ProductService, ProductBundleController and repositories. New CatalogReadController/CatalogCacheAdvice/test symbols returned UNKNOWN/not found; text search confirmed absence before creation. No existing method is edited. Root runs graph change analysis on the assembled wave; a new-symbol UNKNOWN result is not a LOW-risk verdict.

Focused actual MVC tests cover existing DTO compatibility, unchanged timestamp-independent validators, weak/strong/list/wildcard If-None-Match, HEAD, mutation invalidation, sellable ACTIVE delegation/pagination, bundle-item changes without parent version changes, and missing resources/writes. Mocked DTO services prove HTTP behavior, not database or latency performance. Final executed command results are recorded by the orchestrator.

Root verification passed all **eight** focused HTTP cases after the wildcard/page fixes (`.gitnexus/drg423-green-final.log`). The assembled backend unit reactor also passed all **53** product-service cases (`.gitnexus/parallel-wave-20261106-backend-unit.log`). Independent static review found no actionable catalog caching defect. The actual GetProduct latency criterion below remains unmeasured.

## Actual latency measurement

GetProduct p95 is **UNMEASURED** until run against an authorized existing deployment with representative data, authentication and load. No service/browser/container is started by this lane. No mocked timing assertion is evidence of the 100ms acceptance criterion.

The standard-library harness lives at `backend/services/product-service/src/test/tools/catalog_latency.py`. It performs sequential full-response GET200 requests, checks validators/envelope, warms up20 requests and measures500 using nearest-rank p95. It fails if any request fails or p95 is not strictly below100ms. Redirects and nonlocal plaintext HTTP are refused. Optional bearer credentials come only from `CATALOG_BEARER_TOKEN`; do not put credentials in arguments, reports or source. The harness does not create data, start services or send writes.

Example, only after the deployment owner authorizes the target and provides an existing product:

```powershell
python backend/services/product-service/src/test/tools/catalog_latency.py --url https://authorized-host/api/v1/products/1 --environment "staging; representative catalog; dedicated client" > catalog-latency-report.json
```

Record actual head, deployment hardware/data volume, cache warm/cold policy, authentication mode and concurrent load beside the report. Sequential warmed HTTP latency is bounded evidence for that environment, not a general production SLO or a substitute for broader load testing. Keep the story open while this criterion or required integration/CI evidence is missing.
