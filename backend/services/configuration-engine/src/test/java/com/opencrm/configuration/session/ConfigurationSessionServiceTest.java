package com.opencrm.configuration.session;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for Configuration Engine session management.
 * Validates constraint solving and session state transitions; service returns DTOs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Configuration Engine - Session Service")
class ConfigurationSessionServiceTest {

    @Mock
    private ConfigurationSessionRepository sessionRepository;

    @Mock
    private ConstraintSolver constraintSolver;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private ConfigurationModelCache configurationModelCache;

    @Mock
    private ConfigurationModelCompiler configurationModelCompiler;

    @Mock
    private ConfigurationSessionMapper sessionMapper;

    @InjectMocks
    private ConfigurationSessionService sessionService;

    private ConfigurationSession session;

    @BeforeEach
    void setUp() {
        session = ConfigurationSession.builder()
                .productId("PROD-001")
                .userId("user-1")
                .status(ConfigurationStatus.IN_PROGRESS)
                .valid(false)
                .selections(new HashMap<>())
                .build();
        session.setId(1L);

        lenient().when(sessionMapper.toDto(any(ConfigurationSession.class)))
                .thenAnswer(inv -> dto(inv.getArgument(0)));
        lenient().when(sessionMapper.toDtoList(anyList()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<ConfigurationSession> sessions = inv.getArgument(0);
                    return sessions.stream().map(ConfigurationSessionServiceTest::dto).toList();
                });
    }

    @Test
    @DisplayName("should create a new configuration session")
    void createSession() {
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ConfigurationSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(configurationModelCache.getOrCompile(any(), any()))
                .thenReturn(new ConfigurationModel("PROD-001", "product-PROD-001-v1", Instant.now()));

        ConfigurationSessionDto result = sessionService.create("PROD-001", "user-1");

        assertNotNull(result);
        assertEquals("PROD-001", result.getProductId());
        assertEquals(ConfigurationStatus.IN_PROGRESS, result.getStatus());
        assertNotNull(result.getExpiresAt());
        verify(outboxEventPublisher).publish(
                eq("ConfigurationSession"),
                eq("1"),
                eq(EventContractRegistry.CONFIGURATION_SESSION_STARTED_V1),
                any()
        );
    }

