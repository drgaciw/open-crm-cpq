package com.opencrm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Service - Entry point for all client requests.
 *
 * Responsibilities:
 * - Route requests to appropriate microservices
 * - Authentication: backend-for-frontend login against Microsoft Entra ID, plus a bearer lane for agents (ADR-016)
 * - Rate limiting
 * - Request/Response logging
 * - Circuit breaker patterns
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
