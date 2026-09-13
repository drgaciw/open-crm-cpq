package com.opencrm.crm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.opencrm.crm.contact.ContactCreateRequest;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealLoseRequest;
import com.opencrm.crm.lead.LeadCreateRequest;
import com.opencrm.crm.lead.LeadLoseRequest;
import com.opencrm.crm.lead.LeadPriority;
import com.opencrm.crm.lead.LeadRepository;
import com.opencrm.crm.organization.OrganizationCreateRequest;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.crm.organization.OrganizationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the complete Lead-to-Deal lifecycle.
 * This is the crown jewel test that validates the entire CRM flow end-to-end:
 *
 * 1. Create an organization
 * 2. Create a contact linked to the organization
 * 3. Create a lead linked to the contact and organization
 * 4. Mark lead as contacted
 * 5. Qualify the lead
 * 6. Convert the lead to a deal
 * 7. Verify the deal was created with data from the lead
 * 8. Advance the deal through all stages
 * 9. Win the deal
 * 10. Verify final state
 */
@DisplayName("Lead to Deal Lifecycle Integration Test")
@Tag("integration")
class LeadToDealLifecycleTest extends BaseIntegrationTest {

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
    @DisplayName("Complete lead-to-deal lifecycle: create, contact, qualify, convert, advance stages, and win")
    void completeLeadToDealLifecycle_happyPath() throws Exception {
        // Step 1: Create an organization
        OrganizationCreateRequest orgRequest = OrganizationCreateRequest.builder()
                .name("Acme Corporation")
                .website("https://acme.com")
                .websiteDomain("acme.com")
                .industry("Technology")
                .employeeCount(500)
                .annualRevenue(50000000L)
                .phone("+1-555-0199")
                .addressLine1("123 Tech Avenue")
                .city("San Francisco")
                .state("CA")
                .postalCode("94105")
                .country("USA")
                .type(OrganizationType.PROSPECT)
                .description("Leading technology company")
                .build();

        MvcResult orgResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Acme Corporation"))
                .andReturn();

        Long organizationId = objectMapper.readTree(orgResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Step 2: Create a contact linked to the organization
        ContactCreateRequest contactRequest = ContactCreateRequest.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .email("sarah.johnson@acme.com")
                .phone("+1-555-0123")
                .mobile("+1-555-0124")
                .jobTitle("VP of Technology")
                .department("Engineering")
                .organizationId(organizationId)
                .primary(true)
                .build();

        MvcResult contactResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Sarah"))
                .andExpect(jsonPath("$.data.lastName").value("Johnson"))
                .andExpect(jsonPath("$.data.organizationId").value(organizationId))
                .andReturn();

        Long contactId = objectMapper.readTree(contactResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Step 3: Create a lead linked to the contact and organization
        LeadCreateRequest leadRequest = LeadCreateRequest.builder()
                .title("Enterprise Platform Migration")
                .contactId(contactId)
                .organizationId(organizationId)
                .source("Conference")
                .sourceDetail("TechCon 2026 - Main Booth")
                .estimatedValue(new BigDecimal("500000"))
                .currency("USD")
                .description("Acme Corp interested in migrating to our enterprise platform. " +
                        "500 users, 3-year contract potential.")
                .priority(LeadPriority.HIGH)
                .campaign("TechCon 2026")
                .build();

        MvcResult leadResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leadRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Enterprise Platform Migration"))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.estimatedValue").value(500000))
                .andExpect(jsonPath("$.data.contactId").value(contactId))
                .andExpect(jsonPath("$.data.organizationId").value(organizationId))
                .andReturn();

        JsonNode leadData = objectMapper.readTree(leadResult.getResponse().getContentAsString())
                .get("data");
        Long leadId = leadData.get("id").asLong();

        // Step 4: Mark lead as contacted
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead marked as contacted"))
                .andExpect(jsonPath("$.data.status").value("CONTACTED"))
                .andExpect(jsonPath("$.data.firstContactedAt").exists());

        // Step 5: Qualify the lead
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead qualified successfully"))
                .andExpect(jsonPath("$.data.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.data.qualifiedAt").exists());

        // Step 6: Convert the lead to a deal
        MvcResult convertResult = mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lead converted to deal successfully"))
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.convertedDealId").exists())
                .andExpect(jsonPath("$.data.convertedAt").exists())
                .andReturn();

        Long dealId = objectMapper.readTree(convertResult.getResponse().getContentAsString())
                .get("data").get("convertedDealId").asLong();

        // Step 7: Verify the deal was created with data from the lead
        mockMvc.perform(get("/api/v1/deals/{id}", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(dealId))
                .andExpect(jsonPath("$.data.name").value("Enterprise Platform Migration"))
                .andExpect(jsonPath("$.data.organizationId").value(organizationId))
                .andExpect(jsonPath("$.data.primaryContactId").value(contactId))
                .andExpect(jsonPath("$.data.amount").value(500000))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.stage").value("PROSPECTING"))
                .andExpect(jsonPath("$.data.probability").value(10))
                .andExpect(jsonPath("$.data.sourceLeadId").value(leadId));

        // Step 8: Advance the deal through stages
        // PROSPECTING (10%) -> QUALIFICATION (20%)
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stage").value("QUALIFICATION"))
                .andExpect(jsonPath("$.data.probability").value(20));

        // QUALIFICATION (20%) -> NEEDS_ANALYSIS (40%)
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("NEEDS_ANALYSIS"))
                .andExpect(jsonPath("$.data.probability").value(40));

        // NEEDS_ANALYSIS (40%) -> PROPOSAL (60%)
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("PROPOSAL"))
                .andExpect(jsonPath("$.data.probability").value(60));

