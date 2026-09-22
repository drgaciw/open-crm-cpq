package com.opencrm.quote.approval.suggestion;

import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.testing.FakeJevTransport;
import com.opencrm.jev.transport.NoOpJevTransport;
import com.opencrm.quote.approval.suggestion.ApprovalTierSuggestionDto.Status;
import com.opencrm.quote.quote.DealType;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalTierSuggestionService")
class ApprovalTierSuggestionServiceTest {

    private static final String SALES_VP = """
            {"tier":{"type":"choice","choice":"SALES_VP","confidence":0.8,
                     "probabilities":{"DEAL_DESK":0.15,"SALES_VP":0.8,"CFO":0.05}}}""";

    /** In-memory stand-in for the JDBC store: keeps rows and answers findLatest by features hash. */
    private static final class MemoryStore implements JevDecisionLogStore {
        final List<JevDecisionRecord> rows = new ArrayList<>();

        @Override
        public void save(JevDecisionRecord record) {
            rows.add(record);
        }

        @Override
        public Optional<StoredDecision> findLatest(String useCase, String featuresHash) {
            return rows.stream()
                    .filter(row -> row.useCase().equals(useCase) && row.featuresHash().equals(featuresHash))
                    .reduce((first, second) -> second)
                    .map(row -> new StoredDecision(row.status(), row.answerValue(), row.confidence(),
                            row.probabilitiesJson(), row.evaluatorModel(), row.rubricVersion(),
                            Instant.parse("2026-09-21T09:00:00Z")));
        }
    }

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private JevAdvisoryRunner runner;

    private final MemoryStore store = new MemoryStore();
    private final List<Runnable> scheduled = new ArrayList<>();

    @BeforeEach
    void captureScheduledWork() {
        lenient().doAnswer(call -> scheduled.add(call.getArgument(1)))
                .when(runner).submitAfterCommit(anyString(), any());
    }

    private static Quote quote() {
        Quote quote = new Quote();
        quote.setName("Confidential customer name");
        quote.setDealType(DealType.NEW);
        quote.setCurrency("USD");
        quote.setRegion("NA");
        quote.setDurationInMonths(12);
        quote.setSubtotal(new BigDecimal("300000"));
        quote.setTotalDiscount(new BigDecimal("60000"));
        quote.setTotalAmount(new BigDecimal("240000"));
        return quote;
    }

    private static JevProperties properties() {
        JevProperties properties = new JevProperties();
        properties.setEnabled(true);
        properties.setModel("typesafe/jev-1.13");
        properties.getQuoteTierSuggestion().setEnabled(true);
        return properties;
    }

    private ApprovalTierSuggestionService service(FakeJevTransport transport, boolean flag) {
        return new ApprovalTierSuggestionService(quoteRepository, new JevClient(properties(), transport), runner,
                store, flag);
    }

    @Test
    @DisplayName("flag off: UNAVAILABLE without reading the quote or scheduling anything")
    void flagOff() {
        assertThat(service(new FakeJevTransport(), false).suggest(1L).status()).isEqualTo(Status.UNAVAILABLE);

        verifyNoInteractions(quoteRepository, runner);
    }

    @Test
    @DisplayName("no-op transport: UNAVAILABLE even with the flag on")
    void noOpTransport() {
        ApprovalTierSuggestionService service = new ApprovalTierSuggestionService(quoteRepository,
                new JevClient(properties(), new NoOpJevTransport()), runner, store, true);

        assertThat(service.suggest(1L).status()).isEqualTo(Status.UNAVAILABLE);
        verifyNoInteractions(quoteRepository, runner);
    }

    @Test
    @DisplayName("an unknown quote is UNAVAILABLE, not an error")
    void unknownQuote() {
        when(quoteRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.empty());

        assertThat(service(new FakeJevTransport(), true).suggest(9L).status()).isEqualTo(Status.UNAVAILABLE);
        verify(runner, never()).submitAfterCommit(anyString(), any());
    }

