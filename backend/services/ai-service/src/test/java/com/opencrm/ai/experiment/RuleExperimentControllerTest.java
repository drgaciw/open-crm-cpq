package com.opencrm.ai.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RuleExperimentControllerTest {

    @Test
    void returns503WhenExperimentsDisabled() {
        RuleExperimentService service = mock(RuleExperimentService.class);
        RuleExperimentController controller = new RuleExperimentController(service, false);

        ResponseEntity<ExperimentResponse> response = controller.create(
                new CreateExperimentRequest(1L, "exp", null, null, null, null));

        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().success());
        verify(service, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applyReturns503WhenDisabled() {
        RuleExperimentService service = mock(RuleExperimentService.class);
        RuleExperimentController controller = new RuleExperimentController(service, false);

        ResponseEntity<ExperimentResponse> response = controller.apply(
                UUID.randomUUID(), new ApplyExperimentRequest("desk@example.com"));

        assertEquals(503, response.getStatusCode().value());
        verify(service, never()).applyWinningVariant(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
