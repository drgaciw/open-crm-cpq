---
name: jenkins-jcasc-multibranch
description: >-
  Adds Jenkins multibranch or Organization Folder jobs via JCasC for PR CI.
  Requires Context7 Jenkins documentation lookup before proposing YAML.
  Use for branch discovery, webhooks, scan and checkout credentials.
---

# Jenkins JCasC multibranch

## Mandatory pre-step (Context7)

Before editing JCasC:

1. MCP `resolve-library-id` with `libraryName: Jenkins`
2. MCP `query-docs` for: multibranch pipeline, Organization Folder, JCasC job definitions (max 3 queries)

For multi-step design, use **sequential thinking** MCP.

## Reference template

Copy/adapt [infra/jenkins/jenkins/casc/multibranch.yaml.example](infra/jenkins/jenkins/casc/multibranch.yaml.example) into your JCasC bundle or merge into [jenkins.yaml](infra/jenkins/jenkins/casc/jenkins.yaml).

## Credentials

| Purpose | Jenkins credential | Notes |
|---------|-------------------|--------|
| API scan | `scm-token` or GitHub App | Discover branches/PRs |
| Checkout | Same or deploy key | Clone with Jenkinsfile |

Set in [jenkins/casc/jenkins.yaml](infra/jenkins/jenkins/casc/jenkins.yaml) `credentials` block — never commit live secrets.

## Webhooks

- GitHub: repo webhook → `JENKINS_URL/github-webhook/` (requires GitHub plugin).
- Manual: **Scan Repository Now** on multibranch job.

## Seed job conflict

Disable or remove seed `opencrm-cpq-ci` `pipelineJob` when multibranch `opencrm-cpq-multibranch` is primary.

## Verify

```bash
python .cursor/skills/ci-contract-drift-check/scripts/verify-drift.py
```

## Related

- Agent: **`jenkins-multibranch-architect`**
- Playbook: `enable-pr-ci`
