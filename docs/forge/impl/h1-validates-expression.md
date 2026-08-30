---
status: done
id: h1-validates-expression
---

# h1-validates-expression

## Summary

Wave H1: constrained VALIDATES `conditionExpression` dialect in `ProductRuleEvaluationCore`.
Supports `attr:key=value` and `attr:key!=value`. `ConstraintSolver` passes
`session.getSelections()` as the attribute bag. When attrs are null/empty the expression
branch is skipped so the agent `List<Long>` path does not false-fail. `targetProductId`
VALIDATES behavior retained. No gateway or product-service validate edits.

## GitNexus

| Symbol | Risk | Note |
|--------|------|------|
| `ProductRuleEvaluationCore` | **UNKNOWN** (not in index; analyze failed worker crash) | Confirmed callers via `rg`: `StatelessConfigurationEvaluator`, `ConstraintSolver`, unit tests. Treated as **MED** product risk per Wave H. |
| `ConstraintSolver` | **LOW** graph (1 direct: `ConfigurationSessionService`) | **MED** product behavior (session validate/complete + expression attrs) |
| Index | 28 commits behind; re-analyze aborted (native worker startup) | Text confirmation used; UNKNOWN ≠ safe |

## Changes

| Area | Detail |
|------|--------|
| `ProductRuleEvaluationCore` | Optional `Map<String,String> attributes` overload; `attr:key=value` / `attr:key!=value`; empty attrs skip expression |
| `ConstraintSolver` | Passes `sessionAttributes(session)` (= selections) into core |
| `StatelessConfigurationEvaluator` | Unchanged 2-arg path (attrs null → skip expression) |
| Tests | `ProductRuleEvaluationCoreTest`; agent skip fixture; ConstraintSolver selection attr fixtures |
| Docs | this file |

## Acceptance

- [x] Expression-only VALIDATES hard-fail when attrs mismatch
- [x] Expression-only VALIDATES pass when attrs match
- [x] Attrs empty/null → skip expression (no agent false-fail)
- [x] `targetProductId` VALIDATES unchanged
- [x] ConstraintSolver session selections drive expression
- [x] No gateway / product validate edits
