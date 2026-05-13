package com.opencrm.configuration.session;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ConfigurationCommittedEvent;
import com.opencrm.configuration.constraint.ConstraintSolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
    private final CommittedConfigurationRepository committedConfigurationRepository;
    private final ConstraintSolver constraintSolver;
    private final OutboxEventPublisher outboxEventPublisher;

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

        // Validate after each selection change
        List<String> errors = constraintSolver.validate(session);
        if (errors.isEmpty()) {
            session.markAsValid();
        } else {
            session.markAsInvalid(String.join("; ", errors));
        }

        return sessionRepository.save(session);
    }

    public ConfigurationSession removeSelection(Long sessionId, String optionKey) {
        ConfigurationSession session = getById(sessionId);

        if (session.getStatus() != ConfigurationStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot modify a session that is not IN_PROGRESS");
        }

        session.removeSelection(optionKey);

        List<String> errors = constraintSolver.validate(session);
        if (errors.isEmpty()) {
            session.markAsValid();
        } else {
            session.markAsInvalid(String.join("; ", errors));
        }

        return sessionRepository.save(session);
    }

    public ConfigurationSession validate(Long sessionId) {
        ConfigurationSession session = getById(sessionId);

        List<String> errors = constraintSolver.validate(session);
        if (errors.isEmpty()) {
            session.markAsValid();
        } else {
            session.markAsInvalid(String.join("; ", errors));
        }

        return sessionRepository.save(session);
    }

    public ConfigurationSession complete(Long sessionId) {
        ConfigurationSession session = getById(sessionId);
        session.complete(); // Throws if not valid
        ConfigurationSession saved = sessionRepository.save(session);

        CommittedConfiguration committedConfiguration = committedConfigurationRepository
                .findBySessionId(saved.getId())
                .orElseGet(() -> committedConfigurationRepository.save(CommittedConfiguration.fromSession(saved)));

        ConfigurationCommittedEvent event = new ConfigurationCommittedEvent(
                committedConfiguration.getId(),
                saved.getId(),
                saved.getProductId(),
                saved.getUserId(),
                committedConfiguration.getSelections(),
                committedConfiguration.getCommittedAt()
        );
        outboxEventPublisher.publish(
                "CommittedConfiguration",
                String.valueOf(committedConfiguration.getId()),
                KafkaTopics.CONFIGURATION_COMMITTED,
                event
        );

        return saved;
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
