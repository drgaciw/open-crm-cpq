# ADR-015: Jev as advisory, shadow-mode decision support in domain services

**Status:** Accepted (decision); code lands flag-off with a no-op transport — not runtime behavior
**Date:** 2026-09-20
**Decision authority:** Project owner instruction, 2026-09-20
**Relates to:** [ADR-014](ADR-014-jev-openrouter-model-selection.md) (model *selection* — unchanged and still held), [ADR-008](ADR-008-postgres-schema-per-service.md), [ADR-011](ADR-011-shared-maven-libs-published-language.md), [ADR-013](ADR-013-quote-bff-approvals-single-writer.md), [ADR-005](ADR-005-approvals-legacy-dual-read.md)

## Context

Several domain operations make, stub, or omit a judgement:

- `Lead.score` exists end to end but is never computed (`LeadMapper` maps it to constant `0` on create and ignores it on update).
- Discount approval tier is a self-described placeholder (`DiscountApprovalEventConsumer.determineApprovalTier`: `> 30 → VP_SALES`, `> 20 → DIRECTOR`, else `MANAGER`).
- Quote approval tier is supplied by the caller in the HTTP body; there is no server-side judgement.
- There is no duplicate detection for organizations or contacts, although `pg_trgm` indexes exist.

Jev (`typesafe/jev`) answers typed questions — `choice`, `noul` (probability a statement is true), `score` (position on an ordered rubric) — over a compact JSON state and returns probabilities rather than prose. The repository already uses it for UAT (`frontend/e2e/lib/decide.mjs`). `ai-service`'s agents return unparsed free text and are disabled by default; they are not typed decisions and no domain service calls them.

ADR-014 holds "Jev/OpenRouter selection as runtime". That decision concerns choosing which LLM executes AI workloads. This ADR concerns a different use — Jev scoring domain facts — and un-holds it narrowly.

Pricing, rate limits, latency and data-retention terms are **not published** for any known Jev upstream (TypeSafe native, OpenRouter Decisions alpha, AutoJev REST, Cloudflare Workers AI). That uncertainty shapes every constraint below.

## Decision

Introduce Jev into domain services as **advisory, shadow-mode decision support**.

1. **Advisory only.** A Jev answer never gates, blocks, routes, or mutates a business decision. The existing rule or the user remains authoritative. Deal `probability` is out of scope because it feeds `weightedAmount` and pipeline reporting.
2. **Off the request path.** Jev calls run after the surrounding transaction commits, on a separate bounded executor, and swallow every failure. Jev being disabled, slow, rate-limited, over budget, or returning an invalid answer yields *no suggestion* — never a user-facing error, never a Kafka redelivery.
3. **Shadow first.** Every evaluation is recorded beside the authoritative outcome (`RULE` or `USER`) with an agreement flag, so answer quality is measured before anyone relies on it.
4. **Shared library, direct use.** A new published-language module `backend/libs/jev-decision` (ADR-011) provides the question/answer types, strict answer validation, the transport abstraction, the after-commit runner, and the decision log store. Domain services use it directly. Routing through `ai-service` is rejected for now: it is disabled by default, its port collides with `document-generation`, it is under concurrent rewrite for ADR-014, and it would add a synchronous domain→AI dependency no ADR covers. *(Update 2026-09-22: the ADR-014 integration merge moved ai-service to port `8090` and landed the selection code behind a disabled flag, so the port and rewrite reasons no longer apply. The decision stands on the other two, and on ADR-021, a reserved ADR on AI integration topology.)*
5. **Pluggable transport, no-op default.** `JevTransport` has a `NoOp` implementation (default) and one HTTP implementation with selectable wire format (`NATIVE`, `AUTOJEV`, `FACADE`). The production upstream is **not chosen by this ADR**. The intended production topology is single egress through an authenticated facade (`wire-format: FACADE`, e.g. the `jev-worker/` Cloudflare Worker) so domain services hold only a facade token; that choice is made at the canary gate.
6. **Data minimisation enforced in code.** Domain code can only build Jev state through a per-use-case feature schema (`JevState.builder(schema)`): unknown keys are rejected, free-text strings are rejected, and the canonical state is hashed. Names, emails, phone numbers, free-text notes, owner and tenant identifiers are not sent. The single exception is the duplicate-hint `names` mode, which is organizations-only, has its own flag, and **requires a recorded data-processing sign-off** before it is enabled anywhere; contacts never send names or emails.
7. **Pinned, explicit evaluator configuration.** Evaluator model/version, rubric version, transport and wire format are configuration, recorded on every decision row. A model identifier ending in `latest` is rejected when enabled. HTTPS is required except for localhost. Request bodies are size-capped and a daily call budget applies.
8. **Per-service decision log.** Each participating service owns a `jev_decision_log` table in its own schema (ADR-008) with identical DDL, written through JDBC by the library (no JPA entity). It stores allow-listed features and their hash, the answer with probabilities, the authoritative outcome and agreement, status, latency and usage. It never stores raw request or response payloads.

