# ADR review against the code graph (GitNexus) — 2026-09-22

**Scope:** `docs/adr/ADR-001…015`, `docs/backlog/ADR-configuration-api-reconciliation.md`, prior reviews (`docs/forge/24-adr-gitnexus-review.md`, `docs/adr/ADR-IMPLEMENTATION-VERIFICATION-2026-09-19.md`), and the unmerged `codex/adr-*` branches.
**Tree:** `feat/e2e-jev-browser-agent` @ `4a29d31` (17 commits ahead of `main` @ `8db2880`).
**GitNexus:** 1.6.9. The index was 11 commits stale (`50077a4`) and was refreshed with `gitnexus analyze --index-only`, so AGENTS.md, CLAUDE.md and the skills were not touched. After the refresh: 2,105 files, 18,740 nodes, 36,846 edges, 927 clusters and 300 flows.
**Method:** I used Cypher usage queries (callers, importers, implementers and zero-row checks), `impact`, and `check --cycles`, then confirmed every graph result with `rg` or a direct file read. I followed the house rule **UNKNOWN ≠ unused**. For example, GitNexus returned 0 callers for `ApprovalRoutingClient` even though `rg` finds 6 call sites through the injected field. Code on `codex/*` branches is **not** indexed.
**Status:** Recommendations only. No ADR, code or YAML was changed in this review.

---

## 1. Executive summary

The reverse-engineered ADR set (007–013) describes the *intended* architecture well. However, several of its "verified" claims fail when you ask **who uses the abstraction**, not only **whether the abstraction exists**. The 2026-09-19 verification mostly ran existence checks. It found that the port, the outbox and the v1 DTOs exist, and Jev scored them against curated evidence. Usage queries show a different picture:

| # | Finding | ADR(s) | Severity |
|---|---------|--------|----------|
| F1 | **No service-to-service trust model.** Every domain `!dev` chain requires `.authenticated()`, but only the gateway configures a mechanism (`oauth2ResourceServer`). Four services (approval-routing, configuration-engine, contract-asset-management, document-generation) have a single chain with no `dev` permit-all. `/internal/**` is `permitAll()` in pricebook and CE. No RestClient forwards credentials. `libs/security` has **1** call site. | 004, 011, 013 (gap: no ADR) | **HIGH** |
| F2 | **Outbox relay can lose events.** `OutboxRelayPublisher` marks rows published right after the *async* `kafkaTemplate.send()` returns. A later broker failure is only logged. It also has no row lease (`SKIP LOCKED`), and it loads the full backlog before capping at 100. | 010 | **HIGH** |
| F3 | **Quote→CRM sync uses Redis Pub/Sub, not the outbox.** `QuoteEventPublisher.publishQuoteAccepted` → `convertAndSend("quote:accepted")` runs inside the accept transaction. That is the dual-write that ADR-010 rejects as Option B, and a fire-and-forget channel. | 010 (undocumented channel) | **HIGH** |
| F4 | **Versioned events are not on the wire.** All 5 `*EventV1` types are referenced only by their unversioned siblings in `libs/common`. No producer or consumer uses them, so the live contract is unversioned. | 010 | MED |
| F5 | **Consumer idempotency is not uniform.** `approval-routing` `DiscountApprovalEventConsumer` creates a new PENDING request on every redelivery. `contract-asset-management` relies on a `UNIQUE(subscription_number)` violation, which makes a redelivered event a poison pill. | 010, 013 | MED-HIGH |
| F6 | **The rules port is bypassed.** No service references `RuleEnginePort` or `HybridRuleEnginePort`. `PricingExecutionService` injects `DroolsRulesEngine` directly and `RuleService` injects `DroolsRuleCompiler` directly. configuration-engine does not depend on `libs/rules-engine` at all. | 012 | MED |
| F7 | **ADR-008 understates the drift.** Four services default to separate databases (`cpq_approval`, `cpq_configuration`, `cpq_contract`, `cpq_document`) with no `currentSchema`, not only document-generation. Only 5 of 10 domain services are in `docker-compose.yml`, and it has **no Kafka broker**. | 008, 010 | MED |
| F8 | **The ADR lineage has forked.** Nine `codex/adr-*` branches (for example `codex/adr-gap-integration`, 24 ahead / 11 behind) independently add ADR-007…014 with different text for 008, 013, 014, VERIFICATION and README. They also lack ADR-015. ADR-015's rationale ("ai-service port collides") is fixed on those branches (`8090`). | all | MED (merge risk) |
| F9 | **An entity leaks at `/api/v1`.** `PricingExecutionController.getAuditTrail` returns `ApiResponse<List<PricingAuditLog>>`, and `PricingAuditLog` is an `@Entity`. The ADR-006 verification grep only checked four named entity types. | 006 | MED |
| F10 | **ADR-011 is out of date and its libs are not narrow.** There are now **4** libs (`jev-decision`). `libs/common` carries `starter-web` and `starter-data-jpa`, so the gateway must exclude both. `libs/security` pulls OAuth2 resource-server/client, jjwt and Azure AD B2C starters into every service. `jev-decision` ships auto-config, a JDBC store and **test doubles in `src/main`** (`FakeJevTransport`, `RecordedJevTransport`). | 011, 015 | MED |
| F11 | **A service calls back through the gateway.** quote-service `ProductServiceClient` POSTs to the **gateway** (`/api/v1/configuration/validate`). Gateway Cookie CSRF applies to every POST and has no server-to-server lane, so this call is expected to be rejected without an XSRF pair. | 004, 009 | MED |
| F12 | **Sync calls have no resilience policy.** Five internal RestClients have no circuit breaker or retry; Resilience4j is only in the gateway and contract-asset. pricing→pricebook sets **no timeouts**. | (gap: no ADR) | MED |