        // PROPOSAL (60%) -> NEGOTIATION (80%)
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("NEGOTIATION"))
                .andExpect(jsonPath("$.data.probability").value(80));

        // Step 9: Win the deal
        mockMvc.perform(post("/api/v1/deals/{id}/win", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deal marked as won"))
                .andExpect(jsonPath("$.data.stage").value("WON"))
                .andExpect(jsonPath("$.data.probability").value(100))
                .andExpect(jsonPath("$.data.wonAt").exists());

        // Step 10: Verify final state - Get the deal one more time to confirm everything
        mockMvc.perform(get("/api/v1/deals/{id}", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stage").value("WON"))
                .andExpect(jsonPath("$.data.probability").value(100))
                .andExpect(jsonPath("$.data.amount").value(500000))
                .andExpect(jsonPath("$.data.wonAt").exists());

        // Verify the original lead is still marked as converted
        mockMvc.perform(get("/api/v1/leads/{id}", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.convertedDealId").value(dealId));

        // Verify the contact still exists and is linked
        mockMvc.perform(get("/api/v1/contacts/{id}", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Sarah"))
                .andExpect(jsonPath("$.data.organizationId").value(organizationId));

        // Verify the organization still exists
        mockMvc.perform(get("/api/v1/organizations/{id}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Acme Corporation"));
    }

    @Test
    @DisplayName("Lead-to-deal lifecycle: alternate path - lead is lost before conversion")
    void leadToDealLifecycle_leadLost_cannotConvert() throws Exception {
        // Create organization
        OrganizationCreateRequest orgRequest = OrganizationCreateRequest.builder()
                .name("Failed Prospect Inc")
                .website("https://failed.com")
                .build();

        MvcResult orgResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgRequest)))
                .andReturn();

        Long organizationId = objectMapper.readTree(orgResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Create contact
        ContactCreateRequest contactRequest = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Smith")
                .email("john.smith@failed.com")
                .organizationId(organizationId)
                .build();

        MvcResult contactResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactRequest)))
                .andReturn();

        Long contactId = objectMapper.readTree(contactResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Create lead
        LeadCreateRequest leadRequest = LeadCreateRequest.builder()
                .title("Unsuccessful Deal Attempt")
                .contactId(contactId)
                .organizationId(organizationId)
                .estimatedValue(new BigDecimal("25000"))
                .build();

        MvcResult leadResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leadRequest)))
                .andReturn();

        Long leadId = objectMapper.readTree(leadResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Contact the lead
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId))
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));

        // Qualify the lead
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId))
                .andExpect(jsonPath("$.data.status").value("QUALIFIED"));

        // Lose the lead instead of converting
        LeadLoseRequest loseRequest = LeadLoseRequest.builder()
                .reason("Budget constraints")
                .build();

        mockMvc.perform(post("/api/v1/leads/{id}/lose", leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOST"));

        // Try to convert the lost lead - should fail
        mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Deal lifecycle: deal lost after multiple stage advancements")
    void dealLifecycle_advanceThroughStages_thenLose() throws Exception {
        // Create organization
        OrganizationCreateRequest orgRequest = OrganizationCreateRequest.builder()
                .name("Almost Customer Corp")
                .build();

        MvcResult orgResult = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgRequest)))
                .andReturn();

        Long organizationId = objectMapper.readTree(orgResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Create contact
        ContactCreateRequest contactRequest = ContactCreateRequest.builder()
                .firstName("Alice")
                .lastName("Brown")
                .email("alice.brown@almost.com")
                .organizationId(organizationId)
                .build();

        MvcResult contactResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contactRequest)))
                .andReturn();

        Long contactId = objectMapper.readTree(contactResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Create and convert lead
        LeadCreateRequest leadRequest = LeadCreateRequest.builder()
                .title("Nearly Closed Deal")
                .contactId(contactId)
                .organizationId(organizationId)
                .estimatedValue(new BigDecimal("150000"))
                .build();

        MvcResult leadResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leadRequest)))
                .andReturn();

        Long leadId = objectMapper.readTree(leadResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Progress lead to conversion
        mockMvc.perform(post("/api/v1/leads/{id}/contact", leadId));
        mockMvc.perform(post("/api/v1/leads/{id}/qualify", leadId));

        MvcResult convertResult = mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId))
                .andReturn();

        Long dealId = objectMapper.readTree(convertResult.getResponse().getContentAsString())
                .get("data").get("convertedDealId").asLong();

        // Advance deal to NEGOTIATION stage
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId)); // QUALIFICATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId)); // NEEDS_ANALYSIS
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId)); // PROPOSAL
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId)) // NEGOTIATION
                .andExpect(jsonPath("$.data.stage").value("NEGOTIATION"));

        // Lose the deal at negotiation stage
        DealLoseRequest dealLoseRequest = DealLoseRequest.builder()
                .reason("Chose competitor")
                .build();

        mockMvc.perform(post("/api/v1/deals/{id}/lose", dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dealLoseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("LOST"))
                .andExpect(jsonPath("$.data.probability").value(0))
                .andExpect(jsonPath("$.data.lostReason").value("Chose competitor"))
                .andExpect(jsonPath("$.data.lostAt").exists());
    }
}
