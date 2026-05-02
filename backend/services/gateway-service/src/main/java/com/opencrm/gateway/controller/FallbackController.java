package com.opencrm.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller for circuit breaker responses.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/crm")
    public ResponseEntity<Map<String, Object>> crmFallback() {
        return buildFallbackResponse("CRM Service");
    }

    @GetMapping("/cpq")
    public ResponseEntity<Map<String, Object>> cpqFallback() {
        return buildFallbackResponse("CPQ Service");
    }

    @GetMapping("/communication")
    public ResponseEntity<Map<String, Object>> communicationFallback() {
        return buildFallbackResponse("Communication Service");
    }

    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> aiFallback() {
        return buildFallbackResponse("AI Service");
    }

    @GetMapping("/pricebook")
    public ResponseEntity<Map<String, Object>> pricebookFallback() {
        return buildFallbackResponse("Price Book Service");
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String serviceName) {
        log.error("CIRCUIT_BREAKER: {} is unavailable - returning fallback response", serviceName);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "message", serviceName + " is currently unavailable. Please try again later.",
                        "timestamp", Instant.now().toString()
                ));
    }
}