What holds up well: **zero cross-service imports or calls** and **zero libs→services imports** (ADR-011). The gateway has exactly 7 classes with no domain logic (ADR-009). There is no `@ngrx` (ADR-007). GHA is `workflow_dispatch` only (ADR-003). CSRF is disabled on all 9 servlet domain chains (ADR-004). ADR-015's flags default to off with a no-op transport, and its three `jev_decision_log` migrations live in the owning services.

---

## 2. Per-ADR scorecard

| ADR | Decision holds? | Graph / file evidence (HEAD `4a29d31`) | Recommended action |
|-----|-----------------|----------------------------------------|--------------------|
| 001 / 002 | Superseded ✔ | No Anthropic/Zhipu starters. `ai-service` = Azure `gpt-4o` | None. Consider moving both to `docs/adr/superseded/` to cut noise |
| 003 | ✔ | `ci.yml` `workflow_dispatch` only; Jenkins SSOT | Add the ADR-019 fitness checks to the `shared-libraries` stage (see §4) |
| 004 | ✔ for browser CSRF; ✖ silent on S2S | 10 `SecurityConfig` (none in ai-service); `/internal/**` `permitAll` (pricebook, CE); gateway CSRF with no non-browser lane | Amend with a "server-to-server callers" section → defer to new **ADR-016**. Remove the `communication-service` row (the module does not exist) |
| 005 | Proposed ⏸ | Flag `:true`; 7 branches in `ApprovalService` | Add an **expiry/review date**. It has been Proposed since 2026-08-31, so it needs an owner and a J5 evidence target |
| 006 | ✖ one leak | `GET /api/v1/pricing/audit/{quoteExternalId}` → `List<PricingAuditLog>` (`@Entity`, not `BaseEntity`) | Fix with `PricingAuditLogDto`. Replace the name-list grep with an ArchUnit rule: *no `@Entity` type in any `@RestController` return signature* |
| 007 | ✔ | `frontend/package.json` `@ngrx` count = 0 | None |
| 008 | ✖ understated | 4 services default to separate DBs, no `currentSchema`; ai `:8086` = document-generation `:8086`; approval-routing `jev_decision_log` lands in `cpq_approval.public` by default | Merge the `codex/adr008-db-alignment` fix, then update the inventory. Add a **port registry** table |
| 009 | ✔ edge only; ⚠ hairpin | 7 gateway classes; 42 route ids (default + hand-mirrored `dev` profile); quote→gateway POST | Add a rule: *domain services never call the gateway; use the owner's `/internal/v1` or `/api/v1` directly*. Replace the hand-mirrored dev route table with profile-overridden URIs only |
| 010 | ✖ materially inaccurate | See F2–F5. 9 outbox call sites in 6 services; 6 `@KafkaListener` in 4 services + 1 Redis listener | Amend **or supersede with ADR-018** (§3). Correct the "emitters never lose events" claim now |
| 011 | ⚠ out of date | 4 libs; lib fan-in below; `BaseEntity` impact = **CRITICAL**: 250 symbols, 56 direct, 79 processes, 20 modules | Amend: add `jev-decision`, a starter policy, and a lib compatibility rule (§3) |
| 012 | ✖ port bypassed | `hybridPortCallers` = 0 rows (confirmed by `rg`); direct `DroolsRulesEngine` / `DroolsRuleCompiler` injection; `OpenLRulesEngine` 0 refs; CE class is named `ConstraintSolver` while the ADR says "ConstraintSolver remains follow-on" | Pick one: route pricing through `RuleEnginePort` **or** rescope the ADR to "Drools behind a compile/execute facade; the port is aspirational". Rename or clarify `ConstraintSolver` vs S2B-8. Time-box or remove the dormant OpenL dependency |
| 013 | ✔ topology; ⚠ S2S auth | `ApprovalService` → `ApprovalRoutingClient` (6 sites, `rg`); approval-routing chain requires auth with no mechanism and no dev chain | Link to ADR-016. Add an integration test that submits through the real approval-routing security chain |
| 014 | Decision ✔; state differs by branch | HEAD: "backlog". `codex/*`: "implemented behind a disabled flag" | Resolve when the codex lineage merges (§5) |
| 015 | ✔ flag-off | 3 services import `jev-decision` (main: 2/2/3 files); 4 `JevTransport` impls, 2 of them test doubles in `src/main`; per-service `daily-call-budget: 500` | Move test doubles to a `test-jar`/`testFixtures`. State that the budget is per service instance (spend = services × replicas × budget). Refresh the rationale bullet about the port collision after the merge. Add a convergence note → **ADR-021** |
| Config API (backlog) | ✔ | Gateway validate rewrite → CE `/internal/v1/configurations/evaluate` | Give it a number (it is referenced like an ADR everywhere) |

