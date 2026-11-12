package com.opencrm.document.controller;

import com.opencrm.document.config.GlobalExceptionHandler;
import com.opencrm.document.config.ClauseRequestExceptionHandler;
import com.opencrm.document.domain.ClauseStatus;
import com.opencrm.document.dto.*;
import com.opencrm.document.service.*;
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

class ClauseControllerTest {
    ClauseLibraryService library = mock(ClauseLibraryService.class);
    ClauseSelectionService selection = mock(ClauseSelectionService.class);
    MockMvc mvc;
    ClauseDto dto = new ClauseDto(1L, 10L, "terms", 1, "Terms", "Legal text", "legal", ClauseStatus.DRAFT, null, null, null, 0);
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ClauseController(library, selection))
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).setControllerAdvice(new ClauseRequestExceptionHandler(), new GlobalExceptionHandler()).build();
    }
    @Test void addClauseReturnsDtoEnvelope() throws Exception {
        when(library.addClause(any())).thenReturn(dto);
        mvc.perform(post("/api/v1/documents/clauses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clauseKey\":\"terms\",\"title\":\"Terms\",\"bodyContent\":\"Legal text\",\"category\":\"legal\",\"sortOrder\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.versionNumber").value(1)).andExpect(jsonPath("$.data.deleted").doesNotExist());
    }
    @Test void listFetchApproveAndRetireAreRouted() throws Exception {
        when(library.list()).thenReturn(List.of(dto)); when(library.get(10L)).thenReturn(dto);
        when(library.approve(10L)).thenReturn(dto); when(library.retire(10L)).thenReturn(dto);
        mvc.perform(get("/api/v1/documents/clauses")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].clauseKey").value("terms"));
        mvc.perform(get("/api/v1/documents/clauses/versions/10")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/documents/clauses/versions/10/approve")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/documents/clauses/versions/10/retire")).andExpect(status().isOk());
    }
    @Test void selectionEndpointUsesStructuredContext() throws Exception {
        when(selection.select(any())).thenReturn(List.of());
        mvc.perform(post("/api/v1/documents/clauses/select").contentType(MediaType.APPLICATION_JSON)
                .content("{\"region\":\"EU\",\"segment\":\"Enterprise\",\"quoteAttributes\":{\"total\":100}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(selection).select(new ClauseSelectionContext("EU", "Enterprise", java.util.Map.of("total", 100)));
    }
    @Test void malformedAndBlankRequestsAreBadRequests() throws Exception {
        mvc.perform(post("/api/v1/documents/clauses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/documents/clauses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clauseKey\":\"\",\"title\":\"\",\"bodyContent\":\"\",\"category\":\"\",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(library);
    }
    @Test void invalidLifecycleReturnsConflictEnvelope() throws Exception {
        when(library.approve(10L)).thenThrow(new IllegalStateException("Only DRAFT clauses can be approved"));
        mvc.perform(post("/api/v1/documents/clauses/versions/10/approve")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }
}
