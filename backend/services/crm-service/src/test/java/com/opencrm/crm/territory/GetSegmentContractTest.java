package com.opencrm.crm.territory;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.api.v1.segment.OrganizationSegmentResponse;
import com.opencrm.crm.config.GlobalExceptionHandler;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DRG-713 contract pins for GetSegment. The published type lives in the versioned package
 * and the serialized field set is the #748 shape.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-713 GetSegment published contract")
class GetSegmentContractTest {

    private static final String ACCOUNT = "11111111-1111-1111-1111-111111111111";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private TerritoryAssignmentService territoryAssignmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TerritoryAssignmentController controller = new TerritoryAssignmentController(territoryAssignmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("published language serializes the exact GetSegment fields")
    void publishedLanguageSerializesExactFields() throws Exception {
        assertThat(OrganizationSegmentResponse.class.getPackageName())
                .isEqualTo("com.opencrm.crm.api.v1.segment");
        Map<String, Class<?>> fieldTypes = new LinkedHashMap<>();
        for (Field field : OrganizationSegmentResponse.class.getDeclaredFields()) {
            fieldTypes.put(field.getName(), field.getType());
        }
        assertThat(fieldTypes).containsOnly(
                Map.entry("organizationId", String.class),
                Map.entry("territory", String.class),
                Map.entry("segment", String.class),
                Map.entry("ruleId", Long.class),
                Map.entry("assigned", boolean.class));

        JsonNode assigned = JSON.readTree(JSON.writeValueAsString(OrganizationSegmentResponse.builder()
                .organizationId(ACCOUNT)
                .territory("WEST")
                .segment("MID_MARKET")
                .ruleId(9L)
                .assigned(true)
                .build()));
        assertThat(assigned).isEqualTo(JSON.readTree("""
                {"organizationId":"11111111-1111-1111-1111-111111111111","territory":"WEST","segment":"MID_MARKET","ruleId":9,"assigned":true}
                """));

        JsonNode unassigned = JSON.readTree(JSON.writeValueAsString(OrganizationSegmentResponse.builder()
                .organizationId(ACCOUNT)
                .assigned(false)
                .build()));
        assertThat(unassigned).isEqualTo(JSON.readTree("""
                {"organizationId":"11111111-1111-1111-1111-111111111111","territory":null,"segment":null,"ruleId":null,"assigned":false}
                """));
    }

    @Test
    @DisplayName("GET segment for an assigned organization returns the populated contract")
    void assignedOrganizationReturnsSegment() throws Exception {
        when(territoryAssignmentService.getSegment(7L)).thenReturn(OrganizationSegmentResponse.builder()
                .organizationId(ACCOUNT)
                .territory("WEST")
                .segment("MID_MARKET")
                .ruleId(9L)
                .assigned(true)
                .build());

        String body = mockMvc.perform(get("/api/v1/organizations/7/segment").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = JSON.readTree(body);
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.get("data")).isEqualTo(JSON.readTree("""
                {"organizationId":"11111111-1111-1111-1111-111111111111","territory":"WEST","segment":"MID_MARKET","ruleId":9,"assigned":true}
                """));
    }

    @Test
    @DisplayName("an existing unassigned organization returns 200 with assigned=false and a null segment")
    void unassignedOrganizationReturnsAssignedFalseAndNullSegment() throws Exception {
        when(territoryAssignmentService.getSegment(7L)).thenReturn(OrganizationSegmentResponse.builder()
                .organizationId(ACCOUNT)
                .assigned(false)
                .build());

        String body = mockMvc.perform(get("/api/v1/organizations/7/segment"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = JSON.readTree(body).get("data");
        assertThat(data.get("assigned").asBoolean()).isFalse();
        assertThat(data.get("segment").isNull()).isTrue();
        assertThat(data.get("territory").isNull()).isTrue();
        assertThat(data.get("ruleId").isNull()).isTrue();
        assertThat(data.get("organizationId").asText()).isEqualTo(ACCOUNT);
    }

    @Test
    @DisplayName("an unknown organization returns 404")
    void unknownOrganizationReturns404() throws Exception {
        when(territoryAssignmentService.getSegment(99L))
                .thenThrow(new ResourceNotFoundException("Organization", 99L));

        mockMvc.perform(get("/api/v1/organizations/99/segment"))
                .andExpect(status().isNotFound());
    }
}
