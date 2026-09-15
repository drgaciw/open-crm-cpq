package com.opencrm.document.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.dto.DocumentGenerationJobDto;
import com.opencrm.document.service.DocumentGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentController - DTO API boundary")
class DocumentControllerTest {

    private static final Set<String> FORBIDDEN_ENTITY_FIELDS = Set.of(
            "deleted", "isDeleted", "hibernateLazyInitializer", "handler"
    );

    @Mock
    private DocumentGenerationService documentGenerationService;

    @InjectMocks
    private DocumentController documentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private DocumentGenerationJobDto sampleDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                .setMessageConverters(converter)
                .build();

        sampleDto = DocumentGenerationJobDto.builder()
                .id(1L)
                .uuid("uuid-1")
                .quoteId("Q-001")
                .customerId("C-001")
                .documentType(DocumentType.QUOTE_PDF)
                .status(JobStatus.PENDING)
                .idempotencyKey("quote-Q-001-pdf")
                .retryCount(0)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("GET /jobs/{id} returns DTO JSON without deleted flag")
    void getJobByIdReturnsDtoOnly() throws Exception {
        when(documentGenerationService.getJobById(1L)).thenReturn(sampleDto);

        MvcResult result = mockMvc.perform(get("/api/v1/documents/jobs/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.quoteId").value("Q-001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.idempotencyKey").value("quote-Q-001-pdf"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andExpect(jsonPath("$.data.isDeleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET jobs by quote and status return DTO arrays")
    void listEndpointsReturnDtos() throws Exception {
        when(documentGenerationService.getJobsByQuote("Q-001")).thenReturn(List.of(sampleDto));
        when(documentGenerationService.getJobsByStatus(JobStatus.PENDING)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/v1/documents/jobs/quote/Q-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quoteId").value("Q-001"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());

        mockMvc.perform(get("/api/v1/documents/jobs/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
    }

    @Test
    @DisplayName("POST /jobs/{id}/retry returns DTO with queued message")
    void retryReturnsDto() throws Exception {
        DocumentGenerationJobDto retried = sampleDto.toBuilder()
                .status(JobStatus.PENDING)
                .errorMessage(null)
                .build();
        when(documentGenerationService.retryJob(1L)).thenReturn(retried);

        mockMvc.perform(post("/api/v1/documents/jobs/1/retry")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job queued for retry"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
    }

    private void assertNoEntityLeakFields(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        assertFalse(containsForbiddenField(root),
                "JSON must not contain entity leak fields: " + FORBIDDEN_ENTITY_FIELDS);
    }

    private boolean containsForbiddenField(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (FORBIDDEN_ENTITY_FIELDS.contains(name)) {
                    return true;
                }
                if (containsForbiddenField(node.get(name))) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                    .anyMatch(this::containsForbiddenField);
        }
        return false;
    }
}
