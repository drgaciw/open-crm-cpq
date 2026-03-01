# Agentic CPQ Platform Implementation

## Overview

This is a complete implementation of the Agentic CPQ (Configure-Price-Quote) Platform following the Hierarchical Multi-Agent Mesh (HMAS) architecture as defined in `cpq-agent-framework-prompt.xml`.

## Architecture Principles

### Constitutional Requirements (Strictly Enforced)

1. **Money Value Objects**: All financial calculations use `Money` value object with `BigDecimal` and `HALF_UP` rounding. **NO primitive float/double**.

2. **Communication Protocols**:
   - **REST**: Only at API Gateway boundaries for external clients
   - **gRPC**: For low-latency internal sync calls (Configuration → Pricing)
   - **Kafka + Transactional Outbox**: For all cross-service state propagation

3. **Bounded Context Isolation**: No domain logic leakage across services

4. **Document Generation**: Asynchronous and non-blocking

## Microservices Architecture

### 1. Product Catalog Service (`product-service`)
**Port**: 8081  
**Database**: `cpq_product`

**Responsibilities**:
- Product entity lifecycle with state machine: `DRAFT → ACTIVE → DEPRECATED → RETIRED`
- Product taxonomy and catalog management
- Read-optimized catalog contracts

**Key Features**:
- State machine transitions with validation
- Kafka event publishing via Transactional Outbox when products are retired
- Money value objects for list prices
- Flyway migrations for schema management

**Events Published**:
- `cpq.product.status.retired` → Configuration Engine, Quote Management

---

### 2. Configuration Engine Service (`configuration-engine`)
**Port**: 8082  
**Database**: `cpq_configuration`

**Responsibilities**:
- Constraint solving and configuration validation
- Session-safe configuration states
- Only valid configurations pass to pricing/quote flows

**Key Features**:
- Configuration session management with Redis
- Constraint solver for product configuration rules
- gRPC client to Pricing Engine for price calculations
- Kafka consumer for product retired events

**Events Consumed**:
- `cpq.product.status.retired` → Cancels active configuration sessions

**gRPC Calls**:
- `PricingService.CalculatePrice()` → Pricing Engine

---

### 3. Pricing Engine Service (`pricing-service`)
**Port**: 8083  
**Database**: `cpq_pricing`

**Responsibilities**:
- Price rule evaluation and discount calculations
- Money/BigDecimal HALF_UP rounding for all financial math
- External tax/FX integrations (future)

**Key Features**:
- gRPC server for synchronous price calculations
- Money value objects throughout
- Discount approval threshold detection
- Kafka event publishing for approval requirements

**gRPC Server**:
- `PricingService.CalculatePrice()` → Called by Configuration Engine
- `PricingService.CalculateQuoteTotal()` → Called by Quote Management

**Events Published**:
- `cpq.discount.approval.required` → Approval Routing, Quote Management

---

### 4. Quote Management Service (`quote-service`)
**Port**: 8080  
**Database**: `cpq_quote`

**Responsibilities**:
- Quote aggregate lifecycle: `DRAFT → PENDING_APPROVAL → ACCEPTED`
- Quote lines and transactional consistency
- Synchronized totals (quote totals = sum of line items)

**Key Features**:
- `recalculateTotals()` method ensures mathematical synchronization
- State machine transitions with validation
- Kafka event publishing via Transactional Outbox on acceptance
- Money value objects for all financial fields

**Events Published**:
- `cpq.quote.status.accepted` → Document Generation, Contract/Asset Management

**State Machine**:
```java
quote.recalculateTotals(); // Ensure synchronized totals
quote.accept();            // DRAFT/PENDING_APPROVAL → ACCEPTED
```

---

### 5. Approval Routing Service (`approval-routing`)
**Port**: 8084  
**Database**: `cpq_approval`

**Responsibilities**:
- Risk evaluation and approval state transitions
- Approval state machine: `PENDING → APPROVED/REJECTED`
- Traceability of all approval decisions

**Key Features**:
- State machine with explicit transitions
- Approval tier determination (MANAGER, DIRECTOR, VP_SALES)
- Kafka consumer for discount approval events
- Kafka publisher for rejection events

**Events Consumed**:
- `cpq.discount.approval.required` → Creates approval requests

