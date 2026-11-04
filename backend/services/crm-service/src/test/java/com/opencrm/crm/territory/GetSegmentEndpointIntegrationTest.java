package com.opencrm.crm.territory;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DRG-713 Open Host Service read against PostgreSQL. The readiness hold stays closed in production;
 * this test opens it the same way the assignment concurrency tests do.
 */
@Import(GetSegmentEndpointIntegrationTest.ReadyTestPolicy.class)
class GetSegmentEndpointIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TerritoryAssignmentService service;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private TerritoryAssignmentRuleRepository rules;

    @Autowired
    private OrganizationTerritoryAssignmentRepository assignments;

    @Autowired
    private TerritoryAssignmentReadiness readiness;

    @BeforeEach
    void enableReadAndClearRules() {
        when(readiness.isReady()).thenReturn(true);
        assignments.deleteAll();
        rules.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{id}/segment returns assigned, unassigned, and unknown")
    void getSegmentOpenHostService() throws Exception {
        Organization unassigned = organizations.saveAndFlush(Organization.builder().name("Unassigned").build());
        JsonNode unassignedData = readSegment(unassigned.getId());
        assertThat(unassignedData.get("organizationId").asText()).isEqualTo(unassigned.getUuid());
        assertThat(unassignedData.get("assigned").asBoolean()).isFalse();
        assertThat(unassignedData.get("segment").isNull()).isTrue();
        assertThat(unassignedData.get("territory").isNull()).isTrue();
        assertThat(unassignedData.get("ruleId").isNull()).isTrue();

        Organization assigned = organizations.saveAndFlush(Organization.builder()
                .name("Assigned")
                .country("USA")
                .state("CA")
                .industry("Software")
                .employeeCount(25)
                .build());
        service.createRule(TerritoryRuleRequest.builder()
                .name("West software")
                .country("USA")
                .state("CA")
                .industry("Software")
                .minEmployeeCount(0)
                .maxEmployeeCount(100)
                .territoryCode("WEST")
                .segmentCode("MID_MARKET")
                .build());
        service.assign(assigned.getId());

        JsonNode assignedData = readSegment(assigned.getId());
        assertThat(assignedData.get("organizationId").asText()).isEqualTo(assigned.getUuid());
        assertThat(assignedData.get("territory").asText()).isEqualTo("WEST");
        assertThat(assignedData.get("segment").asText()).isEqualTo("MID_MARKET");
        assertThat(assignedData.get("assigned").asBoolean()).isTrue();
        assertThat(assignedData.get("ruleId").isNull()).isFalse();

        mvc.perform(get("/api/v1/organizations/{id}/segment", 999_999L))
                .andExpect(status().isNotFound());
    }

    private JsonNode readSegment(Long organizationId) throws Exception {
        String body = mvc.perform(get("/api/v1/organizations/{id}/segment", organizationId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReadyTestPolicy {
        @Bean
        @Primary
        TerritoryAssignmentReadiness readyPolicy() {
            return mock(TerritoryAssignmentReadiness.class);
        }
    }
}
