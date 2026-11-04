# ADR-028: Evidence-priced fact ledger for machine-written CRM data

**Status:** Accepted on 2026-11-03 by the project owner ([delegation log](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82)). Proposed on 2026-10-15.
**Date:** 2026-10-15
**Deciders:** Project owner (drg amtd), 2026-11-03.
**Relates to:** [ADR-015](ADR-015-jev-advisory-decision-support.md) (JEV advisory), [ADR-016](ADR-016-identity-authentication-service-trust.md) (actor), [ADR-029](ADR-029-durable-agent-work-queue.md) (work queue), [ADR-031](ADR-031-extensible-field-registry-and-saved-views.md) (agent-filled fields), BC-20-01 audit.
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `ContactFact` model and `docs/plan/contact-intelligence-agent.md` §5 (MIT), at `6d4793d`.
**Does not authorize:** any code, table or endpoint.

---

## Context

The harvest backlog (Track CC1) adds an enrichment agent and agent-filled custom fields. Both write facts about people and organizations: job title, employer, industry, size, links. Today nothing in `crm-service` records where a value came from, and nothing stops a later machine write from replacing a value a seller typed.

Model-reported confidence is not evidence. A model that says "0.9" has not checked anything. ADR-015's lead score is different: it is an advisory score, calibrated by the JEV harness, and it never writes a record field. This ADR is about values that *do* land on records.

The source repo solves this with a ledger: each fact carries a list of evidence items with fixed weights, the score is computed from the weights, and the band decides whether the fact is applied, proposed or only kept.

## Decision

### D-01 — Every machine-written attribute goes through a ledger
`crm-service` owns a `crm_fact` table (tenant-scoped): subject type and id, field key, value, score, band, evidence list, method, status, decided by, decided at, observed at, superseded at. Agents call a `ProposeFact` command. They never update record columns directly.

### D-02 — The service computes the score; the model does not
Each evidence item has a kind from a closed list with a configured weight (for example: exact email match on a profile 0.95; they replied on a synced thread 0.85; signature block states the title 0.8; cited web answer 0.4). The score is `1 − Π(1 − wᵢ)` over independent items, capped at 0.99. Weights are configuration, calibrated by an evaluation set, and versioned.

### D-03 — Bands decide behaviour
| Band | Rule | Behaviour |
|---|---|---|
| VERIFIED | score ≥ 0.85 **and** at least one primary-source item | Applied to the record automatically |
| PROBABLE | 0.55 ≤ score < 0.85 | Stored as a proposal; the field stays as it was |
| POSSIBLE | 0.30 ≤ score < 0.55 | Stored for provenance only; never shown as a value |
| below | score < 0.30 | Not stored |

Contradicting evidence (two sources disagree) holds the fact as unresolved regardless of score. Sensitive fields (email, organization link, anything that re-parents a record) require one band higher.

### D-04 — Humans win
A value whose last writer was a human is never overwritten by an agent. The agent may still propose. Accepting or dismissing a proposal is a human command, audited with the actor (ADR-016).

### D-05 — The approval rule is enforced in the service, not the prompt
Whether a fact may auto-apply is decided by `crm-service` from the computed band and the caller's principal type (human session vs. scheduled agent). A scheduled agent below VERIFIED gets a proposal, never a prompt for approval it cannot answer.

### D-06 — Supersession, not mutation
A newer fact for the same subject and field supersedes the older one (`superseded_at`). History is kept for audit and for the provenance panel.

## Consequences
- Good: every machine value is explainable ("LinkedIn + replied on thread, 0.97"); sellers keep control.
- Good: weights can be tuned without touching agent prompts.
- Cost: a proposals inbox UI is needed; facts add rows per attribute.
- Risk: weights start as guesses. The enrichment agent may not be enabled for a tenant until the evaluation harness reports precision per band.

## Alternatives considered
- **Model-supplied confidence on each write.** Rejected: unverifiable and drifts with model versions.
- **Write directly, keep an audit log.** Rejected: an audit log explains damage after the fact; it does not prevent it.
- **JSON provenance column per record.** Rejected: cannot be queried for an inbox, hard to supersede.
