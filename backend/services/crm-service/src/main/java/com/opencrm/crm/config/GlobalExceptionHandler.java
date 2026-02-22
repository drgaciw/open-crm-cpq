package com.opencrm.crm.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the CRM service.
 * Handles all exceptions thrown by controllers and translates them into appropriate HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * Handles ResourceNotFoundException - typically when a requested entity is not found.
     *
     * @param ex the exception
     * @return 404 Not Found response
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        logger.warn("Resource not found: {} [traceId={}]", ex.getMessage(), traceId);

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                "RESOURCE_NOT_FOUND",
                traceId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles BusinessException - domain-specific business rule violations.
     *
     * @param ex the exception
     * @return response with the exception's specified status code
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        HttpStatus status = ex.getStatus();

        if (status.is5xxServerError()) {
            logger.error("Business exception: {} [traceId={}]", ex.getMessage(), traceId, ex);
        } else {
            logger.warn("Business exception: {} [traceId={}]", ex.getMessage(), traceId);
        }

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                ex.getErrorCode(),
                traceId
        );

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handles MethodArgumentNotValidException - validation errors on request body fields.
     *
     * @param ex the exception
     * @return 422 Unprocessable Entity response with field-level errors
     */
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

        ApiResponse<Void> response = ApiResponse.error(
                errorMessage,
                "VALIDATION_ERROR",
                traceId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handles ConstraintViolationException - bean validation constraint violations.
     *
     * @param ex the exception
     * @return 422 Unprocessable Entity response with violation details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        String violations = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " - " + violation.getMessage())
                .collect(Collectors.joining(", "));

        String errorMessage = "Constraint violation: " + violations;

        logger.warn("Constraint violation: {} [traceId={}]", violations, traceId);

        ApiResponse<Void> response = ApiResponse.error(
                errorMessage,
                "CONSTRAINT_VIOLATION",
                traceId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handles DataIntegrityViolationException - database constraint violations.
     *
     * @param ex the exception
     * @return 409 Conflict response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        String errorMessage = "Data integrity violation: The operation conflicts with existing data constraints.";

        // Log the full exception for debugging purposes
        logger.warn("Data integrity violation [traceId={}]: {}", traceId, ex.getMessage(), ex);

        ApiResponse<Void> response = ApiResponse.error(
                errorMessage,
                "DATA_INTEGRITY_VIOLATION",
                traceId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles all other uncaught exceptions.
     *
     * @param ex the exception
     * @return 500 Internal Server Error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        logger.error("Unhandled exception [traceId={}]: {}", traceId, ex.getMessage(), ex);

        ApiResponse<Void> response = ApiResponse.error(
                "An unexpected error occurred. Please contact support if the problem persists.",
                "INTERNAL_SERVER_ERROR",
                traceId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
