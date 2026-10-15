# ADR-034: Team agent definitions, triggers and the deployment gate

**Status:** Proposed on 2026-10-15. Not accepted. Phase 4.
**Date:** 2026-10-15
**Deciders:** Project owner (pending).
**Relates to:** [ADR-015](ADR-015-jev-advisory-decision-support.md) (AI advisory), [ADR-016](ADR-016-identity-authentication-service-trust.md) (actors), [ADR-028](ADR-028-evidence-priced-fact-ledger.md), [ADR-029](ADR-029-durable-agent-work-queue.md), BC-20-06 (run ledger). Does not decide the reserved AI-topology ADR.
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `AgentDefinition`, `AgentVersion`, `AgentTrigger`, `AgentRun`, `AgentAction`, `AgentAuditEvent`, at `6d4793d`.
**Does not authorize:** any code, table or UI.

---

## Context

Once the work queue and fact ledger exist, tenants will want their own small agents: "when a deal has had no activity for 14 days, create a follow-up task for the owner". Without a model for this, each request becomes custom code, or worse, an unreviewed prompt with write access.

## Decision

### D-01 — Definition and immutable versions
An agent definition has a name, status and current version. A version is immutable once created: number, instructions, model id, a manifest of allowed tools, a sandbox policy (egress allowed or not), and validation results.

### D-02 — Deployment is the approval
A version runs only after a human with the right permission deploys it. Deployment records approver and time. Editing creates a new version; it never changes the running one.

### D-03 — Triggers
Types: `MANUAL`, `SCHEDULE`, `EVENT` (CRM domain events, BC-12-05), `WEBHOOK` (signed). Triggers are disabled until a deployed version exists. Each firing enqueues a task (ADR-029).

### D-04 — Scope and actions
Each agent declares which entities and fields it may read and write. Actions are typed (create task, propose fact, add note, send notification), idempotent (`idempotency_key`, `request_hash`) and permission-checked against the deployer's permissions at run time. Destructive actions (delete, purge, merge) and outbound email are not in the tool set in v1.

### D-05 — Actor
Every write carries an agent actor plus the deploying human, per ADR-016 event headers.

### D-06 — Ledger
Runs, actions and audit events are recorded (BC-20-06), with tokens and cost, and are visible to tenant admins.

## Consequences
- Good: tenant automation without code, with a clear human approval point.
- Cost: a builder UI and evaluation for each version; Phase 4.

## Alternatives considered
- **Rules engine only (no LLM).** Kept for deterministic automations; this ADR is for work that needs judgement.
- **Let any user run prompts with write tools.** Rejected.