### Authorised use cases

| Use case | Service | Authoritative | Jev question |
| --- | --- | --- | --- |
| Lead score | `crm-service` | none (column was always `0`) | `score`, 5-level "likelihood to qualify" |
| Discount approval tier (shadow only) | `approval-routing` | existing threshold rule | `choice` over `MANAGER` / `DIRECTOR` / `VP_SALES` |
| Suggested quote approval tier | `quote-service` | the user's selection | `choice` over `DEAL_DESK` / `SALES_VP` / `CFO` |
| Duplicate organization/contact hint | `crm-service` | the user | `noul` "same real-world entity?" |
| OMS exception investigation (shadow only; 2026-09-21 extension) | `order-management` | existing OMS rules and operator | `choice` over seven investigation-only codes; no recovery command |

Any further use case requires an amendment to this table.

### OMS extension, 2026-09-21

The project owner's instruction to proceed with the [OMS Jev plan](../plans/oms-jev-integration-plan.md)
authorizes disabled exception-triage code and offline validation. Only `oms-exception-triage` is
added here; urgency, escalation use cases, UI exposure and live canary remain gated by evaluation.
The [OMS wave extension](../forge/39-oms-jev-shadow.md) records the implemented boundary.

Reconciliation and operator recovery commit before optional bounded evaluation begins. The adapter
may write only advisory claims, scoped bindings and the service-local decision log. Raw evidence,
identifiers and free-text reasons never enter Jev state. OMS commands, permission checks, ownership,
evidence requirements, quantities, money, retries and Kafka delivery are unchanged. Findings from
other creation paths are observed on reconciliation; this is not complete or durable advisory coverage.

Cross-replica claims deduplicate a subject/version/features/evaluator configuration once. Abandoned
claims are not retried automatically; advisory work is best-effort. Exact local order/exception versions
and a 15-minute expiry are retained for a future read API, which must revalidate current state and scope.
No advisory API or UI is exposed by this delivery. Outcomes remain unknown rather than treating the
existing recovery action as an independently chosen investigation label.

Offline fake-transport results establish integration behavior, not model accuracy. Actual held-out
predictions, expert labels and an aggregate deployment budget are still needed before live rollout.
The shared transport currently records the configured model; verification of the evaluator actually
used by a facade remains a canary prerequisite. No live provider or production flag is enabled here.

## Scope and boundaries

