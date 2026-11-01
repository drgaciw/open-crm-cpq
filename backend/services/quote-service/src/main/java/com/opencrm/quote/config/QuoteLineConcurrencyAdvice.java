package com.opencrm.quote.config;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.quote.lineitem.QuoteLineItemController;
import com.opencrm.quote.ramp.RampScheduleController;
import com.opencrm.quote.quote.QuoteController;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Optimistic conflicts on quote and quote-line writes, including failures raised at transaction commit. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {QuoteLineItemController.class, RampScheduleController.class, QuoteController.class})
public class QuoteLineConcurrencyAdvice {
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "Quote changed; reload before editing", "QUOTE_VERSION_CONFLICT", MDC.get("traceId")));
    }
}
