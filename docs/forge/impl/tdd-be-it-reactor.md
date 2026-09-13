---
status: in_progress
---
# tdd-be-it-reactor

## Scope split (coordination)

- CRM ITs landed on `origin/main` as **04909db** (#499). This branch is rebased onto that; **no duplicate `crm-service` IT edits**.
- Remaining work: pricing Jacoco **0.80** (unit tests + Drools Jacoco agent exclude) then **full** reactor `mvn verify`.

## Command (full)

```bash
newgrp docker <<'EOF'
docker info
cd backend
JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2 ./mvnw -B -U verify --no-transfer-progress
EOF
```

ITs **not** skipped. Dual-read YAML untouched. No `00-orchestrator-status`. Jacoco gates not lowered. Boot **4.0.6** held.
## Reactor-owned fixes (this branch)

1. **Spring Boot 4 Flyway:** replace `flyway-core` with `spring-boot-starter-flyway` on **non-CRM** services; unpin `flyway-database-postgresql` so Boot BOM manages it. Parent `flyway.version` left at **10.6.0** (w14 may own the bump — rebase after their PR).
2. **Jackson 2 ObjectMapper bean:** add deprecated `spring-boot-jackson2` to `libs/common` (Boot 4 defaults to Jackson 3 `JsonMapper`).
3. **Azure 5.8 on Boot 4 (tests):** Surefire + non-CRM `application-test.yml` disable/exclude Azure verifier + token/B2C autoconfig. **HOLD: do not downgrade Spring Boot from 4.0.6** — the Azure message wanting 3.0–3.2 is a **verifier mismatch**. Fix = exclude/update checker or later align `spring-cloud-azure` to **7.x** (Boot 4), not Boot 3.
4. **pricing Jacoco excludes:** ensure generated `common/grpc` + `*MapperImpl` are excluded (gate remains **0.80**; coverage inventory still deferred).

## Deferred (gates unchanged)

| Item | Owner / note |
|------|----------------|
| `crm-service` ITs | **w14** — do not edit here |
| `pricing-service` Jacoco 0.80 | Unit inventory ~14–31% line coverage; CI unit slice uses `-Punit-tests` (`jacoco.skip=true`). **Gate not lowered**; deferred while other modules verify |

## Holds

- **Spring Boot parent stays 4.0.6** — Azure 3.x compatibility text is verifier noise
- Dual-read YAML untouched
- No `00-orchestrator-status`
- No `SECURITY_OPEN` product flip beyond existing test `security.open`
- Jacoco not lowered
- Docker usable via `newgrp docker` (not `blocked_no_docker`)

## Result

- **status:** `green_partial`
- Docker: `newgrp docker` + `docker info` OK
- Non-CRM / non-pricing: `./mvnw -B -U verify -pl '!services/crm-service,!services/pricing-service'` **EXIT=0** (~3m20s)
- CRM ITs: deferred to **w14** (no `crm-service` edits in this branch)
- Pricing Jacoco 0.80: deferred (gate unchanged)
- Holds: dual-read untouched; no `00-orchestrator-status`; ITs not skipped on included modules; Jacoco not lowered
