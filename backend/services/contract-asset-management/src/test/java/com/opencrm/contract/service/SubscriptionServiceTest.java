package com.opencrm.contract.service;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.acl.ErpSyncException;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.dto.SubscriptionDto;
import com.opencrm.contract.mapper.SubscriptionMapper;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService - DTO + lifecycle")
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ErpAdapter erpAdapter;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock private SubscriptionRateApplyService rateApply;

    @Mock
    private CancellationNoticePolicy cancellationNotice;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final int NOTICE_DAYS = 30;

    private Subscription pendingSubscription;

    @BeforeEach
    void setUp() {
        pendingSubscription = Subscription.builder()
                .subscriptionNumber("SUB-001")
                .customerId("C-001")
                .quoteId("Q-001")
                .status(SubscriptionStatus.PENDING)
                .planName("Enterprise Plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .renewalDate(LocalDate.now().plusYears(1).minusDays(30))
                .arr(BigDecimal.valueOf(12000.00))
                .currencyCode("USD")
                .erpSyncStatus("PENDING")
                .build();
        pendingSubscription.setId(1L);
    }

    @Test void activationAppliesDueRatesBeforeSyntheticErpSnapshot() {
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingSubscription));
        when(subscriptionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(erpAdapter.syncToErp(any())).thenReturn("ERP-SUB-001");
        subscriptionService.activate(1L);
        var ordered = inOrder(subscriptionRepository, rateApply, erpAdapter);
        ordered.verify(subscriptionRepository).findByIdForUpdate(1L);
        ordered.verify(rateApply).applyLocked(pendingSubscription);
        ordered.verify(erpAdapter).syncToErp(pendingSubscription);
    }

    @Test
    @DisplayName("list returns paginated DTOs")
    void listPaginated() {
        Pageable pageable = PageRequest.of(0, 20);
        when(subscriptionRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(pendingSubscription), pageable, 1));
        when(subscriptionMapper.toDto(pendingSubscription)).thenReturn(dto(pendingSubscription));

        Page<SubscriptionDto> page = subscriptionService.list(pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals("SUB-001", page.getContent().getFirst().getSubscriptionNumber());
        assertEquals(SubscriptionStatus.PENDING, page.getContent().getFirst().getStatus());
    }

    @Test
    @DisplayName("activate syncs ERP, persists ERP fields, returns DTO")
    void activateSyncsErp() {
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingSubscription));
        when(erpAdapter.syncToErp(pendingSubscription)).thenReturn("ERP-SUB-001");
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toDto(any(Subscription.class))).thenAnswer(inv -> dto(inv.getArgument(0)));

        SubscriptionDto result = subscriptionService.activate(1L);

        assertEquals(SubscriptionStatus.ACTIVE, pendingSubscription.getStatus());
        assertEquals("ERP-SUB-001", pendingSubscription.getErpSubscriptionId());
        assertEquals("SYNCED", pendingSubscription.getErpSyncStatus());
        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        assertEquals("ERP-SUB-001", result.getErpSubscriptionId());
        assertEquals("SYNCED", result.getErpSyncStatus());
        verify(erpAdapter).syncToErp(pendingSubscription);
    }

    @Test
    @DisplayName("activate marks ERP FAILED when sync throws (does not remove ErpAdapter call)")
    void activateErpFailurePersistsFailed() {
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingSubscription));
        when(erpAdapter.syncToErp(pendingSubscription))
                .thenThrow(new ErpSyncException("ERP down"));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toDto(any(Subscription.class))).thenAnswer(inv -> dto(inv.getArgument(0)));

        SubscriptionDto result = subscriptionService.activate(1L);

        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        assertEquals("FAILED", result.getErpSyncStatus());
        assertNull(result.getErpSubscriptionId());
        verify(erpAdapter).syncToErp(pendingSubscription);
    }

    @Test
    @DisplayName("suspend / cancel return DTOs and update state")
    void lifecycleReturnsDto() {
        pendingSubscription.activate();
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(pendingSubscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toDto(any(Subscription.class))).thenAnswer(inv -> dto(inv.getArgument(0)));

        LocalDate endBefore = pendingSubscription.getEndDate();
        LocalDate renewalBefore = pendingSubscription.getRenewalDate();

        SubscriptionDto suspended = subscriptionService.suspend(1L);
        assertEquals(SubscriptionStatus.SUSPENDED, suspended.getStatus());
        assertEquals(endBefore, pendingSubscription.getEndDate());
        assertEquals(renewalBefore, pendingSubscription.getRenewalDate());

        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        SubscriptionDto cancelled = subscriptionService.cancel(1L);
        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(endBefore, pendingSubscription.getEndDate());
        assertEquals(renewalBefore, pendingSubscription.getRenewalDate());
    }

    @Test
    @DisplayName("date-only renew is removed from the service and the entity")
    void dateOnlyRenewIsRemoved() {
        assertThrows(NoSuchMethodException.class,
                () -> SubscriptionService.class.getDeclaredMethod("renew", Long.class, LocalDate.class));
        assertThrows(NoSuchMethodException.class,
                () -> Subscription.class.getDeclaredMethod("renew", LocalDate.class));
    }

    @Test
    @DisplayName("getById throws when missing")
    void getByIdNotFound() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> subscriptionService.getById(99L));
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"CANCELLED", "EXPIRED"})
    void rejectedCancellationDoesNotPersist(SubscriptionStatus initial) {
        pendingSubscription.setStatus(initial);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(pendingSubscription));
        lenient().when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalStateException.class, () -> subscriptionService.cancel(1L));

        assertEquals(initial, pendingSubscription.getStatus());
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(erpAdapter, subscriptionMapper);
    }

    @Test
    @DisplayName("cancel inside the notice window is rejected and not persisted")
    void cancelInsideNoticeWindowIsRejectedAndNotPersisted() {
        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        pendingSubscription.setEndDate(TODAY.plusDays(1));
        stubPersist();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noticeService(NOTICE_DAYS).cancel(1L));

        assertEquals(CancellationNoticePolicy.ERROR_CODE, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(SubscriptionStatus.ACTIVE, pendingSubscription.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel outside the notice window sets CANCELLED immediately")
    void cancelOutsideNoticeWindowSetsCancelled() {
        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        LocalDate endDate = TODAY.plusDays(NOTICE_DAYS + 10);
        pendingSubscription.setEndDate(endDate);
        stubPersist();

        SubscriptionDto cancelled = noticeService(NOTICE_DAYS).cancel(1L);

        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(SubscriptionStatus.CANCELLED, pendingSubscription.getStatus());
        assertEquals(endDate, pendingSubscription.getEndDate());
        verify(subscriptionRepository).save(pendingSubscription);
    }

    @Test
    @DisplayName("cancel on the exact boundary day sets CANCELLED")
    void cancelOnExactBoundaryDaySetsCancelled() {
        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        pendingSubscription.setEndDate(TODAY.plusDays(NOTICE_DAYS));
        stubPersist();

        SubscriptionDto cancelled = noticeService(NOTICE_DAYS).cancel(1L);

        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    @DisplayName("cancel one day past the boundary is rejected")
    void cancelOneDayPastBoundaryIsRejected() {
        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        pendingSubscription.setEndDate(TODAY.plusDays(NOTICE_DAYS - 1));
        stubPersist();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noticeService(NOTICE_DAYS).cancel(1L));

        assertEquals(CancellationNoticePolicy.ERROR_CODE, ex.getErrorCode());
        assertEquals(SubscriptionStatus.ACTIVE, pendingSubscription.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("null endDate skips the notice guard and cancels")
    void cancelWithNullEndDateSkipsNoticeGuard() {
        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        pendingSubscription.setEndDate(null);
        stubPersist();

        SubscriptionDto cancelled = noticeService(NOTICE_DAYS).cancel(1L);

        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
        assertNull(pendingSubscription.getEndDate());
    }

    @Test
    @DisplayName("notice-days 0 still cancels after endDate")
    void zeroNoticeDaysStillCancelsAfterEndDate() {
        pendingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        pendingSubscription.setEndDate(TODAY.minusDays(1));
        stubPersist();

        SubscriptionDto cancelled = noticeService(0).cancel(1L);

        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    @DisplayName("notice-days 0 still refuses a terminal status")
    void zeroNoticeDaysKeepsTheStatusGuard() {
        pendingSubscription.setStatus(SubscriptionStatus.CANCELLED);
        pendingSubscription.setEndDate(TODAY.minusDays(1));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(pendingSubscription));

        assertThrows(IllegalStateException.class, () -> noticeService(0).cancel(1L));
        assertEquals(SubscriptionStatus.CANCELLED, pendingSubscription.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    private SubscriptionService noticeService(int noticeDays) {
        return new SubscriptionService(
                subscriptionRepository,
                erpAdapter,
                subscriptionMapper,
                rateApply,
                new CancellationNoticePolicy(noticeDays, CLOCK));
    }

    private void stubPersist() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(pendingSubscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toDto(any(Subscription.class))).thenAnswer(inv -> dto(inv.getArgument(0)));
    }

    private static SubscriptionDto dto(Subscription s) {
        return SubscriptionDto.builder()
                .id(s.getId())
                .subscriptionNumber(s.getSubscriptionNumber())
                .customerId(s.getCustomerId())
                .quoteId(s.getQuoteId())
                .status(s.getStatus())
                .planName(s.getPlanName())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .renewalDate(s.getRenewalDate())
                .arr(s.getArr())
                .currencyCode(s.getCurrencyCode())
                .erpSyncStatus(s.getErpSyncStatus())
                .erpSubscriptionId(s.getErpSubscriptionId())
                .build();
    }
}
