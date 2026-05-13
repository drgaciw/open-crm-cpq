package com.opencrm.configuration.session;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.configuration.constraint.ConstraintSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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
        when(constraintSolver.validate(any())).thenReturn(Collections.emptyList());
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSession result = sessionService.addSelection(1L, "color", "red");

        assertTrue(result.getSelections().containsKey("color"));
        verify(constraintSolver).validate(any());
    }

    @Test
    @DisplayName("should mark session invalid when constraints fail")
    void markInvalidOnConstraintFailure() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.validate(any())).thenReturn(List.of("Color is required"));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSession result = sessionService.addSelection(1L, "premium_feature", "yes");

        assertFalse(result.isValid());
        assertNotNull(result.getValidationErrors());
    }

    @Test
    @DisplayName("should revalidate and mark session invalid when removing required selection")
    void removeSelectionRevalidatesAndMarksInvalid() {
        session.addSelection("color", "red");
        session.markAsValid();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(constraintSolver.validate(any())).thenReturn(List.of("Color selection is required"));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSession result = sessionService.removeSelection(1L, "color");

        assertFalse(result.isValid());
        assertFalse(result.getSelections().containsKey("color"));
        assertEquals("Color selection is required", result.getValidationErrors());
        verify(constraintSolver).validate(session);
    }

    @Test
    @DisplayName("should complete a valid session")
    void completeValidSession() {
        session.markAsValid();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        ConfigurationSession result = sessionService.complete(1L);

        assertEquals(ConfigurationStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
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
