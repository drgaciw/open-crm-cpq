# Backend test mode inventory

This inventory maps backend modules to the narrowest test mode that currently
exists in the repository and the infrastructure required to run it.

## Summary

- Default mock-only modules can run in CI without Docker, Redis, Kafka, or a
  live database.
- Docker is required by modules that use PostgreSQL Testcontainers through
  `jdbc:tc:` test datasources or explicit `PostgreSQLContainer` setup.
- Redis is currently mocked in tests where Redis-facing classes have coverage.
  No backend module requires a live Redis process for its committed tests.
- Kafka is currently mocked or unused in committed tests. No backend module
  requires a live Kafka broker for its committed tests.
- Some service modules declare Testcontainers dependencies or production
  PostgreSQL wiring but do not yet have integration tests. Treat those as
  mock-only today until integration coverage is added.

## Module matrix

| Module | Current committed tests | Narrowest default mode | Docker required | Redis required | Kafka required | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `backend/libs/common` | Unit tests for entities, value objects, outbox, and Kafka constants | Mock-only | No | No | No | `OutboxRelayPublisherTest` mocks `KafkaTemplate`; no broker is started. |
| `backend/libs/security` | `UserContextTest` | Mock-only | No | No | No | Pure security context utility coverage. |
| `backend/libs/rules-engine` | Drools/rule unit tests and `RulesCacheManagerTest` | Mock-only | No | No | No | Redis interactions use mocked `StringRedisTemplate`. |
| `backend/shared` | `MoneyConverterTest` | Mock-only | No | No | No | Shared gRPC conversion coverage only. |
| `backend/services/ai-service` | Agent, registry, and audit unit tests | Mock-only | No | No | No | Production config references Redis/PostgreSQL, but committed tests are JUnit/Mockito-style unit tests. |
| `backend/services/approval-routing` | `ApprovalServiceTest` | Mock-only | No | No | No | POM includes Testcontainers and Kafka dependencies, but current test coverage mocks collaborators. |
| `backend/services/configuration-engine` | `ConfigurationSessionServiceTest` | Mock-only | No | No | No | POM includes Testcontainers plus Redis/Kafka dependencies; current tests mock collaborators. |
| `backend/services/contract-asset-management` | `SubscriptionTest` | Mock-only | No | No | No | POM includes Testcontainers and Kafka dependencies; current test is domain-only. |
| `backend/services/crm-service` | Unit tests plus controller/lifecycle integration tests | Mixed: mock-only unit tests and PostgreSQL integration tests | Yes for integration tests | No | No | Integration tests extend `BaseIntegrationTest`, which starts PostgreSQL Testcontainers and disables Redis. Redis listener/idempotency tests mock `StringRedisTemplate`. |
| `backend/services/document-generation` | `DocumentGenerationJobTest` | Mock-only | No | No | No | POM includes Testcontainers and Kafka dependencies; current test is domain-only. |
| `backend/services/gateway-service` | No committed tests | No current test mode | No | No | No | Production config references Redis for rate limiting/caching. Add WebFlux route tests before assigning a runtime dependency. |
| `backend/services/pricebook-service` | No committed tests | No current test mode | No | No | No | POM includes Testcontainers and PostgreSQL dependencies but no committed tests. |
| `backend/services/pricing-service` | Pricing unit tests | Mock-only | No | No | No | `application-test.yml` uses Testcontainers JDBC, but current committed tests are Mockito-based unit tests. |
| `backend/services/product-service` | `ProductRetireTest` | Mock-only | No | No | No | POM and `application-test.yml` include Testcontainers/PostgreSQL and Kafka wiring, but the current test mocks repositories and outbox publishing. |
| `backend/services/quote-service` | No committed tests | No current test mode | No | No | No | `application-test.yml` uses Testcontainers JDBC and production code publishes Redis/Kafka events, but there are no committed tests in this branch. |

## Recommended command split

Run reactor mock-only modules without Docker:

```bash
cd backend
mvn -pl libs/common,libs/security,libs/rules-engine,services/ai-service,services/approval-routing,services/configuration-engine,services/contract-asset-management,services/document-generation,services/pricing-service,services/product-service test
```

Run CRM integration coverage with Docker available:

```bash
cd backend
mvn -pl services/crm-service verify
```

When integration tests are added to modules that already carry Testcontainers
configuration (`approval-routing`, `configuration-engine`,
`contract-asset-management`, `document-generation`, `pricebook-service`,
`pricing-service`, `product-service`, or `quote-service`), move those tests into
the Docker-required lane unless they are explicitly mocked or tagged out of the
default test phase.

## Current verification notes

- `backend/shared` has its own `pom.xml` but is not listed in
  `backend/pom.xml`; run it through its owning aggregator when that parent is
  available.
- The reactor mock-only command above documents the intended lane. At inventory
  time it is blocked before all modules run because `services/ai-service` does
  not compile without `org.springframework.ai.chat.client.ChatClient`.
- The CRM Docker lane is expected to require a valid Docker/Testcontainers
  environment. Without Docker, controller integration tests and
  `LeadToDealLifecycleTest` fail before their PostgreSQL container starts.