    @Test
    @DisplayName("DRG-424 StartSession: records catalog version, rule-set version, and tenant; emits SessionStarted")
    void startSessionRecordsCatalogRuleSetAndTenant() {
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ConfigurationSession s = inv.getArgument(0);
            s.setId(2L);
            return s;
        });
        when(configurationModelCache.getOrCompile(any(), any()))
                .thenReturn(new ConfigurationModel("PROD-001", "product-PROD-001-v1", Instant.now()));
        AtomicReference<Object> payload = new AtomicReference<>();
        doAnswer(invocation -> {
            payload.set(invocation.getArgument(3));
            return null;
        }).when(outboxEventPublisher).publish(any(), any(), any(), any());

        ConfigurationSessionDto result = sessionService.create("PROD-001", null, "catalog-v9", "user-1");

        assertEquals("catalog-v9", result.getCatalogVersion());
        assertEquals("product-PROD-001-v1", result.getRuleSetVersion());
        assertNull(result.getBundleId());

        ConfigurationSessionStartedEvent event = (ConfigurationSessionStartedEvent) payload.get();
        assertEquals("2", event.getSessionId());
        assertEquals("PROD-001", event.getProductId());
        assertEquals("catalog-v9", event.getCatalogVersion());
        assertEquals("product-PROD-001-v1", event.getRuleSetVersion());
        assertEquals("start-configuration-session", event.getCorrelation().getCausationId());
    }

    @Test
    @DisplayName("DRG-424 StartSession: against a bundle instead of a product")
    void startSessionAgainstBundle() {
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ConfigurationSession s = inv.getArgument(0);
            s.setId(3L);
            return s;
        });
        when(configurationModelCache.getOrCompile(any(), any()))
                .thenReturn(new ConfigurationModel("BUNDLE-001", "product-BUNDLE-001-v1", Instant.now()));

        ConfigurationSessionDto result = sessionService.create(null, "BUNDLE-001", null, "user-1");

        assertNull(result.getProductId());
        assertEquals("BUNDLE-001", result.getBundleId());
        assertEquals("product-BUNDLE-001-v1", result.getRuleSetVersion());
    }

    @Test
    @DisplayName("DRG-424 StartSession: rejects when neither product nor bundle is given")
    void startSessionRejectsWhenNeitherSubjectGiven() {
        assertThrows(BusinessException.class, () -> sessionService.create(null, null, null, "user-1"));
    }

    @Test
    @DisplayName("DRG-424 StartSession: rejects when both product and bundle are given")
    void startSessionRejectsWhenBothSubjectsGiven() {
        assertThrows(BusinessException.class,
                () -> sessionService.create("PROD-001", "BUNDLE-001", null, "user-1"));
    }

    @Test
    @DisplayName("should add selection and validate")
    void addSelectionAndValidate() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.evaluate(any())).thenReturn(ConfigurationValidationResult.ok());
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSessionDto result = sessionService.addSelection(1L, "color", "red");

        assertTrue(result.getSelections().containsKey("color"));
        assertTrue(result.isValid());
        assertNull(result.getValidationErrors());
        verify(constraintSolver).evaluate(any());
    }

    @Test
    @DisplayName("should mark session invalid when hard constraints fail")
    void markInvalidOnConstraintFailure() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.evaluate(any())).thenReturn(ConfigurationValidationResult.of(List.of(
                ConfigurationViolation.builder()
                        .ruleType("REQUIRES")
                        .description("Color is required")
                        .build()
        )));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSessionDto result = sessionService.addSelection(1L, "premium_feature", "yes");

        assertFalse(result.isValid());
        assertEquals("Color is required", result.getValidationErrors());
    }

    @Test
    @DisplayName("soft RECOMMENDS surfaces on session without blocking valid")
    void softRecommendSurfacesWithoutBlocking() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.evaluate(any())).thenReturn(ConfigurationValidationResult.of(List.of(
                ConfigurationViolation.builder()
                        .ruleType("RECOMMENDS")
                        .description("Consider warranty")
                        .build()
        )));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSessionDto result = sessionService.addSelection(1L, "base", "10");

        assertTrue(result.isValid());
        assertEquals("Consider warranty", result.getValidationErrors());
    }

    @Test
    @DisplayName("should complete a valid session")
    void completeValidSession() {
        session.markAsValid();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(configurationModelCache.getOrCompile(any(), any()))
                .thenReturn(new ConfigurationModel("PROD-001", "product-PROD-001-v1", Instant.now()));

        ConfigurationSessionDto result = sessionService.complete(1L);

        assertEquals(ConfigurationStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(outboxEventPublisher).publish(
                eq("ConfigurationSession"),
                eq("1"),
                eq(EventContractRegistry.CONFIGURATION_COMMITTED_V1),
                any()
        );
    }

    @Test
    @DisplayName("ADR-018 D4: the committed event is published under the correlation its payload carries")
    void completePublishesUnderThePayloadCorrelation() {
        session.markAsValid();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(configurationModelCache.getOrCompile(any(), any()))
                .thenReturn(new ConfigurationModel("PROD-001", "product-PROD-001-v1", Instant.now()));
        AtomicReference<EventCorrelation.Context> seen =
                new AtomicReference<>();
        AtomicReference<Object> payload = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(EventCorrelation.current().orElse(null));
            payload.set(invocation.getArgument(3));
            return null;
        }).when(outboxEventPublisher).publish(any(), any(), any(), any());

        sessionService.complete(1L);

        ConfigurationCommittedEvent.CorrelationSnapshot snapshot =
                ((ConfigurationCommittedEvent) payload.get()).getCorrelation();
        assertNotNull(seen.get());
        assertEquals(snapshot.getCorrelationId(), seen.get().correlationId());
        assertEquals(snapshot.getCausationId(), seen.get().causationId());
        assertTrue(EventCorrelation.current().isEmpty(), "the scope is closed after publishing");
    }

    @Test
    @DisplayName("should reject completing an invalid session")
    void rejectCompleteInvalid() {
        session.markAsInvalid("Missing required options");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> sessionService.complete(1L));
    }

    @Test
    @DisplayName("DRG-424: completing a bundle-started session is rejected (no committed-bundle event shape yet)")
    void rejectCompleteBundleSession() {
        ConfigurationSession bundleSession = ConfigurationSession.builder()
                .bundleId("BUNDLE-001")
                .userId("user-1")
                .status(ConfigurationStatus.IN_PROGRESS)
                .selections(new HashMap<>())
                .valid(true) // valid, so only the bundle guard can be what rejects this
                .build();
        bundleSession.setId(4L);
        when(sessionRepository.findById(4L)).thenReturn(Optional.of(bundleSession));

        BusinessException ex = assertThrows(BusinessException.class, () -> sessionService.complete(4L));

        assertEquals("CONFIGURATION_SESSION_BUNDLE_COMPLETE_UNSUPPORTED", ex.getErrorCode());
        verify(sessionRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should cancel an in-progress session")
    void cancelSession() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSessionDto result = sessionService.cancel(1L);

        assertEquals(ConfigurationStatus.CANCELLED, result.getStatus());
    }

    @Test
    @DisplayName("should throw when session not found")
    void sessionNotFound() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sessionService.getById(99L));
    }

    @Test
    @DisplayName("DRG-424 GetSessionState: returns selections, open violations, and available options")
    void getByIdReturnsSessionState() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.evaluate(any())).thenReturn(ConfigurationValidationResult.of(List.of(
                ConfigurationViolation.builder()
                        .ruleType("REQUIRES")
                        .description("Color is required")
                        .build()
        )));
        when(constraintSolver.availableOptionIds(any())).thenReturn(List.of(20L, 30L));

        ConfigurationSessionDto result = sessionService.getById(1L);

        assertEquals(1, result.getOpenViolations().size());
        assertEquals("Color is required", result.getOpenViolations().get(0).getDescription());
        assertEquals(List.of(20L, 30L), result.getAvailableOptionIds());
    }

    private static ConfigurationSessionDto dto(ConfigurationSession s) {
        return ConfigurationSessionDto.builder()
                .id(s.getId())
                .uuid(s.getUuid())
                .productId(s.getProductId())
                .bundleId(s.getBundleId())
                .catalogVersion(s.getCatalogVersion())
                .ruleSetVersion(s.getRuleSetVersion())
                .tenantId(s.getTenantId())
                .userId(s.getUserId())
                .status(s.getStatus())
                .selections(s.getSelections() == null ? Map.of() : Map.copyOf(s.getSelections()))
                .valid(s.isValid())
                .validationErrors(s.getValidationErrors())
                .completedAt(s.getCompletedAt())
                .expiresAt(s.getExpiresAt())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .createdBy(s.getCreatedBy())
                .updatedBy(s.getUpdatedBy())
                .version(s.getVersion())
                .build();
    }
}