**Lib fan-in (service `src/main` files importing each lib, from GitNexus):**

| lib | ai | approval | CE | contract | crm | docgen | pricebook | pricing | product | quote |
|-----|----|----------|----|----------|-----|--------|-----------|---------|---------|-------|
| common | 1 | 8 | 11 | 6 | 16 | 5 | 7 | 17 | 21 | 21 |
| security | – | – | – | – | – | – | – | – | – | 1 |
| rules-engine | – | – | – | – | – | – | – | 6 | – | – |
| jev-decision | – | 2 | – | – | 2 | – | – | – | – | 3 |

Every service declares a Maven dependency on `security`, but only one file uses it. The lib behaves as a **starter bundle**, not as a published language.

---

## 3. Proposed new ADRs (priority order)

Numbering continues from ADR-015. Each item lists the decision to make, the options, and the evidence that forces it.

> **Update 2026-09-22:** ADR-016 and ADR-018 are now drafted as **Proposed**: [ADR-016](../adr/ADR-016-identity-authentication-service-trust.md) and [ADR-018](../adr/ADR-018-event-delivery-guarantees-idempotency.md). Drafting ADR-018 surfaced a P0, **verified by context tests on 2026-09-22**:
> - The 7 Kafka services depend on plain `spring-kafka`, not `spring-boot-starter-kafka`. On Boot 4 that means no `KafkaTemplate` and no listener container factory.
> - The scanned relay's `@ConditionalOnBean` would skip it even with a `KafkaTemplate`.
> - approval-routing's listener context fails with `No bean named 'kafkaListenerContainerFactory'`.
> - Outbox events are never relayed, and the four consumer services cannot boot.
> See ADR-018 Context 1.

