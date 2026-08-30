---
status: done
id: g2-constraint-solver
---

# g2-constraint-solver

## Summary

Wired session `ConstraintSolver` to shared `ProductRuleEvaluationCore` + sync `ProductConfigurationRuleClient` (S2B-8). Removed hardcoded `color` / `option_a` / `premium_feature` placeholder. Session selections `Map<String,String>` values that parse as `Long` (plus numeric `session.productId`) become selected product IDs. Soft RECOMMENDS surface on `validationErrors` without blocking `valid` / complete; hard REQUIRES/EXCLUDES/VALIDATES still fail closed.

## GitNexus

| Symbol | Risk | Note |
|--------|------|------|
| `ConstraintSolver` | **LOW** graph (1 direct: `ConfigurationSessionService`) | **MED** product behavior (session validate/complete semantics) |
| `ConfigurationSessionService` | **UNKNOWN** (0 graph) | Confirmed callers via `rg`: `ConfigurationSessionController` |
| Index | 21 commits behind at edit | Text confirmation used; did not treat UNKNOWN as safe |

## Changes

| Area | Detail |
|------|--------|
| `ConstraintSolver` | Injects `ProductConfigurationRuleClient`; `evaluate` → core; `validate` returns hard messages only; `selectedProductIds` mapping; `isCompatible` uses hard validity |
| `ConfigurationSessionService` | Uses `evaluate`; applies soft warnings to `validationErrors` while `valid=true` |
| Tests | `ConstraintSolverTest`; `ConfigurationSessionServiceTest` soft/hard cases |
| Docs | this file |

## Acceptance

- [x] Placeholder color/option_a/premium_feature gone
- [x] Session validate uses product rules via shared core
- [x] Soft warnings surface on session without blocking unless hard
- [x] Complete still requires hard-valid (`session.valid`)
- [x] Selection mapping: numeric values + numeric productId → selected IDs
