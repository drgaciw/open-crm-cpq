# Architecture index (steer here)

**Not** the 16-month [MODERNIZATION_PLAN.md](../MODERNIZATION_PLAN.md). That is **target**. This page is **navigation for forge panes**.

## Now

| Item | State |
|------|--------|
| Product waves A–P | **COMPLETE** on `main` |
| Hygiene N′ (H1+H2) | **COMPLETE** |
| Wave Q | **this closeout** — [29-wave-q-ux-architecture.md](forge/29-wave-q-ux-architecture.md) (Q1 shared-ui a11y ∥ Q2 quotes R1 + list CTA ∥ Q3 catalog+rule-sets ∥ Q4 chrome+approvals+PC) |
| Dual-read | `${APPROVALS_LEGACY_LOCAL_READ:true}` — **HIGH** to flip; hold **J–Q** ([ADR-005](adr/ADR-005-approvals-legacy-dual-read.md) Proposed) |
| GLM-5 | ADR-002 **Accepted**, **runtime** Azure OpenAI `gpt-4o` |
| ADR-006 | **Proposed**; O3 **on `main`** (subscription DTO+list; **no FE**); other entity leaks remain — **do not Accept** |
| N residuals | **R1** landed in Q2; **R2** price-book list-row delete **HELD**; **R3** this closeout |
| Open GitHub issues | **#238**, **#241** only (**HOLD**) |

## Read in order

1. [AGENT.md](../AGENT.md) — landed vs target (always_applied)  
2. [docs/adr/README.md](adr/README.md) — ADRs  
3. Latest forge plan: [29-wave-q-ux-architecture.md](forge/29-wave-q-ux-architecture.md)  
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
| [26-wave-o-architecture.md](forge/26-wave-o-architecture.md) | Wave O COMPLETE |
| [28-wave-p-architecture.md](forge/28-wave-p-architecture.md) | Wave P COMPLETE |
| [29-wave-q-ux-architecture.md](forge/29-wave-q-ux-architecture.md) | Wave Q closeout |
| [27-architecture-artifacts-plan.md](forge/27-architecture-artifacts-plan.md) | Steering-docs job |
| [24-adr-gitnexus-review.md](forge/24-adr-gitnexus-review.md) | ADR vs code |

## Holds (do not forge unless a new wave plan says so)

Activities API; CI #238; Kafka #241; product-validate deletion; gateway domain validation; dual-read flip (hold **J–Q**); WhatsApp #430 undo; full SpEL; Kafka S2B-9; contract-asset / subscriptions **FE**; NgRx; Accept ADR-006; **R2** price-book list-row delete.
