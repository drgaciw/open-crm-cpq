---
status: done
id: b1-pricing-fixtures
---

# b1-pricing-fixtures

## Summary

S2C-1b golden fixtures for `POST /api/v1/pricing/execute` (forge track 04 PR-F). Docker-free `@Tag("unit")` suite asserts deterministic waterfall/metrics outputs and Drools pack signals (volume named-seat + approval-margin-exception).

## Files

| Path | Change |
|------|--------|
| `backend/services/pricing-service/src/test/java/.../PricingExecuteGoldenFixtureTest.java` | Service-level goldens: stubbed price book + repos; real validator/metrics |
| `backend/services/pricing-service/src/test/java/.../PricingDrulesGoldenFixtureTest.java` | DRL goldens: compile classpath rules, assert fire/discount/approval signals |
| `backend/services/pricing-service/src/test/resources/fixtures/pricing/execute/*.json` | 3 execute fixtures (baseline, multi-line override, override-only) |
| `backend/services/pricing-service/src/test/resources/fixtures/pricing/drl/*.json` | 3 DRL fixtures (volume 500, margin below/above floor) |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | §4 execute row notes fixture paths |
| `docs/forge/04-cpq-contracts.md` | Gap G5 updated — goldens present |

## Dedup

Removed overlapping `PricingExecutionControllerGoldenTest` (MockMvc stubbed service; only ApiResponse wrapping). Kept one solid suite: execute orchestration + DRL.

## Tests

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -pl services/pricing-service -am test \
  -Dtest='PricingExecuteGoldenFixtureTest,PricingDrulesGoldenFixtureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Verified green: 6 tests (3 execute + 3 DRL), Failures: 0.

## Notes

- GitNexus MCP unavailable in this worktree; callers assessed via grep (test-only + docs). **LOW** risk.
- Requires JDK 21 (`EndPosTable` failure under JDK 27 in this environment).
- Controllers/services unchanged — fixtures and unit tests only.
