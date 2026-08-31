# Architecture index (steer here)

**Not** the 16-month [MODERNIZATION_PLAN.md](../MODERNIZATION_PLAN.md). That is **target**. This page is **navigation for forge panes**.

## Now

| Item | State |
|------|--------|
| Product waves A–N | **COMPLETE** on `main` |
| Hygiene N′ (H1+H2) | **COMPLETE** |
| Wave O | **FORGING** — [26-wave-o-architecture.md](forge/26-wave-o-architecture.md) (O1 rule-sets **owns shell** ∥ O2 catalog options/attrs ∥ O3 subscription DTO **no FE**) |
| Dual-read | `${APPROVALS_LEGACY_LOCAL_READ:true}` — **HIGH** to flip ([ADR-005](adr/ADR-005-approvals-legacy-dual-read.md) Proposed) |
| GLM-5 | ADR-002 **Accepted**, **runtime** Azure OpenAI `gpt-4o` |
| ADR-006 | **Proposed**; O3 forging — **do not Accept** until on `main` |
| Open GitHub issues | **#238**, **#241** only (**HOLD**) |

## Read in order

1. [AGENT.md](../AGENT.md) — landed vs target (always_applied)  
2. [docs/adr/README.md](adr/README.md) — ADRs  
3. Latest forge plan: [26-wave-o-architecture.md](forge/26-wave-o-architecture.md)  
4. [CPQ_API_CONTRACT_MATRIX.md](backlog/CPQ_API_CONTRACT_MATRIX.md)  
5. [ci-contract.yaml](ci-contract.yaml) — Jenkins primary ([ADR-003](adr/ADR-003-ci-llm-integration.md))  
6. [ADR-004 CSRF](adr/ADR-004-csrf-edge-boundary.md) (stub: [ADR-004-csrf-edge-boundary.md](ADR-004-csrf-edge-boundary.md))  
7. Config API: [backlog/ADR-configuration-api-reconciliation.md](backlog/ADR-configuration-api-reconciliation.md) (not numbered in `docs/adr/`)

## Forge wave files

| File | Role |
|------|------|
| [07-wave-backlog.md](forge/07-wave-backlog.md) | **Historical** wave-1 batch |
| [23-wave-n-architecture.md](forge/23-wave-n-architecture.md) | Wave N (COMPLETE) |
| [25-wave-n-hygiene.md](forge/25-wave-n-hygiene.md) | H1+H2 COMPLETE |
| [26-wave-o-architecture.md](forge/26-wave-o-architecture.md) | Wave O FORGING |
| [27-architecture-artifacts-plan.md](forge/27-architecture-artifacts-plan.md) | This steering-docs job |
| [24-adr-gitnexus-review.md](forge/24-adr-gitnexus-review.md) | ADR vs code |

## Holds (do not forge unless a new wave plan says so)

Activities API; CI #238; Kafka #241; product-validate deletion; gateway domain validation; dual-read flip; WhatsApp #430 undo; full SpEL; Kafka S2B-9; contract-asset **FE**; NgRx; bundles **portal**.
