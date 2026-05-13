package com.opencrm.ai.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsResourceNotFoundTo404ApiResponse() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFoundException(new ResourceNotFoundException("Agent", "missing-agent"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getErrorCode());
        assertEquals("Agent not found with identifier: missing-agent", response.getBody().getMessage());
    }

    @Test
    void mapsUnexpectedExceptionTo500ApiResponse() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGenericException(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getErrorCode());
    }
}
