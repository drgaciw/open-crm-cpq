package com.opencrm.ai.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RuleWorkflowControllerTest {

    @Test
    void returns503WhenAgentsDisabled() {
        RuleWorkflowService workflowService = mock(RuleWorkflowService.class);
        RuleWorkflowController controller = new RuleWorkflowController(workflowService, false);

        ResponseEntity<RuleWorkflowResult> response = controller.createRule(
                new CreateRuleWorkflowRequest("req", null, null, null, null, null, false));

        assertEquals(503, response.getStatusCode().value());
        assertEquals(false, response.getBody().success());
        verify(workflowService, never()).createRule(org.mockito.ArgumentMatchers.any());
    }
}
