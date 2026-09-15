package com.opencrm.contract.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.dto.SubscriptionDto;
import com.opencrm.contract.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController - DTO API boundary")
class SubscriptionControllerTest {

    private static final Set<String> FORBIDDEN_ENTITY_FIELDS = Set.of(
            "deleted", "isDeleted", "hibernateLazyInitializer", "handler"
    );

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private SubscriptionDto sampleDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        mockMvc = MockMvcBuilders.standaloneSetup(subscriptionController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        sampleDto = SubscriptionDto.builder()
                .id(1L)
                .uuid("uuid-1")
                .subscriptionNumber("SUB-001")
                .customerId("C-001")
                .quoteId("Q-001")
                .status(SubscriptionStatus.PENDING)
                .planName("Enterprise Plan")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .renewalDate(LocalDate.of(2026, 12, 2))
                .arr(BigDecimal.valueOf(12000))
                .currencyCode("USD")
                .erpSyncStatus("PENDING")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("GET / returns paginated DTOs with PageInfo and no entity leak fields")
    void listReturnsPageMetadataAndDtoOnly() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(subscriptionService.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleDto), pageable, 1));

        MvcResult result = mockMvc.perform(get("/api/v1/subscriptions")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.pagination.hasPrevious").value(false))
                .andExpect(jsonPath("$.data.content[0].subscriptionNumber").value("SUB-001"))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET /{id} returns DTO JSON without BaseEntity deleted flag")
    void getByIdReturnsDtoOnly() throws Exception {
        when(subscriptionService.getById(1L)).thenReturn(sampleDto);

        MvcResult result = mockMvc.perform(get("/api/v1/subscriptions/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.subscriptionNumber").value("SUB-001"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andExpect(jsonPath("$.data.isDeleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("activate returns DTO with ERP fields")
    void activateReturnsDto() throws Exception {
        SubscriptionDto activated = SubscriptionDto.builder()
                .id(1L)
                .subscriptionNumber("SUB-001")
                .customerId("C-001")
                .quoteId("Q-001")
                .status(SubscriptionStatus.ACTIVE)
                .planName("Enterprise Plan")
                .startDate(sampleDto.getStartDate())
                .endDate(sampleDto.getEndDate())
                .erpSyncStatus("SYNCED")
                .erpSubscriptionId("ERP-SUB-001")
                .build();
        when(subscriptionService.activate(1L)).thenReturn(activated);

        mockMvc.perform(post("/api/v1/subscriptions/1/activate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Subscription activated"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.erpSyncStatus").value("SYNCED"))
                .andExpect(jsonPath("$.data.erpSubscriptionId").value("ERP-SUB-001"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
    }

    @Test
    @DisplayName("suspend / cancel / renew return DTOs")
    void lifecycleEndpointsReturnDto() throws Exception {
        when(subscriptionService.suspend(1L)).thenReturn(sampleDto.toBuilder()
                .status(SubscriptionStatus.SUSPENDED).build());
        when(subscriptionService.cancel(1L)).thenReturn(sampleDto.toBuilder()
                .status(SubscriptionStatus.CANCELLED).build());
        when(subscriptionService.renew(eq(1L), any(LocalDate.class))).thenReturn(sampleDto.toBuilder()
                .status(SubscriptionStatus.ACTIVE)
                .endDate(LocalDate.of(2028, 1, 1))
                .build());

        mockMvc.perform(post("/api/v1/subscriptions/1/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        mockMvc.perform(post("/api/v1/subscriptions/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/subscriptions/1/renew")
                        .param("newEndDate", "2028-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value("2028-01-01"));
    }

    @Test
    @DisplayName("customer and status lists return DTO arrays")
    void filteredListsReturnDtos() throws Exception {
        when(subscriptionService.getByCustomerId("C-001")).thenReturn(List.of(sampleDto));
        when(subscriptionService.getByStatus(SubscriptionStatus.PENDING)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/v1/subscriptions/customer/C-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].customerId").value("C-001"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());

        mockMvc.perform(get("/api/v1/subscriptions/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
    }

    private void assertNoEntityLeakFields(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        assertFalse(containsForbiddenField(root), "JSON must not contain entity leak fields: " + FORBIDDEN_ENTITY_FIELDS);
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
