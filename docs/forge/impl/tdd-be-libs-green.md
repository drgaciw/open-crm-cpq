---
status: green
---
# tdd-be-libs-green

## Command

```bash
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 ./mvnw -B \
  -pl libs/common,libs/security,libs/rules-engine -am test -Punit-tests \
  -Dsurefire.failIfNoSpecifiedTests=false --no-transfer-progress
```

## Result

| Module | Tests run | Failures | Errors | Skipped |
|--------|-----------|----------|--------|---------|
| libs/common | 86 | 0 | 0 | 0 |
| libs/security | 13 | 0 | 0 | 0 |
| libs/rules-engine | 26 | 0 | 0 | 0 |
| **Total** | **125** | **0** | **0** | **0** |

- BUILD SUCCESS
- JDK: 21.0.2
- Date: 2026-09-12
- Code diffs: none (docs-only closeout)