### ADR-016 — Identity, authentication and service-to-service trust  *(HIGH, next)*
**Why now:** ADR-004 declared auth "out of scope", and nothing else covers it. The code implies a model it does not implement (F1):
- Gateway: JWT resource server (Azure AD B2C issuer) in `!dev`.
- Domain services: `!dev` → `.authenticated()` with **no** `oauth2ResourceServer`, basic or custom filter (0 mechanisms in all 9 servlet `SecurityConfig`). approval-routing, configuration-engine, contract-asset-management and document-generation have **no `dev` chain at all**, so their `/api/v1/**` should reject unauthenticated callers in every profile. That includes quote-service's canonical approval write (ADR-013).
- `crm-service` Javadoc promises a `SECURITY_INTERNAL_API_KEY` header check. That is **not implemented** (the only hit is the comment).
- `/internal/**` is `permitAll()` in pricebook and CE, so network placement is the only control.
- No `ClientHttpRequestInterceptor` or `defaultHeader` on any RestClient, so neither user identity nor service identity propagates. `approval-routing` hard-codes `requesterId("system")`.
- `ai-service` has no `SecurityFilterChain`. Its posture comes from Boot defaults plus whatever `libs/security` drags onto the classpath.

**Decide:** (a) **every service validates the user JWT** (token relay from gateway, `oauth2ResourceServer` in `libs/security`), vs (b) **gateway-trust with a service identity** (mTLS in mesh or signed internal token, plus a user-claims header), vs (c) a hybrid: user JWT on `/api/v1/**` and a service token on `/internal/v1/**`.
**Must also settle:** how the `/internal/**` namespace is protected, the `ai-service` posture, the async identity carried in event envelopes (who approved or accepted), and making `SecurityUtils.getCurrentUser()` real or deleting it.
**Fitness check:** an ArchUnit rule that every `SecurityFilterChain` in `!dev` configures an authentication mechanism, plus an integration test for quote→approval-routing under the prod profile.

### ADR-017 — Synchronous inter-service communication  *(HIGH)*
**Evidence:** 5 internal RestClients (quote→pricing, quote→approval-routing, quote→**gateway**, pricing→pricebook, CE→product) plus contract-asset→ERP. Timeouts are 5s/30s where set and **absent** on pricing→pricebook. No circuit breaker or retry outside the gateway and contract-asset. There is one gateway hairpin (F11).
**Decide:** the allowed call graph (a DAG, owned in the ADR), the `/internal/v1` vs `/api/v1` namespace rule, default timeouts, retry and idempotency-key policy, circuit breakers in-service (Resilience4j) or in a mesh, the **no-hairpin-through-gateway** rule, and the error-mapping convention (`BusinessException` 502 codes are already de facto).
**Fitness check:** a GitNexus/ArchUnit inventory of `*Client` classes versus an allow-list, and a test that every `RestClient` bean sets a request factory with timeouts.

