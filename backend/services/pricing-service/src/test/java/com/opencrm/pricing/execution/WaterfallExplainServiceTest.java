package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.config.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WaterfallExplainServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private PricingAuditLogRepository repository;
    private WaterfallExplainService service;
    private MockMvc mvc;
    private static final String TRACE = "{\"steps\":[{\"stepType\":\"DISCOUNT\",\"amount\":-1.25},{\"stepType\":\"NET_PRICE\",\"amount\":8.75}]}";

    @BeforeEach
    void setup() {
        repository = mock(PricingAuditLogRepository.class);
        service = new WaterfallExplainService(repository, mapper);
        PricingExecutionController controller = new PricingExecutionController(mock(PricingExecutionService.class));
        controller.setWaterfallExplainService(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private PricingAuditLog row(long id) {
        return PricingAuditLog.builder().id(id).quoteExternalId("Q-1")
                .executedAt(Instant.parse("2026-01-01T00:00:00Z")).waterfallTrace(TRACE).build();
    }

    @Test
    void preservesRecordedOrderAndUsesIdToBreakTimestampTie() {
        PricingAuditLog latest = row(2);
        latest.setWaterfallTrace("{\"steps\":[{\"stepType\":\"NET_PRICE\",\"amount\":9.00}]}");
        when(repository.findByQuoteExternalIdOrderByExecutedAtDesc("Q-1")).thenReturn(List.of(latest, row(1)));
        assertThat(service.explain("Q-1", null).getSteps().getFirst().getAmount()).isEqualByComparingTo("9.00");
    }

    @Test
    void historicalSnapshotFallbackDoesNotRecalculate() throws Exception {
        PricingAuditLog historical = row(1);
        historical.setWaterfallTrace(null);
        historical.setOutputSnapshot("{\"waterfall\":" + TRACE + "}");
        when(repository.findById(1L)).thenReturn(Optional.of(historical));
        assertThat(service.explain("Q-1", 1L).getSteps()).extracting(step -> step.getStepType().name())
                .containsExactly("DISCOUNT", "NET_PRICE");
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void doesNotExposeAnAuditBelongingToAnotherQuote() {
        when(repository.findById(1L)).thenReturn(Optional.of(row(1)));
        assertThatThrownBy(() -> service.explain("OTHER", 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsCorruptAndMissingWaterfall() {
        PricingAuditLog audit = row(1);
        when(repository.findById(1L)).thenReturn(Optional.of(audit));
        for (String trace : List.of("bad json", "null", "{}")) {
            audit.setWaterfallTrace(trace);
            assertThatThrownBy(() -> service.explain("Q-1", 1L)).isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("AUDIT_WATERFALL_INVALID"));
        }
    }

    @Test
    void endpointReturnsRecordedStepsInOrder() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(row(1)));
        mvc.perform(get("/api/v1/pricing/waterfall/Q-1").param("auditId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.steps[0].stepType").value("DISCOUNT"))
                .andExpect(jsonPath("$.data.steps[1].amount").value(8.75));
    }

    @Test
    void endpointReturnsLatestAndMissingIs404() throws Exception {
        when(repository.findByQuoteExternalIdOrderByExecutedAtDesc("Q-1")).thenReturn(List.of(row(1)));
        mvc.perform(get("/api/v1/pricing/waterfall/Q-1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/pricing/waterfall/missing")).andExpect(status().isNotFound());
    }
}
