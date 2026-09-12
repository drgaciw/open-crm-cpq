---
status: green
---
# tdd-be-cpq-svcs-green

## Command

```bash
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 ./mvnw -B \
  -pl services/product-service,services/pricing-service,services/pricebook-service,services/quote-service \
  -am test -Punit-tests -Dsurefire.failIfNoSpecifiedTests=false --no-transfer-progress
```

## Result

| Module | Tests run | Failures | Errors | Skipped |
|--------|-----------|----------|--------|---------|
| libs/common | 86 | 0 | 0 | 0 |
| libs/security | 13 | 0 | 0 | 0 |
| libs/rules-engine | 26 | 0 | 0 | 0 |
| services/pricing-service | 23 | 0 | 0 | 0 |
| services/pricebook-service | 7 | 0 | 0 | 0 |
| services/product-service | 28 | 0 | 0 | 0 |
| services/quote-service | 31 | 0 | 0 | 0 |
| **Total (slice)** | **214** | **0** | **0** | **0** |

- BUILD SUCCESS
- JDK: 21.0.2 (`JAVA_HOME` as above)
- Date: 2026-09-12
- Wall time: ~9m 46s
- HEAD at run: `2235cc91ba95f459e75e17617f36573abacbe0ee`
- Code diffs: none (docs-only closeout)
- Holds respected: product `POST /validate` retained; dual-read not flipped; `00-orchestrator-status` not edited
