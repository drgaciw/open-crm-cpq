# Open CRM Backend

A Spring Boot 3.2 microservices backend for CRM and CPQ operations, built with Java 21.

## Stacked pull requests (Graphite)

Use [Graphite](https://graphite.dev) for dependent PR chains against `main`. Conventions and commands: [docs/GRAPHITE_STACKED_PRS.md](../docs/GRAPHITE_STACKED_PRS.md).

## Shared libraries (verify first)

Before merging service changes that depend on shared types or rules, run:

```bash
cd backend
.\mvnw.cmd -B verify --no-transfer-progress -pl libs/common,libs/security,libs/rules-engine -am
```

Module list and rationale: [libs/README.md](libs/README.md).

## Architecture

```
backend/
  libs/
    common/          # Shared entities, DTOs, exceptions, utilities
    security/        # JWT auth, UserContext, SecurityUtils
    rules-engine/    # Drools/OpenL rule engine helpers
  services/
    crm-service/     # CRM API (Contacts, Organizations, Leads, Deals) - port 8081
    gateway-service/ # API Gateway with routing & circuit breakers   - port 8080
```

## Prerequisites

- **Java 21** (Microsoft OpenJDK or any JDK 21+)
- **Maven 3.9+**
- **Docker** and **Docker Compose** (for PostgreSQL and Redis)

## Quick Start

### 1. Start infrastructure

```bash
cd backend
docker compose up -d postgres redis
```

This starts:
- **PostgreSQL 16** on port 5432 (database: `opencrm`, user: `postgres`, password: `postgres`)
- **Redis 7** on port 6379

Optionally, start management tools:
```bash
docker compose --profile tools up -d
```
- **pgAdmin** on port 5050 (admin@opencrm.local / admin)
- **Redis Commander** on port 8085

### 2. Build the project

```bash
mvn clean package -DskipTests
```

### 3. Run the CRM Service

```bash
java -jar services/crm-service/target/crm-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

The CRM service starts on **http://localhost:8081**.

### 4. Verify

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **OpenAPI Docs**: http://localhost:8081/api-docs
- **Health Check**: http://localhost:8081/actuator/health

## API Endpoints

### Organizations
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/organizations` | Create organization |
| GET | `/api/v1/organizations` | List organizations (paginated) |
| GET | `/api/v1/organizations/{id}` | Get organization by ID |
| PUT | `/api/v1/organizations/{id}` | Update organization |
| DELETE | `/api/v1/organizations/{id}` | Soft delete organization |
| GET | `/api/v1/organizations/search?q=` | Search by name |

### Contacts
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/contacts` | Create contact |
| GET | `/api/v1/contacts` | List contacts (paginated) |
| GET | `/api/v1/contacts/{id}` | Get contact by ID |
| PUT | `/api/v1/contacts/{id}` | Update contact |
| DELETE | `/api/v1/contacts/{id}` | Soft delete contact |
| GET | `/api/v1/contacts/search?q=` | Search by name/email |

### Leads
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/leads` | Create lead |
| GET | `/api/v1/leads` | List leads (paginated, filter by status) |
| GET | `/api/v1/leads/{id}` | Get lead by ID |
| PUT | `/api/v1/leads/{id}` | Update lead |
| DELETE | `/api/v1/leads/{id}` | Soft delete lead |
| POST | `/api/v1/leads/{id}/contact` | Mark as contacted |
| POST | `/api/v1/leads/{id}/qualify` | Qualify lead |
| POST | `/api/v1/leads/{id}/convert` | Convert to deal |
| POST | `/api/v1/leads/{id}/lose` | Mark as lost |
| GET | `/api/v1/leads/search?q=` | Search by title |

### Deals
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/deals` | Create deal |
| GET | `/api/v1/deals` | List deals (paginated, filter by stage) |
| GET | `/api/v1/deals/{id}` | Get deal by ID |
| PUT | `/api/v1/deals/{id}` | Update deal |
| DELETE | `/api/v1/deals/{id}` | Soft delete deal |
| POST | `/api/v1/deals/{id}/advance` | Advance to next stage |
| POST | `/api/v1/deals/{id}/win` | Mark as won |
| POST | `/api/v1/deals/{id}/lose` | Mark as lost |
| GET | `/api/v1/deals/pipeline` | Pipeline summary |
| GET | `/api/v1/deals/search?q=` | Search by name |

## Configuration

Environment variables for production:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `opencrm` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

## Running Tests

Fast unit-only verification excludes Docker-backed tests:

```bash
mvn verify -Punit-tests -pl services/crm-service -am
```

Integration tests use **Testcontainers** with PostgreSQL 16. Docker must be accessible:

```bash
mvn verify -Pintegration-tests -pl services/crm-service -am
```

The `integration-tests` profile preserves full Maven verification and coverage gates. To run only the integration-tagged Testcontainers tests while diagnosing Docker readiness:

```bash
mvn test -Dgroups=integration -pl services/crm-service -am
```

**Windows (Docker Desktop)**: before running integration tests, verify Docker Desktop is running with the Linux engine:

```powershell
docker version
docker info --format '{{.ServerVersion}}'
docker context ls
Test-Path '\\.\pipe\dockerDesktopLinuxEngine'
```

If Docker CLI works but Testcontainers cannot find Docker, start Docker Desktop, confirm Linux containers are enabled, clear stale Docker host overrides, and retry before adding an explicit host:

```powershell
Remove-Item Env:\DOCKER_HOST -ErrorAction SilentlyContinue
```

If auto-detection still fails, create `%USERPROFILE%\.testcontainers.properties`:

```properties
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

Or set the environment variable for the current PowerShell session:

```powershell
$env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
```

## Tech Stack

- **Java 21**, **Spring Boot 3.2.2**, **Spring Cloud 2023.0.0**
- **Spring Data JPA** + **PostgreSQL 16** (schema-per-service isolation)
- **Flyway 10.6** for database migrations
- **MapStruct 1.5.5** + **Lombok** for entity/DTO mapping
- **Spring Cloud Gateway** (WebFlux) with Resilience4j circuit breakers
- **Redis 7** for caching
- **SpringDoc OpenAPI 2.3** for Swagger UI
- **TestContainers 1.19** for integration testing
- **Spring Security** with OAuth2/JWT (Azure AD B2C ready)

## Project Structure

```
services/crm-service/src/main/java/com/opencrm/crm/
  config/             # SecurityConfig, GlobalExceptionHandler
  contact/            # Contact entity, DTO, mapper, repo, service, controller
  organization/       # Organization entity, DTO, mapper, repo, service, controller
  lead/               # Lead entity, DTO, mapper, repo, service, controller
  deal/               # Deal entity, DTO, mapper, repo, service, controller
```

Each domain package follows the same pattern:
- `Entity.java` - JPA entity extending BaseEntity
- `EntityDto.java` - Response DTO
- `EntityCreateRequest.java` - Create request DTO with validation
- `EntityUpdateRequest.java` - Update request DTO
- `EntityMapper.java` - MapStruct mapper
- `EntityRepository.java` - Spring Data JPA repository
- `EntityService.java` - Business logic
- `EntityController.java` - REST controller
