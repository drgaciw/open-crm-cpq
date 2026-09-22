# ADR gap implementation

Implementation branches share baseline `6c50370` (source baseline `50077a4`). The original workspace and its unrelated UAT changes are preserved. Integration is in `codex/adr-gap-integration`.

## Scope and ownership

| Lane | Implementation | Verification |
|---|---|---|
| ADR-008 | Configuration, approval, document, contract schemas in shared `opencrm`; legacy overrides; no migration checksum edits | Disposable PostgreSQL 16: four schemas, outbox isolation, history/checksums, legacy public schemas, refusal to baseline unmanaged data |
| ADR-013 | Canonical quote-scoped history includes completed decisions; BFF uses history endpoint | Routing and quote tests; legacy flag, fallback and deduplication remain |
| Runtime | AI moves to 8090, gateway default/dev routes, launcher and opt-in Compose profile | Actual gateway YAML binding, launcher syntax; live startup evidence recorded separately |
| ADR-014 | Catalog, decimal cost, Jev evaluation, deterministic policy ranking, constrained execution behind a disabled flag | Offline fixtures and mock HTTP; live canary remains a rollout gate |
| ADR-005 | Read-only readiness inventory | Default remains true; no environment cutover |

## Approvals readiness

Run `psql -X -v ON_ERROR_STOP=1 -f backend/scripts/approval-readiness.sql` with the target connection supplied through PostgreSQL environment variables or a service file. The query runs in a read-only transaction, emits an aggregate evidence record, and never flips configuration or includes customer payloads. A database error means evidence is unavailable, not that the count is zero.

Preserve its output with the environment, operator and change-ticket identity. A zero count satisfies only J5-A. Follow the [authoritative J5 checklist](forge/impl/j5-dual-read-readiness.md) for the separately authorized environment soak and rollback drill; the checked-in `${APPROVALS_LEGACY_LOCAL_READ:true}` remains unchanged.

## Database deployment

See [database-schema-alignment.md](database-schema-alignment.md). New defaults do not migrate existing standalone databases. Pin legacy database/schema overrides until the reviewed backup, migration rehearsal, validation and rollback procedure is complete.

## AI replay and rollout

`com.opencrm.ai.selection.SelectionReplayCli` accepts profile, catalog and evidence JSON files, an output report path, an explicit evaluation timestamp, a policy version, and `complete` or `partial`. It performs no network calls and never activates a policy. Partial evaluation cannot publish a winner. Evidence must match the exact profile and catalog versions.

Ranking rejects missing prices, restricted providers, failed capability probes, stale evidence, insufficient successful samples, low quality/confidence, excessive latency and excessive cost. Remaining candidates are ranked by total measured cost per success (including failures and evaluation overhead), quality, latency and stable model/provider key. Policy expiry cannot exceed catalog or benchmark freshness.

Live rollout requires workload-owner quality/latency/confidence/sample thresholds, permitted evaluator/execution providers, a spending cap, cost reconciliation, shadow/canary evidence and a rollback drill. No live result is implied by passing offline tests.

See the [runnable evaluation guide and AC-01–12 evidence](ai-model-selection.md), [refresh integration API](../backend/services/ai-service/src/main/java/com/opencrm/ai/selection/refresh/README.md), and [gateway startup evidence](../backend/services/gateway-service/LOCAL_RUNTIME.md).

## Integration checks and remaining gates

The final seven-service clean package build passed **620 tests across 126 suites, with zero failures, errors or skips** (AI 280, gateway 62, quote 43, approval 24, configuration 81, document 13, contract 18, shared common/security 99). Run from `backend`:

```powershell
.\mvnw.cmd -pl services/ai-service,services/quote-service,services/approval-routing,services/configuration-engine,services/document-generation,services/contract-asset-management,services/gateway-service -am clean package
```

Verification uses disposable PostgreSQL 16 for schema/checksum migration tests and read-only approval readiness failure cases, local HTTP servers for OpenRouter/Jev faults, and real Netty gateways for dispatch. Packaged default/dev gateways also started with private Redis: health passed, default JWT protection returned 401, dev CSRF protection returned 403, rate-limit keys were written, and AI/document routes remained separate. Temporary resources were removed. No browser visual test or existing-database migration is claimed.

Whole AI application startup tests cover default Azure and opt-in OpenRouter against PostgreSQL 16 and Redis, including real Flyway migrations, Hibernate validation, agent/tool-client wiring and HTTP health. Both passed without inference. The OpenRouter case omits Azure credentials and keeps direct legacy clients unavailable. Startup fixes remove accidental common-outbox component scanning while agent Kafka remains held, select the production tool-client constructor explicitly, and isolate the unused Boot 3-era Azure credential bootstrap. Live Azure transport still requires validation.

These results cover the integrated implementation, including the audit mapping/evidence fixes committed after the user acknowledged their HIGH/CRITICAL impact. No application code changed between the successful build and that acknowledgment.

The final packaged evaluation CLI also ran the checked-in example successfully: `VALIDATED_NO_NETWORK_NO_ACTIVATION`, zero calls, zero spend and no selected policy. Compose configuration validation and `git diff --check` passed. Java 25/dependency compatibility warnings remain; live transport compatibility is not inferred from compilation or synthetic tests.

The final pre-commit forced GitNexus index was bound to `C:/Users/dgorn/open-crm-cpq-adr-integration` at commit `0dd579a` plus the reviewed patch. Complete checks reported 47 changed symbols across 17 files and 26 affected flows; comparison with implementation baseline `6c50370` reported 600 symbols across 90 files and 29 affected flows. Neither check was partial or truncated. Both aggregate reports were CRITICAL, principally because the shared audit entity/logger reaches agent, orchestration and listener paths. The user explicitly acknowledged these risks before the final commit; this does not authorize deployment or live model activation.

Public, unauthenticated OpenRouter metadata was inspected without inference calls. Tilde-prefixed model IDs are supported, and detected pagination fails explicitly instead of silently publishing partial discovery. The inspected live catalog omitted request-fee metadata; affected endpoints remain ineligible. A verified tariff source or explicitly reviewed price supplement is still needed before those endpoints can qualify. A missing price is not zero, and this implementation does not invent a supplement.

Candidate replay samples retain measured token/cache usage, reported cost, returned model and provider, objective output and attributed spend. Catalog evidence records source, units, raw responses and normalized prices. Failed calls with unavailable usage retain their conservative reservation. These records support later billing reconciliation but do not establish that live billing has been reconciled.

Selection and execution remain disabled by default. Refresh is an explicit integration API, not an automatically running Spring scheduler; it requires version-bound evaluations and a shared lifetime budget. The file policy repository supports one writer, so multi-instance deployment needs external coordination. Streaming is buffered through the existing text interface, not incremental provider token streaming. Quality/confidence calibration, representative workload fixtures, provider permissions, approved spending, paid synthetic validation and production shadow/canary evidence remain outstanding.

Final audit/evidence fixes are included following the user acknowledgment required by the git-commit skill. They retain replayable usage/provenance, remove raw invocation context from routine shared logs, and correct the shared audit entity's existing JSONB mappings without altering migrations. The entity/logger changes have CRITICAL shared-call impact; the combined evidence change has HIGH graph impact. The original isolated logger review copy remains in `codex/adr-audit-privacy` / `../open-crm-cpq-adr-audit-review` as a superseded review artifact. Tests cover payload-free logs with preserved durable audit data, Hibernate schema validation, and JSONB object round trips.
