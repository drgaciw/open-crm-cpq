# Track PD1/PD2 harvest backlog

**Parent:** [price-dools-reuse-gap-analysis.md](./price-dools-reuse-gap-analysis.md)  
**Matrix:** [price-dools-capability-matrix-p1.md](./price-dools-capability-matrix-p1.md)  
**Source pin:** `cf994fc055ad2b9723040ff1be73796ba3708014`

```
PD0 docs (this) → PD1-1 conflicts → PD1-2 templates → PD1-3 test suites → PD2-1/2 history
AI Context7/Sequential/cache: HOLD
```

| ID | Status | Owner | Depends |
|----|--------|-------|---------|
| PD0 | Done | docs | — |
| PD1-1 | Done | `pricing-service` | PD0 |
| PD1-2 | Done | `pricing-service` | PD1-1 |
| PD1-3 | Done | `pricing-service` | PD1-2 |
| PD2-1 | Done | `pricing-service` Flyway V10 | PD1-3 |
| PD2-2 | Done | `pricing-service` | PD2-1 |
| PD-AI | **HOLD** | `ai-service` | Path B ADR |

**Rejected:** second Drools SoR; filesystem template compile; auto-activate; AI in pricing-service; snapshot cron.

**Tests:** `./mvnw -B -pl services/pricing-service -am test -DskipITs -Djacoco.skip=true`  
**ITs:** Docker / Testcontainers. GitNexus impact before symbol edits; `detect_changes` before commit.
