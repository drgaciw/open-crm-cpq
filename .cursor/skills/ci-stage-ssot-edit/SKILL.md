---
name: ci-stage-ssot-edit
description: >-
  Coordinates adding or reordering CI stages across scripts/ci/run-core.sh,
  Jenkinsfile, GitHub Actions workflow, and docs/ci-contract.yaml in one PR.
  Use when changing the CI stage graph or SSOT scripts.
---

# CI stage SSOT edit

## Files to change together

| File | Change |
|------|--------|
| `scripts/ci/<stage>.sh` | New stage implementation |
| `scripts/ci/run-core.sh` | New `case` arm |
| `Jenkinsfile` | Stage block + `run-core.sh` call |
| `.github/workflows/ci.yml` | Job + `needs` |
| `docs/ci-contract.yaml` | `scripts_ci.stages`, `jenkins`, `github_actions` |

## Integration `needs` rule

`backend-integration` in GHA must **only** need:

- `shared-libraries`
- `backend-unit`

Not `frontend` or `cpq-beta-python`.

## Jenkins parallel group

Fast checks: Backend Unit, CPQ Beta Python, Frontend — then **sequential** Backend Integration.

## AI assist

Adding Jenkins-only AI stages: update `ai_assist` in contract + `run-ai-stage.sh` — **not** GHA.

## Verify (required)

```bash
python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
bash scripts/ci/run-core.sh toolchain
```

## Related

- Agent: **`ci-ssot-editor`**
- Playbook: `ci-change-safe`
