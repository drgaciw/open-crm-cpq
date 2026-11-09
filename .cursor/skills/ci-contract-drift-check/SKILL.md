---
name: ci-contract-drift-check
description: >-
  Verifies GitHub Actions and Jenkins CI parity against docs/ci-contract.yaml.
  Use when a PR touches Jenkinsfile, .github/workflows/ci.yml, scripts/ci/*,
  or docs/ci-contract.yaml, or before declaring CI changes complete.
---

# CI contract drift check

## When to use

- Any change to [Jenkinsfile](../../../Jenkinsfile), [.github/workflows/ci.yml](../../../.github/workflows/ci.yml), [scripts/ci/](../../../scripts/ci/), or [docs/ci-contract.yaml](../../../docs/ci-contract.yaml).
- After `ci-ssot-editor` completes a stage add/remove/reorder.

## Run verification

From repository root:

```bash
python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
```

Optional contract verify commands from [docs/ci-contract.yaml](docs/ci-contract.yaml):

```bash
bash scripts/ci/run-core.sh toolchain
```

## What the script checks

1. GHA `backend-integration` `needs` matches contract (`shared-libraries`, `backend-unit` only).
2. Both platforms invoke `scripts/ci/run-core.sh` for each SSOT stage.
3. GHA does **not** reference `ai-assist` (Jenkins-only per ADR-003).
4. Jenkinsfile contains required stage names (Toolchain through Security Audit).

## On failure

- Fix the mismatch in the same PR — update contract + both platforms together.
- Delegate to **`ci-ssot-editor`** if the fix spans multiple files.

## Related

- Agent: **`ci-drift-guardian`**
- Runtime (Jenkins): `infra/jenkins/ai-assist/drift_sentinel.py` (advisory report)
