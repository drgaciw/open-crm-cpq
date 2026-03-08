package com.opencrm.dealdesk.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.dealdesk.exception.DealNotFoundException;
import com.opencrm.dealdesk.exception.InvalidDealStateException;
import com.opencrm.dealdesk.exception.SalesforceIntegrationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DealNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDealNotFound(DealNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "DEAL_NOT_FOUND", null));
    }

    @ExceptionHandler(InvalidDealStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDealState(InvalidDealStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_DEAL_STATE", null));
    }

    @ExceptionHandler(SalesforceIntegrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSalesforceIntegration(SalesforceIntegrationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ex.getMessage(), "SALESFORCE_INTEGRATION_ERROR", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "Validation failed";
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(message, "VALIDATION_ERROR", null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage(), "CONSTRAINT_VIOLATION", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Unexpected server error", "INTERNAL_SERVER_ERROR", null));
    }
}
