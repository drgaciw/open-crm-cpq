package com.opencrm.document.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.document.controller.ClauseController;
import com.opencrm.document.controller.DocumentRenditionController;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {ClauseController.class, DocumentRenditionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClauseRequestExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                "Invalid clause or rendition request", "VALIDATION_ERROR", MDC.get("traceId")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "Conflicting clause revision or rendition", "CLAUSE_CONFLICT", MDC.get("traceId")));
    }
}
