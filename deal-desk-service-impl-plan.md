# deal-desk-service Integration Plan

## Context

The `deal-desk-service` (https://github.com/drgaciw/deal-desk-service) is a standalone Spring Boot
microservice that handles deal lifecycle management, TCV (Total Contract Value) calculation, business
rules execution (EasyRules + Drools), and Salesforce CRM synchronization. The goal is to port it into
the existing `backend/` multi-module Maven project as `services/deal-desk-service`, making it a
first-class sibling alongside crm-service, pricing-service, quote-service, etc.

**Key integration challenges identified:**
- Spring Boot version mismatch: source uses 4.0.2; monorepo uses 3.2.2
- Persistence mismatch: source uses MongoDB; monorepo uses PostgreSQL exclusively
- Cache mismatch: source uses Caffeine; monorepo uses Redis
- Package namespace: `com.aciworldwide.dealdesk` → `com.opencrm.dealdesk`
- Rules engine: source uses EasyRules 4.1.0 (not in parent BOM); monorepo has Drools 8.44.0.Final in `libs/rules-engine`
- CRM service overlap: `crm-service` already has a lightweight `deal/` domain (pipeline tracking). `deal-desk-service` is a deeper CPQ-layer service handling approval workflows, TCV, and Salesforce sync — complementary, not duplicate.
- Port: assign **8085** (currently not used by a checked-in service `application.yml`; re-verify if `communication-service` later claims it)

---

## Step 1 — Register Module in Parent POM

**File:** `backend/pom.xml`

Add `services/deal-desk-service` to the `<modules>` section alongside the other services:

```xml
<modules>
  ...
  <module>services/deal-desk-service</module>
</modules>
```

**Also add EasyRules to `<dependencyManagement>`** (not currently in the parent BOM):

```xml
<dependency>
  <groupId>org.jeasy</groupId>
  <artifactId>easy-rules-core</artifactId>
  <version>4.1.0</version>
</dependency>
<dependency>
  <groupId>org.jeasy</groupId>
  <artifactId>easy-rules-support</artifactId>
  <version>4.1.0</version>
</dependency>
```

---

## Step 2 — Create Module POM

**File:** `backend/services/deal-desk-service/pom.xml`

- **Parent:** `com.opencrm:open-crm-backend:1.0.0-SNAPSHOT` (inherits Spring Boot 3.2.2, Java 21, all BOM versions)
- **ArtifactId:** `deal-desk-service`
- **Packaging:** `jar`

**Dependencies (follow existing service POM conventions):**
- No versions for Spring Boot starters, Spring Cloud-managed deps, Testcontainers, and internal `com.opencrm:*` modules
- Explicit versions (via parent properties) for `postgresql`, `flyway-*`, `mapstruct`, and `springdoc`
- EasyRules may omit versions only if added to parent `<dependencyManagement>` in Step 1

**Dependencies:**
```xml
<!-- Spring Boot -->
spring-boot-starter-web
spring-boot-starter-data-jpa          <!-- replaces spring-boot-starter-data-mongodb -->
spring-boot-starter-validation
spring-boot-starter-actuator
spring-boot-starter-cache
spring-boot-starter-data-redis        <!-- replaces caffeine -->
spring-boot-starter-aop               <!-- replaces spring-boot-starter-aspectj -->
spring-boot-starter-security
spring-boot-starter-oauth2-resource-server   <!-- actual JWT resource server config in service -->
spring-retry

<!-- Database -->
org.postgresql:postgresql
org.flywaydb:flyway-core
org.flywaydb:flyway-database-postgresql

<!-- Shared libs -->
com.opencrm:common
com.opencrm:security                  <!-- shared SecurityUtils/UserContext helpers -->
com.opencrm:rules-engine              <!-- provides DroolsRulesEngine -->

<!-- Rules -->
org.jeasy:easy-rules-core             <!-- version managed in parent BOM (Step 1) -->
org.jeasy:easy-rules-support

<!-- OpenAPI -->
org.springdoc:springdoc-openapi-starter-webmvc-ui

<!-- Mapping -->
org.mapstruct:mapstruct
org.projectlombok:lombok (optional)
org.apache.commons:commons-lang3

<!-- Resilience -->
io.github.resilience4j:resilience4j-spring-boot3

<!-- Test -->
spring-boot-starter-test
spring-security-test
org.testcontainers:testcontainers
org.testcontainers:junit-jupiter
org.testcontainers:postgresql           <!-- replaces testcontainers:mongodb -->
```

**Removed (not ported):**
- `spring-boot-starter-data-mongodb` — migrating to PostgreSQL
- `com.github.ben-manes.caffeine:caffeine` — using Redis
- `org.apache.httpcomponents:httpclient` — use Spring's `RestClient` (Boot 3.2+)
- `com.nimbusds:nimbus-jose-jwt` — use Spring Security OAuth2 resource-server stack instead
- `commons-collections:3.2.2` — use `commons-lang3` from parent BOM

**Build plugins:** Match the pattern from `crm-service/pom.xml` / `pricing-service/pom.xml`:
- `spring-boot-maven-plugin` (fat jar, exclude lombok)
- `maven-compiler-plugin` config is already provided by parent `pluginManagement` (annotation processors: lombok, lombok-mapstruct-binding, mapstruct-processor)
- `jacoco-maven-plugin` with `prepare-agent`, `report`, and `check` (80% line coverage minimum, matching crm/pricing)

---

## Step 3 — Module Directory Structure

```
backend/services/deal-desk-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/opencrm/dealdesk/
    │   │   ├── DealDeskServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── RetryConfig.java
    │   │   │   ├── SalesforceConfig.java
    │   │   │   └── CacheConfig.java           (Redis, not Caffeine)
    │   │   ├── controller/
    │   │   │   └── DealController.java
    │   │   ├── dto/
    │   │   │   ├── DealRequestDTO.java
    │   │   │   └── DealResponseDTO.java
    │   │   ├── exception/
    │   │   │   ├── DealDeskException.java
    │   │   │   ├── DealNotFoundException.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── InvalidDealStateException.java
    │   │   │   └── SalesforceIntegrationException.java
    │   │   ├── mapper/
    │   │   │   ├── DealMapper.java
    │   │   │   ├── DealMapperHelper.java
    │   │   │   └── MapStructDateTimeConverter.java
    │   │   ├── model/
    │   │   │   ├── Deal.java                  (JPA @Entity)
    │   │   │   ├── DealStatus.java
    │   │   │   └── tcv/
    │   │   │       ├── TCVComponent.java      (@Embeddable or @Entity)
    │   │   │       ├── TCVCalculation.java    (@Embeddable or @Entity)
    │   │   │       ├── PricingModel.java
    │   │   │       ├── DiscountsAndAdjustments.java
    │   │   │       ├── ContingentRevenue.java
    │   │   │       ├── ContractTerm.java
    │   │   │       └── RepricingTriggers.java
    │   │   ├── repository/
    │   │   │   ├── DealRepository.java        (JpaRepository)
    │   │   │   └── tcv/
    │   │   │       └── RepricingTriggerRepository.java
    │   │   ├── rules/
    │   │   │   ├── config/
    │   │   │   │   ├── EasyRulesConfig.java
    │   │   │   │   ├── RuleEngineConfig.java
    │   │   │   │   └── RuleEngineProperties.java
    │   │   │   ├── controller/
    │   │   │   │   └── RuleDefinitionController.java
    │   │   │   ├── core/
    │   │   │   │   ├── DynamicRule.java
    │   │   │   │   └── RuleEngineListener.java
    │   │   │   ├── engine/
    │   │   │   │   ├── PricingRuleEngine.java
    │   │   │   │   └── PricingRuleEngineAdapter.java
    │   │   │   ├── fact/
    │   │   │   │   ├── DealFactProvider.java
    │   │   │   │   └── FactProvider.java
    │   │   │   ├── impl/
    │   │   │   │   ├── CreditPercentageRules.java
    │   │   │   │   ├── DealStatusRules.java
    │   │   │   │   ├── DealValidationRules.java
    │   │   │   │   ├── DebitRegulationRules.java
    │   │   │   │   ├── PaymentRules.java
    │   │   │   │   ├── PaymentThresholdRule.java
    │   │   │   │   ├── ProfitabilityRules.java
    │   │   │   │   └── TCVCalculationRules.java
     │   │   │   ├── model/
     │   │   │   │   ├── DealRule.java          (JPA @Entity)
     │   │   │   │   ├── RuleDefinitionEntity.java    (JPA @Entity; avoid name clash with libs/rules-engine)
     │   │   │   │   └── RuleVersion.java
    │   │   │   ├── repository/
    │   │   │   │   ├── RuleDefinitionRepository.java (JpaRepository)
    │   │   │   │   ├── RuleRepository.java
    │   │   │   │   └── RuleVersionRepository.java
    │   │   │   └── service/
    │   │   │       ├── DealStatusRuleExecutorService.java
    │   │   │       ├── DealValidationRuleExecutorService.java
    │   │   │       ├── RuleCachingService.java
    │   │   │       ├── RuleContainer.java
    │   │   │       ├── RuleDefinitionService.java
    │   │   │       ├── RuleExecutionService.java
    │   │   │       ├── RuleService.java
    │   │   │       ├── RuleValidationService.java
    │   │   │       ├── RuleVersioningService.java
    │   │   │       └── TCVRuleExecutorService.java
    │   │   └── service/
    │   │       ├── CurrencyService.java
    │   │       ├── DealService.java
    │   │       ├── SalesforceService.java
    │   │       └── impl/
    │   │           ├── CurrencyServiceImpl.java
    │   │           ├── DealServiceImpl.java
    │   │           └── SalesforceServiceImpl.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/
    │           └── migration/
    │               ├── V1__create_deal_desk_schema.sql
    │               ├── V2__create_deals_table.sql
    │               ├── V3__create_deal_products_table.sql
    │               ├── V4__create_rule_definitions_table.sql
    │               ├── V5__create_deal_rules_and_rule_versions_tables.sql
    │               └── V6__create_rule_audit_table.sql
    └── test/
        └── java/com/opencrm/dealdesk/
            ├── config/
            │   └── PostgresTestConfig.java     (replaces MongoTestConfig)
            ├── integration/
            │   └── IntegrationTestBase.java    (PostgreSQL TestContainers)
            └── rules/
                └── ...                         (ported rule tests)
```

---

## Step 4 — Source Code Porting Changes

### 4a. Application Class
**File:** `DealDeskServiceApplication.java`

```java
@SpringBootApplication(scanBasePackages = {
    "com.opencrm.dealdesk",
    "com.opencrm.common",
    "com.opencrm.security",
    "com.opencrm.rules"
})
@EnableJpaAuditing          // replaces @EnableMongoAuditing
@EnableCaching
@EnableRetry
@EnableConfigurationProperties(RuleEngineProperties.class)
public class DealDeskServiceApplication { ... }
```

Pattern reference: `pricing-ai-rules-service/src/main/java/com/opencrm/pricingairules/PricingAiRulesServiceApplication.java`

### 4b. Package Rename (all files)
Global rename across all Java files:
- `com.aciworldwide.dealdesk` → `com.opencrm.dealdesk`

### 4c. Entity Migration: MongoDB @Document → JPA @Entity

**`Deal.java`** — most complex migration:
```java
// BEFORE (MongoDB)
@Document(collection = "deals")
public class Deal {
    @Id private String id;
    private List<TCVComponent> components;   // embedded doc
    private PricingModel pricingModel;       // embedded doc
    private TCVCalculation tcvCalculation;   // embedded doc
    private DiscountsAndAdjustments adjustments; // embedded doc
}

// AFTER (JPA + PostgreSQL JSONB)
@Entity
@Table(name = "deals", schema = "deal_desk")
public class Deal extends BaseEntity {  // inherit from com.opencrm.common.entity.BaseEntity
    // @Id Long id from BaseEntity
    // Use @Column(columnDefinition = "jsonb") for complex nested objects
    @Column(columnDefinition = "jsonb")
    private String componentsJson;       // store as JSON, deserialize in service layer

    @Column(columnDefinition = "jsonb")
    private String pricingModelJson;

    @Column(columnDefinition = "jsonb")
    private String tcvCalculationJson;

    @Column(columnDefinition = "jsonb")
    private String adjustmentsJson;

    @ElementCollection
    @CollectionTable(name = "deal_products", schema = "deal_desk")
    private List<String> products;
    ...
}
```

**Alternative for TCVComponent (if preferred):** Use `@OneToMany` with a separate `deal_tcv_components` table. JSONB is recommended for the complex TCV models as it mirrors the original document model, avoids a schema explosion, and PostgreSQL JSONB is queryable.

**`RuleDefinitionEntity.java`** (MongoDB → JPA):
```java
@Entity
@Table(name = "rule_definitions", schema = "deal_desk",
    uniqueConstraints = @UniqueConstraint(columnNames = "rule_key"))
public class RuleDefinitionEntity extends BaseEntity {
    @Column(name = "rule_key") private String ruleKey;
    ...
}
```

Same pattern for `DealRule.java`, `RuleVersion.java`.

**`DealRepository.java`** — MongoDB → JPA:
```java
// BEFORE
public interface DealRepository extends MongoRepository<Deal, String>

// AFTER
public interface DealRepository extends JpaRepository<Deal, Long>

// Replace @Aggregation with @Query (JPQL/native):
@Query("SELECT SUM(d.value) FROM Deal d WHERE d.status = :status")
Optional<BigDecimal> calculateTotalValueByStatus(@Param("status") DealStatus status);

// MongoDB @Query → JPQL:
@Query("SELECT d FROM Deal d WHERE d.value >= :minValue AND d.status = :status")
List<Deal> findHighValueDeals(@Param("minValue") BigDecimal minValue, @Param("status") DealStatus status);
```

### 4d. Security Configuration (service-local JWT resource server)
**Create/keep:** `src/main/java/com/opencrm/dealdesk/config/SecurityConfig.java`

`libs/security` in this repo provides shared helpers (`SecurityUtils`, `UserContext`) but does **not**
provide a `SecurityFilterChain`. Follow the same service-local pattern used by `crm-service` /
`pricing-service`, and enable actual JWT resource-server validation:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.open:false}")
    private boolean securityOpen;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var builder = http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityOpen) {
            builder.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            builder.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().authenticated()
            );

            builder.oauth2ResourceServer(oauth2 -> oauth2.jwt());
        }

        return builder.build();
    }
}
```

Role-based annotations (`@PreAuthorize`) in controllers remain.

### 4e. Cache Configuration
**Remove:** Caffeine-based `CacheConfig.java`
**Replace with:** Redis cache config (as in crm-service — no custom `@Bean` needed if using
`spring.cache.type: redis` in application.yml; add `@EnableCaching` on the application class).

If a named cache bean is needed for `rulesCache`, add a minimal `CacheConfig.java`:
```java
@Configuration
public class CacheConfig {
    @Bean
    public RedisCacheConfiguration rulesCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues();
    }
}
```

### 4f. Rules Engine Integration

**Dual-engine architecture** (keep both, they serve different purposes):

1. **EasyRules** (annotation-driven, keep as-is):
   - `DealValidationRules` — field validation, business constraints
   - `DealStatusRules` — state machine transitions (DRAFT→SUBMITTED→APPROVED→REJECTED)
   - `TCVCalculationRules` — TCV math rules
   - `ProfitabilityRules`, `PaymentRules`, `CreditPercentageRules`, etc.
   - Wire via `EasyRulesConfig` (bean: `Rules`, `RulesEngine`, `RuleEngineListener`)

2. **Drools via `libs/rules-engine`** (database-backed dynamic rules):
   - `RuleDefinitionService` stores/retrieves `RuleDefinitionEntity` from PostgreSQL
   - The `DroolsRuleEngine.java` (from `deal-desk-rules`) generates DRL strings dynamically
   - **Replace** the source's standalone `DroolsRuleEngine` with `com.opencrm.rules.drools.DroolsRulesEngine` from `libs/rules-engine`
   - `RuleDefinitionService.createRule()` should validate DRL via `libs/rules-engine`'s `DroolsRuleCompiler` (e.g., `compileFromDrlString(...)`) before persisting
   - Map persisted entities to `com.opencrm.rules.engine.RuleDefinition` before calling `DroolsRulesEngine.execute(context, rules)` (the no-rules overload is not implemented in the shared library yet)

**Remove:** The `deal-desk-rules` Maven sub-module entirely — its Drools engine is superseded by
`libs/rules-engine`, and the EasyRules adapters move into the main service.

**DRL Integration pattern** — reuse from `libs/rules-engine`:
- Reference: `backend/libs/rules-engine/src/main/java/com/opencrm/rules/drools/`
- The `PricingRuleEngineAdapter` remains, wrapping EasyRules for deal-level evaluation

### 4g. Salesforce Service
Retain the full `SalesforceService` interface and `SalesforceServiceImpl`. Replace
`org.apache.httpcomponents:httpclient` with Spring Boot 3.2's `RestClient`:
```java
// Replace HttpClient with:
private final RestClient restClient;  // Spring 6.1+ built-in
```

**`SalesforceConfig.java`** — convert from standalone `@ConfigurationProperties` to match the
`@ConfigurationProperties(prefix = "salesforce")` pattern with a `SalesforceProperties.java` record.

---

## Step 5 — application.yml Configuration

**File:** `backend/services/deal-desk-service/src/main/resources/application.yml`

```yaml
server:
  port: 8085                    # selected port for deal-desk-service (re-verify if communication-service later binds 8085)

