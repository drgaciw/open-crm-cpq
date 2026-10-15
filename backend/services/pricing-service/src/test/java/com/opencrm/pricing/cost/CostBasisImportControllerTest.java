package com.opencrm.pricing.cost;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@DisplayName("POST/GET /api/v1/cost-basis/imports: DTOs only, errors through the module's handler")
class CostBasisImportControllerTest {

    private static final CostBasisImportSummary SUMMARY = new CostBasisImportSummary(7L, 1L, "Finance", "oid-of-admin",
            Instant.parse("2026-10-15T09:00:00Z"), "a".repeat(64), 2, 1, 1, 0, 0, 1, 1);

    private CostBasisIngestionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CostBasisIngestionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CostBasisImportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("a text/csv body is ingested and the report returned with its rejections")
    void ingest() throws Exception {
        when(service.ingest(anyString())).thenReturn(new CostBasisImportReport(SUMMARY, List.of(
                new RowRejection(3, "GHOST", "DIRECT", RejectionReason.UNKNOWN_PRODUCT, "not in the catalog"))));

        mvc.perform(post("/api/v1/cost-basis/imports").contentType("text/csv").content("csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.audit.importId").value(7))
                .andExpect(jsonPath("$.data.audit.ingestedBy").value("oid-of-admin"))
                .andExpect(jsonPath("$.data.audit.accepted").value(1))
                .andExpect(jsonPath("$.data.audit.rejected").value(1))
                .andExpect(jsonPath("$.data.rejections[0].line").value(3))
                .andExpect(jsonPath("$.data.rejections[0].reason").value("UNKNOWN_PRODUCT"));
        verify(service).ingest("csv");
    }

    @Test
    @DisplayName("a file-level refusal maps to its status and error code")
    void fileLevelRefusal() throws Exception {
        when(service.ingest(anyString())).thenThrow(new BusinessException("header", HttpStatus.BAD_REQUEST,
                "COST_EXPORT_HEADER_INVALID"));

        mvc.perform(post("/api/v1/cost-basis/imports").contentType("text/csv").content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COST_EXPORT_HEADER_INVALID"));
    }

    @Test
    @DisplayName("the audit trail lists imports newest first")
    void list() throws Exception {
        when(service.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(SUMMARY), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/cost-basis/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].importId").value(7))
                .andExpect(jsonPath("$.data.content[0].exportVersion").value(1))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }
}
