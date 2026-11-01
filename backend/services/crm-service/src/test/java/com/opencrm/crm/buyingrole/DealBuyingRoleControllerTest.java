package com.opencrm.crm.buyingrole;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DealBuyingRoleControllerTest {

    @Mock
    private DealBuyingRoleService dealBuyingRoleService;

    @InjectMocks
    private DealBuyingRoleController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new DealBuyingRoleExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET returns the committee in ApiResponse")
    void lists() throws Exception {
        when(dealBuyingRoleService.list(10L)).thenReturn(List.of(DealBuyingRoleDto.builder()
                .id(70L)
                .dealId(10L)
                .contactId(4L)
                .contactName("Ada Lovelace")
                .role(BuyingRole.DECISION_MAKER)
                .build()));

        mockMvc.perform(get("/api/v1/deals/10/buying-roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("DECISION_MAKER"))
                .andExpect(jsonPath("$.data[0].contactId").value(4))
                .andExpect(jsonPath("$.data[0].contactName").value("Ada Lovelace"));
    }

    @Test
    @DisplayName("GET maps a missing deal to 404")
    void missingDeal() throws Exception {
        when(dealBuyingRoleService.list(9L)).thenThrow(new ResourceNotFoundException("Deal", 9L));

        mockMvc.perform(get("/api/v1/deals/9/buying-roles"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST assigns under the deal path and returns 201")
    void assigns() throws Exception {
        when(dealBuyingRoleService.assign(eq(10L), any())).thenReturn(DealBuyingRoleDto.builder()
                .id(70L)
                .dealId(10L)
                .contactId(4L)
                .role(BuyingRole.ECONOMIC_BUYER)
                .build());

        mockMvc.perform(post("/api/v1/deals/10/buying-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":4,\"role\":\"ECONOMIC_BUYER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Buying role assigned"))
                .andExpect(jsonPath("$.data.role").value("ECONOMIC_BUYER"));

        verify(dealBuyingRoleService).assign(10L, AssignBuyingRoleRequest.builder()
                .contactId(4L)
                .role(BuyingRole.ECONOMIC_BUYER)
                .build());
    }

    @Test
    @DisplayName("POST rejects a missing role and text outside the enum")
    void rejectsBadRole() throws Exception {
        mockMvc.perform(post("/api/v1/deals/10/buying-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":4}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/deals/10/buying-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":4,\"role\":\"influencer\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value(DealBuyingRoleService.INVALID));

        verify(dealBuyingRoleService, never()).assign(any(), any());
    }

    @Test
    @DisplayName("POST does not treat a bad contact id as an unknown buying role")
    void badContactIdIsNotAnUnknownRole() throws Exception {
        mockMvc.perform(post("/api/v1/deals/10/buying-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":\"nope\",\"role\":\"CHAMPION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));

        verify(dealBuyingRoleService, never()).assign(any(), any());
    }

    @Test
    @DisplayName("POST maps a duplicate pair to 409")
    void duplicate() throws Exception {
        when(dealBuyingRoleService.assign(eq(10L), any())).thenThrow(new BusinessException(
                "This contact already has a buying role on the deal",
                HttpStatus.CONFLICT,
                DealBuyingRoleService.ALREADY_ASSIGNED));

        mockMvc.perform(post("/api/v1/deals/10/buying-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":4,\"role\":\"CHAMPION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BUYING_ROLE_ALREADY_ASSIGNED"));
    }

    @Test
    @DisplayName("DELETE removes the contact's role")
    void removes() throws Exception {
        mockMvc.perform(delete("/api/v1/deals/10/buying-roles/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Buying role removed"));

        verify(dealBuyingRoleService).remove(10L, 4L);
    }

    @Test
    @DisplayName("a body that is not JSON is 400 and does not mention the closed list")
    void malformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/deals/10/buying-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));

        verify(dealBuyingRoleService, never()).assign(any(), any());
    }

    @Test
    @DisplayName("unreadable bodies that are not an unknown role stay out of the closed-list error")
    void detectorIgnoresUnrelatedReadFailures() {
        assertThat(DealBuyingRoleExceptionHandler.isUnknownBuyingRole(new IllegalArgumentException("broken"))).isFalse();
    }
}
