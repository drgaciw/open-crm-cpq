# OMS manual pilot operations

Status: integrated pilot; operational acceptance pending. This runbook is not production acceptance evidence.

## Build and database roles

Build the OMS reactor with `./mvnw -pl services/order-management -am package` from the
backend directory (Windows: `mvnw.cmd`). Build the service Dockerfile with the service directory
as context; the container runs as a nonroot user on port 8091. The gateway defaults OMS to `localhost:8091`;
AI keeps `localhost:8090`. `OMS_HTTP_PORT` overrides the published OMS host port. Run additive migrations in the
deployment's separate migration job as the schema owner, then apply
`backend/services/order-management/db/runtime-grants.sql` as that owner with `ON_ERROR_STOP`.
See `backend/services/order-management/db-role.md` for required role attributes and tested permissions. Configure the deployed
service with `SPRING_FLYWAY_ENABLED=false` and only `OMS_DB_USERNAME`/`OMS_DB_PASSWORD` runtime
credentials. Do not give the service process migration credentials. Reapply grants after migrations.

An explicit Compose pilot overlay is available in `backend/docker-compose.oms.yml`. Validate it
with `docker compose -f docker-compose.yml -f docker-compose.oms.yml --profile oms config --quiet`
from `backend`, with deployment credentials supplied externally. It registers the OMS container and
gateway destination and enables the gateway's browser-login profile. Migrations/grants, registered
HTTPS callback, TLS ingress, broker credential mounts and topic ACLs must exist first. All OMS
intake, source consumers, executor, relay and recovery switches remain off by default. The overlay
has not been launched against production or a real IdP. Keep secrets out of rendered config logs.

## Recovery and monitoring configuration

Keep `OMS_RECOVERY_ENABLED=false` until schema, scope and operator readiness are verified. Enable
with explicit `OMS_RECOVERY_SCOPES`; default batch size is 100 and scan delay is 5000 ms. Capacity
must be measured in the pilot environment. Scrapes use cached bounded database samples; an
uninitialized or stale sample must alert rather than appear healthy. Import `operations-alerts.yml`
and adapt `prometheus-scrape.example.yml` to the deployment's TLS endpoint and rotated service
credential with only `oms:monitor` in the correct scope. Exercise a failed scrape and stale-sample
alert and record receipt by the named on-call owner before intake. These files are configuration
artifacts, not evidence that monitoring has been installed.

OMS Kafka listeners retain failed records at their offsets, including malformed records. Repair
the producer/cause and use an approved, attributable quarantine/replay procedure if necessary;
never advance offsets merely to make lag disappear. Monitor consumer lag and broker retention
headroom externally. The local broker tests prove retained offsets and replay identity, not
production ACLs, replicated broker durability or the required retention horizon.

## Deployment prerequisites

Use a dedicated migration identity and a separate OMS application role. The application must not
own the schema, functions, or tables, and must not have TRUNCATE, trigger-disable, or DDL privileges.
Grant SELECT/INSERT on snapshot_inbox and order_audit; normal mutation rights only on operational
tables. Keep database backups encrypted and restrict snapshot/address access to the same account scope.
Supply issuer, JWKS URI, audience order-management, deployment scope, and trusted service scopes.
The identity provider must issue permissions and account_ids as arrays, and scope_id as a string.
Never infer these claims from browser input or permit wildcard accounts.

Before any OMS consumer is enabled, create authenticated Kafka principals and topic ACLs:
quote may write cpq.oms.quote-accepted.v1; OMS may write oms.activation-requested.v1 and
oms.quote-revision-requested.v1; contract may write oms.activation-acknowledged.v1. Only the relevant
service consumer group may read each topic. Require SASL_SSL and verify denial using an unrelated
principal. Record principal names, test results and configuration checksum; attestation properties
are a startup gate, not a substitute for actual ACL verification.

## Ordered rollout

1. Back up affected schemas and record restore point, offsets, active service versions and cohort.
2. Apply additive schema migrations with all OMS intake and consumers disabled. Every service using
   the common outbox relay needs the lease migration before the new relay binary starts.
3. Stop all old relay processes before enabling new relay claims; old binaries ignore lease columns.
4. Deploy the contract legacy ownership fence to EVERY active consumer. Configure the selected
   customer cohort before quote acceptance can publish OMS ownership. Verify delayed and reordered
   legacy events cannot create subscriptions for that cohort. Drain incompatible consumers.
5. Deploy quote atomic snapshots/ownership/outbox. Enable source consumption first in OMS and contract.
6. Verify snapshot hashes, canonical aliases and per-line terms through shadow reconciliation.
7. Enable OMS executor/ack consumers and relay separately from new intake. Enable selected intake
   only after every release-gate artifact below has a named approver.