spring:
  application:
    name: deal-desk-service

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:opencrm}?currentSchema=deal_desk
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        default_schema: deal_desk
        format_sql: true
        default_batch_fetch_size: 25
        jdbc.batch_size: 25
        order_inserts: true
        order_updates: true

  flyway:
    enabled: true
    schemas: deal_desk
    locations: classpath:db/migration

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

  cache:
    type: redis
    redis:
      time-to-live: 600000      # 10 minutes (matches other services)
      cache-null-values: false

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:https://localhost:8080/auth/realms/opencrm}

security:
  open: ${SECURITY_OPEN:false}   # local dev/test escape hatch; keep false in normal environments

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        "[http.server.requests]": true
      percentiles:
        "[http.server.requests]": 0.5,0.90,0.95,0.99
      slo:
        "[http.server.requests]": 50ms,100ms,200ms,500ms

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
  packages-to-scan: com.opencrm.dealdesk

rules:
  engine:
    versioning:
      enabled: true
      keep-versions: 5
  audit:
    enabled: true
    retention-days: 30
    batch-size: 100
  validation:
    expression-timeout-ms: 5000
    max-condition-length: 2000
    max-action-length: 2000
    allowed-packages:
      - java.math
      - java.time
      - java.util
      - com.opencrm.dealdesk.model   # updated package

