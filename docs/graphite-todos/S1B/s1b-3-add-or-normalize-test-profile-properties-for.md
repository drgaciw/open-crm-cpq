# S1B-3: Normalize test profile properties

**Status:** Implemented on branch `todo/s1b-3-add-or-normalize-test-profile-properties-for`.

## Deliverables

- `src/test/resources/application-test.yml` for each Spring Boot service that runs tests or will soon (Testcontainers JDBC URL, Flyway schema, `cache.type: none`, Redis host `localhost` when used).
- Services without JDBC (gateway) use Redis-only test config and disable rate-limit filters for unit runs.

## Convention

- `SPRING_PROFILES_ACTIVE=test` in CI (see `.github/workflows/ci.yml`).
- Postgres via `jdbc:tc:postgresql:16-alpine` and per-service schema in Flyway.
- Do not rely on developer-local `application-dev.yml` for tests.
