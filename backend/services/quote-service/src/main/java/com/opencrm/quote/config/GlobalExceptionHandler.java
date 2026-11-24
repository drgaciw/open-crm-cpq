package com.opencrm.quote.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.approval.PolicyBlockedException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        logger.warn("Resource not found: {} [traceId={}]", ex.getMessage(), traceId);
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), "RESOURCE_NOT_FOUND", traceId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(PolicyBlockedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePolicyBlocked(PolicyBlockedException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        logger.warn("Policy blocked: {} {} [traceId={}]", ex.getReasonCodes(), ex.getMessage(), traceId);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .errors(ex.getReasonCodes())
                .traceId(traceId)
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        HttpStatus status = ex.getStatus();
        if (status.is5xxServerError()) {
            logger.error("Business exception: {} [traceId={}]", ex.getMessage(), traceId, ex);
        } else {
            logger.warn("Business exception: {} [traceId={}]", ex.getMessage(), traceId);
        }
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), ex.getErrorCode(), traceId);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        String errorMessage = "Validation failed: " + fieldErrors.entrySet().stream()
                .map(entry -> entry.getKey() + " - " + entry.getValue())
                .collect(Collectors.joining(", "));
        logger.warn("Validation failed: {} [traceId={}]", fieldErrors, traceId);
        ApiResponse<Void> response = ApiResponse.error(errorMessage, "VALIDATION_ERROR", traceId);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        String violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " - " + v.getMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Constraint violation: {} [traceId={}]", violations, traceId);
        ApiResponse<Void> response = ApiResponse.error("Constraint violation: " + violations, "CONSTRAINT_VIOLATION", traceId);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        logger.warn("Data integrity violation [traceId={}]: {}", traceId, ex.getMessage(), ex);
        ApiResponse<Void> response = ApiResponse.error("Data integrity violation: The operation conflicts with existing data constraints.", "DATA_INTEGRITY_VIOLATION", traceId);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /** A required query parameter is absent: the caller's error, not a server fault (UAT 2026-11-14). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        String message = "Required request parameter '" + ex.getParameterName() + "' is missing";
        logger.warn("{} [traceId={}]", message, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR", traceId));
    }

    /** A path variable or parameter that does not convert (e.g. /quotes/search for /quotes/{id}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        String message = "Invalid value '" + ex.getValue() + "' for '" + ex.getName() + "'";
        logger.warn("{} [traceId={}]", message, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        logger.error("Unhandled exception [traceId={}]: {}", traceId, ex.getMessage(), ex);
        ApiResponse<Void> response = ApiResponse.error("An unexpected error occurred. Please contact support if the problem persists.", "INTERNAL_SERVER_ERROR", traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
