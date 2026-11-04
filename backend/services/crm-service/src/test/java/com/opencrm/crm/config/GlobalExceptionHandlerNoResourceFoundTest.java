package com.opencrm.crm.config;

import com.opencrm.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UAT against crm-service (:8081) probes sibling CPQ paths such as {@code /api/v1/assets}.
 * Spring Boot maps unmapped routes to {@link NoResourceFoundException}; that must be HTTP 404,
 * not the generic 500 INTERNAL_SERVER_ERROR path.
 */
class GlobalExceptionHandlerNoResourceFoundTest {

    @Test
    void unmappedApiPathReturnsNotFoundNotInternalError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/v1/assets", "api/v1/assets");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("api/v1/assets");
    }
}
