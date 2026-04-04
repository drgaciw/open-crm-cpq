# SKILL.md - Recursive SkillBank

Skills are decoupled into portable `SKILL.md` modules to enable dynamic loading and cross-agent reuse. The system maintains a Recursive SkillBank that distills successful executions into reusable primitives.

| Skill ID | Module Name | Execution Profile | Trigger Context |
|----------|-------------|-------------------|-----------------|
| `SK-01` | `z3-constraint-validator` | Millisecond SAT Solving | Config rule conflicts, dependency chain breaks |
| `SK-02` | `ml-elasticity-predictor` | Lightweight XGBoost | Regional promo forecasting, price sensitivity modeling |
| `SK-03` | `merkle-audit-logger` | Cryptographic Hashing | Quote versioning, compliance snapshots |
| `SK-04` | `spc-asc606-mapper` | Revenue Logic Engine | Bundled vs. itemized structuring, deferred revenue calculation |
| `SK-05` | `erp-reservation-lock` | Idempotent API Call | Stock allocation, backorder routing |
| `SK-06` | `partner-tier-calculator` | Cohort Matching | Volume incentives, channel margin splits |

**Coordination Topologies:** Each workflow is explicitly tagged: `Sequential Chain` (for doc assembly), `Parallel Fan-Out` (for pricing + LCA + ATP checks), `Adversarial Debate` (Legal vs. Pricing on risk trade-offs), or `Blackboard` (shared state for bundle optimization).