- **ADR-014 is unchanged.** Model selection, OpenRouter execution and policy caching remain backlog and held. Do not recursively select Jev with itself.
- **ADR-005 / ADR-013 are untouched.** The quote-tier suggestion lives in its own package and endpoint. `ApprovalService`, `ApprovalController`, `ApprovalRoutingClient`, `ApprovalRequestRepository` and `approvals.legacy-local-read-enabled` are not edited; reviews verify an empty diff on those paths.
- No new Kafka topics (Kafka S2B-9 and agent Kafka #241 remain held). No Activities API. No NgRx — UI uses signals + `HttpClient`.
- Production code does not import or shell out to `frontend/e2e` tooling; validation semantics are ported to Java.
- Credentials stay server-side and are never committed. Normal CI makes no paid calls: the HTTP transport is only constructed when `opencrm.jev.enabled=true`, `transport=http` **and** a key is present; the only live test is tagged and environment-gated.
- The agent-governance AutoJev skills (`AGENTS.md`) are a separate concern from this ADR.

### Recorded deviations from house style

- `JevProperties` is the backend's first `@ConfigurationProperties` class. It is confined to the library; services keep constructor `@Value("${opencrm.jev.<use-case>.enabled:false}")` plus a guard clause.
- `opencrm.jev.api-key: ${JEV_API_KEY:}` has an empty default, unlike other secrets, so that a flag-off service boots without the variable. An empty key forces the no-op transport.
- `leads.score_probabilities` is `TEXT` holding JSON, not `JSONB`. `crm-service` boots with `ddl-auto=validate`, the value is only read back whole, and a JSON type mapping could not be boot-tested without Docker when it landed. Revisit if it ever needs querying.
- Lead scoring has no event/listener layer: `JevAdvisoryRunner` already defers to after-commit, so `LeadService` gains one collaborator and one call per save.
- The quote-tier suggestion is **on demand**. The UI has no tier picker (Submit posts no tier), so there is nothing to show it beside; a "Suggest approval tier (advisory)" button on DRAFT quotes fetches it. An ordinary page load therefore makes no extra request, and the suggestion never preselects or submits anything.
- The first request for a quote's feature state returns `PENDING` and starts one evaluation; later requests read the stored decision by feature hash. A state already evaluated without a usable answer is not retried, so polling cannot spend the call budget. The user's eventual tier is attached to the logged decision by a scheduled, read-only reconciler; `quote.approvalTier` values outside the `ApprovalTier` vocabulary (it is also written from pricing data) are ignored.

## Rollout and gates

1. **Offline** — synthetic fixtures replayed through a recorded transport; agreement rate, confusion matrix and calibration error reported. No network.
2. **Shadow** — a use-case flag enabled in a local or non-production environment; decisions logged, nothing shown.
3. **Advisory** — suggestion surfaced in the UI with a fixed "Advisory — not a decision" label and its probability.
4. **Canary** — blocked until the chosen upstream's pricing, rate limits and data-retention terms are documented here, the production transport is selected, and a rollback (flag off) exercise is recorded.

Merging the code changes no runtime behavior: every flag defaults to `false` and the default transport is a no-op.

## Consequences

Domain services gain calibrated, measurable suggestions without taking a dependency that can break them. Costs: a new shared module, one table per participating service, a third-party data flow that must stay minimised, and an evaluation burden before any suggestion is trusted. The discount-tier v1 rubric restates the existing thresholds, so its agreement rate is a fidelity baseline rather than evidence of added value; a later intent-based rubric is the informative comparison. In `features-only` mode the duplicate hint is close to a calibrated threshold over similarity features; its main purpose is evidence on whether `names` mode is worth its privacy cost.

A separate, unmerged ADR-014 workstream adds a Jev adapter inside `ai-service`. The two may converge later — that adapter may delegate to this library — but neither depends on the other.

## Acceptance

Complete only when: flag-off tests prove no bean, no call and no behavior change; each use case has unit tests with a fake transport and feature-schema tests proving excluded fields are rejected; the offline evaluation report is reproducible; and the dual-read paths show an empty diff. A successful Jev response is not implementation evidence.

## Sources

Checked 2026-09-20; recheck at the canary gate:

- [Cloudflare Workers AI — `typesafe/jev`](https://developers.cloudflare.com/ai/models/typesafe/jev/) (request/response schema, 32k context; no published pricing or limits)
- [AutoJev integration docs](https://autojev.ai/docs) (REST decisions endpoint; no published pricing, limits or retention)
- Repository reference: `frontend/e2e/lib/decide.mjs` and its tests (validation semantics ported, not imported).
