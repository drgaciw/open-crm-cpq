package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaybookControllerTest {

    @Mock
    private PlaybookService playbookService;

    @InjectMocks
    private PlaybookController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsThePlaybookAtVersionOne() throws Exception {
        when(playbookService.create(any())).thenReturn(PlaybookDto.builder()
                .id(5L)
                .name("Discovery")
                .version(1)
                .steps(List.of())
                .build());

        mockMvc.perform(post("/api/v1/playbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Discovery\",\"steps\":[{\"stage\":\"PROSPECTING\",\"questions\":[{\"prompt\":\"Who is the buyer?\"}]}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.name").value("Discovery"));
    }

    @Test
    void createRequiresAName() throws Exception {
        mockMvc.perform(post("/api/v1/playbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(playbookService, never()).create(any());
    }

    @Test
    void nextStepReturnsNullWhenTheStageIsDone() throws Exception {
        when(playbookService.nextStep(9L, "PROSPECTING", "1,2")).thenReturn(PlaybookNextStepResponse.builder()
                .playbookId(9L)
                .version(4)
                .stage("PROSPECTING")
                .step(null)
                .build());

        mockMvc.perform(get("/api/v1/playbooks/9/steps/next")
                        .param("stage", "PROSPECTING")
                        .param("completedStepIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.playbookId").value(9))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.stage").value("PROSPECTING"))
                .andExpect(jsonPath("$.data.step").value(nullValue()));

        verify(playbookService).nextStep(9L, "PROSPECTING", "1,2");
    }

    @Test
    void nextStepReturnsTheSelectedStep() throws Exception {
        when(playbookService.nextStep(eq(9L), eq("QUALIFICATION"), isNull())).thenReturn(PlaybookNextStepResponse.builder()
                .playbookId(9L)
                .version(1)
                .stage("QUALIFICATION")
                .step(PlaybookStepDto.builder()
                        .id(3L)
                        .stage("QUALIFICATION")
                        .sortOrder(0)
                        .questions(List.of(PlaybookQuestionDto.builder().id(30L).prompt("Budget confirmed?").sortOrder(0).build()))
                        .build())
                .build());

        mockMvc.perform(get("/api/v1/playbooks/9/steps/next").param("stage", "QUALIFICATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.step.id").value(3))
                .andExpect(jsonPath("$.data.step.stage").value("QUALIFICATION"))
                .andExpect(jsonPath("$.data.step.questions[0].prompt").value("Budget confirmed?"));
    }

    @Test
    void missingPlaybookIs404() throws Exception {
        when(playbookService.nextStep(eq(9L), eq("PROSPECTING"), isNull()))
                .thenThrow(new ResourceNotFoundException("Playbook", 9L));

        mockMvc.perform(get("/api/v1/playbooks/9/steps/next").param("stage", "PROSPECTING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unknownStageIs400() throws Exception {
        when(playbookService.nextStep(eq(9L), eq("CLOSED"), isNull()))
                .thenThrow(new BusinessException("Unknown playbook stage: CLOSED", "PLAYBOOK_STAGE_UNKNOWN"));

        mockMvc.perform(get("/api/v1/playbooks/9/steps/next").param("stage", "CLOSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLAYBOOK_STAGE_UNKNOWN"));
    }

    @Test
    void addStepReturnsTheUpdatedPlaybook() throws Exception {
        when(playbookService.addStep(eq(5L), any())).thenReturn(PlaybookDto.builder()
                .id(5L)
                .name("Discovery")
                .version(2)
                .steps(List.of())
                .build());

        mockMvc.perform(post("/api/v1/playbooks/5/steps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"NEGOTIATION\",\"questions\":[{\"prompt\":\"What is the commercial path?\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(2));
    }
}
