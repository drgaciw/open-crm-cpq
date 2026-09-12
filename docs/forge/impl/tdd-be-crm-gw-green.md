---
status: green
---
# tdd-be-crm-gw-green

## Command

```bash
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 ./mvnw -B \
  -pl services/crm-service,services/gateway-service -am test -Punit-tests \
  -Dsurefire.failIfNoSpecifiedTests=false --no-transfer-progress
```

## Result

| Module | Tests run | Failures | Errors | Skipped |
|--------|-----------|----------|--------|---------|
| libs/common | 86 | 0 | 0 | 0 |
| libs/security | 13 | 0 | 0 | 0 |
| services/crm-service | 139 | 0 | 0 | 0 |
| services/gateway-service | 52 | 0 | 0 | 0 |
| **Total (slice)** | **290** | **0** | **0** | **0** |

- BUILD SUCCESS
- JDK: 21.0.2 (`JAVA_HOME` as above)
- Date: 2026-09-12
- Wall time: ~8m 38s
- Code diffs: none (docs-only closeout)
- Holds respected: no dual-read YAML flip, no `00-orchestrator-status`, no `SECURITY_OPEN`, no CORS wildcards