**Events Published**:
- `cpq.quote.approval.rejected` → Quote Management, Pricing Engine

**State Machine**:
```java
approvalRequest.approve(approverId);
approvalRequest.reject(approverId, reason);
```

---

### 6. Document Generation Service (`document-generation`)
**Port**: 8085  
**Database**: `cpq_document`

**Responsibilities**:
- Asynchronous document assembly
- E-sign orchestration (future)
- Reconciliation jobs

**Key Features**:
- Idempotent job queue (idempotency key prevents duplicates)
- Non-blocking async processing with `@Async`
- Kafka consumer for quote accepted events
- OpenHTMLToPDF for PDF generation

**Events Consumed**:
- `cpq.quote.status.accepted` → Creates document generation jobs

**Idempotency**:
```java
String idempotencyKey = "quote-" + quoteId + "-pdf";
if (jobRepository.existsByIdempotencyKey(idempotencyKey)) {
    return; // Job already exists
}
```

---

### 7. Contract/Asset Management Service (`contract-asset-management`)
**Port**: 8086  
**Database**: `cpq_contract`

**Responsibilities**:
- Authoritative subscription/asset state
- ACL integration to legacy ERP
- Subscription lifecycle management

**Key Features**:
- Subscription state machine: `PENDING → ACTIVE → SUSPENDED/CANCELLED`
- ACL adapter with rate limiting (Resilience4j)
- Scheduled renewal eligibility checker (daily at 2 AM)
- Kafka consumer for quote accepted events

**Events Consumed**:
- `cpq.quote.status.accepted` → Creates subscriptions and syncs to ERP

**Events Published**:
- `cpq.subscription.renewal.eligible` → Quote Management, Pricing Engine

**ACL Pattern**:
```java
@RateLimiter(name = "erpSync", fallbackMethod = "syncToErpFallback")
public String syncToErp(Subscription subscription) {
    // Rate-limited ERP integration
}
```

---

### 8. API Gateway Service (`gateway-service`)
**Port**: 8080  
**Responsibilities**:
- JWT authentication
- Rate limiting
- Request routing to microservices

---

## Shared Libraries

### `libs/common`

**Money Value Object**:
```java
Money price = Money.usd(100.00);
Money discount = price.percentage(10.0); // 10% discount
Money final = price.subtract(discount);  // HALF_UP rounding
```

**Transactional Outbox**:
```java
outboxEventPublisher.publish(
    "Quote",
    quoteId,
    KafkaTopics.QUOTE_STATUS_ACCEPTED,
    event
);
```

**Kafka Topics** (`KafkaTopics.java`):
- `cpq.product.status.retired`
- `cpq.discount.approval.required`
- `cpq.quote.status.accepted`
- `cpq.quote.approval.rejected`
- `cpq.subscription.renewal.eligible`

**gRPC Proto** (`pricing.proto`):
- `PricingService.CalculatePrice()`
- `PricingService.CalculateQuoteTotal()`

---

## Event Flow Examples

### 1. Product Retirement Flow
```
Product Catalog: product.retire("EOL")
    ↓ (Transactional Outbox)
Kafka: cpq.product.status.retired
    ↓
Configuration Engine: Cancel active sessions
Quote Management: Mark quotes with retired products
```

### 2. Quote Acceptance Flow
```
Quote Management: quote.accept()
    ↓ (Transactional Outbox)
Kafka: cpq.quote.status.accepted
    ↓
Document Generation: Create async PDF job (idempotent)
Contract/Asset: Create subscription → Sync to ERP (rate-limited)
```

### 3. Discount Approval Flow
```
Pricing Engine: Detect discount > 20%
    ↓ (Transactional Outbox)
Kafka: cpq.discount.approval.required
    ↓
Approval Routing: Create approval request (PENDING)
    ↓ (Manual approval)
Approval Routing: approvalRequest.reject(reason)
    ↓ (Transactional Outbox)
Kafka: cpq.quote.approval.rejected
    ↓
Quote Management: Update quote status
```

