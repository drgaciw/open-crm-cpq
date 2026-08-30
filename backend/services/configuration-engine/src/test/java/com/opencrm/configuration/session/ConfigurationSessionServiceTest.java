package com.opencrm.configuration.session;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.configuration.constraint.ConstraintSolver;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for Configuration Engine session management.
 * Validates constraint solving and session state transitions.
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
                .build();
        session.setId(1L);
    }

    @Test
    @DisplayName("should create a new configuration session")
    void createSession() {
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            ConfigurationSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        ConfigurationSession result = sessionService.create("PROD-001", "user-1");

        assertNotNull(result);
        assertEquals("PROD-001", result.getProductId());
        assertEquals(ConfigurationStatus.IN_PROGRESS, result.getStatus());
        assertNotNull(result.getExpiresAt());
    }

    @Test
    @DisplayName("should add selection and validate")
    void addSelectionAndValidate() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.evaluate(any())).thenReturn(ConfigurationValidationResult.ok());
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSession result = sessionService.addSelection(1L, "color", "red");

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

        ConfigurationSession result = sessionService.addSelection(1L, "premium_feature", "yes");

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

        ConfigurationSession result = sessionService.addSelection(1L, "base", "10");

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

        ConfigurationSession result = sessionService.complete(1L);

        assertEquals(ConfigurationStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(outboxEventPublisher).publish(
                eq("ConfigurationSession"),
                eq("1"),
                eq(KafkaTopics.CONFIGURATION_COMMITTED),
                any()
        );
    }

    @Test
    @DisplayName("should reject completing an invalid session")
    void rejectCompleteInvalid() {
        session.markAsInvalid("Missing required options");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> sessionService.complete(1L));
    }

    @Test
    @DisplayName("should cancel an in-progress session")
    void cancelSession() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSession result = sessionService.cancel(1L);

        assertEquals(ConfigurationStatus.CANCELLED, result.getStatus());
    }

    @Test
    @DisplayName("should throw when session not found")
    void sessionNotFound() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sessionService.getById(99L));
    }
}
