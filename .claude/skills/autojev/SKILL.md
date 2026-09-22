---
name: autojev
description: Route bounded AI-agent decisions through AutoJev's Jev MCP tools. Use when an agent needs model selection, task routing, tool-call review, research verification, completion review, or another explicit choice, probability, or score.
---

# AutoJev Tool Router

Use AutoJev at a real decision boundary where explicit options and calibrated probabilities are more useful than generated prose. Call only the tool that matches the current decision:

## Ensure AutoJev is connected

The Skill expects an AutoJev MCP server with this endpoint:

```text
https://autojev.ai/mcp
```

Authentication uses an AutoJev access key. Prefer a local credential store or an `AUTOJEV_API_KEY` environment variable; never put the key in a Skill file, project file, committed configuration, log, or tool output. If the six `autojev_*` tools are unavailable, determine whether the MCP connection or its credential is missing before continuing. Do not invent successful tool calls.

### Recover a missing or invalid access key

If the AutoJev MCP server is configured but the host reports that `AUTOJEV_API_KEY` is missing, empty, or invalid, stop the decision workflow and give the user this recovery path:

1. Create an access key at [https://autojev.ai/settings/apikeys](https://autojev.ai/settings/apikeys). The page will ask the user to sign in first when necessary.
2. Save the new key in the Agent host's local credential store, or as `AUTOJEV_API_KEY` in the environment that launches the Agent or MCP client.
3. Restart the Agent host so it reloads the credential.
4. Retry tool discovery or the original AutoJev call.

Use a concise response such as:

> AutoJev is connected, but this Agent host does not have a usable access key. Create one at https://autojev.ai/settings/apikeys, then either save it in the Agent host's local credential store or explicitly send it here and ask me to configure AutoJev on this machine. I will not echo it or write it into the project.

If AutoJev returns HTTP `401`, use the same recovery path because the configured key may have been revoked, copied incorrectly, or created under the wrong environment.

### Store a key supplied during setup

A standalone value matching the AutoJev key shape `sk_` followed by 43 URL-safe characters counts as authorization to configure AutoJev only when either condition is true:

- the user sends it directly after this Skill reported that the AutoJev credential is missing or invalid; or
- the user explicitly asks to save, configure, connect, or use that key for AutoJev.

In that setup context, do not refuse merely because the key appeared in the conversation. Configure it immediately with these safeguards:

1. Do not repeat, quote, summarize, or expose any part of the key in the response, command output, logs, filenames, or configuration.
2. Store it in the operating system's credential store when one is available. For Codex HTTP MCP, prefer a user-level `http_headers_helper` backed by that credential store; this keeps the bearer value out of `config.toml` and project files.
3. If no credential store or secret-management capability is available, use a user-level credential file outside every project, restrict it to the current user (`0600` on Unix-like systems), and configure a local header helper to read it. Do not use a repository `.env` file as the fallback.
4. Configure or update the user-level AutoJev MCP entry for `https://autojev.ai/mcp`. Do not place the raw key in `http_headers`, `config.toml`, a shell startup file, or command history.
5. Validate the credential with AutoJev or refresh MCP tool discovery without printing the credential. Restart the Agent host if its MCP catalog or environment is initialized only at startup.
6. Report only where the credential was stored, whether verification succeeded, and whether a restart is required.

If permissions or sandboxing prevent safe local storage, say so and give the user the exact local action required. Recommend revocation only when the key was posted to a shared/public channel, written to an insecure location, or otherwise disclosed beyond the trusted local setup context; do not automatically declare every key supplied to a trusted local Agent compromised.

## Choose one tool

- `autojev_route_model`: choose among models the current environment can actually invoke when quality, cost, latency, context, or tool support affects the choice.
- `autojev_route_task`: choose `proceed_fast`, `deep_review`, `split_task`, or `block` when an execution path is ambiguous or risky.
- `autojev_guard_tool_call`: evaluate a consequential tool call immediately before execution when it can mutate external state, spend money, expose data, publish, deploy, change permissions, or be difficult to reverse.
- `autojev_check_research`: evaluate whether supplied evidence supports one precise claim before presenting that claim as established.
- `autojev_review_completion`: compare the objective, completed work, verification, and known gaps before reporting a non-trivial task as complete.
- `autojev_decide`: define custom `choice`, `noul`, or `score` questions only when none of the preset tools describes the decision.

## Prepare and apply the decision

Send compact task state and only evidence relevant to the decision. Never include passwords, provider keys, access tokens, or unrelated private data.

Treat the returned choice, probability, or score as decision support. It does not grant new authority, replace deterministic validation, or bypass permissions and confirmations required by the user, platform, or policy. If material facts, arguments, candidates, or scope change, evaluate again.
