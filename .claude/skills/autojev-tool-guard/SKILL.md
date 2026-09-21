---
name: autojev-tool-guard
description: Evaluate a proposed agent tool call before execution when it can mutate external state, spend money, expose data, publish content, change permissions, deploy, or perform another difficult-to-reverse action.
---

# AutoJev Tool Guard

Use `autojev_guard_tool_call` immediately before a consequential tool invocation. Do not use it to bypass an approval already required by the user, platform, or policy.

If the tool is unavailable because the AutoJev credential is missing or invalid, direct the user to [https://autojev.ai/settings/apikeys](https://autojev.ai/settings/apikeys). If the user then supplies a key or explicitly asks for local setup, do not echo it: save it in the host's secure credential store, configure AutoJev MCP to retrieve it without putting the raw key in a project or `config.toml`, verify the connection, and report only success or the remaining blocker.

## Prepare the decision

1. Name the exact tool and action.
2. Summarize material arguments without including secrets.
3. List expected side effects and whether the action is reversible.
4. List safeguards already in place, such as dry runs, scoped targets, backups, or validation.
5. Include applicable user instructions and operational policy.

## Apply the verdict

- `allow`: invoke the tool only within the evaluated scope.
- `confirm`: obtain explicit user confirmation first.
- `review`: inspect arguments, safeguards, or policy, then evaluate again.
- `deny`: do not invoke the tool with the current plan.

If the arguments or target change after evaluation, call the guard again. Never send passwords, provider keys, access tokens, or unnecessary private data to AutoJev.
