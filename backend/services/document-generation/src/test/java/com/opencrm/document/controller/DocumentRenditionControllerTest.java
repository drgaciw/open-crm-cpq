package com.opencrm.document.controller;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.document.config.*;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.dto.*;
import com.opencrm.document.service.DocumentRenditionService;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentRenditionControllerTest {
    DocumentRenditionService service = mock(DocumentRenditionService.class);
    MockMvc mvc;
    DocumentRenditionDto dto = new DocumentRenditionDto(20L, 10L, "Q-1", JobStatus.PENDING, null,
            List.of(new SelectedClauseDto(1L, 12L, "terms", 2, "Terms", "Approved body", 0)));

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new DocumentRenditionController(service))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .setControllerAdvice(new ClauseRequestExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test void generateReturnsExactSelectedVersions() throws Exception {
        when(service.generate(any())).thenReturn(dto);
        mvc.perform(post("/api/v1/documents/renditions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":\"Q-1\",\"customerId\":\"C-1\",\"documentType\":\"QUOTE_PDF\",\"context\":{\"region\":\"EU\",\"quoteAttributes\":{}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.jobId").value(10))
                .andExpect(jsonPath("$.data.selectedClauses[0].versionId").value(12))
                .andExpect(jsonPath("$.data.selectedClauses[0].versionNumber").value(2))
                .andExpect(jsonPath("$.data.selectedClauses[0].bodyContent").value("Approved body"))
                .andExpect(jsonPath("$.data.selectedClauses[0].deleted").doesNotExist());
    }

    @Test void fetchReturnsSnapshotAndStatus() throws Exception {
        when(service.get(20L)).thenReturn(dto);
        mvc.perform(get("/api/v1/documents/renditions/20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedClauses[0].clauseKey").value("terms"));
    }

    @Test void crossTenantRenditionIsNotFound() throws Exception {
        when(service.get(20L)).thenThrow(new ResourceNotFoundException("DocumentRendition", 20L));
        mvc.perform(get("/api/v1/documents/renditions/20")).andExpect(status().isNotFound());
    }

    @Test void missingContextIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/documents/renditions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":\"Q-1\",\"customerId\":\"C-1\",\"documentType\":\"QUOTE_PDF\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
