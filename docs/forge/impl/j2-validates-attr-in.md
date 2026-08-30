---
status: done
id: j2-validates-attr-in
---

# j2-validates-attr-in

## Summary

Wave J2: VALIDATES `conditionExpression` dialect adds `attr:<key> in:<v1>,<v2>,...`
in `ProductRuleEvaluationCore`. Comma-separated values are trimmed and compared
case-sensitively. ` in:` is parsed **before** `=` / `!=` so list members may contain
`=` / `!=`. Missing key or value not in set → hard VALIDATES fail (same as `=` mismatch).
Unsupported expressions still skip (`null`). `=` / `!=` unchanged. No product-service
validate, gateway, quote-service, or FE edits.

## GitNexus

| Symbol | Risk | Note |
|--------|------|------|
| `ProductRuleEvaluationCore` | **UNKNOWN** (not in index) | Confirmed callers via `rg`: `StatelessConfigurationEvaluator`, `ConstraintSolver`, unit tests. Treated **LOW–MED** per Wave J. |
| `ConstraintSolver` | session path already passes selections as attrs (H1) | Fixture-only for `in:` |
| Index | stale / worktree not registered | Text confirmation used; UNKNOWN ≠ safe |

## Changes

| Area | Detail |
|------|--------|
| `ProductRuleEvaluationCore` | `attr:key in:v1,v2,...`; parse ` in:` before `=`/`!=`; missing key / not-in-set → `false` |
| `ProductRuleEvaluationCoreTest` | match/mismatch/case/trim; `in:` before `=`; evaluate hard-fail + pass + missing key |
| `ConstraintSolverTest` | session selections `attr:color in:red,blue` match + mismatch |
| `StatelessConfigurationEvaluatorRestClientTest` | use public 2-arg `ProductConfigurationRuleClient` ctor (pre-existing testCompile break) |
| Docs | this file |

## Acceptance

- [x] `attr:color in:red,blue` match / mismatch covered
- [x] Missing key hard-fails
- [x] `=` / `!=` unchanged
- [x] Unsupported still skip
- [x] ConstraintSolver fixture for `in:`
- [x] No product validate / gateway / quote / FE edits
