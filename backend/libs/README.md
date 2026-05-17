# Shared backend libraries

Jar modules consumed by CPQ microservices. Service stacks should treat these as **stable dependencies**: run shared-library verification before depending on new APIs from a service PR.

## Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `common` | `com.opencrm:common` | Shared entities, DTOs, events, exceptions, value objects |
| `security` | `com.opencrm:security` | JWT helpers, `UserContext`, security utilities |
| `rules-engine` | `com.opencrm:rules-engine` | Drools/OpenL rule compilation and evaluation helpers |

All three are plain JARs (`spring-boot-maven-plugin` skip repackage). They ship no `application-test.yml`; tests are **unit-only** (no Docker, Redis, or Postgres).

## Verification (Stack S1B-1)

From `backend/`:

```bash
.\mvnw.cmd -B verify --no-transfer-progress -pl libs/common,libs/security,libs/rules-engine -am
```

**Expected:** reactor builds `open-crm-backend` parent plus all three libs; Surefire runs tests under `libs/*/src/test/java` (no integration profile required).

Use this command in CI or as a pre-push gate when changing published types in `common`, auth helpers in `security`, or rule APIs in `rules-engine`.

## Related docs

- [backend/README.md](../README.md) — reactor-wide test modes
- [docs/CRM_TESTING_STRATEGY.md](../../docs/CRM_TESTING_STRATEGY.md) — service integration inventory
