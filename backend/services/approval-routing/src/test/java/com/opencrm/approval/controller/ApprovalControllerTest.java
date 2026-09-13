package com.opencrm.approval.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalController - DTO API boundary")
class ApprovalControllerTest {

    private static final Set<String> FORBIDDEN_ENTITY_FIELDS = Set.of(
            "deleted", "isDeleted", "hibernateLazyInitializer", "handler"
    );

    /** Fields quote-service RoutingApprovalDto reads from routing JSON. */
    private static final Set<String> ROUTING_APPROVAL_WIRE_FIELDS = Set.of(
            "id", "uuid", "quoteId", "approvalType", "status", "requesterId", "approverId",
            "approvalTier", "reason", "rejectionReason", "submittedAt", "approvedAt",
            "rejectedAt", "createdAt"
    );

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ApprovalController approvalController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ApprovalRequestDto sampleDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(approvalController)
                .setMessageConverters(converter)
                .build();

        sampleDto = ApprovalRequestDto.builder()
                .id(9001L)
                .uuid("u-1")
                .quoteId("42")
                .lineId("L-1")
                .approvalType(ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .requesterId("agent")
                .approverId(null)
                .approvalTier("DEAL_DESK")
                .reason("Quote approval submitted via BFF")
                .rejectionReason(null)
                .submittedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .approvedAt(null)
                .rejectedAt(null)
                .createdAt(Instant.parse("2026-09-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST / returns DTO with RoutingApprovalDto wire fields and no deleted")
    void submitReturnsDtoOnly() throws Exception {
        when(approvalService.submit(any(ApprovalSubmitRequest.class))).thenReturn(sampleDto);

        MvcResult result = mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteId":"42","tier":"DEAL_DESK","requesterId":"agent","reason":"Quote approval submitted via BFF"}
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Approval request created"))
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.uuid").value("u-1"))
                .andExpect(jsonPath("$.data.quoteId").value("42"))
                .andExpect(jsonPath("$.data.approvalType").value("QUOTE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requesterId").value("agent"))
                .andExpect(jsonPath("$.data.approvalTier").value("DEAL_DESK"))
                .andExpect(jsonPath("$.data.reason").value("Quote approval submitted via BFF"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andExpect(jsonPath("$.data.isDeleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
        assertRoutingWireFieldsPresent(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET /{id} returns DTO without BaseEntity deleted flag")
    void getByIdReturnsDtoOnly() throws Exception {
        when(approvalService.getById(9001L)).thenReturn(sampleDto);

        MvcResult result = mockMvc.perform(get("/api/v1/approvals/9001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.quoteId").value("42"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET /pending returns DTO list without deleted")
    void getPendingReturnsDtoList() throws Exception {
        when(approvalService.getPendingApprovals()).thenReturn(List.of(sampleDto));

        MvcResult result = mockMvc.perform(get("/api/v1/approvals/pending")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(9001))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET /pending/tier/{tier} returns DTO list")
    void getPendingByTierReturnsDtoList() throws Exception {
        when(approvalService.getPendingApprovalsByTier("DEAL_DESK")).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/v1/approvals/pending/tier/DEAL_DESK")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].approvalTier").value("DEAL_DESK"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
    }

    @Test
    @DisplayName("POST /{id}/approve returns DTO without deleted")
    void approveReturnsDto() throws Exception {
        ApprovalRequestDto approved = sampleDto.toBuilder()
                .status(ApprovalStatus.APPROVED)
                .approverId("mgr-1")
                .approvedAt(Instant.parse("2026-09-01T11:00:00Z"))
                .build();
        when(approvalService.approve(eq(9001L), eq("mgr-1"))).thenReturn(approved);

        MvcResult result = mockMvc.perform(post("/api/v1/approvals/9001/approve")
                        .param("approverId", "mgr-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Approval request approved"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approverId").value("mgr-1"))
                .andExpect(jsonPath("$.data.approvedAt").exists())
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("POST /{id}/reject returns DTO without deleted")
    void rejectReturnsDto() throws Exception {
        ApprovalRequestDto rejected = sampleDto.toBuilder()
                .status(ApprovalStatus.REJECTED)
                .approverId("mgr-1")
                .rejectionReason("Too steep")
                .rejectedAt(Instant.parse("2026-09-01T11:30:00Z"))
                .build();
        when(approvalService.reject(eq(9001L), eq("mgr-1"), eq("Too steep"))).thenReturn(rejected);

        MvcResult result = mockMvc.perform(post("/api/v1/approvals/9001/reject")
                        .param("approverId", "mgr-1")
                        .param("reason", "Too steep")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Approval request rejected"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Too steep"))
                .andExpect(jsonPath("$.data.rejectedAt").exists())
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    private void assertNoEntityLeakFields(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        assertFalse(containsForbiddenField(root),
                "JSON must not contain entity leak fields: " + FORBIDDEN_ENTITY_FIELDS);
    }

    private void assertRoutingWireFieldsPresent(String json) throws Exception {
        JsonNode data = objectMapper.readTree(json).path("data");
        for (String field : ROUTING_APPROVAL_WIRE_FIELDS) {
            // Null-valued optional fields (approverId, rejectionReason, approvedAt, rejectedAt)
            // may be omitted under NON_NULL; required identity/status fields must exist.
            if (Set.of("approverId", "rejectionReason", "approvedAt", "rejectedAt").contains(field)) {
                continue;
            }
            assertFalse(data.path(field).isMissingNode(),
                    "RoutingApprovalDto wire field missing from JSON: " + field);
        }
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
