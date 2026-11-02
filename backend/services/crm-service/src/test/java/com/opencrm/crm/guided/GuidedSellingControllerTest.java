package com.opencrm.crm.guided;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Guided selling API")
class GuidedSellingControllerTest {

    @Mock
    private GuidedSellingService guidedSellingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        GuidedSellingController controller = new GuidedSellingController(guidedSellingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/guided-sessions starts a session")
    void start() throws Exception {
        when(guidedSellingService.start(any())).thenReturn(sample(GuidedSessionStatus.OPEN));

        mockMvc.perform(post("/api/v1/guided-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealId\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST answers and derive stay on the guided-sessions resource")
    void answerAndDerive() throws Exception {
        when(guidedSellingService.answer(eq(11L), any())).thenReturn(sample(GuidedSessionStatus.OPEN));
        GuidedSessionDto derived = sample(GuidedSessionStatus.DERIVED);
        derived.setAdvisory(true);
        derived.setProductId("prod-governed-suite");
        derived.setConfigurationSessionId(42L);
        derived.setRecommendations(List.of(GuidedRecommendationDto.builder()
                .productId("prod-governed-suite")
                .optionKey("edition")
                .optionValue("enterprise")
                .advisory(true)
                .build()));
        when(guidedSellingService.derive(11L)).thenReturn(derived);

        mockMvc.perform(post("/api/v1/guided-sessions/11/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionKey\":\"priority\",\"answer\":\"compliance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));

        mockMvc.perform(post("/api/v1/guided-sessions/11/derive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advisory").value(true))
                .andExpect(jsonPath("$.data.configurationSessionId").value(42))
                .andExpect(jsonPath("$.data.recommendations[0].advisory").value(true));

        verify(guidedSellingService).derive(11L);
    }

    @Test
    @DisplayName("a blank answer is rejected")
    void blankAnswerIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/guided-sessions/11/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionKey\":\"priority\",\"answer\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET returns the session and the question catalog")
    void getAndQuestions() throws Exception {
        when(guidedSellingService.get(11L)).thenReturn(sample(GuidedSessionStatus.OPEN));
        when(guidedSellingService.questions()).thenReturn(List.of(GuidedQuestionDto.builder()
                .key("deployment")
                .prompt("Where will this run?")
                .allowedValues(List.of("cloud", "on-prem", "hybrid"))
                .build()));

        mockMvc.perform(get("/api/v1/guided-sessions/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").value("session-1"));
        mockMvc.perform(get("/api/v1/guided-sessions/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("deployment"));
    }

    private static GuidedSessionDto sample(GuidedSessionStatus status) {
        return GuidedSessionDto.builder()
                .id(11L)
                .uuid("session-1")
                .status(status)
                .dealId(5L)
                .advisory(false)
                .recordedLocally(false)
                .answers(List.of())
                .selections(Map.of())
                .recommendations(List.of())
                .build();
    }
}
