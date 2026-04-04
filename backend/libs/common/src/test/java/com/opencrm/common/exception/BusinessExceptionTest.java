package com.opencrm.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BusinessException")
class BusinessExceptionTest {

    @Test
    @DisplayName("should create BusinessException with message")
    void createWithMessage() {
        String message = "Test message";
        BusinessException exception = new BusinessException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("BUSINESS_ERROR", exception.getErrorCode());
    }

    @Test
    @DisplayName("should create BusinessException with message and status")
    void createWithMessageAndStatus() {
        String message = "Test message";
        HttpStatus status = HttpStatus.NOT_FOUND;
        BusinessException exception = new BusinessException(message, status);

        assertEquals(message, exception.getMessage());
        assertEquals(status, exception.getStatus());
        assertEquals("BUSINESS_ERROR", exception.getErrorCode());
    }

    @Test
    @DisplayName("should create BusinessException with message and errorCode")
    void createWithMessageAndErrorCode() {
        String message = "Test message";
        String errorCode = "CUSTOM_ERROR_CODE";
        BusinessException exception = new BusinessException(message, errorCode);

        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    @DisplayName("should create BusinessException with message, status, and errorCode")
    void createWithMessageStatusAndErrorCode() {
        String message = "Test message";
        HttpStatus status = HttpStatus.CONFLICT;
        String errorCode = "CUSTOM_ERROR_CODE";
        BusinessException exception = new BusinessException(message, status, errorCode);

        assertEquals(message, exception.getMessage());
        assertEquals(status, exception.getStatus());
        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    @DisplayName("should create BusinessException with message and cause")
    void createWithMessageAndCause() {
        String message = "Test message";
        Throwable cause = new RuntimeException("Test cause");
        BusinessException exception = new BusinessException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
        assertEquals("BUSINESS_ERROR", exception.getErrorCode());
        assertEquals(cause, exception.getCause());
    }
}
