---
status: green
---
# tdd-be-clean 2026-09-15

## Intent

CI **backend-integration** on `origin/main` @ `6312a3c`: full reactor `./mvnw -B -U clean verify` (unit + Testcontainers ITs). TDD any red tests / failing compiler errors. Dual-read default untouched. No `00-orchestrator-status`. Jacoco line **0.80** not lowered. Boot **4.0.6** held. Drools **10.2.0** held. ITs not skipped / not untagged.

## Command

```bash
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend
./mvnw -B -U clean verify --no-transfer-progress
EOF
```

Login `id` primary group is not `docker`; wrap Maven in `newgrp docker` (do **not** `chmod docker.sock`). Engine **29.7.2**, Testcontainers **1.21.4**, Docker API **1.44** (parent surefire). Images: `postgres:16-alpine`, `redis:7-alpine`.

## Result

| Module | Tests run | Failures | Errors | Skipped | Status |
|--------|----------:|---------:|-------:|--------:|--------|
| libs/common | 86 | 0 | 0 | 0 | SUCCESS |
| libs/security | 13 | 0 | 0 | 0 | SUCCESS |
| libs/rules-engine | 56 | 0 | 0 | 0 | SUCCESS |
| crm-service (units + ITs) | 202 | 0 | 0 | 0 | SUCCESS |
| gateway-service | 54 | 0 | 0 | 0 | SUCCESS |
| pricing-service | 101 | 0 | 0 | 0 | SUCCESS |
| pricebook-service | 7 | 0 | 0 | 0 | SUCCESS |
| product-service | 28 | 0 | 0 | 0 | SUCCESS |
| quote-service | 31 | 0 | 0 | 0 | SUCCESS |
| ai-service | 191 | 0 | 0 | 0 | SUCCESS |
| configuration-engine | 76 | 0 | 0 | 0 | SUCCESS |
| approval-routing | 20 | 0 | 0 | 0 | SUCCESS |
| document-generation | 13 | 0 | 0 | 0 | SUCCESS |
| contract-asset-management | 18 | 0 | 0 | 0 | SUCCESS |
| **Reactor total** | **896** | **0** | **0** | **0** | **BUILD SUCCESS** |

- JDK: 21.0.2 (`JAVA_HOME` as above)
- Date: 2026-09-15T04:54:28-05:00
- Wall time: 08:51 min
- HEAD at run: `6312a3c` (`docs(plans): E0–E2 price-book P1 pin, matrix, and harvest backlog (#513)`)
- Code diffs: none (docs-only closeout)
- CRM ITs executed (`@Tag("integration")` retained): LeadToDeal lifecycle, Contact/Deal/Lead/Organization controllers, QuoteAccepted Redis; Testcontainers `postgres:16-alpine` + `redis:7-alpine`
- Jacoco **0.80** gate: crm-service + pricing-service `All coverage checks have been met.`
- Drools **10.2.0** (parent `${drools.version}`) — rules-engine corpus compile tests green

## Compiler notes (non-blocking)

javac INFO only — **not** `-Werror`; verify still **EXIT=0**. Not expanded into a Jackson/deprecation sweep (w8).

| File | Note |
|------|------|
| `libs/common/.../OutboxRelayPublisherTest.java` | unchecked / unsafe operations |
| `quote-service/.../QuoteServiceSubmitForReviewTest.java` | unchecked / unsafe operations |
| `*/config/GlobalExceptionHandler.java` (crm, pricing, pricebook, product, quote) | deprecated API |
| CRM `ContactControllerIntegrationTest` + pricing `GlobalExceptionHandlerTest` | deprecated API (tests) |

## Holds

- Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched
- No `docs/forge/00-orchestrator-status.md`
- Jacoco minimum **0.80** not lowered
- Spring Boot parent **4.0.6** not downgraded
- Drools **10.2.0** not reverted
- ITs not skipped; `@Tag("integration")` not removed
- Architect worktree / w8 not touched
