# Orders workspace

Signals, OnPush components and HttpClient use `/api/v1/orders` and `/api/v1/order-exceptions` through the shell's `apiResponseInterceptor`. Route the exported `ORDERS_ROUTES` at `/orders`.

The UI displays immutable accepted economics separately from fulfillment projections, execution tasks and audit history. Commands carry expectedVersion, reason and a new operation key. A 202 means persisted/pending, never completed. After errors, commands remain disabled until reload. Conversion reuses its idempotency key while references stay unchanged. The browser does not manufacture evidence or optimistic completion.

No verified identity/claims service exists in the current shell. Controls therefore remain visible and backend authorization is mandatory; 401/403 responses give actionable guidance. This feature adds no auth bypass or token parsing. Provide the normal gateway-authenticated session for live use.

Run tests from frontend with `NX_DAEMON=false node node_modules/vitest/vitest.mjs run --config libs/cpq/orders/vite.config.mts --maxWorkers 1`. Unit/component tests use real HttpTestingController plus the shell response interceptor; they do not prove live backend acceptance. The separate Playwright suite under `e2e/uat/suites/orders.mjs` exercises the rendered workspace with deterministic intercepted HTTP fixtures. Run live authenticated integration separately.
