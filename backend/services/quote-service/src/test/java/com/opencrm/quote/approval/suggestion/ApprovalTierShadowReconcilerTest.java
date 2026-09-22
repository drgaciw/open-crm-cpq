package com.opencrm.quote.approval.suggestion;

import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.quote.approval.ApprovalService;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalTierShadowReconciler")
class ApprovalTierShadowReconcilerTest {

    private static final String USE_CASE = "quote-tier-suggestion";

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private JevDecisionLogStore decisionLog;

    private static Quote quoteWithTier(String tier) {
        Quote quote = new Quote();
        quote.setApprovalTier(tier);
        return quote;
    }

    @Test
    @DisplayName("flag off: touches nothing")
    void flagOff() {
        new ApprovalTierShadowReconciler(quoteRepository, decisionLog, false).reconcile();

        verifyNoInteractions(quoteRepository, decisionLog);
    }

    @Test
    @DisplayName("records the tier the user picked as the authoritative outcome")
    void recordsUserTier() {
        when(decisionLog.findSubjectsAwaitingOutcome(USE_CASE, "quote", 100)).thenReturn(List.of("7"));
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quoteWithTier("CFO")));

        new ApprovalTierShadowReconciler(quoteRepository, decisionLog, true).reconcile();

        verify(decisionLog).recordOutcome(USE_CASE, "quote", "7", "CFO", JevDecisionRecord.AuthoritativeSource.USER);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("skips quotes with no tier yet, a non-ApprovalTier value, a missing quote or a bad id")
    void skipsIncomparable() {
        when(decisionLog.findSubjectsAwaitingOutcome(USE_CASE, "quote", 100))
                .thenReturn(List.of("1", "2", "3", "not-a-number"));
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quoteWithTier(null)));
        when(quoteRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(quoteWithTier("TIER_2_PRICING")));
        when(quoteRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.empty());

        new ApprovalTierShadowReconciler(quoteRepository, decisionLog, true).reconcile();

        verify(decisionLog, never()).recordOutcome(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a failure never escapes the scheduler thread")
    void neverThrows() {
        when(decisionLog.findSubjectsAwaitingOutcome(anyString(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new ApprovalTierShadowReconciler(quoteRepository, decisionLog, true).reconcile())
                .doesNotThrowAnyException();
        verify(quoteRepository, never()).findByIdAndDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("ApprovalService does not depend on the suggestion package (ADR-005 / ADR-013 untouched)")
    void approvalServiceIsIndependent() {
        List<Class<?>> dependencies = new java.util.ArrayList<>();
        for (Field field : ApprovalService.class.getDeclaredFields()) {
            dependencies.add(field.getType());
        }
        Arrays.stream(ApprovalService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameters()))
                .map(Parameter::getType)
                .forEach(dependencies::add);

        assertThat(dependencies).isNotEmpty().noneMatch(type ->
                type.getName().startsWith("com.opencrm.quote.approval.suggestion")
                        || type.getName().startsWith("com.opencrm.jev"));
    }
}