    @Test
    @DisplayName("first request is PENDING and never calls Jev on the request thread; the next is READY")
    void pendingThenReady() {
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote()));
        FakeJevTransport transport = new FakeJevTransport().reply(SALES_VP);
        ApprovalTierSuggestionService service = service(transport, true);

        ApprovalTierSuggestionDto first = service.suggest(1L);

        assertThat(first.status()).isEqualTo(Status.PENDING);
        assertThat(first.suggestion()).isNull();
        assertThat(transport.requests()).isEmpty();
        assertThat(scheduled).hasSize(1);

        scheduled.get(0).run();
        ApprovalTierSuggestionDto second = service.suggest(1L);

        assertThat(second.status()).isEqualTo(Status.READY);
        assertThat(second.suggestion().value()).isEqualTo("SALES_VP");
        assertThat(second.suggestion().confidence()).isEqualTo(0.8);
        assertThat(second.suggestion().probabilities()).containsEntry("CFO", 0.05).hasSize(3);
        assertThat(second.suggestion().advisory()).isTrue();
        assertThat(second.suggestion().rubricVersion()).isEqualTo("quote-tier-v1");
        assertThat(transport.requests()).hasSize(1);
    }

    @Test
    @DisplayName("the logged row awaits the user's pick and holds no quote name")
    void logsForLaterReconciliation() {
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote()));
        service(new FakeJevTransport().reply(SALES_VP), true).suggest(1L);
        scheduled.get(0).run();

        assertThat(store.rows).singleElement().satisfies(row -> {
            assertThat(row.subjectType()).isEqualTo("quote");
            assertThat(row.subjectId()).isEqualTo("1");
            assertThat(row.authoritativeOutcome()).isNull();
            assertThat(row.authoritativeSource()).isEqualTo(JevDecisionRecord.AuthoritativeSource.NONE);
            assertThat(row.featuresJson()).doesNotContain("Confidential");
        });
    }

    @Test
    @DisplayName("polling while an evaluation is in flight starts only one")
    void pollingStartsOneEvaluation() {
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote()));
        ApprovalTierSuggestionService service = service(new FakeJevTransport().reply(SALES_VP), true);

        service.suggest(1L);
        service.suggest(1L);
        service.suggest(1L);

        verify(runner, times(1)).submitAfterCommit(anyString(), any());
    }

    @Test
    @DisplayName("a dropped evaluation can be claimed again after the in-flight TTL")
    void claimExpires() {
        ApprovalTierSuggestionService service = service(new FakeJevTransport(), true);

        assertThat(service.claim("h", 1_000)).isTrue();
        assertThat(service.claim("h", 1_000 + ApprovalTierSuggestionService.IN_FLIGHT_TTL_MS)).isFalse();
        assertThat(service.claim("h", 1_001 + ApprovalTierSuggestionService.IN_FLIGHT_TTL_MS)).isTrue();
    }

    @Test
    @DisplayName("a failed evaluation is UNAVAILABLE and is not retried by polling")
    void failureIsNotRetried() {
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote()));
        FakeJevTransport transport = new FakeJevTransport().fail(new IllegalStateException("upstream down"));
        ApprovalTierSuggestionService service = service(transport, true);

        service.suggest(1L);
        scheduled.get(0).run();

        assertThat(service.suggest(1L).status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(service.suggest(1L).status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(transport.requests()).hasSize(1);
        verify(runner, times(1)).submitAfterCommit(anyString(), any());
    }

    @Test
    @DisplayName("a changed quote is a new feature state and gets its own evaluation")
    void changedQuoteIsReevaluated() {
        Quote quote = quote();
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        ApprovalTierSuggestionService service = service(new FakeJevTransport().reply(SALES_VP), true);

        service.suggest(1L);
        scheduled.get(0).run();
        quote.setTotalDiscount(new BigDecimal("150000"));

        assertThat(service.suggest(1L).status()).isEqualTo(Status.PENDING);
        assertThat(scheduled).hasSize(2);
    }

    @Test
    @DisplayName("never throws: a repository failure is UNAVAILABLE")
    void neverThrows() {
        doThrow(new IllegalStateException("db down")).when(quoteRepository).findByIdAndDeletedFalse(1L);

        assertThat(service(new FakeJevTransport(), true).suggest(1L).status()).isEqualTo(Status.UNAVAILABLE);
    }
}
