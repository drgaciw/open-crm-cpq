---
status: done
id: f4-validate-contracts
---

# f4-validate-contracts

## Summary

Wave F4 contract / dual-run parity harness for S2B-11. Docker-free unit tests lock expected equivalence between product-service `POST /api/v1/configuration/validate` hard failures (`List<String>`) and configuration-engine evaluate (`ConfigurationValidationResult`) for the same `List<Long>` fixtures (REQUIRES / EXCLUDES only). Gateway route-predicate stub documents F3 **validate-before-`configuration/**`** ordering.

**Waiting on F2 merge** for live dual-run against `StatelessConfigurationEvaluator` / `POST /internal/v1/configurations/evaluate` (CE classpath hook fails closed once F2 lands until wired). F3 validate shim not required for this PR; ordering stub stays green pre-F3 and will assert specific-before-wildcard once F3 routes exist.

## Files

| Path | Change |
|------|--------|
| `backend/services/product-service/src/test/resources/contracts/s2b11-validate-parity-fixtures.json` | Shared REQUIRES/EXCLUDES oracle (product errors ↔ CE violations) |
| `backend/services/product-service/src/test/java/.../ConfigurationValidateParityFixtureTest.java` | Product validate vs fixture + CE equivalence assertions |
| `backend/services/configuration-engine/src/test/resources/contracts/s2b11-validate-parity-fixtures.json` | Mirrored fixture for CE module |
| `backend/services/configuration-engine/src/test/java/.../contract/ConfigurationValidateParityFixtureTest.java` | CE-side oracle + F2 live dual-run deferred hook |
| `backend/services/gateway-service/src/test/java/.../ConfigurationValidateRoutePredicateTest.java` | validate-before-`configuration/**` predicate order stub |
| `docs/forge/impl/f4-validate-contracts.md` | This note |

## Acceptance

- [x] Docker-free unit/contract tests (no Testcontainers)
- [x] Fixtures encode empty / REQUIRES miss / EXCLUDES hit / RECOMMENDS ignored / combined
- [x] Product side exercised against `ConfigurationRuleService.validateConfiguration`
- [x] CE expected structured shape documented as equivalent hard failures
- [x] Gateway ordering stub for validate vs `configuration/**` (and CPQ alias)
- [x] ERP / other Appendix A routes untouched
- [ ] Live product↔CE dual-run — **blocked on F2** evaluator merge

## Verify

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -pl services/product-service,services/configuration-engine,services/gateway-service -am test -Punit-tests \
  -Dtest=ConfigurationValidateParityFixtureTest,ConfigurationValidateRoutePredicateTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
