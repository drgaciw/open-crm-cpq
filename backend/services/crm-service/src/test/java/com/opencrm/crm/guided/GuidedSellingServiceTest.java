package com.opencrm.crm.guided;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SolutionDerivedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Guided selling session")
class GuidedSellingServiceTest {

    @Mock
    private GuidedSellingSessionRepository sessions;

    @Mock
    private ConfigurationSessionStarter starter;

    @Mock
    private OutboxEventPublisher outbox;

    @Mock
    private CurrentActor currentActor;

    private GuidedSellingService service;
    private final Map<Long, GuidedSellingSession> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(sessions.save(any())).thenAnswer(invocation -> {
            GuidedSellingSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(11L);
            }
            store.put(session.getId(), session);
            return session;
        });
        lenient().when(sessions.findByIdAndDeletedFalse(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        lenient().when(currentActor.id()).thenReturn("user:seller-1");
        service = new GuidedSellingService(sessions, starter, outbox, currentActor);
    }

    @Test
    @DisplayName("derive starts a configuration session, publishes SolutionDerived, and stays advisory")
    void derivePublishesAndStartsConfiguration() {
        when(starter.start(any(), any())).thenAnswer(invocation -> new StartedConfiguration(
                invocation.getArgument(0), invocation.getArgument(1), 42L, false));
        GuidedSessionDto started = service.start(StartGuidedSessionRequest.builder().dealId(5L).build());
        service.answer(started.getId(), answer("deployment", "On-Prem"));
        service.answer(started.getId(), answer("priority", "cost"));

        GuidedSessionDto derived = service.derive(started.getId());

        assertThat(derived.getStatus()).isEqualTo(GuidedSessionStatus.DERIVED);
        assertThat(derived.getDealId()).isEqualTo(5L);
        assertThat(derived.getSellerActor()).isEqualTo("user:seller-1");
        assertThat(derived.getProductId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GOVERNED);
        assertThat(derived.getConfigurationSessionId()).isEqualTo(42L);
        assertThat(derived.isRecordedLocally()).isFalse();
        assertThat(derived.isAdvisory()).isTrue();
        assertThat(derived.getConstraintNote()).isEqualTo(NeedsSolutionDerivation.CONSTRAINT_NOTE);
        assertThat(derived.getRecommendations()).isNotEmpty().allMatch(GuidedRecommendationDto::isAdvisory);
        assertThat(derived.getSelections()).containsEntry("deployment", "on-prem");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> selections = ArgumentCaptor.forClass(Map.class);
        verify(starter).start(eq(NeedsSolutionDerivation.PRODUCT_GOVERNED), selections.capture());
        assertThat(selections.getValue()).containsEntry("data-residency", "required");

        ArgumentCaptor<SolutionDerivedEvent> event = ArgumentCaptor.forClass(SolutionDerivedEvent.class);
        verify(outbox).publish(
                eq("GuidedSellingSession"),
                eq(derived.getUuid()),
                eq(EventContractRegistry.SOLUTION_DERIVED_V1),
                event.capture());
        assertThat(event.getValue().isAdvisory()).isTrue();
        assertThat(event.getValue().getGuidedSessionId()).isEqualTo(derived.getUuid());
        assertThat(event.getValue().getDealId()).isEqualTo(5L);
        assertThat(event.getValue().getProductId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GOVERNED);
        assertThat(event.getValue().getConfigurationSessionId()).isEqualTo(42L);
        assertThat(event.getValue().isRecordedLocally()).isFalse();
        assertThat(event.getValue().getDerivedAt()).isEqualTo(derived.getDerivedAt());
        assertThat(EventContractRegistry.SOLUTION_DERIVED_V1.payloadClass()).isEqualTo(SolutionDerivedEvent.class);
        assertThat(EventContractRegistry.SOLUTION_DERIVED_V1.topic())
                .isEqualTo("cpq.crm.opportunity.solution-derived");
        assertThat(EventContractRegistry.SOLUTION_DERIVED_V1.owner()).isEqualTo("crm-service");
        assertThat(EventContractRegistry.SOLUTION_DERIVED_V1.eventType()).isEqualTo("SolutionDerived");
    }

    @Test
    @DisplayName("a blank configuration URL still returns the recorded intent and publishes")
    void deriveWithBlankStarterReturnsRecordedIntent() {
        HttpConfigurationSessionStarter local = new HttpConfigurationSessionStarter("", null);
        GuidedSellingService localService = new GuidedSellingService(sessions, local, outbox, currentActor);
        GuidedSessionDto started = localService.start(null);
        localService.answer(started.getId(), answer("team-size", "enterprise"));
        localService.answer(started.getId(), answer("priority", "speed"));
        localService.answer(started.getId(), answer("deployment", "hybrid"));

        GuidedSessionDto derived = localService.derive(started.getId());

        assertThat(derived.isRecordedLocally()).isTrue();
        assertThat(derived.getConfigurationSessionId()).isNull();
        assertThat(derived.getProductId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GROWTH);
        assertThat(derived.getSelections()).containsExactly(
                Map.entry("edition", "enterprise"),
                Map.entry("deployment", "hybrid"),
                Map.entry("support", "premium"));
        assertThat(derived.getRecommendations()).allMatch(GuidedRecommendationDto::isAdvisory);
        assertThat(local.recordedIntents()).singleElement().satisfies(intent -> {
            assertThat(intent.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GROWTH);
            assertThat(intent.selections()).containsEntry("deployment", "hybrid");
        });
        verify(outbox).publish(
                eq("GuidedSellingSession"),
                eq(derived.getUuid()),
                eq(EventContractRegistry.SOLUTION_DERIVED_V1),
                any(SolutionDerivedEvent.class));
    }

    @Test
    @DisplayName("a second derive does not start another session or publish again")
    void secondDeriveIsRejected() {
        when(starter.start(any(), any())).thenAnswer(invocation -> new StartedConfiguration(
                invocation.getArgument(0), invocation.getArgument(1), 7L, false));
        GuidedSessionDto started = service.start(StartGuidedSessionRequest.builder().build());
        service.derive(started.getId());

        assertThatThrownBy(() -> service.derive(started.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("GUIDED_SESSION_ALREADY_DERIVED");
        verify(starter, times(1)).start(any(), any());
        verify(outbox, times(1)).publish(any(), any(), eq(EventContractRegistry.SOLUTION_DERIVED_V1), any());
    }

    @Test
    @DisplayName("answering after derive does not call the starter again")
    void answerAfterDeriveIsRejected() {
        when(starter.start(any(), any())).thenReturn(
                new StartedConfiguration("prod-growth-platform", Map.of(), 7L, false));
        GuidedSessionDto started = service.start(null);
        service.derive(started.getId());

        assertThatThrownBy(() -> service.answer(started.getId(), answer("priority", "speed")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("GUIDED_SESSION_ALREADY_DERIVED");
        verify(starter, times(1)).start(any(), any());
    }

    @Test
    @DisplayName("the same question keeps one answer")
    void answerReplacesTheSameQuestion() {
        GuidedSessionDto started = service.start(null);
        service.answer(started.getId(), answer("priority", "speed"));
        GuidedSessionDto replaced = service.answer(started.getId(), answer("priority", "cost"));

        assertThat(replaced.getAnswers()).singleElement().satisfies(answer -> {
            assertThat(answer.getQuestionKey()).isEqualTo("priority");
            assertThat(answer.getAnswer()).isEqualTo("cost");
        });
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown question is rejected before the session changes")
    void unknownQuestionIsRejected() {
        GuidedSessionDto started = service.start(null);

        assertThatThrownBy(() -> service.answer(started.getId(), answer("sku", "widget")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("GUIDED_QUESTION_UNKNOWN");
        assertThat(service.get(started.getId()).getAnswers()).isEmpty();
        verify(starter, never()).start(any(), any());
    }

    private static AnswerQuestionRequest answer(String questionKey, String value) {
        return AnswerQuestionRequest.builder().questionKey(questionKey).answer(value).build();
    }

    @Test
    @DisplayName("a missing session is not found and no event is published")
    void missingSessionIsNotFound() {
        assertThatThrownBy(() -> service.derive(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(outbox, never()).publish(any(), any(), any(), any());
        verify(starter, never()).start(any(), any());
    }
}
