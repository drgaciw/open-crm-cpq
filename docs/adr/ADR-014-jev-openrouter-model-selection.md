# ADR-014: Jev selects the best qualifying LLM at the lowest cost through OpenRouter

**Status:** Accepted; implemented behind a disabled flag, live qualification and activation pending
**Date:** 2026-09-19
**Decision authority:** Project owner instruction, 2026-09-19
**Supersedes:** [ADR-002](ADR-002-primary-llm-glm-5.md); replaces the fixed-model direction originating in [ADR-001](ADR-001-primary-llm-claude-sonnet-4-6.md)
**Requirements:** [Jev/OpenRouter model selection](../backlog/REQ-jev-openrouter-model-selection.md)

## Context

ADR-002 selected GLM-5 using a static provider and price comparison but was never implemented. The project now requires Jev to determine the best LLM at the lowest cost using OpenRouter's available models and current prices. A fixed model cannot satisfy that requirement as models, prices, and workload performance change.

The checked-in AI service still defaults to Azure OpenAI `gpt-4o`. Backend catalog ingestion, Jev evaluation, deterministic selection, versioned policies and restricted OpenRouter execution now exist under `com.opencrm.ai.selection`, behind an explicit disabled execution flag. The existing frontend Jev UAT client remains separate. Offline tests establish implementation behavior; they do not establish live model quality, cost reconciliation or production readiness. See [implementation evidence](../adr-gap-implementation.md).

## Decision

Use **Jev as the model-selection decision engine**, OpenRouter as the source of candidate models and pricing, and OpenRouter as the execution gateway for selected models once implemented.

1. Define task profiles for actual CPQ workloads, including tool use, structured output, context needs, quality thresholds, latency limits, budgets, and permitted providers/data handling.
2. Discover candidates from OpenRouter's models API and resolve provider endpoint details where needed. Persist timestamped catalog and pricing snapshots. No model, including GLM-5, receives permanent preference.
3. Evaluate candidates on versioned, representative fixtures. Supply Jev with measured outcomes, capability metadata, cost estimates, and task requirements. Model descriptions or Jev confidence alone are insufficient quality evidence.
4. Jev produces typed suitability decisions over an explicit eligible candidate set. Deterministic code validates those decisions and enforces hard constraints. Unsupported model IDs, invalid distributions, insufficient evidence, and low confidence cause abstention.
5. Select the **lowest expected total cost per successful task among candidates meeting the quality and operational requirements**. Break cost ties by measured quality, then latency, then stable model ID. Costs include applicable provider charges, expected retries/fallbacks, and Jev evaluation overhead. A cheaper model that fails requirements is ineligible.
6. Store versioned selection policies per task profile, with the chosen model/provider, qualifying fallback order, evidence, expiry, and rollback version. Use cached policies for normal requests; invoke Jev again when the profile or relevant evidence changes, rather than paying for a full evaluation on every request.
7. Re-evaluate on configured catalog/pricing refreshes, model retirement, performance degradation, or task-profile changes. Runtime provider routing and fallback must remain within the policy's capability, privacy, and cost constraints.
8. Preserve the last known valid policy during transient selection failures while it remains eligible and within its freshness limits. If no qualifying route exists, return an explicit unavailable/escalation result; do not silently relax quality or provider restrictions.

“Best” is workload-specific qualification followed by cost optimization, not a claim that one model is globally best. Report the evaluated candidate coverage and uncertainty alongside every selection.

## Scope and boundaries

- Applies to application AI workloads. Jenkins AI assistance remains governed by ADR-003; this ADR does not change CI routing or browser-agent behavior.
- Jev's evaluator model/version and decision transport are explicit configuration, independently versioned from the selected execution model. Do not recursively select Jev with itself.
- Reuse the existing client's validation patterns where appropriate; production code must not depend on frontend E2E tooling.
- Provider permissions apply to both Jev evaluation data and execution requests. Use synthetic/redacted fixtures for development. Credentials remain server-side.
- Boot/Java/Spring AI compatibility must be demonstrated during implementation; the obsolete ADR-002 starter/version instructions are no longer a backlog requirement.
- Existing holds, including agent Kafka, Activities, NgRx, and approvals cutover, are unchanged.

## Rollout and consequences

Implement catalog discovery, evaluation, selection, and execution behind configuration/feature flags. Validate offline first, then shadow existing behavior, then canary eligible workloads with recorded rollback. Azure remains the configured runtime until that rollout; any Azure rollback route must be explicitly configured and eligible.

The approach adapts to price and quality changes but adds evaluation cost, provider availability dependencies, and operational monitoring. Benchmark budgets, policy caching, hysteresis, and bounded retries limit that overhead. No paid benchmark, provider deployment, or runtime change occurs through this documentation update.

## Acceptance

Implementation is complete only when the linked requirements and acceptance scenarios pass, reproducible evaluation artifacts explain selections, and a canary/rollback exercise demonstrates operational behavior. The existence of this ADR or a successful Jev response is not implementation evidence.

## Sources

External API references checked 2026-09-19; recheck at implementation time:

- [OpenRouter models API](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties)
- [OpenRouter provider routing](https://openrouter.ai/docs/guides/routing/provider-selection)
- [TypeSafe: Jev typed decision model](https://typesafe.ai/blog/introducing-system-one-models-and-jev)
- Repository reference: `frontend/e2e/lib/decide.mjs` and its tests (existing integration, not a production dependency).
