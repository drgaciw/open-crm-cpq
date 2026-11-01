package com.opencrm.crm.organization;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvalidAccountIdIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

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
}
