# Phase 0 Drools Compatibility (10.2.0)

## Scope

Phase 0 spike evidence for Drools compatibility in `backend/libs/rules-engine` using the project's current target version `10.2.0`.

## What Was Tested

Targeted JUnit test added at `backend/libs/rules-engine/src/test/java/com/opencrm/rules/drools/Drools844CompatibilityEvidenceTest.java`.

Covered cases:

1. Adapted source-style DRL sample (based on `volume-discount-named-seat.drl` shape and logic)
- Compiles with `DroolsRuleCompiler` (`KieHelper` + DRL string)
- Executes in a `KieSession`
- Asserts firing count and pricing mutation (`discountRate=0.12`, final price updated)

2. Source-verbatim DRL sample (`backend/services/pricing-service/src/main/resources/rules/approval-margin-exception.drl`)
- Loaded directly from the service module file without modification
- Compiles with `DroolsRuleCompiler`
- Executes and asserts expected metadata outputs (`approvalState`, `approvalTier`, `explanationCode`)

## Evidence

Lane 3 validation command executed (from `backend/`):

```powershell
.\mvnw.cmd -B -pl libs/rules-engine,services/pricing-service -am test -DskipITs
```

Result:

- `BUILD SUCCESS` (reactor: common, security, rules-engine, pricing-service)
- **117 tests** total: common 61, security 13, rules-engine 26 (includes `Drools844CompatibilityEvidenceTest`: 2), pricing-service 17 — all **Failures: 0, Errors: 0, Skipped: 0**
- Resolved runtime: `org.drools:drools-engine:10.2.0` on the pricing-service test classpath

Observed warnings:

- Drools warns that in-memory DRL content (`file0.drl`) package names do not match a folder path. This is expected with `KieHelper.addContent(...)` and does not block compilation/execution.

## Findings

- Drools `10.2.0` can compile and execute at least one representative adapted DRL written in the same source style used by pricing rules.
- A real pricing-service rule (`approval-margin-exception.drl`) compiles and executes verbatim under the same runtime/compiler path used by `rules-engine`.
- No Phase 0 syntax incompatibility was observed in the tested samples.

## Recommendation (Phase 1)

Recommendation: stay on Drools **10.2.0** for Phase 1 (aligned with the backend BOM target upgraded from `8.44.0.Final`).

Rationale:

- Lane 3 validation demonstrates successful compile+execute behavior for representative source-style and verbatim DRL samples on `10.2.0`.
- Rolling back to `8.44.0.Final` would reintroduce the superseded baseline without a demonstrated blocker on `10.2.0`.
- Revisit only if broader rule corpus testing reveals unsupported constructs, performance issues, or dependency/security constraints.

## Next Evidence to Add (Phase 1+)

- Batch compile all pricing-service DRLs as verbatim inputs and capture a compatibility matrix.
- Add at least one sample covering null-sensitive `eval(...)` branches and one multi-fact rule interaction scenario.
- Add regression tests around `RuleDefinition` multi-rule compilation order and conflict resolution behavior.