### ADR-018 — Event delivery guarantees and consumer idempotency  *(HIGH; amends or supersedes ADR-010)*
**Correct the record first.** ADR-010's "emitters never lose events" does not hold (F2). The versioned published language exists but is unused (F4). A second, unmanaged channel exists (F3).
**Decide:**
1. **Relay semantics.** Mark published only on broker ack: block on the future, or use `send().get(timeout)` per batch, or transactional producer. Lease rows with `FOR UPDATE SKIP LOCKED` (or a partition/owner column) so multiple replicas are safe. Page at the query, not in the loop. Emit lag, age and DLQ metrics.
2. **Consumer contract.** An idempotent-consumer rule with a mechanism (an `inbox`/`processed_event(event_id)` table per schema, or a natural-key upsert). A DLQ and retry topology for poison messages (the contract-asset UNIQUE violation today). Fix `DiscountApprovalEventConsumer` duplicates.
3. **Envelope.** Add `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `causationId`/`correlationId` and actor. Pick one of: **put `*V1` on the wire now**, or delete the v1 classes and declare "unversioned v0 plus additive-only changes" until a registry exists.
4. **Redis Pub/Sub `quote:accepted`.** Retire it (the CRM consumes `cpq.quote.status.accepted` from the outbox) or ratify it explicitly as best-effort with reconciliation. Either way, move the publish **after commit**.
5. **Local topology.** Kafka is absent from `docker-compose.yml`, and the relay is `@ConditionalOnBean(KafkaTemplate)`, so outbox rows accumulate silently where Kafka is excluded. Decide whether dev runs a broker or a "no relay" warning or health indicator is required.

### ADR-019 — Architecture fitness functions and ADR lifecycle  *(MED-HIGH; governance)*
The set is now 15 ADRs plus a numbered backlog ADR, reverse-engineered provenance, hold lists in ARCHITECTURE §17, and branch-specific status text. Codify it:
- **Two status axes:** *Decision* (Proposed/Accepted/Superseded/Deprecated) and *Implementation* (None/Flagged-off/Partial/Landed/Verified@sha). Stop encoding both in one status string ("Accepted (decision); implementation backlog, not runtime behavior").
- **Every Accepted ADR names ≥1 executable check.** Use ArchUnit for Java rules and GitNexus Cypher for graph rules (§4). Run them in the `shared-libraries` CI stage so an ADR regression fails like a lib break.
- **Verification uses usage queries** (callers or importers of the abstraction, negative/zero-row checks), not existence queries. The 2026-09-19 Jev scoring of ADR-012 at "implemented (0.91)" is the cautionary example.
- **Branch-lineage rule:** ADR text changes land on `main` first. Feature or `codex/*` branches only append "Implementation evidence" sections, which prevents the add/add fork (F8).
- **Review dates** on Proposed ADRs (ADR-005) and on holds.
- Normalize line endings (`.gitattributes` `*.md text eol=lf`). The ADR files (and many others) currently show as modified in `git status`, but the diffs are **CRLF-only** (`git diff --ignore-cr-at-eol -- docs/adr` is empty).

### ADR-020 — Observability and correlation  *(MED)*
`ApiResponse` carries `traceId`, but no service includes `micrometer-tracing` or OpenTelemetry (only `spring-boot-starter-actuator` in 10 poms). No trace context crosses RestClients, the outbox or Kafka. **Decide:** a tracing stack (Micrometer Tracing → OTLP), propagation through outbox headers, correlation into `jev_decision_log` and the AI audit log, and SLO metrics for relay lag, consumer lag and circuit-breaker state.

### ADR-021 — AI integration topology: one Jev client, one egress  *(MED)*
Two Jev integrations are converging from opposite directions: `libs/jev-decision` (ADR-015, domain advisory) and the unmerged `ai-service` Jev/OpenRouter adapter (ADR-014). **Decide:**
- `ai-service` delegates to `jev-decision` for transport, validation and budget.
- There is a single egress (the `jev-worker/` facade), with a **global** spend budget instead of a per-instance one.
- One audit or decision-log schema.
- The `ai-service` security posture (see ADR-016) and its port (the `8090` fix is on codex).
- Whether production code may ever call ai-service synchronously from a domain transaction (today: no).

### ADR-022 — Runtime and deployment topology  *(MED-LOW)*
Compose runs gateway plus crm, pricing, pricebook, product and quote only. approval-routing, CE, contract-asset, document-generation and ai-service are absent, and so is Kafka. Gateway `lb://` routes have no registry. **Decide:** the local topology contract (all services? profiles?), a port registry (fixing ai/docgen `:8086`; redis-commander host `:8085` collides with approval-routing), service discovery (static URIs vs Kubernetes DNS), and the AKS target shape that ADR-008 defers to.

### Smaller items
- **Number the configuration-API reconciliation ADR.** It governs gateway ordering and dual-run, and it is cited as an ADR everywhere.
- **Optional ADR — AI-driven UAT (Jev browser agent).** The current branch is `feat/e2e-jev-browser-agent`. Record the determinism rules, which suites may make paid calls, the confidence gating, and how results gate (or don't gate) Jenkins. ADR-014 explicitly excludes this.

---

## 4. Fitness functions: the queries behind this review

Each query ran against the refreshed index. The expected value is what the ADR asserts. Run them with `gitnexus cypher -r <repo-path> "<query>"`. Pass `-r` with a **path**, because about 30 worktrees are registered under the same name.

| ADR | Rule | Cypher (abridged) | Expected | Actual @ `4a29d31` |
|-----|------|-------------------|----------|--------------------|
| 011 | No service→service code deps | `MATCH (a:File)-[:CodeRelation{type:'IMPORTS'}]->(b:File) WHERE a.filePath STARTS WITH 'backend/services/' AND b.filePath STARTS WITH 'backend/services/' AND regexp_extract(a.filePath,'backend/services/([^/]+)/',1) <> regexp_extract(b.filePath,'backend/services/([^/]+)/',1) RETURN a.filePath,b.filePath` | 0 | **0 ✔** (CALLS variant also 0) |
| 011 | Libs never import services | `… a.filePath STARTS WITH 'backend/libs/' AND b.filePath STARTS WITH 'backend/services/' …` | 0 | **0 ✔** |
| 012 | Rule execution goes through the port | `MATCH (a)-[r:CodeRelation]->(b) WHERE b.name IN ['HybridRuleEnginePort','RuleEnginePort'] AND a.filePath STARTS WITH 'backend/services/' RETURN a.filePath` | ≥1 per rule-executing service | **0 ✖** |
| 012 | No direct engine dependency | `MATCH (a:File)-[:CodeRelation{type:'IMPORTS'}]->(b:File) WHERE (b.filePath ENDS WITH 'DroolsRulesEngine.java' OR b.filePath ENDS WITH 'DroolsRuleCompiler.java') AND a.filePath CONTAINS '/src/main/' RETURN a.filePath` | 0 | **2 ✖** (`PricingExecutionService`, `RuleService`; the symbol-level CALLS variant returns 0 because of field injection, so use IMPORTS) |
| 010 | Versioned events on the wire | `MATCH (a)-[r:CodeRelation]->(b) WHERE b.filePath CONTAINS '/event/dto/v1/' AND NOT a.filePath CONTAINS '/event/dto/' RETURN a.filePath` | ≥1 per producer/consumer | **0 ✖** |
| 010 | Only the outbox emits integration events | callers of `convertAndSend` / `publishQuoteAccepted` / `KafkaTemplate.send` outside `OutboxRelayPublisher` | 0 | **1 ✖** (`QuoteService.accept` → Redis) |
| 009 | Gateway has no domain logic | `MATCH (c:Class) WHERE c.filePath STARTS WITH 'backend/services/gateway-service/src/main/' RETURN c.name` | allow-list of 7 | **7 ✔** |
| 006 | No entity at the boundary | (ArchUnit recommended; the graph lacks annotations) `rg` over `@Entity` names in `*Controller` signatures | 0 | **1 ✖** (`PricingAuditLog`) |
| 015 | Only the authorised services use `jev-decision` | importer set of `backend/libs/jev-decision/` | {crm, quote, approval-routing} | **✔** |
| — | Import cycles | `gitnexus check --cycles` | 0 | 2 backend (JPA bidirectional `Contact↔Organization`, `Quote↔QuoteLineItem`) + 1 legacy `cpq_beta` — acceptable; document |

Recommendation: commit these queries as `scripts/ci/adr-fitness.cypher.json` with expected counts, plus a small runner, and call it from the `shared-libraries` stage after `gitnexus analyze --index-only`. Put rules that need annotations (006, security chains, RestClient timeouts) in an ArchUnit test module. There are currently **0** ArchUnit dependencies in the reactor.

---

## 5. Merge plan for the forked ADR lineage (F8)

`main`/HEAD added ADR-007…014 in `66650ae`. The `codex/adr-*` branches (merge base `50077a4`) added the same files independently in `6c50370` and later amended them. Expect **add/add conflicts** on these files:

| File | HEAD vs `codex/adr-gap-integration` |
|------|-------------------------------------|
| ADR-001, 002, 007, 009, 010, 011, 012 | identical ✔ |
| ADR-008 | differs (codex: CE/approval/document/contract → `configuration`/`approval`/`document`/`contract` schemas; pricing owns pricebook migrations) |
| ADR-013 | differs (+ canonical history retention) |
| ADR-014 | differs (codex: "implemented behind a disabled flag") |
| ADR-IMPLEMENTATION-VERIFICATION, README | differ; codex README has no ADR-015 row |
| ADR-015 | HEAD only |

**Suggested order:**
1. Land the codex **code** fixes that remove documented drift: `72bf390` (DB defaults) and `7486f5d` (AI port 8090 / gateway routing).
2. Resolve the ADR text by taking codex for 008/013/014 and HEAD for README (keeping the ADR-015 row). Then update ADR-015's "Context" bullet about the port collision.
3. Only then merge the ADR-014 selection stack. Re-run `gitnexus analyze` and the §4 checks on the merged tip.

---

> **Executed 2026-09-22** on branch `merge/codex-adr-gap-integration`, which merges `codex/adr-gap-integration` into `main` in one pass. It contains every patch from the other eight `codex/*` branches. ADR conflicts were resolved as planned: codex text for 008/013/014/VERIFICATION/REQ/AGENT/ARCHITECTURE, and main's rows for ADR-015–018. There were no Flyway version collisions.

## 6. Prioritised roadmap

| Horizon | Item | Type |
|---------|------|------|
| **Now** | Draft **ADR-016** (auth/S2S trust). Add the quote→approval-routing prod-profile integration test | New ADR + test |
| **Now** | Correct ADR-010 claims (a "Known gaps" section), then draft **ADR-018** (ack-before-mark, leasing, idempotent consumers, v1-on-wire, Redis retirement) | Amend + new ADR |
| **Now** | Fix the ADR-006 leak (`PricingAuditLogDto`) and add the ArchUnit boundary rule | Code + check |
| Next | Merge plan §5. Update ADR-008 inventory and port registry | Docs + merge |
| Next | **ADR-017** (sync calls) — includes removing the quote→gateway hairpin | New ADR |
| Next | Rescope or enforce ADR-012 (port vs direct Drools). Clarify `ConstraintSolver` naming. Decide on OpenL | Amend |
| Next | Amend ADR-011 (4 libs, starter policy, test doubles out of `src/main`, lib compatibility) | Amend |
| Later | **ADR-019** governance + CI fitness runner; **ADR-020** observability; **ADR-021** AI topology; **ADR-022** runtime topology | New ADRs |

> **Progress 2026-09-22 (after the review).**
> - **CI gate (PR #542).**
>   - `backend-unit` now runs the unit profile over the whole reactor: 15 modules, 1,125 tests.
>   - Before this, it built only crm, gateway and the three libraries they depend on.
>   - The stage also fails if any unit test starts a Testcontainer. That check matters because a crm helper test had been quietly starting PostgreSQL and Redis.
>   - Still missing: nothing posts `jenkins/opencrm-cpq-ci`. There is no Jenkins instance and no webhook, and `main` has no commit statuses. Getting a PR check needs a reachable Jenkins or an ADR-003 amendment.
> - **ADR-016 pre-acceptance hardening.**
>   - The approval actor now comes only from the principal. A spoofed `approver` or `requestedBy` gets 403, and a request with no principal gets 401 outside `dev`.
>   - `SecurityUtils` no longer treats anonymous callers as users.
>   - The four services that lacked one now have the `dev` permitAll chain.
>   - F1 itself stays open until ADR-016 steps 1–5 are done. See ADR-016, "Pre-acceptance hardening".
> - **ADR-018 relay hardening (Rollout step 1).**
>   - Rows are marked published only after the broker acknowledges them (D2).
>   - Leasing uses `FOR UPDATE SKIP LOCKED` and takes only the oldest unpublished row of each aggregate (D3). The oldest-row rule was added during implementation because `SKIP LOCKED` alone lets replicas reorder an aggregate's events.
>   - Failed attempts back off, and rows that reach the retry limit are marked `FAILED`.
>   - An `outbox` health indicator and `opencrm.outbox.*` metrics were added.
>   - Additive migrations cover all seven owning schemas.
>   - The relay stays opt-in: each environment still has to decide what to do with its backlog before enabling it.

---

## 7. GitNexus hygiene observed

- **Registry ambiguity.** About 30 registered repos are all named `open-crm-cpq` (superpowers worktrees, `open-crm-cpq-adr-*`, `oms-*`). Unscoped CLI and MCP calls fail with "Multiple repositories indexed". Use `analyze --name <alias>` per worktree, and prune the `prunable` worktrees (`git worktree prune`).
- **Index freshness.** The index was 11 commits stale (predated all of ADR-015). Consider a post-merge hook or CI step running `gitnexus analyze --index-only`. `--index-only` avoids the AGENTS.md/CLAUDE.md/skills churn currently sitting uncommitted in the tree.
- **Process leak.** 14 long-lived `gitnexus mcp` node processes (some from 2026-09-19) were running on the workstation.
- **Resolved later on 2026-09-22.** In `~/.gitnexus/registry.json` (backup `registry.json.bak-2026-09-22T18-44-52-714Z`), the main checkout is now `open-crm-cpq`, which matches CLAUDE.md/AGENTS.md. Each worktree or clone has a unique alias, `open-crm-cpq-wt-<leaf>` or `open-crm-cpq-clone-cpq-crm`, and `open-crm-cpq-evaluation` is unchanged. These aliases differ from each folder name, so GitNexus keeps them when the folder is re-analysed; this was checked by re-analysing `oms-evidence-docs`. 11 leftover `gitnexus mcp` servers started on 09-19/20 by the Codex app-server were stopped, and today's 3 were left running. The `cmano-clone` x3 name collision is still open.
- **Resolution gaps.** Field-injected method calls sometimes lack `CALLS` edges (`ApprovalRoutingClient` showed 0 callers versus 6 in `rg`), and one row had a corrupted `filePath` in a CALLS query. Keep the `rg` confirmation step. Consider `analyze --force` if corruption recurs.

---

## Appendix — evidence pointers

- Relay: `backend/libs/common/src/main/java/com/opencrm/common/event/OutboxRelayPublisher.java` (`send()` then `markAsPublished()`; `@ConditionalOnBean(KafkaTemplate)`), `OutboxEventRepository` (unbounded `findUnpublishedEventsWithRetryLimit`).
- Redis channel: `quote-service/.../event/QuoteEventPublisher.java` (`quote:accepted`), `crm-service/.../event/QuoteAcceptedEventListener.java` (Redis `MessageListener`, SETNX dedupe).
- Consumers: `approval-routing/.../event/DiscountApprovalEventConsumer.java` (no dedupe), `contract-asset-management/.../event/QuoteAcceptedEventConsumer.java` (UNIQUE `subscription_number`, ERP sync inside the listener transaction).
- Rules: `pricing-service/.../execution/PricingExecutionService.java:60` (`DroolsRulesEngine`), `.../rule/RuleService.java` (`DroolsRuleCompiler`).
- Security: `*/config/SecurityConfig.java` (10). Only `gateway-service` calls `oauth2ResourceServer`. `approval-routing` has a single chain. `pricebook`/`configuration-engine` `/internal/**` `permitAll`. `libs/security/pom.xml` starters.
- DB defaults: `approval-routing|configuration-engine|contract-asset-management|document-generation/src/main/resources/application.yml` (`DB_NAME:cpq_*`); `backend/docker-compose.yml` (5 domain services, no Kafka).
- Entity leak: `pricing-service/.../execution/PricingExecutionController.java:61-64`.
- `BaseEntity` impact: `gitnexus impact BaseEntity` → 250 impacted / 56 direct / 79 processes / 20 modules (CRITICAL).
