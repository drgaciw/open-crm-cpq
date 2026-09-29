package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.security.CurrentActor;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiscountApprovalEventConsumer")
class DiscountApprovalEventConsumerTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private DiscountTierShadowService discountTierShadow;

    @Mock
    private KafkaInbox inbox;

    private DiscountApprovalEventConsumer consumer;

    /** Ids the mocked repository hands out to new rows, as the database would. */
    private final AtomicLong nextId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        consumer = new DiscountApprovalEventConsumer(approvalRequestRepository, new ObjectMapper(), discountTierShadow,
                inbox, new CurrentActor("service:approval-routing", false));
        when(inbox.claim(eq(DiscountApprovalEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING)).thenReturn(List.of());
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(call -> {
            ApprovalRequest saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(nextId.getAndIncrement());
            }
            return saved;
        });
    }

    private static ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, 0, 7L, "Q-1", payload);
    }

    private static ConsumerRecord<String, String> event(String marginDelta) {
        return record("""
                {"quoteId":"Q-1","lineId":"L-1","marginDelta":%s,"requestedDiscount":18.5,"currencyCode":"USD"}"""
                .formatted(marginDelta));
    }

    /** The request the event created: the one PENDING row saved (replaced rows are saved too, as SUPERSEDED). */
    private ApprovalRequest saved() {
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository, atLeastOnce()).save(captor.capture());
        List<ApprovalRequest> created = captor.getAllValues().stream()
                .filter(request -> request.getStatus() == ApprovalStatus.PENDING).distinct().toList();
        assertThat(created).as("exactly one new PENDING request").hasSize(1);
        return created.get(0);
    }

    private static ApprovalRequest open(long id, String lineId, String discount, String marginDelta) {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId("Q-1").lineId(lineId).approvalType(ApprovalType.DISCOUNT).status(ApprovalStatus.PENDING)
                .requestedDiscount(new BigDecimal(discount)).marginDelta(new BigDecimal(marginDelta))
                .currencyCode("USD").requesterId("service:approval-routing").build();
        request.setId(id);
        return request;
    }

    @ParameterizedTest(name = "margin delta {0} routes to {1}")
    @CsvSource({
            "0, MANAGER",
            "20, MANAGER",
            "20.01, DIRECTOR",
            "30, DIRECTOR",
            "30.01, VP_SALES",
            "75, VP_SALES"
    })
    @DisplayName("tier thresholds are exclusive at 20 and 30")
    void tierThresholds(String marginDelta, String expectedTier) {
        consumer.handleDiscountApprovalRequired(event(marginDelta));

        assertThat(saved().getApprovalTier()).isEqualTo(expectedTier);
    }

    @Test
    @DisplayName("creates a pending discount approval request from the event")
    void createsPendingRequest() {
        consumer.handleDiscountApprovalRequired(event("25"));

        ApprovalRequest request = saved();
        assertThat(request.getQuoteId()).isEqualTo("Q-1");
        assertThat(request.getLineId()).isEqualTo("L-1");
        assertThat(request.getApprovalType()).isEqualTo(ApprovalType.DISCOUNT);
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(request.getRequestedDiscount()).isEqualByComparingTo("18.5");
        assertThat(request.getMarginDelta()).isEqualByComparingTo("25");
        assertThat(request.getCurrencyCode()).isEqualTo("USD");
        assertThat(request.getSubmittedAt()).isNotNull();
        assertThat(request.getRequesterId()).as("the consumer acts as this service (ADR-016 decision 6)")
                .isEqualTo("service:approval-routing");
    }

    @Test
    @DisplayName("D6: an event the inbox has already seen creates nothing")
    void alreadyProcessedEventIsSkipped() {
        when(inbox.claim(eq(DiscountApprovalEventConsumer.CONSUMER_GROUP), any())).thenReturn(false);

        consumer.handleDiscountApprovalRequired(event("25"));

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("D6: an identical PENDING request for the quote line makes the event a duplicate (scale-insensitive)")
    void identicalOpenRequestIsADuplicate() {
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(open(1L, "L-1", "18.50", "25.0000")));

        consumer.handleDiscountApprovalRequired(event("25"));

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("decision 9: changed figures create a new request and supersede the open one, naming its replacement")
    void changedFiguresSupersedeTheOpenRequest() {
        ApprovalRequest older = open(1L, "L-1", "18.50", "22");
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(older));

        consumer.handleDiscountApprovalRequired(event("25"));

        ApprovalRequest created = saved();
        assertThat(created.getId()).isEqualTo(100L);
        assertThat(created.getMarginDelta()).isEqualByComparingTo("25");
        assertThat(older.getStatus()).isEqualTo(ApprovalStatus.SUPERSEDED);
        assertThat(older.getSupersededBy()).isEqualTo(100L);
        assertThat(older.getSupersededAt()).isNotNull();
        verify(approvalRequestRepository).save(older);
        verify(discountTierShadow).recordAfterCommit(eq(100L), any(), eq("DIRECTOR"));
    }

    @Test
    @DisplayName("decision 9: every open request for the line is superseded, oldest included")
    void everyOpenRequestForTheLineIsSuperseded() {
        ApprovalRequest first = open(1L, "L-1", "18.50", "22");
        ApprovalRequest second = open(2L, "L-1", "18.50", "23");
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(second, first));

        consumer.handleDiscountApprovalRequired(event("25"));

        assertThat(first.getSupersededBy()).isEqualTo(100L);
        assertThat(second.getSupersededBy()).isEqualTo(100L);
        assertThat(saved().getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("decision 9: another quote line, and other approval types, are left alone")
    void otherLinesAndTypesAreLeftAlone() {
        ApprovalRequest otherLine = open(1L, "L-2", "18.50", "22");
        ApprovalRequest otherType = open(2L, "L-1", "18.50", "22");
        otherType.setApprovalType(ApprovalType.QUOTE);
        ApprovalRequest wholeQuote = open(3L, null, "18.50", "22");
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(otherLine, otherType, wholeQuote));

        consumer.handleDiscountApprovalRequired(event("25"));

        assertThat(saved().getMarginDelta()).isEqualByComparingTo("25");
        assertThat(List.of(otherLine, otherType, wholeQuote))
                .allSatisfy(request -> {
                    assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
                    assertThat(request.getSupersededBy()).isNull();
                });
        verify(approvalRequestRepository, never()).save(otherLine);
        verify(approvalRequestRepository, never()).save(otherType);
        verify(approvalRequestRepository, never()).save(wholeQuote);
    }

    @Test
    @DisplayName("decision 9: an identical open request stays current; older ones with other figures are superseded by it")
    void identicalRequestStaysCurrent() {
        ApprovalRequest stale = open(1L, "L-1", "18.50", "22");
        ApprovalRequest current = open(2L, "L-1", "18.50", "25.0000");
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(stale, current));

        consumer.handleDiscountApprovalRequired(event("25"));

        assertThat(stale.getStatus()).isEqualTo(ApprovalStatus.SUPERSEDED);
        assertThat(stale.getSupersededBy()).isEqualTo(2L);
        assertThat(current.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(current.getSupersededBy()).isNull();
        verify(approvalRequestRepository).save(stale);
        verify(approvalRequestRepository, never()).save(current);
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("decision 9: with two identical open requests the newest stays and the older is superseded")
    void newestIdenticalRequestStays() {
        ApprovalRequest legacyDuplicate = open(1L, "L-1", "18.50", "25");
        ApprovalRequest newest = open(2L, "L-1", "18.50", "25");
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(newest, legacyDuplicate));

        consumer.handleDiscountApprovalRequired(event("25"));

        assertThat(legacyDuplicate.getSupersededBy()).isEqualTo(2L);
        assertThat(newest.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }
    @Test
    @DisplayName("D7: malformed JSON is not retryable, so it goes to the DLT; nothing is saved")
    void malformedJsonIsNotRetryable() {
        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(record("{not json")))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("Unreadable discount approval event");

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("D7: an event without quoteId or marginDelta is not retryable")
    void incompleteEventIsNotRetryable() {
        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(record("{\"lineId\":\"L-1\"}")))
                .isInstanceOf(NonRetryableEventException.class);

        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("a repository failure propagates so the error handler retries it")
    void repositoryFailurePropagates() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(event("25")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("hands the rule's tier to the shadow only after the request is saved")
    void shadowReceivesTheAuthoritativeTier() {
        consumer.handleDiscountApprovalRequired(event("25"));

        verify(discountTierShadow).recordAfterCommit(eq(100L), any(), eq("DIRECTOR"));
        assertThat(saved().getApprovalTier()).isEqualTo("DIRECTOR");
    }
}
