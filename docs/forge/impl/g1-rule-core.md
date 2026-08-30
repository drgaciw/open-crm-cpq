---
status: done
id: g1-rule-core
---

# g1-rule-core

## Summary

Extracted shared `ProductRuleEvaluationCore` from Wave F thin evaluator. Extended RECOMMENDS (soft), DEFAULT (expand selection), VALIDATES (hard via targetProductId). `ProductConfigurationRuleDto` gains `conditionExpression`. `ConstraintSolver` untouched (G2).

## GitNexus

`StatelessConfigurationEvaluator` **UNKNOWN** (not in index) — confirmed callers via `rg` (`InternalConfigurationEvaluateController` + tests).

## Acceptance

- [x] REQUIRES/EXCLUDES parity retained
- [x] RECOMMENDS soft; DEFAULT expand; VALIDATES hard (target)
- [x] No ConstraintSolver / gateway / product validate edits