salesforce:
  client-id: ${SALESFORCE_CLIENT_ID:test-client-id}
  client-secret: ${SALESFORCE_CLIENT_SECRET:test-client-secret}
  security-token: ${SALESFORCE_SECURITY_TOKEN:test-security-token}
  password: ${SALESFORCE_PASSWORD:test-password}
  username: ${SALESFORCE_USERNAME:test-username}
  base-url: ${SALESFORCE_BASE_URL:https://test.salesforce.com}
  opportunity-pricebook-id: ${SALESFORCE_OPPORTUNITY_PRICEBOOK_ID:test-pricebook-id}

resilience4j:
  circuitbreaker:
    instances:
      salesforce:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 50s
        failureRateThreshold: 50

logging:
  level:
    root: INFO
    com.opencrm.dealdesk: DEBUG
```

---

## Step 6 — Database Migrations

**Schema:** `deal_desk` (follows convention: one schema per service)

**V1:** Create schema
```sql
CREATE SCHEMA IF NOT EXISTS deal_desk;
```

**V2:** Deals table (PostgreSQL, with JSONB for complex nested structures)
```sql
CREATE TABLE deal_desk.deals (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    VARCHAR(36) NOT NULL UNIQUE,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    value                   NUMERIC(19,4),
    status                  VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    salesforce_opportunity_id VARCHAR(255),
    account_id              VARCHAR(255),
    account_name            VARCHAR(255),
    sales_rep_id            VARCHAR(255),
    sales_rep_name          VARCHAR(255),
    notes                   TEXT,
    approved_by             VARCHAR(255),
    approved_at             TIMESTAMPTZ,
    start_date              TIMESTAMPTZ,
    end_date                TIMESTAMPTZ,
    components_json         JSONB,
    pricing_model_json      JSONB,
    adjustments_json        JSONB,
    contingent_revenue_json JSONB,
    tcv_calculation_json    JSONB,
    synced                  BOOLEAN NOT NULL DEFAULT FALSE,
    sync_error              TEXT,
    last_sync_at            TIMESTAMPTZ,
    last_sync_status        VARCHAR(50),
    last_sync_error         TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    version                 BIGINT NOT NULL DEFAULT 0,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_deal_uuid ON deal_desk.deals(uuid);
CREATE UNIQUE INDEX idx_deal_sf_opp_id ON deal_desk.deals(salesforce_opportunity_id);
CREATE INDEX idx_deal_status ON deal_desk.deals(status);
CREATE INDEX idx_deal_account ON deal_desk.deals(account_id);
CREATE INDEX idx_deal_sales_rep ON deal_desk.deals(sales_rep_id);
```

**V3:** Deal products (element collection)
```sql
CREATE TABLE deal_desk.deal_products (
    deal_id     BIGINT NOT NULL REFERENCES deal_desk.deals(id),
    product_id  VARCHAR(255) NOT NULL
);
```

**V4:** Rule definitions (migrated from MongoDB `rule_definitions` collection)
```sql
CREATE TABLE deal_desk.rule_definitions (
    id                   BIGSERIAL PRIMARY KEY,
    uuid                 VARCHAR(36) NOT NULL UNIQUE,
    rule_key             VARCHAR(255) NOT NULL UNIQUE,
    name                 VARCHAR(255),
    description          TEXT,
    category             VARCHAR(100),
    priority             INTEGER,
    condition_expression TEXT,
    action_expression    TEXT,
    enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    parameters_json      JSONB,
    valid_from           TIMESTAMP,
    valid_to             TIMESTAMP,
    version              BIGINT NOT NULL DEFAULT 0,
    created_by           VARCHAR(255),
    created_at           TIMESTAMP,
    updated_by           VARCHAR(255),
    updated_at           TIMESTAMP,
    is_deleted           BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX idx_rule_def_uuid ON deal_desk.rule_definitions(uuid);
CREATE INDEX idx_rule_def_category ON deal_desk.rule_definitions(category);
CREATE INDEX idx_rule_def_enabled ON deal_desk.rule_definitions(enabled);
```

**V5:** Deal rules and rule versions tables (similar pattern to V4; include `uuid`, audit columns,
`version`, and `is_deleted` for tables backed by `BaseEntity`)

**V6:** Rule audit table (fix MySQL→PostgreSQL syntax from source)
```sql
-- Fix source V1__create_rule_audit_table.sql (MySQL syntax → PostgreSQL):
CREATE TABLE deal_desk.rule_audit (
    id             BIGSERIAL PRIMARY KEY,
    rule_key       VARCHAR(255) NOT NULL,
    execution_time BIGINT NOT NULL,
    success        BOOLEAN NOT NULL,
    error_message  TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_rule_audit_key ON deal_desk.rule_audit(rule_key);
CREATE INDEX idx_rule_audit_created ON deal_desk.rule_audit(created_at);
CREATE INDEX idx_rule_audit_success ON deal_desk.rule_audit(success);
```

---

## Step 7 — Gateway Service Route Registration

**File:** `backend/services/gateway-service/src/main/resources/application.yml`

Add a new route entry in `spring.cloud.gateway.routes`:
```yaml
- id: deal-desk-service
  uri: http://localhost:8085
  predicates:
    - Path=/api/deal-desk/**
  filters:
    - StripPrefix=2
    - name: CircuitBreaker
      args:
        name: dealDeskCircuitBreaker
        fallbackUri: forward:/fallback/deal-desk
  metadata:
    connect-timeout: 1000
    response-timeout: 5000
```

Use `/api/deal-desk/**` (not `/api/v1/deals/**`) to avoid colliding with `crm-service`'s existing deal routes.

Also add a circuit breaker config entry in `resilience4j.circuitbreaker.instances`:
```yaml
dealDeskCircuitBreaker:
  slidingWindowSize: 10
  minimumNumberOfCalls: 5
  waitDurationInOpenState: 5s
  failureRateThreshold: 50
```

Also register the API docs endpoint in the gateway's springdoc aggregation **if you add one** (the
current gateway config does not yet define aggregated service URLs):
```yaml
springdoc:
  swagger-ui:
    urls:
      - name: deal-desk-service
        url: /api/deal-desk/api-docs
```

---

## Step 8 — Shared Library Wiring (Other Services)

### crm-service
The existing `crm-service` deal domain (`com.opencrm.crm.deal`) covers pipeline management
(DealStage: PROSPECTING → WON/LOST). The new `deal-desk-service` covers CPQ-level deal approval
workflow and Salesforce sync. They are **complementary**.

**Add a `DealDeskClient` to crm-service** to allow promoting a CRM deal at PROPOSAL/NEGOTIATION
stage into the deal-desk approval workflow.

**Contract placement (recommended):** create a small local integration contract in `crm-service`
(`crm/integration/dealdesk/`) and map from CRM's `Deal`/`DealDto` into it. Do **not** couple
`crm-service` directly to `deal-desk-service` implementation DTO classes.

**New file:** `backend/services/crm-service/src/main/java/com/opencrm/crm/deal/DealDeskClient.java`
```java
@Component
public class DealDeskClient {
    private final RestClient restClient;

    public DealDeskClient(@Value("${services.deal-desk.url:http://localhost:8085}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Promote a CRM deal into the deal-desk approval workflow */
    public ApiResponse<DealDeskPromotionResponse> promoteDeal(DealDeskPromotionRequest request, String jwtToken) {
        return restClient.post()
            .uri("/api/v1/deals")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<ApiResponse<DealDeskPromotionResponse>>() {});
    }
}
```

Prefer wiring this via a dedicated `RestClient` bean in CRM (same pattern as
`quote-service/src/main/java/com/opencrm/quote/config/RestClientConfig.java`) once the endpoint
contract stabilizes.

**Add to `crm-service/pom.xml`** — no new library dependency (RestClient is in `spring-boot-starter-web`).

**Add to `crm-service/src/main/resources/application.yml`:**
```yaml
services:
  deal-desk:
    url: ${DEAL_DESK_SERVICE_URL:http://localhost:8085}
```

**Wire promotion in `DealService`** — when a CRM deal advances to NEGOTIATION stage and a flag
`promote-to-deal-desk: true` is set, call `DealDeskClient.promoteDeal()` to create the CPQ-layer deal.

### quote-service
Quote service orchestrates pricing; it may need to query deal-desk-service for deal approval status
before finalizing a quote. Add a `DealDeskClient` in quote-service if needed.

### rules-engine (libs)
No changes needed — deal-desk-service consumes `libs/rules-engine` as a dependency.

---

## Step 9 — EasyRules `@Rule` Adaptation Notes

The source `DealValidationRules` uses nested static inner classes annotated with `@Rule`.
This pattern works with EasyRules 4.1.0. When porting, ensure the rules are registered as beans
in `EasyRulesConfig`:

```java
// EasyRulesConfig.java (adapted)
@Configuration
public class EasyRulesConfig {

    @Bean
    public Rules dealValidationRules(DealValidationRules.RequiredFieldsRule r1,
                                     DealValidationRules.DateValidationRule r2,
                                     DealValidationRules.ValueValidationRule r3) {
        Rules rules = new Rules();
        rules.register(r1);
        rules.register(r2);
        rules.register(r3);
        return rules;
    }

    @Bean
    public Rules dealStatusRules(...) { ... }

    @Bean
    public Rules tCVRules(TCVCalculationRules tcvRules) {
        Rules rules = new Rules();
        rules.register(tcvRules);
        return rules;
    }

    @Bean
    public Rules pricingRules() { return new Rules(); }
}
```

The `RuleEngineConfig` and `RuleEngineProperties` port cleanly — only package rename needed.

---

## Step 10 — Test Infrastructure Adaptation

### Replace MongoTestConfig with PostgreSQL TestContainers

**File:** `backend/services/deal-desk-service/src/test/java/com/opencrm/dealdesk/config/PostgresTestConfig.java`

Pattern from: `backend/services/crm-service/src/test/java/...`

```java
@TestConfiguration
@Testcontainers
public class PostgresTestConfig {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("opencrm")
        .withUsername("postgres")
        .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
```

### Security tests with local JWT resource-server config
Because the service will use a real `SecurityFilterChain` + JWT resource-server config, do **not**
assume the source `TestSecurityConfig` ports cleanly.

Recommended test strategy:
- Use `spring-security-test` request post-processors (e.g., `jwt()`) for authenticated endpoint tests
- Use `security.open=true` in selected integration test profiles only when auth is not under test

### IntegrationTestBase
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
public abstract class IntegrationTestBase { ... }
```

---

## Critical Files to Modify

| File | Change |
|------|--------|
| `backend/pom.xml` | Add `services/deal-desk-service` module; add EasyRules to dependencyManagement |
| `backend/services/gateway-service/src/main/resources/application.yml` | Add deal-desk-service route |
| `backend/services/deal-desk-service/pom.xml` | **Create** — inherits backend parent |
| `backend/services/deal-desk-service/src/main/resources/application.yml` | **Create** — port 8085, schema deal_desk |
| `backend/services/deal-desk-service/src/main/java/com/opencrm/dealdesk/config/SecurityConfig.java` | **Create** — service-local JWT resource-server config |
| All ported Java files | Package rename `com.aciworldwide.dealdesk` → `com.opencrm.dealdesk` |

---

## Reusable Existing Utilities

| Utility | Location | Usage in deal-desk-service |
|---------|----------|---------------------------|
| `BaseEntity` | `backend/libs/common/src/main/java/com/opencrm/common/entity/BaseEntity.java` | Extend in `Deal`, `RuleDefinitionEntity`, `DealRule` |
| `DroolsRulesEngine` | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/drools/DroolsRulesEngine.java` | Replace source's standalone DroolsRuleEngine |
| `RulesCacheManager` | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/cache/RulesCacheManager.java` | Use for rules caching instead of Caffeine RuleCache |
| `SecurityUtils`, `UserContext` | `backend/libs/security/` | Shared security helper methods and user context extraction (not a `SecurityFilterChain`) |
| `build plugin + jacoco check` | `backend/services/crm-service/pom.xml` / `backend/services/pricing-service/pom.xml` | Copy service plugin config incl. 80% JaCoCo check |
| Gateway route pattern | `backend/services/gateway-service/src/main/resources/application.yml` | Copy existing route structure |

---

## Verification

1. **Build the module in isolation:**
   ```bash
   cd backend && mvn install -pl services/deal-desk-service -am -DskipTests
   ```

2. **Build the full backend:**
   ```bash
   cd backend && mvn install -DskipTests
   ```

3. **Run service with local PostgreSQL + Redis:**
   ```bash
    cd backend/services/deal-desk-service
    mvn spring-boot:run -Dspring-boot.run.profiles=dev
    ```

4. **Verify actuator health:** `curl http://localhost:8085/actuator/health`

5. **Verify OpenAPI docs:** `curl http://localhost:8085/api-docs`

6. **Verify deals endpoint:** `curl -H "Authorization: Bearer <jwt>" http://localhost:8085/api/v1/deals`

7. **Run unit tests:**
   ```bash
   mvn test -pl services/deal-desk-service
   ```

8. **Run integration tests** (requires Docker for TestContainers):
   ```bash
   mvn verify -pl services/deal-desk-service
   ```

9. **Verify gateway routing:** With gateway running on 8080, `curl http://localhost:8080/api/deal-desk/api/v1/deals`

10. **Check coverage:** `mvn verify -pl services/deal-desk-service` — JaCoCo report at
    `target/site/jacoco/index.html` (must be ≥80%)

---

## Implementation Order

1. `backend/pom.xml` — add module + EasyRules BOM entries
2. `backend/services/deal-desk-service/pom.xml` — create module POM
3. `application.yml` — create service config
4. Flyway migrations V1-V6
5. Model entities (Deal, RuleDefinitionEntity, DealRule, RuleVersion) — MongoDB→JPA
6. Repositories (MongoRepository→JpaRepository)
7. Package rename across all remaining source files
8. Application class + config classes
9. Rules engine wiring (EasyRules + Drools libs/rules-engine adapter)
10. Create local `SecurityConfig` (JWT resource server); remove MongoTestConfig; create PostgresTestConfig; adapt tests for `jwt()` / `security.open`
11. Gateway route registration
12. Build verification + test run
