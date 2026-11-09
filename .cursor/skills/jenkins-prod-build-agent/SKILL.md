---
name: jenkins-prod-build-agent
description: >-
  Hardens Jenkins for production using docker-compose.build-agent.yml: controller
  without docker.sock, inbound agents with labels linux docker maven node22.
  Use for prod CI layout and Testcontainers on agents.
---

# Jenkins prod build agent

## Compose overlay

```bash
cd infra/jenkins
docker compose -f docker-compose.jenkins.yml -f docker-compose.build-agent.yml \
  --env-file env/jenkins.env up -d --build
```

Confirm `JENKINS_NUM_EXECUTORS=0` on controller service.

## Agent provisioning

1. Jenkins → **Manage Jenkins** → **Nodes** → create **build-agent-1**
2. Copy inbound agent secret
3. Mount secret: `/run/secrets/agent_secret` (see [docker-compose.build-agent.yml](../../../infra/jenkins/docker-compose.build-agent.yml))
4. Labels: `linux docker maven node22`
5. Set `JENKINS_AGENT_LABEL` in pipeline env to match

## Pipeline agent

[Jenkinsfile](Jenkinsfile) uses `agent { label "${env.JENKINS_AGENT_LABEL ?: 'any'}" }` — set label in prod.

## Security

- No `docker.sock` on controller
- `LLM_API_KEY` only via Jenkins credential `ai-api-key`, not compose env
- TLS reverse proxy in front of Jenkins

## Verify integration

Run build; confirm **Backend Integration** executes on agent with Docker.

## Related

- Agent: **`jenkins-build-agent-provisioner`**
- Playbook: `prod-hardening`
