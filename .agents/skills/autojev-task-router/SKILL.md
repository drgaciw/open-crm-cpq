---
name: autojev-task-router
description: Route an agent task through AutoJev when the next action is uncertain, risky, broad, weakly verified, or potentially irreversible. Use before choosing whether to proceed quickly, review deeply, split the task, or block.
---

# AutoJev Task Router

Use the `autojev_route_task` MCP tool before committing to an execution path when the task has meaningful ambiguity or risk.

If the tool is unavailable because the AutoJev credential is missing or invalid, direct the user to [https://autojev.ai/settings/apikeys](https://autojev.ai/settings/apikeys). If the user then supplies a key or explicitly asks for local setup, do not echo it: save it in the host's secure credential store, configure AutoJev MCP to retrieve it without putting the raw key in a project or `config.toml`, verify the connection, and report only success or the remaining blocker.

## Procedure

1. Summarize the task in one factual paragraph.
2. Supply only evidence already observed. Do not label assumptions as evidence.
3. List binding constraints, especially irreversibility, security, cost, external effects, and missing verification.
4. Call `autojev_route_task`.
5. Follow the returned route unless it conflicts with a higher-priority instruction or safety policy.
6. Treat `guidance` as preset guidance and probabilities as decision evidence, not authorization.

## Route handling

- `proceed_fast`: continue with normal checks.
- `deep_review`: investigate the uncertain or sensitive boundary first.
- `split_task`: separate the work into independently verifiable changes.
- `block`: stop the risky action and resolve the blocker or ask the user.

If `needs_human_review.noul` is high, obtain human review before an irreversible or externally visible action. Never use AutoJev to widen the user's requested scope.
