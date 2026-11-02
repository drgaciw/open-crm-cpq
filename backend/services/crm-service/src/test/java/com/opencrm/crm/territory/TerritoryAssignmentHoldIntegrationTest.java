package com.opencrm.crm.territory;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the real component-scanned policy, with no test-ready override. */
class TerritoryAssignmentHoldIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TerritoryAssignmentReadiness readiness;

    @Test
    void allRoutesReturn503AndWriteNothingByDefault() throws Exception {
        assertThat(readiness.isReady()).isFalse();
        long rulesBefore = jdbc.queryForObject("select count(*) from territory_assignment_rules", Long.class);
        mvc.perform(post("/api/v1/organizations/territory-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule","country":"USA","industry":"Software",
                                 "territoryCode":"WEST","segmentCode":"SMB"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("TERRITORY_ASSIGNMENT_NOT_READY"));
        mvc.perform(get("/api/v1/organizations/territory-rules"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v1/organizations/1/territory-assignment"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/organizations/1/segment"))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from territory_assignment_rules", Long.class)).isEqualTo(rulesBefore);
        assertThat(jdbc.queryForObject("select count(*) from organization_territory_assignments", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isZero();
    }
}
