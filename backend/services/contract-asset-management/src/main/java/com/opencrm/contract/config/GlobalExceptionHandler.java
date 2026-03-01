package com.opencrm.contract.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.acl.ErpRateLimitException;
import com.opencrm.contract.acl.ErpSyncException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("Resource not found: {} [traceId={}]", ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "RESOURCE_NOT_FOUND", traceId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("Business exception: {} [traceId={}]", ex.getMessage(), traceId);
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode(), traceId));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("Illegal state: {} [traceId={}]", ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_STATE_TRANSITION", traceId));
    }

    @ExceptionHandler(ErpRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleErpRateLimit(ErpRateLimitException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("ERP rate limit exceeded [traceId={}]", traceId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage(), "ERP_RATE_LIMIT_EXCEEDED", traceId));
    }

    @ExceptionHandler(ErpSyncException.class)
    public ResponseEntity<ApiResponse<Void>> handleErpSync(ErpSyncException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.error("ERP sync error [traceId={}]", traceId, ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ex.getMessage(), "ERP_SYNC_FAILED", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.error("Unhandled exception [traceId={}]", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred.", "INTERNAL_SERVER_ERROR", traceId));
    }
}
