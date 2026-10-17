# Environment ladder

**Status: PROPOSED, UNACCEPTED, OWNER DECISION REQUIRED.**

Reading base: `main` at `63e6ec8d` (merge of #663). This file asks the project owner to accept or replace every default below. Merging the pull request records the proposal. It does not accept a default.

**Accepting this authorizes no code, migration, flag, workflow, infrastructure or deploy.** No secret, host, pipeline, feature flag, or Flyway script is created or changed here. No backfill is run.

## How to read this file

| Mark | Meaning |
|---|---|
| `verified` | Opened in this tree at the reading base, or read from the GitHub Actions run named in the CI section. |
| `unverified` | Not opened, not executed, or a claim whose source is outside this repository. |

A proposed default is a suggestion sitting beside its alternatives. It is not a decision. The owner-decision table is the list of forks. Later sections apply those proposed defaults and say when a step stays closed.

## What the repository already is

These facts were read at the base above. They are the ground the proposal stands on.

**There is no deploy workflow for the Spring services or the Angular app.** `verified`:

- `.github/workflows/` contains `ci.yml`, `main-verify.yml`, and `pr-check.yml`. None has a deploy job. `ci.yml` triggers are `workflow_dispatch` and a nightly `schedule` (`17 2 * * *`). It has no `push` or `pull_request` trigger.
- `Jenkinsfile` stages are Checkout, Toolchain, Shared Libraries, Parallel — Fast checks (Backend Unit, CPQ Beta Python, Frontend), Backend Integration, AI Assist, and Security Audit. There is no deploy stage.
- `docs/ci-contract.yaml` maps those same jobs. Its `github_actions`, `github_pr_gate`, `github_main_gate`, and `jenkins` blocks name test and audit stages.
- `cpq_beta/.github/workflows/on_release.yml` runs `npx semantic-release` on a push to that tree's `main` and on `workflow_dispatch`. That publishes a GitHub release for the Frappe tree. It does not deploy contract-asset-management, the gateway, or the CRM UI.
- `docs/adr/` has no ADR-022 file. The ADR-023 catalog row says ADR-019 to ADR-022 stay reserved. A runtime-topology ADR is not in the tree.
- `docs/runbooks/event-operations.md` says the runbook has been exercised by Testcontainers tests, not yet in a shared environment, and leaves page and ticket destinations as placeholders the owner still owes.
- `docs/CRM_GATEWAY_ENV.md` tells an operator to set `FRONTEND_URL` for "Staging / Azure". That sentence is a CORS note. It does not name a host, a workflow, or a database.

**CI that does exist is test CI.** `verified`:

- `.github/workflows/pr-check.yml` runs on pull requests to `main`. Its only job is Backend Unit (PR gate): `verify-drift.py` and `bash scripts/ci/run-core.sh backend-unit`.
- `.github/workflows/main-verify.yml` runs on push to `main`. Its only job is Backend Unit (main), the same Docker-free stage. The file says the full matrix runs nightly from `ci.yml`.
- `scripts/ci/run-core.sh` defines `backend-unit` as the Docker-free Maven unit profile and `backend-integration` as full Maven verify with Testcontainers. `scripts/ci/backend-unit.sh` fails the unit stage if a test starts a Testcontainer.
- `docs/adr/ADR-003-ci-llm-integration.md` section 7 and section 8, and `docs/ci-jenkins-cutover.md`, describe the same split. Section 8 is marked Proposed until the owner merges that amendment. The workflows themselves are on `main`.
- ADR-003 section 8 and the cutover note say Jenkins is the primary CI and that no Jenkins instance was reachable when those paragraphs were written (2026-10-16). This pass did not contact a Jenkins controller. Whether a controller is reachable now is `unverified`.

**Feature-flag defaults in committed config are mixed.** `verified` from the files named. "Off" is not the state of every `enabled` key.

| Key | Committed default | Where read |
|---|---|---|
| `opencrm.pricing.ramp.enabled` | `false` | `quote-service` and `pricing-service` `application.yml` |
| `opencrm.pricing.metrics.acv-version` | `legacy` | `pricing-service` `application.yml` |
| `opencrm.jev.enabled` and the per-use-case Jev flags | `${…:false}` | `quote-service`, `approval-routing`, `crm-service` `application.yml` |
| `opencrm.jev.transport` | `${JEV_TRANSPORT:noop}` | same files |
| `opencrm.ai.selection.execution-enabled` | `${OPENCRM_AI_SELECTION_EXECUTION_ENABLED:false}` | `ai-service` `application.yml` |
| `opencrm.ai.agents.enabled` | `${OPENCRM_AI_AGENTS_ENABLED:false}` | `ai-service` `application.yml` |
| `opencrm.crm.quote-accepted.kafka.enabled` | `${CRM_QUOTE_ACCEPTED_KAFKA_ENABLED:false}` | `crm-service` `application.yml` |
| `opencrm.crm.duplicate-hints.enabled` | `${CRM_DUPLICATE_HINTS_ENABLED:false}` | `crm-service` `application.yml` |
| `opencrm.outbox.relay.enabled` | bean exists only when the property equals `true` | `OutboxRelayAutoConfiguration` (`havingValue = "true"`). Not set in the `application.yml` files searched. `backend/docker-compose.yml` sets `OPENCRM_OUTBOX_RELAY_ENABLED` default `false` |
| `approvals.legacy-local-read-enabled` | `${APPROVALS_LEGACY_LOCAL_READ:true}` | `quote-service` `application.yml`. ADR-005 is **Proposed**; the catalog says do not flip the YAML |
| `opencrm.erp-sync.enabled` | `${ERP_SYNC_ENABLED:true}` | `contract-asset-management` `application.yml` |
| `opencrm.kafka.inbox.enabled` | `true` | `quote-service`, `crm-service`, `contract-asset-management` `application.yml` |
| `opencrm.rules.enabled` | `true` | `pricing-service` `application.yml` |

`OpenCrmSecurityAutoConfiguration.mode` uses `opencrm.security.mode` when set, otherwise `dev` under the `dev` profile and `jwt` otherwise. `isProduction` is true when `opencrm.env` (`OPENCRM_ENV`) is `prod` or `production`, and dev mode then refuses to start. `verified` in that class. No `application.yml` searched selects `opencrm.security.mode: dev` in the main document (the security fitness test states that rule; this pass did not re-read the fitness test body). Profiles found are `dev`, `test`, and, on pricing-service and crm-service, `prod` for log levels. No `staging` profile was found.

Local process ports in committed `application.yml`: gateway `8080`, crm-service `8081`, pricing-service `8082`, product-service `8083`, quote-service `8084`, approval-routing `8085`, document-generation `8086`, contract-asset-management `8087`, configuration-engine `8088`, pricebook-service `8089`, ai-service `8090`. `frontend/proxy.conf.json` targets `http://localhost:8080`. `docs/CRM_GATEWAY_ENV.md` defaults `FRONTEND_URL` to `http://localhost:4200`. These are local defaults. They are not a named ladder environment.

**V1_8 installs functions and does not run them.** `verified` in `backend/services/contract-asset-management/src/main/resources/db/migration/V1_8__contract_header_backfill.sql` and `docs/renewals-amendments/s7-v1-8-header-backfill.md`:

- Flyway creates `contract_header_backfill_count(uuid)`, `contract_header_backfill_apply(uuid)`, `contract_header_at_creation(bigint, uuid)`, and `contract_header_backfill_compensate()`. The header comment says Flyway does not execute the backfill. A null tenant raises and writes nothing.
- `subscriptions` has no `tenant_id`. The count function requires a tenant argument and then counts rows with `NOT is_deleted`, `contract_id IS NULL`, `end_date >= start_date`, and a canonical UUID `customer_id`. The `WHERE` clause has no tenant predicate and no end-date window.
- Apply inserts `contracts` for the passed tenant and links matching subscriptions. Its return value is `ROW_COUNT` after that `UPDATE` (subscriptions linked), not a count of inserted headers. Apply also requires `contract_header_backfill_status(status)` to be non-null. The status function maps only PENDING, ACTIVE, SUSPENDED, EXPIRED, and CANCELLED. Count does not apply that status filter, so the dry-run count can be larger than the apply return.
- Compensate deletes only `created_by = V1_8_HEADER_BACKFILL` after clearing `contract_id` on the subscription whose number is `C-` plus that subscription's uuid. It raises and leaves rows in place when an amendment document, a `prior_contract_id`, or another subscription references a marked row. The function comment says restore from backup in that case. `S7_HEADER_AT_CREATION` rows are not deleted. Flyway has no down migration.
- `REVOKE ALL … FROM PUBLIC` is applied to the functions. Which database role may execute them in a given environment is `unverified`. No environment in this proposal grants that role to a bot.
- `ContractHeaderBackfillIntegrationTest` is tagged `integration` and uses Testcontainers. Its tenant constant is a fixture UUID. That UUID is not an environment tenant.

**Delegated migration rule already on `main`.** `verified` in `docs/renewals-amendments/prereq/delegated-renewals-amendments-decisions-2026-10-17.md` (merged as #663). That record adopts header-only migration, one thin contract per existing subscription, and an initial manifest window of subscription end dates from execution date through plus 90 days, inclusive. It says the existing unfiltered V1_8 function is not a manifest selector and must not be used to claim a subset migration, that a separately delivered selector with exact-manifest validation is required before a phased run, and that no backfill runs in that decision. That record also says it does not enable a flag or deploy. This ladder does not deliver the selector and does not run the backfill.

## Owner-decision table

Every row is open. The proposed default is what the rest of this file uses until the owner replaces it.

| Id | Fork | Proposed default | Alternatives |
|---|---|---|---|
| EL-1 | Adopt a named ladder at all | Three names: dev, staging, production. This document is the proposal. Nothing is provisioned | (1) Keep today's local compose and CI only, and adopt no named environment. (2) Two names, dev and production. (3) Add a fourth, per-pull-request preview. (4) Reject the names and keep the question open |
| EL-2 | Dev purpose | Agents' day-to-day work: branches, local runs, and review | (1) Humans only; agents read and do not run the stack. (2) One throwaway database per agent. (3) One long-lived shared sandbox |
| EL-3 | What is allowed to run in dev | Local unit tests and the existing `backend/docker-compose.yml` on a machine the owner already uses. No deploy workflow. No call to `contract_header_backfill_apply` or `contract_header_backfill_compensate` | (1) Unit tests only, no compose. (2) A future owner-named dev deploy after EL-7. (3) Allow a human to call the functions on a local database under the S7 sequence |
| EL-4 | Staging purpose | A production twin used for integration runs and for the V1_8 migration check (functions installed by Flyway, not executed) | (1) No standing staging; the nightly Testcontainers job is the only integration run. (2) Staging holds a restored production snapshot. (3) Staging holds synthetic data only. (4) Staging is where a human may later apply, after EL-12 and EL-16 |
| EL-5 | What deploys to staging | Nothing, until the owner names a workflow and a target (EL-7) and the first-batch list passes. The thing that would deploy is the owner-named sha, not "whatever is on main" | (1) Never deploy; run Testcontainers only. (2) Deploy `main` on a schedule after the list passes. (3) Deploy only contract-asset-management. (4) Deploy the full compose set the owner lists |
| EL-6 | Production purpose | Behind the owner's flag gates. Touched only when the owner says so, in writing, naming this environment | (1) No production environment until a later decision. (2) Production exists and receives only the sha that already passed staging. (3) Production is the first place real data is migrated, under the same human sequence |
| EL-7 | Deploy mechanism | None. The owner identifies the workflow file and the target before anything runs. This spec does not add a workflow | (1) A later GitHub Actions workflow the owner authors. (2) A later Jenkins stage the owner authors. (3) A human runs an owner-named command and records the sha. (4) Stay with no deploy indefinitely |
| EL-8 | What "flags verified OFF" covers | The owner-named gate set in the readiness list: ramp off, ACV `legacy`, Jev flags false and transport `noop`, AI selection execution and agents false, quote-accepted Kafka false, outbox relay unset or false. Keys that are committed `true` stay as committed until a separate owner decision | (1) Freeze every committed value and change nothing. (2) Also force `opencrm.erp-sync.enabled` false. (3) Also flip `approvals.legacy-local-read-enabled` to false (ADR-005 still Proposed; the catalog says do not flip). (4) Turn a named subset on in staging only |
| EL-9 | Bot access in dev | Agents may edit, review, and run the unit stage and local compose. They do not run apply or compensate. They do not deploy | (1) No bot runtime access; review only. (2) Bots may run `contract_header_backfill_count` and the owner records the number. (3) Bots may deploy to dev after EL-7 |
| EL-10 | Bot access in staging | No bot credentials and no bot session. Humans the owner names may operate it. No bot runs apply or compensate | (1) A bot may read health endpoints. (2) A bot may run the count function and stop. (3) CI's deploy identity, once EL-7 exists, may roll a sha and may not open a database session |
| EL-11 | Bot access in production | Owner-only. No bot runs apply, compensate, or a deploy | (1) A break-glass human list the owner writes down. (2) The same CI identity as staging, still without database execute rights on the backfill functions |
| EL-12 | Where the V1_8 check happens | Staging, and only as a schema check: after a deploy the owner has authorized, confirm Flyway installed the functions and did not call them | (1) The same check on a disposable database before staging. (2) The check on production when the owner names production. (3) Testcontainers evidence only, with no shared database |
| EL-13 | Who may apply or compensate | A human. The owner runs apply. The owner runs compensate. No bot runs either function in any environment | (1) Any human the owner names in the GO line, still never a bot. (2) Two humans, one runs and one watches. (3) Nobody, until a selector wrapper exists (see EL-16) |
| EL-14 | Dry-run count | The owner runs `contract_header_backfill_count` and writes the returned bigint down. The function writes nothing | (1) A bot runs the count and the owner records the number before any GO. (2) Do not call the function; the count is not tenant-scoped (EL-15) |
| EL-15 | Tenant rule | A tenant is chosen only when the owner-named environment has exactly one eligible tenant. Otherwise stop and ask the owner. The count function has no tenant filter, so its number is not proof of one tenant | (1) The owner names the tenant even when several exist, and does not treat the count as a subset. (2) Refuse any apply until a count that filters by tenant exists. (3) Zero candidate tenants is also a stop |
| EL-16 | What "eligible tenant" means, and the 90-day window | The owner writes, in the GO record, the enumeration used. This file supplies no query: CAM migrations searched have no `tenants` table, and `contracts.tenant_id` misses a tenant that has subscriptions and no contract yet. The #663 manifest window is not implemented by V1_8. Proposed operating rule: do not call apply for a phased or windowed run. Calling the current apply writes every status-mapped eligible subscription onto the tenant argument | (1) Authorize a full unwindowed apply later, explicitly, knowing it ignores the 90-day window. (2) Wait for a selector this file does not create. (3) Redefine the window |
| EL-17 | Restore point | Before apply, the owner states the restore point: what was backed up, when, and how a restore would be requested. Compensate is not that restore point. Flyway has no down migration | (1) Platform point-in-time recovery, named the same way. (2) Compensate only, with no backup (the function refuses once other rows reference the headers). (3) Backup plus compensate as a first attempt, restore if compensate raises |
| EL-18 | Reconciliation | After apply, record the dry-run count, the apply return (subscriptions linked), a second count, and the difference. A difference caused by the status filter is explained, not treated as success by silence | (1) Compare only the apply return to the dry-run count. (2) Also count inserted `created_by = V1_8_HEADER_BACKFILL` rows. (3) No after-check |
| EL-19 | GO line | One owner line, written before apply, naming the environment, the tenant, and the dry-run count. Apply without that line does not happen | (1) The same three fields in a ticket the owner owns. (2) Two-person sign-off with the same three fields. (3) GO by environment only, which this proposal rejects |
| EL-20 | First batch versus later batches | Nothing in the first batch runs until the readiness list passes. Later batches are a new owner decision | (1) Dev-only runs are exempt from the list. (2) Docs merges are outside the list (they are not deploys). (3) One list covers every future batch |
| EL-21 | Integration gate on migration and CAM pull requests | Owner governance decision. This spec does not add a gate. Today's pull-request and main-verify workflows stay unit-only until the owner changes them | (1) Require a green `backend-integration` job on pull requests that touch `db/migration` or `contract-asset-management`. (2) Require it only for the deploy sha (already in the readiness list) and leave pull requests unit-only. (3) Require the nightly job green before any merge to `main` |
| EL-22 | Production flag gate versus the production marker | Every production service must set `OPENCRM_ENV=prod` or `production`, in addition to EL-8 and a written owner go. The ADR-016 marker is an unconditional prerequisite that activates identity preflight; it is not a feature-flag system and cannot be omitted by choosing another ladder default | (1) Add further flags later, in a different change. (2) Add stronger deployment checks. Omitting the production marker or treating it as the only gate is not an admissible alternative |

## Dev

**Purpose.** Proposed default EL-2: agents' day-to-day work.

**What deploys there.** Nothing through CI. `verified` absence of a deploy workflow, above. A developer machine may run `backend/docker-compose.yml`. That file is a local stack (Postgres, Redis, an opt-in Kafka profile, and service containers). It is not staging and it is not production. This proposal does not point compose at a shared host.

**CI/CD and flags.** Pull requests get the unit gate in `pr-check.yml`. `main` gets the unit gate in `main-verify.yml`. The nightly `ci.yml` run is GitHub-hosted Testcontainers, not a deploy onto a dev environment. Committed flag defaults are the table above. Dev does not turn flags on. `approvals.legacy-local-read-enabled` stays at its committed default; flipping it is ADR-005 and is not this decision. Jev flags stay false. `AGENT.md` holds enabling a Jev flag beyond local/offline.

**Bot access and owner-only access.** Proposed EL-9. Agents do the day-to-day work. Apply and compensate stay owner-operated, and a bot does not run them. Production stays owner-only even if a dev session is open.

**S7.** Dev is not the V1_8 migration check (EL-12). Flyway on a local database would install the functions if that database migrates; installation is still not apply. A bot does not call apply or compensate against a local database either.

## Staging

**Purpose.** Proposed default EL-4: a production twin for integration runs and for the V1_8 migration check.

"Production twin" here means the same sha and the same committed flag defaults the owner would later consider for production, on data the owner chooses under EL-4's alternatives. No twin exists in the repo. `unverified` that any staging host, database, or DNS name exists. This file does not create one.

**What deploys there.** Proposed EL-5: nothing until EL-7 and the readiness list. The migration check is a look at the schema after that future deploy: the functions exist, and no backfill has been executed. `ContractHeaderBackfillIntegrationTest` is evidence the SQL can be installed in Testcontainers. It is not a staging run. `unverified` whether that test executed inside run 38995521632; the retrieved failed-job log did not mention it.

**CI/CD and flags.** The integration run that counts for a future deploy is `bash scripts/ci/run-core.sh backend-integration` on that sha (the `backend-integration` job in `ci.yml`, which needs `shared-libraries` and `backend-unit`). A green Backend Unit job is a different result. Flags stay at EL-8. Staging does not enable the outbox relay; `docs/runbooks/event-operations.md` says enabling it replays a backlog and is an owner decision per environment.

**Bot access and owner-only access.** Proposed EL-10. Bots do not hold staging credentials. The owner, or a human the owner names, operates the database. No bot runs apply or compensate.

**S7.** Staging is the proposed place for the migration check, not for an unattended apply. The human sequence in the next section is the only proposed way an apply could ever happen, and EL-16 keeps a phased run closed. The #663 record says no backfill runs in that decision.

## Production

**Purpose.** Proposed default EL-6: behind the owner's flag gates, touched only when the owner says so.

**What deploys there.** Nothing in this proposal. A production deploy is a later owner instruction that names production, the sha, and the workflow from EL-7. Every production service must set `OPENCRM_ENV=prod` or `production`, as required by [the identity go-live runbook](../../runbooks/identity-go-live.md). This activates the existing ADR-016 identity preflight, including JWT issuer/audience, client-secret and actuator checks; without the marker those production checks can be skipped. Marker verification is mandatory in the readiness evidence in addition to EL-8 and written approval. This documentation sets no environment variable and authorizes no deploy.

**CI/CD and flags.** Same absence of a deploy workflow. Flag gates are EL-8 and EL-22. The committed `true` defaults (legacy approval read, ERP sync, kafka inbox, pricing rules) are not silently switched off in production by this document. Switching them is its own owner decision.

**Bot access and owner-only access.** Proposed EL-11. Owner-only. No bot runs apply, compensate, or a deploy. No bot receives a production connection string from this spec, because this spec stores no secret.

**S7.** Production is not the default site of the first migration check. If the owner later names production in a GO line, the same human sequence applies, including the exactly-one-tenant rule and the restore point. Until that line exists, production is not touched.

## S7 backfill sequence

This is the proposed operating sequence for a later owner authorization. It is not an authorization. No bot runs apply or compensate. The count function has no tenant filter.

The sequence stays closed while EL-16 stands: V1_8 cannot select the #663 90-day manifest, and using apply to claim a subset is the use that record forbids. A full unwindowed apply is alternative EL-16 (1) and needs its own owner words.

When the owner has authorized a run, the order is:

1. The owner names the environment. That name is one of dev, staging, or production, and it matches a target the owner has already identified under EL-7. A local compose project is not that name unless the owner says it is.
2. The owner states the restore point before any apply (EL-17). Compensate remains available only as the function is written, and the function itself says to restore from backup when it refuses.
3. The owner enumerates eligible tenants with the method written in the GO record (EL-15, EL-16). If the count of eligible tenants is not exactly one, the owner is flagged and the sequence stops. The dry-run function cannot do this enumeration.
4. The owner runs `contract_header_backfill_count` with that tenant and records the bigint (EL-14). A null tenant raises and writes nothing. The number includes every eligible subscription, not one tenant's rows, because the `WHERE` clause does not mention the tenant.
5. The owner writes the GO line naming the environment, the tenant, and that count (EL-19).
6. The owner runs `contract_header_backfill_apply` with the same tenant. A second call links nothing new where headers already match. No bot runs this step.
7. The owner reconciles (EL-18): dry-run count, apply return, second count, and the status-filter gap.
8. Compensate, if it is needed, is the owner running `contract_header_backfill_compensate`. No bot runs it. If it raises, the restore point from step 2 is the fallback the SQL names.

`contract_header_at_creation` is a different function (`created_by = S7_HEADER_AT_CREATION`). The S7 note leaves wiring it open because the accepted event carries no tenant. This ladder does not close that item and does not call that function.

## First-batch deploy readiness

Nothing in the first batch runs until every ruled item below is true. "Runs" means a deploy or a backfill apply. It does not mean merging a docs pull request.

The three ruled items are the readiness list this spec records for CPQ Decision. A search of this repository did not find those three sentences in a file. `unverified` as a repo citation. Notion page [Owner go: all in-flight steps and to-dos](https://app.notion.com/p/3fcf7cb4e4df8156b9b1fa64070a8fad) (fetched 2026-10-17, `verification.state` `unverified`) says deploys for the first green batch follow "CPQ Decision's readiness list" and does not quote the three items. They are recorded here as the proposal's ruled list:

1. The owner identifies a deploy workflow and a target. The repository has no such workflow today (paths in the evidence section).
2. The integration run on the deploy sha is green. That run is the `backend-integration` job, not the unit job in `main-verify.yml` or `pr-check.yml`.
3. Flags are verified OFF, using the owner-named set in EL-8. Verification is a reading of the deployed configuration the owner records. This file does not change a flag.

Proposed additions, each justified from the tree, each still an owner decision:

| Id | Proposed addition | Why it is in the list |
|---|---|---|
| FB-1 | The green integration run's sha equals the sha that would deploy | `ci.yml` checks out the workflow sha. A green run on a different sha is a different result |
| FB-2 | Backend Unit green on that sha is recorded and is not substituted for FB-1 | Run 38995521632 had a green Backend Unit job and a failed integration job |
| FB-3 | The nightly failure below is cleared or the owner explicitly waives it for that sha | A failed integration job on `main` means the last scheduled full matrix did not pass |
| FB-4 | Flyway installing V1_8 is checked separately from apply. Apply stays off | The migration comment says Flyway does not execute the backfill |
| FB-5 | The GO line and the exactly-one-tenant rule are satisfied before any apply | Count has no tenant filter; #663 forbids using the unfiltered function as a subset selector |
| FB-6 | The restore point is stated before apply | Compensate refuses and tells the caller to restore from backup; there is no down migration |
| FB-7 | `approvals.legacy-local-read-enabled` is left at its committed default unless ADR-005 is accepted in its own change | The catalog says do not flip that YAML |
| FB-8 | Outbox relay stays off unless the owner follows `docs/runbooks/event-operations.md` for that environment | Enabling the relay replays a backlog |
| FB-9 | No bot identity is on the apply or compensate grant | `REVOKE ALL … FROM PUBLIC` is in V1_8; the role that can execute is an owner choice this file does not make |
| FB-10 | Jenkins reachability is re-checked by the owner before anyone treats Jenkins as the deploy path | ADR-003 section 8 says it was unreachable; this pass did not re-check. `unverified` now |

## Known gap: unit gates, and a failed nightly integration run

`main` verify and the pull-request gate are unit-only. `verified` in `.github/workflows/main-verify.yml`, `.github/workflows/pr-check.yml`, and `docs/ci-contract.yaml` (`github_main_gate`, `github_pr_gate`). Both run `scripts/ci/run-core.sh backend-unit`. Neither runs `backend-integration`.

The nightly integration run **38995521632** failed. `verified` via `gh run view` against `drgaciw/open-crm-cpq`:

| Field | Value |
|---|---|
| URL | https://github.com/drgaciw/open-crm-cpq/actions/runs/38995521632 |
| Workflow | CI (manual fallback) (`.github/workflows/ci.yml`) |
| Event | `schedule` |
| Branch | `main` |
| Sha | `8bf4ce324a315a8b578664cac65ab93b9769e8bc` |
| Created | 2026-10-17T02:35:26Z |
| Conclusion | failure |
| Failed job | Backend Integration (Testcontainers) |
| Failed test | `com.opencrm.pricebook.PriceBookOutboxWiringTest` |
| Error shown | `IllegalStateException`: multiple `@SpringBootConfiguration` classes (`PriceBookEntryRepositoryIntegrationTest$RepositoryConfiguration` and `PriceBookServiceApplication`) |

In that same run, Shared Libraries, Backend Unit, CPQ Beta, Frontend, and Dependency Security Audit completed with conclusion success. A search of the retrieved failed-job log for `ContractHeaderBackfillIntegrationTest` returned no hits. This spec does not claim the CAM module passed or failed inside that run.

An integration gate for migration and CAM pull requests is an owner governance decision (EL-21). This spec does not add one. The readiness list still requires a green integration run on a future deploy sha, which is a deploy rule, not a pull-request rule.

## Unverified

- Any real dev, staging, or production host, account, cluster, or database. None is named in the files read.
- That a Jenkins controller is reachable on the day this file was written.
- The CPQ Decision readiness sentences as a repository artifact. They are recorded above as the ruled list for this proposal.
- Notion `verification.state` on the owner-go page cited above.
- Which database role can execute the V1_8 functions after `REVOKE ALL … FROM PUBLIC`.
- A query that lists eligible tenants. This file refuses to invent one.
- Whether `ContractHeaderBackfillIntegrationTest` ran in workflow run 38995521632.
- Live flag values on any machine. Committed YAML was read. Deployed configuration was not, because there is no deploy to read.
