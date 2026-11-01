package com.opencrm.crm.outbox;

import com.opencrm.crm.contact.ContactCreateRequest;
import com.opencrm.crm.deal.DealCreateRequest;
import com.opencrm.crm.deal.DealLoseRequest;
import com.opencrm.crm.organization.OrganizationCreateRequest;
import com.opencrm.crm.organization.OrganizationType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP calls the CRM event tests use to change state. */
final class CrmOutboxHttp {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    CrmOutboxHttp(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    Created createOrganization(String name) throws Exception {
        OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                .name(name)
                .type(OrganizationType.PROSPECT)
                .build();
        return created(mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    Created createContact(String firstName, String email) throws Exception {
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName(firstName)
                .lastName("Outbox")
                .email(email)
                .build();
        return created(mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    Created createDeal(String name) throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name(name)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .build();
        return created(mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    void advance(long dealId) throws Exception {
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isOk());
    }

    void win(long dealId) throws Exception {
        mockMvc.perform(post("/api/v1/deals/{id}/win", dealId))
                .andExpect(status().isOk());
    }

    void lose(long dealId, String reason) throws Exception {
        DealLoseRequest request = DealLoseRequest.builder().reason(reason).build();
        mockMvc.perform(post("/api/v1/deals/{id}/lose", dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private Created created(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new Created(data.get("id").asLong(), data.get("uuid").asString());
    }

    record Created(long id, String uuid) {
    }
}
