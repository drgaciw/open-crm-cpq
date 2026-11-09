---
name: jenkins-ai-assist-config
description: >-
  Configures Jenkins AI assist stages: AI_CI_ENABLED, LLM_* gateway, credential
  ai-api-key, AI_CI_BLOCKING policy. Rejects ai-service:8086 endpoints.
  Use when enabling or tuning advisory CI LLM stages.
---

# Jenkins AI assist config

## Environment (Jenkins only)

```bash
AI_CI_ENABLED=true
AI_CI_BLOCKING=false   # default; enable only after false-positive review
LLM_ENDPOINT=https://your-resource.openai.azure.com
LLM_MODEL=gpt-4o
LLM_PROVIDER=azure
```

Dev: set `LLM_API_KEY` in `infra/jenkins/env/jenkins.env` → JCasC seeds credential `ai-api-key`.

Prod: vault → Jenkins credential only.

## Guardrails (ADR-003)

- **Do not** set `LLM_ENDPOINT` to `http://ai-service:8086` — product AgentRouter, not chat API.
- **Do not** add `ai-assist` to [.github/workflows/ci.yml](../../../.github/workflows/ci.yml).

## Reports

Archived under `ci-reports/`:

- `ai-code-review.md`
- `ai-test-suggestions.md`
- `ai-log-analysis.md` (on failure)

## Stages

Via [Jenkinsfile](Jenkinsfile) when `AI_CI_ENABLED=true`:

- `code_review`, `test_suggestions` (main pipeline)
- `log_analysis` (post failure)

## Blocking policy

Consult **`jenkins-ai-gate-curator`** before `AI_CI_BLOCKING=true`.

## Related

- [infra/jenkins/ai-assist/ai_client.py](infra/jenkins/ai-assist/ai_client.py)
- Playbook: `ai-advisory-on`
