package com.opencrm.crm.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.lead.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OrganizationController.
 * Tests CRUD operations, contact relationships, and search functionality.
 */
@DisplayName("Organization Controller Integration Tests")
@Tag("integration")
class OrganizationControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private DealRepository dealRepository;

    @BeforeEach
    void setUp() {
        // BaseIntegrationTest cleanup runs before each test
    }

    @Test
    @DisplayName("Create organization with valid data returns 201")
    void createOrganization_withValidData_returns201() throws Exception {
        OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                .name("TechCorp International")
                .website("https://techcorp.com")
                .websiteDomain("techcorp.com")
                .industry("Software")
                .employeeCount(250)
                .annualRevenue(15000000L)
                .phone("+1-555-0100")
                .addressLine1("100 Tech Boulevard")
                .city("San Francisco")
                .state("CA")
                .postalCode("94105")
                .country("USA")
                .type(OrganizationType.CUSTOMER)
                .description("Leading software company")
                .build();

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Organization created successfully"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("TechCorp International"))
                .andExpect(jsonPath("$.data.website").value("https://techcorp.com"))
                .andExpect(jsonPath("$.data.industry").value("Software"))
                .andExpect(jsonPath("$.data.employeeCount").value(250))
                .andExpect(jsonPath("$.data.type").value("CUSTOMER"));
    }

    @Test
    @DisplayName("Create organization with blank name returns 400")
    void createOrganization_withBlankName_returns400() throws Exception {
        OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                .name("")
                .website("https://example.com")
                .build();

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Get organization by ID returns 200")
    void getOrganization_withExistingId_returns200() throws Exception {
        OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                .name("Test Organization")
                .website("https://test.com")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long orgId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/organizations/{id}", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orgId))
                .andExpect(jsonPath("$.data.name").value("Test Organization"));
    }

    @Test
    @DisplayName("Get organization with non-existent ID returns 404")
    void getOrganization_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("List organizations with pagination returns 200")
    void listOrganizations_withPagination_returns200() throws Exception {
        // Create multiple organizations
        for (int i = 1; i <= 5; i++) {
            OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                    .name("Organization " + i)
                    .website("https://org" + i + ".com")
                    .build();

            mockMvc.perform(post("/api/v1/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }

        mockMvc.perform(get("/api/v1/organizations")
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
    @DisplayName("Update organization with valid data returns 200")
    void updateOrganization_withValidData_returns200() throws Exception {
        OrganizationCreateRequest createRequest = OrganizationCreateRequest.builder()
                .name("Original Name")
                .website("https://original.com")
                .industry("Technology")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();

        Long orgId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        OrganizationUpdateRequest updateRequest = OrganizationUpdateRequest.builder()
                .name("Updated Name")
                .website("https://updated.com")
                .industry("Software")
                .employeeCount(1000)
                .description("Updated description")
                .build();

        mockMvc.perform(put("/api/v1/organizations/{id}", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Organization updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.website").value("https://updated.com"))
                .andExpect(jsonPath("$.data.industry").value("Software"))
                .andExpect(jsonPath("$.data.employeeCount").value(1000));
    }

    @Test
    @DisplayName("Delete organization returns 204")
    void deleteOrganization_returns204() throws Exception {
        OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                .name("Organization to Delete")
                .website("https://delete.com")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long orgId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/organizations/{id}", orgId))
                .andExpect(status().isOk());

        // Verify organization is soft-deleted
        mockMvc.perform(get("/api/v1/organizations/{id}", orgId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Get organization contacts returns associated contacts")
    void getOrganizationContacts_returnsAssociatedContacts() throws Exception {
        // Create organization
        OrganizationCreateRequest orgRequest = OrganizationCreateRequest.builder()
                .name("Multi-Contact Corp")
                .website("https://multicontact.com")
                .build();

        MvcResult orgResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgRequest)))
                .andReturn();

        Long orgId = objectMapper.readTree(orgResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Create contacts linked to organization
        Organization savedOrg = organizationRepository.findById(orgId).orElseThrow();
        Contact contact1 = Contact.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@multicontact.com")
                .organization(savedOrg)
                .build();

        Contact contact2 = Contact.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@multicontact.com")
                .organization(savedOrg)
                .build();

        contactRepository.save(contact1);
        contactRepository.save(contact2);

        // Get organization contacts
        mockMvc.perform(get("/api/v1/organizations/{id}/contacts", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].organizationId", everyItem(equalTo(orgId.intValue()))));
    }

    @Test
    @DisplayName("Search organizations by name returns matching results")
    void searchOrganizations_byName_returnsMatchingResults() throws Exception {
        OrganizationCreateRequest request1 = OrganizationCreateRequest.builder()
                .name("Acme Corporation")
                .website("https://acme.com")
                .build();
        OrganizationCreateRequest request2 = OrganizationCreateRequest.builder()
                .name("Tech Solutions Inc")
                .website("https://techsolutions.com")
                .build();
        OrganizationCreateRequest request3 = OrganizationCreateRequest.builder()
                .name("Acme Tech")
                .website("https://acmetech.com")
                .build();

        mockMvc.perform(post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));
        mockMvc.perform(post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));
        mockMvc.perform(post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)));

        // Search for "Acme" — data is T[] + pagination (same contract as list)
        mockMvc.perform(get("/api/v1/organizations/search")
                        .param("q", "Acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }
}
