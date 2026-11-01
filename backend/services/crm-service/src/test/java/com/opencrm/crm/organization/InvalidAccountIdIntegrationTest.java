package com.opencrm.crm.organization;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvalidAccountIdIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void nonpositiveOrganizationIdsReturnNotFound() throws Exception {
        for (long id : new long[]{0, -1}) {
            mvc.perform(get("/api/v1/organizations/{id}", id)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/organizations/{id}/contacts", id)).andExpect(status().isNotFound());
            mvc.perform(put("/api/v1/organizations/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/organizations/{id}", id)).andExpect(status().isNotFound());
        }
    }

    @Test
    void nonpositiveContactOrganizationIdsReturnNotFound() throws Exception {
        String created = mvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long contactId = objectMapper.readTree(created).get("data").get("id").asLong();
        for (long id : new long[]{0, -1}) {
            mvc.perform(post("/api/v1/contacts").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"organizationId\":" + id + "}"))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/v1/contacts/{id}", contactId).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"organizationId\":" + id + "}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void legacyNumericRouteStillResolvesUppercaseStoredUuid() throws Exception {
        String response = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Legacy UUID\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("data").get("id").asLong();
        jdbc.update("UPDATE organizations SET uuid = UPPER(uuid) WHERE id = ?", id);
        mvc.perform(get("/api/v1/organizations/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(post("/api/v1/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"organizationId\":" + id + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.organizationId").value(id));
    }

    @Test
    void caseCollidingUuidsFailClosedInsteadOfSelectingAnotherAccount() throws Exception {
        String first = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"First\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Second\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var firstData = objectMapper.readTree(first).get("data");
        long firstId = firstData.get("id").asLong();
        long secondId = objectMapper.readTree(second).get("data").get("id").asLong();
        mvc.perform(get("/api/v1/organizations/{id}", firstId)).andExpect(status().isOk());
        jdbc.update("UPDATE organizations SET uuid = UPPER(?) WHERE id = ?", firstData.get("uuid").asString(), secondId);
        mvc.perform(get("/api/v1/organizations/{id}", firstId)).andExpect(status().is5xxServerError());
        mvc.perform(get("/api/v1/organizations/{id}", secondId)).andExpect(status().is5xxServerError());
    }

    @Test
    void contactsRouteStillReturnsNotFoundForDeletedOrganization() throws Exception {
        String response = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Archived\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("data").get("id").asLong();
        mvc.perform(delete("/api/v1/organizations/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizations/{id}/contacts", id)).andExpect(status().isNotFound());
    }
}
