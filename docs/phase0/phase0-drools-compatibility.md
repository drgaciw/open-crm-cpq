# Phase 0 Drools Compatibility (8.44.0.Final)

## Scope

Phase 0 spike evidence for Drools compatibility in `backend/libs/rules-engine` using the project's current target version `8.44.0.Final`.

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

Test command executed (from `backend/`):

```powershell
.\mvnw.cmd -pl libs\rules-engine -Dtest=Drools844CompatibilityEvidenceTest test
```

Result:

- `BUILD SUCCESS`
- `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
- Both the adapted source-style sample and the source-verbatim sample compiled and fired successfully.

Observed warnings:

- Drools warns that in-memory DRL content (`file0.drl`) package names do not match a folder path. This is expected with `KieHelper.addContent(...)` and does not block compilation/execution.

## Findings

- Drools `8.44.0.Final` can compile and execute at least one representative adapted DRL written in the same source style used by pricing rules.
- A real pricing-service rule (`approval-margin-exception.drl`) compiles and executes verbatim under the same runtime/compiler path used by `rules-engine`.
- No Phase 0 syntax incompatibility was observed in the tested samples.

## Recommendation (Phase 1)

Recommendation: stay on Drools 8.x for Phase 1 (specifically current `8.44.0.Final`) rather than introducing a version upgrade during the compatibility migration.

Rationale:

- Current target version already demonstrates successful compile+execute behavior for representative source-style and verbatim DRL samples.
- Upgrading during Phase 1 would add migration variables without Phase 0 evidence of a blocker in `8.44.0.Final`.
- Revisit upgrade only if broader rule corpus testing reveals unsupported constructs, performance issues, or dependency/security constraints.

## Next Evidence to Add (Phase 1+)

- Batch compile all pricing-service DRLs as verbatim inputs and capture a compatibility matrix.
- Add at least one sample covering null-sensitive `eval(...)` branches and one multi-fact rule interaction scenario.
- Add regression tests around `RuleDefinition` multi-rule compilation order and conflict resolution behavior.
