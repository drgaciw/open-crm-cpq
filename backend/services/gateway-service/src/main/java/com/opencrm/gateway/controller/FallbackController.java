package com.opencrm.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

    @RequestMapping(value = "/crm", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> crmFallback() {
        return buildFallbackResponse("CRM Service");
    }

    @GetMapping("/cpq")
    public ResponseEntity<Map<String, Object>> cpqFallback() {
        return buildFallbackResponse("CPQ Service");
    }

    @RequestMapping(value = "/communication", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> communicationFallback() {
        return buildFallbackResponse("Communication Service");
    }

    @RequestMapping(value = "/ai", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> aiFallback() {
        return buildFallbackResponse("AI Service");
    }

    @RequestMapping(value = "/pricebook", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> pricebookFallback() {
        return buildFallbackResponse("Price Book Service");
    }

    @RequestMapping(value = "/pricing", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> pricingFallback() {
        return buildFallbackResponse("Pricing Service");
    }

    @RequestMapping(value = "/product", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> productFallback() {
        return buildFallbackResponse("Product Service");
    }

    @RequestMapping(value = "/quote", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> quoteFallback() {
        return buildFallbackResponse("Quote Service");
    }

    @RequestMapping(value = "/configuration", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> configurationFallback() {
        return buildFallbackResponse("Configuration Engine");
    }

    @RequestMapping(value = "/approval", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> approvalFallback() {
        return buildFallbackResponse("Approval Routing Service");
    }

    @RequestMapping(value = "/document", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> documentFallback() {
        return buildFallbackResponse("Document Generation Service");
    }

    @RequestMapping(value = "/contract", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> contractFallback() {
        return buildFallbackResponse("Contract/Asset Management Service");
    }

    /**
     * S2A-7: reserved agent-contract ERP paths until erp-adapter-service exists (CPQ_API_CONTRACT_MATRIX §7c, §9).
     */
    @RequestMapping(value = "/erp/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE
    })
    public ResponseEntity<Map<String, Object>> erpNotImplemented() {
        log.warn("ERP stub: agent-contract path invoked before erp-adapter-service is available");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "success", false,
                        "message", "ERP adapter is not yet available. Agent-contract ERP paths are reserved at the gateway.",
                        "service", "erp-adapter-service",
                        "timestamp", Instant.now().toString()
                ));
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
