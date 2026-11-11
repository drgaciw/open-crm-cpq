package com.opencrm.crm.activity;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.crm.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

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
class ActivityControllerTest {

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private ActivityController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createNoteReturnsCreated() throws Exception {
        when(activityService.create(any())).thenReturn(ActivityDto.builder()
                .id(11L)
                .type(ActivityType.NOTE)
                .body("Spoke with the buyer")
                .organizationId(7L)
                .build());

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOTE\",\"organizationId\":7,\"body\":\"Spoke with the buyer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.type").value("NOTE"))
                .andExpect(jsonPath("$.data.body").value("Spoke with the buyer"))
                .andExpect(jsonPath("$.data.organizationId").value(7));
    }

    @Test
    void createRequiresAType() throws Exception {
        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Spoke with the buyer\",\"organizationId\":7}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(activityService, never()).create(any());
    }

    @Test
    void emailCreateSurfacesTheIngestGap() throws Exception {
        when(activityService.create(any())).thenThrow(new BusinessException(
                "EMAIL and MEETING activities are recorded by mailbox ingestion (DRG-521)",
                HttpStatus.UNPROCESSABLE_CONTENT,
                "ACTIVITY_INGEST_NOT_IMPLEMENTED"));

        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EMAIL\",\"organizationId\":7,\"body\":\"thread\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("ACTIVITY_INGEST_NOT_IMPLEMENTED"));
    }

    @Test
    void timelineReturnsThePageForOneParent() throws Exception {
        when(activityService.timeline(eq(7L), isNull(), isNull(), eq(ActivityType.TASK), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ActivityDto.builder()
                        .id(12L)
                        .type(ActivityType.TASK)
                        .subject("Send the recap")
                        .organizationId(7L)
                        .dueAt(Instant.parse("2026-12-01T15:00:00Z"))
                        .build())));

        mockMvc.perform(get("/api/v1/activities")
                        .param("organizationId", "7")
                        .param("type", "TASK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(12))
                .andExpect(jsonPath("$.data[0].subject").value("Send the recap"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void completeReturnsTheClosedTask() throws Exception {
        when(activityService.complete(11L)).thenReturn(ActivityDto.builder()
                .id(11L)
                .type(ActivityType.TASK)
                .subject("Send the recap")
                .completedAt(Instant.parse("2026-11-11T16:00:00Z"))
                .build());

        mockMvc.perform(post("/api/v1/activities/11/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").value("2026-11-11T16:00:00Z"))
                .andExpect(jsonPath("$.data.type").value("TASK"));
    }
}
