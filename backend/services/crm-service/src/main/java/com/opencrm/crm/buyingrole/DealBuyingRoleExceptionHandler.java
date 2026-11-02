package com.opencrm.crm.buyingrole;

import com.opencrm.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Rejects a buying role that is not one of the closed enum values.
 * Scoped to this controller so other CRM payloads keep the existing handler.
 */
@RestControllerAdvice(assignableTypes = DealBuyingRoleController.class)
public class DealBuyingRoleExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DealBuyingRoleExceptionHandler.class);
    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (isUnknownBuyingRole(ex)) {
            logger.warn("Rejected buying role outside the closed list [traceId={}]", traceId);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.error(
                    "Buying role must be one of DECISION_MAKER, CHAMPION, ECONOMIC_BUYER, "
                            + "BILLING_CONTACT, TECHNICAL_EVALUATOR, BLOCKER",
                    DealBuyingRoleService.INVALID,
                    traceId));
        }
        logger.warn("Unreadable buying-role request [traceId={}]", traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(
                "Request body could not be read",
                "MALFORMED_REQUEST",
                traceId));
    }

    static boolean isUnknownBuyingRole(Throwable error) {
        for (Throwable cause = error; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(".BuyingRole")) {
                return true;
            }
        }
        return false;
    }
}
