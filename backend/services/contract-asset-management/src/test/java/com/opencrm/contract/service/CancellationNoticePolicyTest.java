package com.opencrm.contract.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("CancellationNoticePolicy - global notice guard")
class CancellationNoticePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final int NOTICE_DAYS = 30;

    @Test
    @DisplayName("rejects a cancel inside the notice window")
    void rejectsInsideTheNoticeWindow() {
        Subscription subscription = subscriptionEnding(TODAY.plusDays(1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy(NOTICE_DAYS).enforce(subscription));

        assertEquals(CancellationNoticePolicy.ERROR_CODE, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
    }

    @Test
    @DisplayName("allows a cancel outside the notice window")
    void allowsOutsideTheNoticeWindow() {
        Subscription subscription = subscriptionEnding(TODAY.plusDays(NOTICE_DAYS + 10));

        assertDoesNotThrow(() -> policy(NOTICE_DAYS).enforce(subscription));
    }

    @Test
    @DisplayName("allows a cancel on the exact boundary day")
    void allowsTheExactBoundaryDay() {
        Subscription subscription = subscriptionEnding(TODAY.plusDays(NOTICE_DAYS));

        assertDoesNotThrow(() -> policy(NOTICE_DAYS).enforce(subscription));
    }

    @Test
    @DisplayName("rejects a cancel one day past the boundary")
    void rejectsOneDayPastTheBoundary() {
        Subscription subscription = subscriptionEnding(TODAY.plusDays(NOTICE_DAYS - 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy(NOTICE_DAYS).enforce(subscription));

        assertEquals(CancellationNoticePolicy.ERROR_CODE, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @DisplayName("rejects when the clock moves one day past the boundary")
    void rejectsWhenTheClockMovesOneDayPastTheBoundary() {
        Clock nextDay = Clock.fixed(TODAY.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        Subscription subscription = subscriptionEnding(TODAY.plusDays(NOTICE_DAYS));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> new CancellationNoticePolicy(NOTICE_DAYS, nextDay).enforce(subscription));

        assertEquals(CancellationNoticePolicy.ERROR_CODE, ex.getErrorCode());
    }

    @Test
    @DisplayName("skips the guard when endDate is null")
    void skipsGuardWhenEndDateIsNull() {
        Subscription subscription = subscriptionEnding(null);

        assertDoesNotThrow(() -> policy(NOTICE_DAYS).enforce(subscription));
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
    }

    @Test
    @DisplayName("a zero notice period does not reject, including after endDate")
    void zeroNoticeDaysDoesNotRejectAfterEndDate() {
        Subscription subscription = subscriptionEnding(TODAY.minusDays(1));

        assertDoesNotThrow(() -> policy(0).enforce(subscription));
    }

    @Test
    @DisplayName("NOTICE_PERIOD_ELAPSED is returned as HTTP 400")
    void noticePeriodElapsedIsAClientError() {
        Subscription subscription = subscriptionEnding(TODAY.plusDays(1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy(NOTICE_DAYS).enforce(subscription));

        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(CancellationNoticePolicy.ERROR_CODE, response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("CAM application.yml binds the global notice default to 0")
    void applicationYamlBindsGlobalNoticeDaysToZero() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        String configured = null;
        for (PropertySource<?> source : sources) {
            Object raw = source.getProperty("opencrm.contract.cancellation.notice-days");
            if (raw != null) {
                configured = String.valueOf(raw);
            }
        }
        assertEquals("0", configured);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            for (int i = sources.size() - 1; i >= 0; i--) {
                context.getEnvironment().getPropertySources().addFirst(sources.get(i));
            }
            context.register(CancellationNoticePolicy.class);
            context.refresh();
            assertEquals(0, context.getBean(CancellationNoticePolicy.class).noticeDays());
        }
    }

    private static CancellationNoticePolicy policy(int noticeDays) {
        return new CancellationNoticePolicy(noticeDays, CLOCK);
    }

    private static Subscription subscriptionEnding(LocalDate endDate) {
        return Subscription.builder()
                .subscriptionNumber("SUB-001")
                .customerId("C-001")
                .quoteId("Q-001")
                .status(SubscriptionStatus.ACTIVE)
                .planName("Enterprise Plan")
                .startDate(TODAY.minusMonths(6))
                .endDate(endDate)
                .currencyCode("USD")
                .build();
    }
}
