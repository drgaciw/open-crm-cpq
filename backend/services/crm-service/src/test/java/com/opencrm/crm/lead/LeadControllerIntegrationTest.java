package com.opencrm.crm.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LeadController.
 * Tests lead lifecycle: creation, status transitions, qualification, conversion, and statistics.
 */
@DisplayName("Lead Controller Integration Tests")
@Tag("integration")
class LeadControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private DealRepository dealRepository;

    private Long testContactId;
    private Long testOrganizationId;

    @BeforeEach
    void setUp() {
        // Create test organization
        Organization org = Organization.builder()
                .name("Test Corp")
                .website("https://testcorp.com")
                .build();
        Organization savedOrg = organizationRepository.save(org);
        testOrganizationId = savedOrg.getId();

        // Create test contact
        Contact contact = Contact.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@testcorp.com")
                .organization(savedOrg)
                .build();
        testContactId = contactRepository.save(contact).getId();
    }

    @Test
    @DisplayName("Create lead with valid data returns 201")
    void createLead_withValidData_returns201() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Enterprise Software Deal")
                .contactId(testContactId)
                .organizationId(testOrganizationId)
                .source("Website")
                .sourceDetail("Contact Form")
                .estimatedValue(new BigDecimal("50000"))
                .currency("USD")
                .description("Large enterprise opportunity")
                .priority(LeadPriority.HIGH)
                .build();

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead created successfully"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value("Enterprise Software Deal"))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.estimatedValue").value(50000))
                .andExpect(jsonPath("$.data.contactId").value(testContactId));
    }

    @Test
    @DisplayName("Create lead with blank title returns 400")
    void createLead_withBlankTitle_returns400() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("")
                .contactId(testContactId)
                .build();

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Create lead with negative estimated value returns 400")
    void createLead_withNegativeValue_returns400() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .estimatedValue(new BigDecimal("-1000"))
                .build();

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Get lead by ID returns 200")
    void getLead_withExistingId_returns200() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/leads/{id}", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(leadId))
                .andExpect(jsonPath("$.data.title").value("Test Lead"));
    }

    @Test
    @DisplayName("List leads with pagination returns 200")
    void listLeads_withPagination_returns200() throws Exception {
        // Create multiple leads
        for (int i = 1; i <= 5; i++) {
            LeadCreateRequest request = LeadCreateRequest.builder()
                    .title("Test Lead " + i)
                    .contactId(testContactId)
                    .build();

            mockMvc.perform(post("/api/v1/leads")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }

        mockMvc.perform(get("/api/v1/leads")
                        .param("size", "3")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.pagination.totalElements").value(5))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(true));
    }

    @Test
    @DisplayName("List leads filtered by status returns matching results")
    void listLeads_filteredByStatus_returnsMatchingResults() throws Exception {
        // Create leads with different statuses
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("New Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Mark one as contacted
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId));

        // Filter by CONTACTED status
        mockMvc.perform(get("/api/v1/leads")
                        .param("status", "CONTACTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("CONTACTED"));
    }

    @Test
    @DisplayName("Mark lead as contacted changes status to CONTACTED")
    void markLeadAsContacted_changesStatusToContacted() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead marked as contacted"))
                .andExpect(jsonPath("$.data.status").value("CONTACTED"))
                .andExpect(jsonPath("$.data.firstContactedAt").exists());
    }

    @Test
    @DisplayName("Qualify contacted lead changes status to QUALIFIED")
    void qualifyContactedLead_changesStatusToQualified() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // First mark as contacted
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId));

        // Then qualify
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead qualified successfully"))
                .andExpect(jsonPath("$.data.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.data.qualifiedAt").exists());
    }

    @Test
    @DisplayName("Qualify NEW lead without contact returns 400")
    void qualifyNewLead_withoutContact_returns400() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Try to qualify without contacting first
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Convert qualified lead creates deal and sets status to CONVERTED")
    void convertQualifiedLead_createsDeal_andSetsStatusToConverted() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Enterprise Deal")
                .contactId(testContactId)
                .organizationId(testOrganizationId)
                .estimatedValue(new BigDecimal("100000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Contact and qualify the lead
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId));
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId));

        // Convert to deal
        mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead converted to deal successfully"))
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.convertedDealId").exists())
                .andExpect(jsonPath("$.data.convertedAt").exists());
    }

    @Test
    @DisplayName("Convert non-qualified lead returns 400")
    void convertNonQualifiedLead_returns400() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Try to convert without qualifying
        mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Lose lead changes status to LOST")
    void loseLead_changesStatusToLost() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        LeadLoseRequest loseRequest = LeadLoseRequest.builder()
                .reason("Not interested")
                .build();

        mockMvc.perform(post("/api/v1/leads/{id}/lose", leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead marked as lost"))
                .andExpect(jsonPath("$.data.status").value("LOST"))
                .andExpect(jsonPath("$.data.lostReason").value("Not interested"))
                .andExpect(jsonPath("$.data.lostAt").exists());
    }

    @Test
    @DisplayName("Lose already converted lead returns 400")
    void loseConvertedLead_returns400() throws Exception {
        LeadCreateRequest request = LeadCreateRequest.builder()
                .title("Test Lead")
                .contactId(testContactId)
                .organizationId(testOrganizationId)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long leadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Contact, qualify, and convert
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId));
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId));
        mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId));

        // Try to mark as lost
        LeadLoseRequest loseRequest = LeadLoseRequest.builder()
                .reason("Test")
                .build();

        mockMvc.perform(post("/api/v1/leads/{id}/lose", leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loseRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Get lead stats returns status counts")
    void getLeadStats_returnsStatusCounts() throws Exception {
        // Create leads with different statuses
        LeadCreateRequest request1 = LeadCreateRequest.builder()
                .title("Lead 1")
                .contactId(testContactId)
                .build();
        LeadCreateRequest request2 = LeadCreateRequest.builder()
                .title("Lead 2")
                .contactId(testContactId)
                .build();
        LeadCreateRequest request3 = LeadCreateRequest.builder()
                .title("Lead 3")
                .contactId(testContactId)
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))).andReturn();
        MvcResult result2 = mockMvc.perform(post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))).andReturn();
        MvcResult result3 = mockMvc.perform(post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3))).andReturn();

        Long lead1Id = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        Long lead2Id = objectMapper.readTree(result2.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Mark one as contacted, one as qualified
        mockMvc.perform(post("/api/v1/leads/{id}/contact", lead1Id));
        mockMvc.perform(post("/api/v1/leads/{id}/contact", lead2Id));
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", lead2Id));

        // Get stats
        mockMvc.perform(get("/api/v1/leads/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.NEW").value(1))
                .andExpect(jsonPath("$.data.CONTACTED").value(1))
                .andExpect(jsonPath("$.data.QUALIFIED").value(1));
    }

    @Test
    @DisplayName("Search leads by title returns matching results")
    void searchLeads_byTitle_returnsMatchingResults() throws Exception {
        LeadCreateRequest request1 = LeadCreateRequest.builder()
                .title("Enterprise Software Deal")
                .contactId(testContactId)
                .build();
        LeadCreateRequest request2 = LeadCreateRequest.builder()
                .title("Small Business Deal")
                .contactId(testContactId)
                .build();
        LeadCreateRequest request3 = LeadCreateRequest.builder()
                .title("Enterprise Hardware Deal")
                .contactId(testContactId)
                .build();

        mockMvc.perform(post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));
        mockMvc.perform(post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));
        mockMvc.perform(post("/api/v1/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)));

        // Search for "Enterprise" — data is T[] + pagination (same contract as list)
        mockMvc.perform(get("/api/v1/leads/search")
                        .param("q", "Enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }
}
