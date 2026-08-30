---
status: done
id: b2-gateway-erp501
---

# b2-gateway-erp501

## Summary

Wave B4 / forge `06-gateway-ci.md` **PR-B** (W1-10): expand Docker-free `CpqGatewayRoutesTest` so ERP reserved paths return **HTTP 501** with documented JSON fields, keep CPQ_API_CONTRACT_MATRIX **Appendix A** coverage, and assert every default-profile gateway route id.

## Files

| Path | Change |
|------|--------|
| `backend/services/gateway-service/src/main/java/com/opencrm/gateway/routes/CpqGatewayRoutes.java` | Add `DEFAULT_PROFILE_OWNER_ROUTE_IDS` + `DEFAULT_PROFILE_ROUTE_IDS` (CRM/CPQ/ERP owners, document/contract, communication/ai, and all `cpq-*` aliases) |
| `backend/services/gateway-service/src/test/java/com/opencrm/gateway/routes/CpqGatewayRoutesTest.java` | Assert default-profile route ids; WebTestClient HTTP 501 + exact JSON fields for Appendix A ERP rows + configured `forward:/fallback/erp/unavailable`; keep Appendix A path/predicate coverage |
| `docs/forge/impl/b2-gateway-erp501.md` | This status note |

## Acceptance

- [x] Removing a covered default-profile route id from yaml fails unit test (`DEFAULT_PROFILE_ROUTE_IDS`)
- [x] Agent-contract prefixes from matrix §9 / Appendix A still asserted (fixture + Path predicates)
- [x] ERP reserved paths return HTTP **501** with documented JSON: `success`, `message`, `service`, `timestamp` (`success=false`, `service=erp-adapter-service`, reserved message fragment)
- [x] Tests remain Docker-free (`WebTestClient.bindToController(FallbackController)` — no Redis / full Gateway context)
- [x] `CpqGatewayRoutesTest` green under `-Punit-tests` (Java 21)

## Verification

```bash
export JAVA_HOME=…/java/21.0.2
cd backend && ./mvnw -pl services/gateway-service -am test -Punit-tests \
  -Dtest=CpqGatewayRoutesTest -Dsurefire.failIfNoSpecifiedTests=false
# → Tests run: 35, Failures: 0, Errors: 0
```

## Blast radius

GitNexus MCP unavailable; grep callers:

- `CpqGatewayRoutes` — **only** `CpqGatewayRoutesTest` (additive constants)
- `FallbackController.erpNotImplemented` — unchanged; exercised via WebTestClient

**Risk: LOW** (test + constants; no runtime route/yaml edits).

## Notes

- `communication-service` / `ai-service` included in `DEFAULT_PROFILE_ROUTE_IDS` as default-profile-only (still absent from `dev` profile — PR-C scope).
- No full Spring Cloud Gateway HTTP mesh; ERP HTTP asserts the local forward target handler that `erp-stub` uses.
- JDK 27 hits known `EndPosTable` compile failure; verify with Java 21.