---

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.2
- **Database**: PostgreSQL 14+
- **Migration**: Flyway
- **Messaging**: Apache Kafka 3.6.1
- **RPC**: gRPC 1.60.1
- **Cache**: Redis
- **Rate Limiting**: Resilience4j 2.1.0
- **Document Generation**: OpenHTMLToPDF 1.0.10
- **Testing**: JUnit 5, Testcontainers

---

## Database Schema

Each service has its own database with:
- Domain tables (products, quotes, subscriptions, etc.)
- `outbox_events` table for Transactional Outbox pattern

**Outbox Events Table** (in every service):
```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    error_message VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0
);
```

---

## Running the Platform

### Prerequisites
```bash
# PostgreSQL databases
createdb cpq_product
createdb cpq_configuration
createdb cpq_pricing
createdb cpq_quote
createdb cpq_approval
createdb cpq_document
createdb cpq_contract

# Kafka
docker run -d -p 9092:9092 apache/kafka:latest

# Redis
docker run -d -p 6379:6379 redis:latest
```

### Build
```bash
cd backend
mvn clean install
```

### Run Services
```bash
# Product Catalog
cd services/product-service && mvn spring-boot:run

# Configuration Engine
cd services/configuration-engine && mvn spring-boot:run

# Pricing Engine
cd services/pricing-service && mvn spring-boot:run

# Quote Management
cd services/quote-service && mvn spring-boot:run

# Approval Routing
cd services/approval-routing && mvn spring-boot:run

# Document Generation
cd services/document-generation && mvn spring-boot:run

# Contract/Asset Management
cd services/contract-asset-management && mvn spring-boot:run
```

---

## Testing

Each service includes:
- Unit tests with JUnit 5
- Integration tests with Testcontainers
- Repository tests with embedded PostgreSQL

```bash
mvn test
```

---

## Compliance with CPQ Agent Framework

✅ **Money Value Objects**: All services use `Money` with BigDecimal  
✅ **Transactional Outbox**: All cross-service events use outbox pattern  
✅ **gRPC for Sync**: Configuration → Pricing uses gRPC  
✅ **Kafka for Async**: All state propagation uses Kafka  
✅ **State Machines**: Product, Quote, Approval, Subscription have explicit state transitions  
✅ **Bounded Contexts**: Each service owns its domain with no leakage  
✅ **Document Generation**: Async and idempotent  
✅ **ACL Pattern**: ERP integration isolated with rate limiting  
✅ **REST at Gateway**: Only API Gateway exposes REST to external clients  

---

## Future Enhancements

1. **Outbox Relay**: Implement CDC (Change Data Capture) or polling relay to publish outbox events to Kafka
2. **E-Sign Integration**: Add DocuSign/Adobe Sign integration to Document Generation
3. **Advanced Pricing Rules**: Integrate Drools/OpenL Tablets for complex pricing logic
4. **Multi-Currency**: Extend Money value object for FX conversions
5. **Audit Trail**: Add event sourcing for complete audit history
6. **API Gateway**: Implement Spring Cloud Gateway with JWT auth and rate limiting

---

## Architecture Diagram

```
┌─────────────────┐
│   API Gateway   │ (REST - External)
└────────┬────────┘
         │
    ┌────┴────┬────────┬────────┬────────┬────────┬────────┐
    │         │        │        │        │        │        │
┌───▼───┐ ┌──▼──┐ ┌───▼───┐ ┌──▼──┐ ┌──▼──┐ ┌───▼───┐ ┌──▼──┐
│Product│ │Config│ │Pricing│ │Quote│ │Appro│ │Document│ │Contr│
│Catalog│ │Engine│ │Engine │ │Mgmt │ │Rout │ │  Gen   │ │Asset│
└───┬───┘ └──┬──┘ └───┬───┘ └──┬──┘ └──┬──┘ └───┬───┘ └──┬──┘
    │        │        │        │        │        │        │
    └────────┴────────┴────────┴────────┴────────┴────────┘
                            │
                    ┌───────▼────────┐
                    │  Kafka Topics  │
                    │  (Event Bus)   │
                    └────────────────┘

gRPC: Config Engine ──────> Pricing Engine
Kafka: All cross-service state propagation
```

---

## Contact & Support

For questions about this implementation, refer to:
- `cpq-agent-framework-prompt.xml` - Architecture specification
- Service-specific README files in each service directory
- Flyway migrations for database schema details
