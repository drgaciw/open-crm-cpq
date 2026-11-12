package com.opencrm.configuration.session;

import com.opencrm.common.configuration.ConfigurationValidityAuthority;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ConfigurationCommittedEvent;
import com.opencrm.common.event.dto.ConfigurationSessionStartedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.configuration.constraint.ConstraintSolver;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.configuration.mapper.ConfigurationSessionMapper;
import com.opencrm.configuration.model.ConfigurationModel;
import com.opencrm.configuration.model.ConfigurationModelCache;
import com.opencrm.configuration.model.ConfigurationModelCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for configuration session management.
 * Ensures only valid configuration combinations pass to pricing/quote flows.
 * Public API returns {@link ConfigurationSessionDto} only.
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
    private final ConfigurationSessionMapper sessionMapper;

    /**
     * Backward-compatible shorthand for a product-only start with no catalog version pin.
     * Delegates to {@link #create(String, String, String, String)} (DRG-424 StartSession).
     */
    public ConfigurationSessionDto create(String productId, String userId) {
        return create(productId, null, null, userId);
    }

    /**
     * StartSession (DRG-424 / BC-02-01): opens a configuration session against a catalog version and a
     * product or a bundle, so a seller can build a solution incrementally via the existing selection
     * endpoints. Records the catalog version as given (there is no landed catalog-versioning authority
     * yet to validate it against — DRG-421 is not landed), the compiled rule-set version in effect, and
     * the caller's tenant. Emits {@code SessionStarted}.
     */
    public ConfigurationSessionDto create(String productId, String bundleId, String catalogVersion, String userId) {
        validateProductOrBundle(productId, bundleId);
        log.debug("Starting configuration session for product: {}, bundle: {}, catalogVersion: {}, user: {}",
                productId, bundleId, catalogVersion, userId);

        Instant startedAt = Instant.now();
        String ruleSetVersion = resolveRuleSetVersion(productId, bundleId);
        String tenantId = ConfigurationSessionTenantResolver.currentTenantId();

        ConfigurationSession session = ConfigurationSession.builder()
                .productId(productId)
                .bundleId(bundleId)
                .catalogVersion(catalogVersion)
                .ruleSetVersion(ruleSetVersion)
                .tenantId(tenantId)
                .userId(userId)
                .status(ConfigurationStatus.IN_PROGRESS)
                .valid(false)
                .expiresAt(startedAt.plusSeconds(3600)) // 1 hour expiry
                .build();

        ConfigurationSession saved = sessionRepository.save(session);
        log.info("Started configuration session ID: {}", saved.getId());
        publishSessionStarted(saved, startedAt);
        return sessionMapper.toDto(saved);
    }

    private static void validateProductOrBundle(String productId, String bundleId) {
        boolean hasProduct = productId != null && !productId.isBlank();
        boolean hasBundle = bundleId != null && !bundleId.isBlank();
        if (hasProduct == hasBundle) {
            throw new BusinessException(
                    "Exactly one of productId or bundleId is required to start a configuration session",
                    "CONFIGURATION_SESSION_SUBJECT_REQUIRED");
        }
    }

    private String resolveRuleSetVersion(String productId, String bundleId) {
        ConfigurationModel model = configurationModelCache.getOrCompile(
                subjectId(productId, bundleId), configurationModelCompiler);
        return model.schemaVersion();
    }

    /**
     * The session's catalog subject for model compilation: {@code productId} when present, else
     * {@code bundleId}. {@link ConfigurationModelCache} is keyed by a {@link java.util.concurrent.ConcurrentHashMap},
     * which rejects a null key, so this must never be called with both null (DRG-424: {@link #validateProductOrBundle}
     * guarantees exactly one is set before a session exists).
     */
    private static String subjectId(String productId, String bundleId) {
        return productId != null ? productId : bundleId;
    }

    private void publishSessionStarted(ConfigurationSession session, Instant startedAt) {
        ConfigurationSessionStartedEvent event = ConfigurationSessionStartedEvent.v1(
                String.valueOf(session.getId()),
                session.getProductId(),
                session.getBundleId(),
                session.getCatalogVersion(),
                session.getRuleSetVersion(),
                session.getTenantId(),
                session.getUserId(),
                startedAt,
                ConfigurationSessionStartedEvent.CorrelationSnapshot.builder()
                        .correlationId(UUID.randomUUID().toString())
                        .causationId("start-configuration-session")
                        .sourceService(ConfigurationValidityAuthority.OWNER_SERVICE)
                        .build()
        );
        ConfigurationSessionStartedEvent.CorrelationSnapshot correlation = event.getCorrelation();
        try (EventCorrelation.Scope ignored =
                     EventCorrelation.begin(correlation.getCorrelationId(), correlation.getCausationId())) {
            outboxEventPublisher.publish(
                    "ConfigurationSession",
                    String.valueOf(session.getId()),
                    EventContractRegistry.CONFIGURATION_SESSION_STARTED_V1,
                    event
            );
        }
    }

    /**
     * GetSessionState (DRG-424 / BC-02-01): current selections, open violations, and available options,
     * computed live from the session's selections. {@code openViolations}/{@code availableOptionIds} are
     * not persisted — they reflect this moment's rule evaluation, not the last-saved snapshot.
     */
    @Transactional(readOnly = true)
    public ConfigurationSessionDto getById(Long id) {
        ConfigurationSession session = findEntityById(id);
        ConfigurationSessionDto dto = sessionMapper.toDto(session);
        ConfigurationValidationResult result = constraintSolver.evaluate(session);
        dto.setOpenViolations(result == null ? List.of() : result.getViolations());
        dto.setAvailableOptionIds(constraintSolver.availableOptionIds(session));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ConfigurationSessionDto> getByUserAndStatus(String userId, ConfigurationStatus status) {
        return sessionMapper.toDtoList(sessionRepository.findByUserIdAndStatus(userId, status));
    }

    public ConfigurationSessionDto addSelection(Long sessionId, String optionKey, String optionValue) {
        ConfigurationSession session = findEntityById(sessionId);

        if (session.getStatus() != ConfigurationStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot modify a session that is not IN_PROGRESS");
        }

        session.addSelection(optionKey, optionValue);
        applyConstraintResult(session, constraintSolver.evaluate(session));
        return sessionMapper.toDto(sessionRepository.save(session));
    }

    public ConfigurationSessionDto removeSelection(Long sessionId, String optionKey) {
        ConfigurationSession session = findEntityById(sessionId);

        if (session.getStatus() != ConfigurationStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot modify a session that is not IN_PROGRESS");
        }

        session.removeSelection(optionKey);
        applyConstraintResult(session, constraintSolver.evaluate(session));
        return sessionMapper.toDto(sessionRepository.save(session));
    }

    public ConfigurationSessionDto validate(Long sessionId) {
        ConfigurationSession session = findEntityById(sessionId);
        configurationModelCache.getOrCompile(
                subjectId(session.getProductId(), session.getBundleId()), configurationModelCompiler);
        applyConstraintResult(session, constraintSolver.evaluate(session));
        return sessionMapper.toDto(sessionRepository.save(session));
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

    /**
     * A bundle-started session cannot be completed yet (DRG-424 follow-up). Bundle support today is a
     * shell — StartSession/GetSessionState only; there is no bundle-item expansion anywhere in this
     * service ({@link ConstraintSolver} and {@link ConfigurationModelCompiler} treat {@code bundleId} as
     * an opaque subject id, nothing more) — and {@link ConfigurationCommittedEvent} has no
     * {@code bundleId} field and, as of DRG-424, no live consumer that would need one. Completing a
     * bundle session would publish an event with {@code productId=null} and no bundle identity at all:
     * an ambiguous, half-built event for a feature that is not otherwise wired. Reject it here instead
     * of guessing at a committed-bundle event shape; define that shape (most likely adding a nullable,
     * mutually-exclusive {@code bundleId} to {@code ConfigurationCommittedEvent}, mirroring StartSession)
     * when bundle-item expansion actually lands.
     */
    public ConfigurationSessionDto complete(Long sessionId) {
        ConfigurationSession session = findEntityById(sessionId);
        if (session.getBundleId() != null) {
            throw new BusinessException(
                    "Completing a session started against a bundle is not supported yet; "
                            + "bundle sessions can be built incrementally and cancelled, but not completed",
                    HttpStatus.CONFLICT,
                    "CONFIGURATION_SESSION_BUNDLE_COMPLETE_UNSUPPORTED");
        }
        session.complete(); // Throws if not valid
        ConfigurationSession saved = sessionRepository.save(session);
        publishConfigurationCommitted(saved);
        return sessionMapper.toDto(saved);
    }

    private void publishConfigurationCommitted(ConfigurationSession session) {
        ConfigurationModel model = configurationModelCache.getOrCompile(
                subjectId(session.getProductId(), session.getBundleId()),
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
        // ADR-018 D4: the envelope headers carry the same correlation as the payload's snapshot.
        ConfigurationCommittedEvent.CorrelationSnapshot correlation = event.getCorrelation();
        try (EventCorrelation.Scope ignored =
                     EventCorrelation.begin(correlation.getCorrelationId(), correlation.getCausationId())) {
            outboxEventPublisher.publish(
                    "ConfigurationSession",
                    String.valueOf(session.getId()),
                    EventContractRegistry.CONFIGURATION_COMMITTED_V1,
                    event
            );
        }
    }

    public ConfigurationSessionDto cancel(Long sessionId) {
        ConfigurationSession session = findEntityById(sessionId);

        if (session.getStatus() == ConfigurationStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed session");
        }

        session.setStatus(ConfigurationStatus.CANCELLED);
        return sessionMapper.toDto(sessionRepository.save(session));
    }

    private ConfigurationSession findEntityById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConfigurationSession", id));
    }
}