## Attributable manual evidence

Use named operator accounts. Preserve original evidence in the approved external repository and
record its durable reference and actual occurrence time. Allocation requires the inventory authority's
allocation reference and expiration; it makes no automated availability promise. Shipment identifies
the allocation consumed. Delivery identifies its shipment. A delayed shipment leaves delivery pending
until the causal facts reconcile; never substitute an operator's intention for proof of delivery.
Service/provisioning needs the pinned task's completion reference. Finance evidence must come from
an authorized external system or authorized finance operator verifying that system's acknowledgment.
Creating a request is not proof of an invoice. Never enter synthetic evidence to clear a hold.

## Recovery rules

Use the exception queue to assign an accountable owner and next action. Unknown outcomes require
inspection of downstream records using the stable operation key before retry. Preserve contradictions
and attach reconciliation evidence; do not delete history or alter accepted economics. Operational
holds may be resolved by additional evidence. Commercial holds require a new approved CPQ revision.
Only an entirely unexecuted order may request P0 replacement; in-flight amendments remain held.

If the broker is unavailable, local command/audit/outbox transactions remain durable. Alert on oldest
unpublished age, exhausted rows and lease expiry. Repair the cause before authorized recovery of an
exhausted row, record reason/actor, and keep the original event UUID. Do not manually mark published.

## Disable and restore drill

Disable NEW intake first. Keep source ownership, contract fencing, existing-order visibility and
reconciliation active. Existing OMS orders must never fall back to legacy subscription creation.
Pause executor dispatch before a restore. Restore into an isolated database/network with outbound
side effects disabled. Reconcile operation keys against downstream authoritative records and broker
retention/offsets. Validate snapshot hashes, audit rows, quantity constraints and pending outbox rows.
Resume only after unknown effects have owners and a reconciliation decision. Record backup age,
restore start/end, acknowledged transactions retained/lost, replay results and actual RPO/RTO.
A local restart test alone does not prove infrastructure-loss RPO or RTO.

## Required release evidence

- Named pilot owner, fulfillment/finance/integration escalation owners and coverage hours.
- Approved snapshot/audit/evidence retention and access policy; backup and deletion treatment.
- Complete requirement-to-test matrix, CI results and live browser workflow results.
- Manual keyboard and screen-reader verification alongside automated accessibility checks.
- Measured representative load: 100000 orders, up to 200 lines/order, 25 UI users and 10 intake/s
  for 30 minutes; p95 read <=500ms and command acknowledgment <=1s, with backpressure evidence.
- Restore and disable-intake drill results; RPO <=15min and RTO <=4h on the agreed environment.
- 99.5% monthly API availability SLO, alert thresholds and owner sign-off.

Until these are recorded, production launch remains closed even if engineering tests pass.

## Browser identity integration

Activate gateway profile oms-browser with OMS_OIDC_ISSUER_URI, OMS_OIDC_CLIENT_ID,
OMS_OIDC_CLIENT_SECRET, OMS_OIDC_REDIRECT_URI and OMS_OIDC_SCOPES. The registered redirect must
exactly match the externally visible HTTPS /login/oauth2/code/oms address. Configure API scopes
that cause the provider to issue an ACCESS token for audience order-management with the trusted
scope/account/permission claims; successful ID-token login alone is insufficient. The OMS service
independently validates access-token signature, issuer, expiry and audience. Never send a client
secret or bearer token to browser storage. Session cookies are secure, HttpOnly, SameSite=Lax;
the CSRF cookie is deliberately script-readable so Angular can send X-XSRF-TOKEN.

Serve the Angular application and gateway under one trusted HTTPS origin. Forward /api, /oauth2,
/login/oauth2 and /oms/logout to gateway, serve /orders from Angular. The development proxy retains
all targets at gateway :8080; local browser login still needs a registered redirect and HTTPS to honor
the secure session cookie. POST /oms/logout requires the CSRF header. Verify real provider login,
expired access-token renewal, logout and denied account claims before enabling the pilot. The local
PKCE/token-relay tests do not replace this deployment check.

For direct bearer-token requests through the gateway, separately configure its resource-server
`AZURE_AD_B2C_ISSUER_URI` and `AZURE_AD_B2C_JWK_SET_URI` for the intended trusted issuer.
`OMS_OIDC_ISSUER_URI` configures the browser OAuth client; it does not override that bearer
decoder. Validate the intended browser-session and direct-bearer paths independently.
