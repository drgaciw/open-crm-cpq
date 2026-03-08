package com.opencrm.dealdesk.rules.exception;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.dealdesk.rules.controller.RuleDefinitionController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RuleDefinitionController.class)
public class RuleDefinitionExceptionHandler {

    @ExceptionHandler(RuleDefinitionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuleDefinitionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "RULE_DEFINITION_NOT_FOUND", null));
    }

    @ExceptionHandler(RuleDefinitionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuleDefinitionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "RULE_DEFINITION_CONFLICT", null));
    }

    @ExceptionHandler(RuleDefinitionValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(RuleDefinitionValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage(), "RULE_DEFINITION_VALIDATION_ERROR", null));
    }
}
