# Local Jev + Playwright UAT and TDD fixes

Date: 2026-09-19. Repository: C:/Users/dgorn/cpq-crm/open-crm-cpq. Baseline: baf4a277.

## Results

- Live Jev via OpenRouter (typesafe/jev-1.13) navigated four targets correctly: catalog, organizations, quotes, pricing simulator. Final confidence: 1.00, 0.96, 0.98, 0.99.
- Jev filled the synthetic product form at confidence1.00; its Create decision was0.55, below the unchanged0.90 guard. Earlier it was0.41. Both runs stopped before submission. The model-driven transaction remains incomplete; no threshold bypass.
- Existing deterministic Playwright smoke:21/21 checks passed, including synthetic organization/contact/lead/deal creation. CPQ shell checks are not backend transaction tests.
- Deterministic product regression: UI initially returned500. After fix, created SKU UAT-PW-FIX-1789848478689 (ID1); a fresh browser catalog session and API query confirmed visible persistence, price25, configurable=false, bundle=false. Creation navigates to detail; an initial test expecting catalog after reload was corrected to open a fresh catalog session.
- Simulated browser503 responses on catalog/quotes now show the error without empty CTA or 'No data available'. These are explicit fixture tests, not real backend outage recovery.

## Fixes with red/green evidence

1. ProductCreateRequest: explicit no-arg Jackson creator preserves defaults for omitted optional booleans. Sanitized400 for malformed JSON; explicit null booleans remain invalid. Four HTTP regressions failed before/passed after;11product tests passed including lifecycle/security checks. No global Jackson relaxation.
2. Pricing YAML: existing Azure exclusions/compatibility settings were incorrectly nested under grpc. Moved unchanged block to spring. Isolated YAML binding regression failed before/passed after. Rebuilt pricing starts without runtime Azure overrides.
3. Pricebook: shared outbox publisher lacked repository wiring. Added common.event repository scanning. PostgreSQL-backed context regression failed before/passed after; checks event commit and rollback with caller transaction. Seven existing unit tests also passed.
4. Catalog/Quotes: suppress successful-empty CTA and empty data table on failed initial loads; retain populated rows on later errors. Both regression tests failed before/passed after;24component tests pass. Browser fixture verification passes.

Baseline catalog suite:74tests/10files passed. Existing uncommitted negative-price dialog validation was preserved and is not credited as a new fix.

## Remaining limitations

Pricing customers, rule sets, and price books reject anonymous access with403. Their security requires authentication for /api/v1/**; docs/forge/impl/d2-remove-security-open.md documents removing anonymous bypasses. Local JWT/resource-server integration was not configured. Protected transactions remain unverified; no authentication weakening was made.

Separate customer/admin portals are roadmap items, not landed UI, per AGENT.md and docs/ARCHITECTURE.md.

Product-type direct combobox click was intercepted by the Material label. Real mouse click and normal containing mat-form-field click work, so no application style change was made.

## Runtime and rerun

Frontend http://localhost:4200 proxies gateway8080. CRM8081, pricing8082, product8083, quote8084, pricebook8089 health checks pass. Existing PostgreSQL/Redis reused. Pricing gRPC9095 avoids Prometheus9090. Gateway uses runtime Azure exclusions. Logs: backend/logs/uat-*.

Runner: frontend/e2e/uat-jev-local.mjs. From frontend, supply OPENROUTER_API_KEY securely and run node e2e/uat-jev-local.mjs. Node --env-file can use an ignored external env file; clear stale inherited key first. No secrets copied to this repo. This is a bounded fixed-action navigation/form benchmark, not a general agent. Provider cost unavailable. Synthetic UAT records remain in the local database with UAT-prefixed names.

Evidence, local/untracked: .ci-tmp/jev-local-navigation.json, .ci-tmp/jev-local-uat.json, .ci-tmp/product-browser-regression.json. An early run wrote ../.ci-tmp/jev-local-uat.json; the saved runner now anchors output to its file location.

Existing dirty gateway config/test, product dialog/spec and UAT scripts were preserved. No commits or pushes performed. GitNexus application impact checks reported LOW; graph diff reported low risk including pre-existing edits. Framework/configuration relationships were additionally verified through actual tests. Runner syntax and git diff whitespace checks passed.
