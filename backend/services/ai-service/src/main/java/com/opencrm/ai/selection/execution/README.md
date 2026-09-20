# Gated execution and policy operations

Execution is disabled unless `opencrm.ai.selection.execution-enabled=true`. The existing Azure adapter remains the default. No policy is activated by evaluation or by saving a proposal. This implementation makes no live evaluation calls during CI.

## Configuration

When explicitly enabling execution, set:

| Property | Value |
| --- | --- |
| `opencrm.ai.selection.profile-file` | Absolute path to approved `TaskProfile` JSON |
| `opencrm.ai.selection.catalog-file` | Absolute path to current normalized `CatalogSnapshot` JSON |
| `opencrm.ai.selection.policy-directory` | Durable, access-controlled directory for immutable versions, active pointers and revocations |
| `opencrm.ai.selection.audit-file` | Writable metadata-only JSONL audit path |
| `opencrm.ai.selection.api-key` | Server-side secret injection (environment `OPENCRM_AI_SELECTION_API_KEY`), never commit it |
| `opencrm.ai.selection.completions-url` | Defaults to `https://openrouter.ai/api/v1/chat/completions`; HTTP accepted only on loopback for fixtures |
| `opencrm.ai.selection.timeout-seconds` | Default 30; positive and at most 120 |
| `opencrm.ai.selection.max-attempts` | Default 1; bounded 1–5 eligible routes, no same-route retries |

Exclude the old Azure autoconfiguration when using OpenRouter alone:
`spring.autoconfigure.exclude=org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration`.
Append any other deployment exclusions. Spring AI 0.8.1's chat/embedding `enabled=false` flags alone do not stop its Azure connection bean from requiring a credential. Default Azure and isolated OpenRouter bean paths have application-context tests on Boot 4/Java 25; live Azure transport compatibility remains a rollout check (the SDK reports a Netty version alignment warning).

The string `LlmAdapter` requests the benchmarked `json_object` response format and returns a complete buffered response. Its `stream()` emits one complete value on a blocking scheduler; it does **not** claim provider token streaming. The typed `PolicyExecutor` accepts client-side function tools and JSON response formats; it returns validated tool calls without executing them. Profiles requiring tools reject requests without tool definitions; profiles requiring a response format reject requests without it. Existing string agents cannot silently opt into tool execution. Callers must explicitly pass tool schemas and mark requests carrying prior tool results, which disables retry/fallback to avoid replay.

## Provisioning and rollback

Use a dependency classpath generated for the AI module and run the Java main class:

```text
com.opencrm.ai.selection.policy.PolicyAdminCli save POLICY_DIR profile.json catalog.json proposed-policy.json
com.opencrm.ai.selection.policy.PolicyAdminCli activate POLICY_DIR profile.json catalog.json POLICY_VERSION
com.opencrm.ai.selection.policy.PolicyAdminCli rollback POLICY_DIR profile.json catalog.json ignored
com.opencrm.ai.selection.policy.PolicyAdminCli revoke POLICY_DIR profile.json catalog.json POLICY_VERSION
```

`save` only journals a proposed policy. `activate` and `rollback` revalidate current profile/catalog versions, exact endpoint identity/pricing/capabilities, measured quality/confidence/cost/latency, expiry and persistent revocation. Atomic pointer writes provide immediate process-restart recovery. This is a **single-writer** local repository: serialize CLI administration and runtime policy mutations. Deployments requiring multiple writer instances must provide an external locking/database repository before activation.

`CanaryMonitor.observe` takes a completed batch with policy version, measured quality, total attributable spend, successful tasks and p95 latency. It revokes a policy when quality, latency or cost per success breaches the profile, restoring only an eligible predecessor. Zero successes is a breach. Late observations for superseded policies cannot roll back the current policy. Runtime billed-cost/token violations also revoke and attempt rollback. Neither path replays the triggering request or tool actions.

Runtime reads current profile/catalog and policy for every attempt. Any refresh, retirement, changed restriction, price, expired evidence or policy causes explicit unavailability until a newly qualified policy is activated. During catalog refresh failure, retain the last snapshot only while it and its policy remain valid. Dry-run/shadow evaluation should save proposals without calling `activate`; no automatic activation or arbitrary canary traffic percentage is configured here.

## Bounds and audit

Reservations cover the entire configured input/output token ceilings at conservative endpoint rates plus request fees. Failed/uncertain calls retain their full reservation before any fallback. UTF-8 request bytes plus protocol allowance bound accepted input. Requested maximum prices constrain OpenRouter routing; unknown/missing billed cost and malformed responses fail closed without replay. Actual usage (including completion/reasoning once) and billed cost are recorded. Provider routing uses exact endpoint tags while returned provider identity uses the catalog's explicit display-name mapping. Unexpected model/provider identity is rejected.

Request JSON schemas use a closed subset: object properties/required/additionalProperties, arrays/items/size, primitive types, enum, numeric bounds and string lengths. Unsupported keywords (including `$ref`, composition, regex) are rejected before dispatch. The client validates response/tool arguments against this subset, and never enables remote/server tools. Responses are capped at 2 MiB; redirects are disabled. Cache read/write tokens are validated as nonnegative integers within prompt usage, retained separately in audits without adding them to prompt totals, and rejected if the corresponding price is unknown. Logs contain identifiers, reason codes, attempts, timing, usage and cost—not raw prompts, model outputs, credentials or error bodies.

Production activation still needs approved workload limits/provider permissions, confidence calibration, synthetic live transport validation and actual cost reconciliation. HTTP contract: [OpenRouter provider routing](https://openrouter.ai/docs/guides/routing/provider-selection), [chat completions](https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion).
