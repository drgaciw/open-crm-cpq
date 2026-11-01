package com.opencrm.crm.guided;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SolutionDerivedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * StartGuidedSession, AnswerQuestion, and DeriveSolution (DRG-487 / BC-13-03).
 *
 * <p>DeriveSolution chooses a product and pre-selected options with {@link NeedsSolutionDerivation},
 * then asks {@link ConfigurationSessionStarter} to open a configuration session. Recommendations
 * stay advisory: this service does not complete the configuration and does not mark a deal won.
 * {@link EventContractRegistry#SOLUTION_DERIVED_V1} is published from this class only.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GuidedSellingService {

    private final GuidedSellingSessionRepository sessions;
    private final ConfigurationSessionStarter configurationSessionStarter;
    private final OutboxEventPublisher outboxEventPublisher;
    private final CurrentActor currentActor;

    public GuidedSessionDto start(StartGuidedSessionRequest request) {
        Long dealId = request == null ? null : request.getDealId();
        if (dealId != null && dealId <= 0) {
            throw new BusinessException("Deal id must be positive", "GUIDED_DEAL_INVALID");
        }
        GuidedSellingSession session = new GuidedSellingSession();
        session.setUuid(UUID.randomUUID().toString());
        session.setDealId(dealId);
        session.setStatus(GuidedSessionStatus.OPEN);
        session.setSellerActor(currentActor.id());
        session.setRecordedLocally(false);
        return toDto(sessions.save(session));
    }

    public GuidedSessionDto answer(Long sessionId, AnswerQuestionRequest request) {
        GuidedSellingSession session = requireOpen(sessionId);
        if (request == null) {
            throw new BusinessException("An answer is required", "GUIDED_ANSWER_REQUIRED");
        }
        String questionKey = NeedsSolutionDerivation.normalize(request.getQuestionKey());
        String answer = NeedsSolutionDerivation.normalize(request.getAnswer());
        if (questionKey.isEmpty() || answer.isEmpty()) {
            throw new BusinessException("Question key and answer are required", "GUIDED_ANSWER_REQUIRED");
        }
        if (questionKey.length() > 64 || answer.length() > 255) {
            throw new BusinessException("Question or answer is too long", "GUIDED_ANSWER_TOO_LONG");
        }
        NeedsSolutionDerivation.requireKnown(questionKey, answer);
        session.putAnswer(questionKey, answer);
        return toDto(sessions.save(session));
    }

    public GuidedSessionDto derive(Long sessionId) {
        GuidedSellingSession session = requireOpen(sessionId);
        NeedsSolutionDerivation.DerivedNeeds needs = NeedsSolutionDerivation.derive(session.answerMap());
        StartedConfiguration started = configurationSessionStarter.start(needs.productId(), needs.selections());
        Instant derivedAt = Instant.now();
        session.setProductId(started.productId());
        session.setConfigurationSessionId(started.configurationSessionId());
        session.setRecordedLocally(started.recordedLocally());
        session.setRationale(needs.rationale());
        session.setDerivedAt(derivedAt);
        session.setStatus(GuidedSessionStatus.DERIVED);
        session.replaceSelections(started.selections(), needs.rationale());
        sessions.save(session);

        outboxEventPublisher.publish(
                "GuidedSellingSession",
                session.getUuid(),
                EventContractRegistry.SOLUTION_DERIVED_V1,
                new SolutionDerivedEvent(
                        session.getUuid(),
                        session.getDealId(),
                        started.productId(),
                        started.selections(),
                        started.configurationSessionId(),
                        started.recordedLocally(),
                        true,
                        derivedAt));

        log.info("Derived advisory solution for guided session {} product {} recordedLocally {}",
                session.getUuid(), started.productId(), started.recordedLocally());
        return toDto(session);
    }

    @Transactional(readOnly = true)
    public GuidedSessionDto get(Long sessionId) {
        return toDto(requireSession(sessionId));
    }

    @Transactional(readOnly = true)
    public List<GuidedQuestionDto> questions() {
        return NeedsSolutionDerivation.catalog().stream()
                .map(question -> GuidedQuestionDto.builder()
                        .key(question.key())
                        .prompt(question.prompt())
                        .allowedValues(question.allowedValues())
                        .build())
                .toList();
    }

    private GuidedSellingSession requireOpen(Long sessionId) {
        GuidedSellingSession session = requireSession(sessionId);
        if (session.getStatus() != GuidedSessionStatus.OPEN) {
            throw new BusinessException("This guided session is already derived",
                    HttpStatus.CONFLICT, "GUIDED_SESSION_ALREADY_DERIVED");
        }
        return session;
    }

    private GuidedSellingSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw new ResourceNotFoundException("GuidedSellingSession", "null");
        }
        return sessions.findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("GuidedSellingSession", sessionId));
    }

    private static GuidedSessionDto toDto(GuidedSellingSession session) {
        boolean derived = session.getStatus() == GuidedSessionStatus.DERIVED;
        List<GuidedRecommendationDto> recommendations = new ArrayList<>();
        if (derived) {
            session.selectionMap().forEach((key, value) -> recommendations.add(GuidedRecommendationDto.builder()
                    .productId(session.getProductId())
                    .optionKey(key)
                    .optionValue(value)
                    .rationale(session.getRationale())
                    .advisory(true)
                    .build()));
        }
        return GuidedSessionDto.builder()
                .id(session.getId())
                .uuid(session.getUuid())
                .dealId(session.getDealId())
                .status(session.getStatus())
                .sellerActor(session.getSellerActor())
                .productId(session.getProductId())
                .configurationSessionId(session.getConfigurationSessionId())
                .recordedLocally(session.isRecordedLocally())
                .advisory(derived)
                .constraintNote(derived ? NeedsSolutionDerivation.CONSTRAINT_NOTE : null)
                .rationale(session.getRationale())
                .derivedAt(session.getDerivedAt())
                .answers(session.getAnswers().stream()
                        .map(answer -> GuidedAnswerDto.builder()
                                .questionKey(answer.getQuestionKey())
                                .answer(answer.getAnswerValue())
                                .build())
                        .toList())
                .selections(session.selectionMap())
                .recommendations(recommendations)
                .build();
    }
}
