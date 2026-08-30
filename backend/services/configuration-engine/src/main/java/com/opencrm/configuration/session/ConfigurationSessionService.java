package com.opencrm.configuration.session;

import com.opencrm.common.configuration.ConfigurationValidityAuthority;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ConfigurationCommittedEvent;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.configuration.constraint.ConstraintSolver;
import com.opencrm.configuration.model.ConfigurationModel;
import com.opencrm.configuration.model.ConfigurationModelCache;
import com.opencrm.configuration.model.ConfigurationModelCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for configuration session management.
 * Ensures only valid configuration combinations pass to pricing/quote flows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConfigurationSessionService {

    private final ConfigurationSessionRepository sessionRepository;
    private final ConstraintSolver constraintSolver;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ConfigurationModelCache configurationModelCache;
    private final ConfigurationModelCompiler configurationModelCompiler;

    public ConfigurationSession create(String productId, String userId) {
        log.debug("Creating configuration session for product: {}, user: {}", productId, userId);

        ConfigurationSession session = ConfigurationSession.builder()
                .productId(productId)
                .userId(userId)
                .status(ConfigurationStatus.IN_PROGRESS)
                .valid(false)
                .expiresAt(Instant.now().plusSeconds(3600)) // 1 hour expiry
                .build();

        ConfigurationSession saved = sessionRepository.save(session);
        log.info("Created configuration session ID: {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public ConfigurationSession getById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConfigurationSession", id));
    }

    @Transactional(readOnly = true)
    public List<ConfigurationSession> getByUserAndStatus(String userId, ConfigurationStatus status) {
        return sessionRepository.findByUserIdAndStatus(userId, status);
    }

    public ConfigurationSession addSelection(Long sessionId, String optionKey, String optionValue) {
        ConfigurationSession session = getById(sessionId);

        if (session.getStatus() != ConfigurationStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot modify a session that is not IN_PROGRESS");
        }

        session.addSelection(optionKey, optionValue);
        applyConstraintResult(session, constraintSolver.evaluate(session));
        return sessionRepository.save(session);
    }

    public ConfigurationSession removeSelection(Long sessionId, String optionKey) {
        ConfigurationSession session = getById(sessionId);

        if (session.getStatus() != ConfigurationStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot modify a session that is not IN_PROGRESS");
        }

        session.removeSelection(optionKey);
        applyConstraintResult(session, constraintSolver.evaluate(session));
        return sessionRepository.save(session);
    }

    public ConfigurationSession validate(Long sessionId) {
        ConfigurationSession session = getById(sessionId);
        configurationModelCache.getOrCompile(session.getProductId(), configurationModelCompiler);
        applyConstraintResult(session, constraintSolver.evaluate(session));
        return sessionRepository.save(session);
    }

    /**
     * Hard failures block {@code valid}; soft RECOMMENDS still land in {@code validationErrors}
     * without blocking complete.
     */
    private static void applyConstraintResult(ConfigurationSession session, ConfigurationValidationResult result) {
        List<String> messages = ConstraintSolver.allViolationMessages(result);
        if (result.isValid()) {
            session.setValid(true);
            session.setValidationErrors(messages.isEmpty() ? null : String.join("; ", messages));
        } else {
            session.markAsInvalid(messages.isEmpty() ? "Configuration is invalid" : String.join("; ", messages));
        }
    }

    public ConfigurationSession complete(Long sessionId) {
        ConfigurationSession session = getById(sessionId);
        session.complete(); // Throws if not valid
        ConfigurationSession saved = sessionRepository.save(session);
        publishConfigurationCommitted(saved);
        return saved;
    }

    private void publishConfigurationCommitted(ConfigurationSession session) {
        ConfigurationModel model = configurationModelCache.getOrCompile(
                session.getProductId(),
                configurationModelCompiler
        );
        String committedVersion = model.schemaVersion() + ":" + session.getId();
        ConfigurationCommittedEvent event = ConfigurationCommittedEvent.v1(
                String.valueOf(session.getId()),
                committedVersion,
                session.getProductId(),
                session.getUserId(),
                session.getCompletedAt(),
                Map.copyOf(session.getSelections()),
                ConfigurationCommittedEvent.CorrelationSnapshot.builder()
                        .correlationId(UUID.randomUUID().toString())
                        .causationId("complete-configuration")
                        .sourceService(ConfigurationValidityAuthority.OWNER_SERVICE)
                        .build()
        );
        outboxEventPublisher.publish(
                "ConfigurationSession",
                String.valueOf(session.getId()),
                KafkaTopics.CONFIGURATION_COMMITTED,
                event
        );
    }

    public ConfigurationSession cancel(Long sessionId) {
        ConfigurationSession session = getById(sessionId);

        if (session.getStatus() == ConfigurationStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed session");
        }

        session.setStatus(ConfigurationStatus.CANCELLED);
        return sessionRepository.save(session);
    }
}
