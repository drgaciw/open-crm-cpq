package com.opencrm.crm.deal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DealController.
 * Tests deal lifecycle: creation, stage progression, win/loss, and pipeline analytics.
 */
@DisplayName("Deal Controller Integration Tests")
class DealControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Long testContactId;
    private Long testOrganizationId;

    @BeforeEach
    void setUp() {
        dealRepository.deleteAll();
        contactRepository.deleteAll();
        organizationRepository.deleteAll();

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
    @DisplayName("Create deal with valid data returns 201")
    void createDeal_withValidData_returns201() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Enterprise Software License")
                .organizationId(testOrganizationId)
                .primaryContactId(testContactId)
                .amount(new BigDecimal("250000"))
                .currency("USD")
                .expectedCloseDate(LocalDate.now().plusMonths(3))
                .description("Annual enterprise license for 500 users")
                .type(DealType.NEW_BUSINESS)
                .build();

        mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deal created successfully"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("Enterprise Software License"))
                .andExpect(jsonPath("$.data.stage").value("PROSPECTING"))
                .andExpect(jsonPath("$.data.amount").value(250000))
                .andExpect(jsonPath("$.data.probability").value(10))
                .andExpect(jsonPath("$.data.organizationId").value(testOrganizationId))
                .andExpect(jsonPath("$.data.primaryContactId").value(testContactId));
    }

    @Test
    @DisplayName("Create deal with blank name returns 400")
    void createDeal_withBlankName_returns400() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("")
                .amount(new BigDecimal("10000"))
                .build();

        mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("Create deal with negative amount returns 400")
    void createDeal_withNegativeAmount_returns400() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Test Deal")
                .amount(new BigDecimal("-5000"))
                .build();

        mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Get deal by ID returns 200")
    void getDeal_withExistingId_returns200() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Test Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/deals/{id}", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(dealId))
                .andExpect(jsonPath("$.data.name").value("Test Deal"));
    }

    @Test
    @DisplayName("Get deal with non-existent ID returns 404")
    void getDeal_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/deals/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("List deals with pagination returns 200")
    void listDeals_withPagination_returns200() throws Exception {
        // Create multiple deals
        for (int i = 1; i <= 7; i++) {
            DealCreateRequest request = DealCreateRequest.builder()
                    .name("Deal " + i)
                    .amount(new BigDecimal(i * 10000))
                    .build();

            mockMvc.perform(post("/api/v1/deals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }

        mockMvc.perform(get("/api/v1/deals")
                        .param("size", "5")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.pagination.totalElements").value(7))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(true));
    }

    @Test
    @DisplayName("List deals filtered by stage returns matching results")
    void listDeals_filteredByStage_returnsMatchingResults() throws Exception {
        DealCreateRequest request1 = DealCreateRequest.builder()
                .name("Deal 1")
                .amount(new BigDecimal("10000"))
                .build();
        DealCreateRequest request2 = DealCreateRequest.builder()
                .name("Deal 2")
                .amount(new BigDecimal("20000"))
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))).andReturn();

        mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));

        Long deal1Id = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Advance one deal
        mockMvc.perform(post("/api/v1/deals/{id}/advance", deal1Id));

        // Filter by QUALIFICATION stage
        mockMvc.perform(get("/api/v1/deals")
                        .param("stage", "QUALIFICATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].stage").value("QUALIFICATION"));
    }

    @Test
    @DisplayName("Advance deal stage moves to next stage")
    void advanceDealStage_movesToNextStage() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Test Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Advance from PROSPECTING to QUALIFICATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deal advanced to next stage"))
                .andExpect(jsonPath("$.data.stage").value("QUALIFICATION"))
                .andExpect(jsonPath("$.data.probability").value(20));
    }

    @Test
    @DisplayName("Advance deal through all stages successfully")
    void advanceDeal_throughAllStages_successfully() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Multi-Stage Deal")
                .amount(new BigDecimal("100000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // PROSPECTING -> QUALIFICATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(jsonPath("$.data.stage").value("QUALIFICATION"));

        // QUALIFICATION -> NEEDS_ANALYSIS
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(jsonPath("$.data.stage").value("NEEDS_ANALYSIS"));

        // NEEDS_ANALYSIS -> PROPOSAL
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(jsonPath("$.data.stage").value("PROPOSAL"));

        // PROPOSAL -> NEGOTIATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(jsonPath("$.data.stage").value("NEGOTIATION"))
                .andExpect(jsonPath("$.data.probability").value(75));
    }

    @Test
    @DisplayName("Advance deal from NEGOTIATION returns 400")
    void advanceDeal_fromNegotiation_returns400() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Test Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Advance to NEGOTIATION
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId));
        }

        // Try to advance from NEGOTIATION (should use win/lose instead)
        mockMvc.perform(post("/api/v1/deals/{id}/advance", dealId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Win deal sets stage to WON and probability to 100")
    void winDeal_setsStageToWon_andProbabilityTo100() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Winning Deal")
                .amount(new BigDecimal("75000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/v1/deals/{id}/win", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deal marked as won"))
                .andExpect(jsonPath("$.data.stage").value("WON"))
                .andExpect(jsonPath("$.data.probability").value(100))
                .andExpect(jsonPath("$.data.wonAt").exists());
    }

    @Test
    @DisplayName("Lose deal sets stage to LOST and probability to 0")
    void loseDeal_setsStageToLost_andProbabilityTo0() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Lost Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        DealLoseRequest loseRequest = DealLoseRequest.builder()
                .reason("Chose competitor")
                .build();

        mockMvc.perform(post("/api/v1/deals/{id}/lose", dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deal marked as lost"))
                .andExpect(jsonPath("$.data.stage").value("LOST"))
                .andExpect(jsonPath("$.data.probability").value(0))
                .andExpect(jsonPath("$.data.lostReason").value("Chose competitor"))
                .andExpect(jsonPath("$.data.lostAt").exists());
    }

    @Test
    @DisplayName("Win already lost deal returns 400")
    void winAlreadyLostDeal_returns400() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Test Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Mark as lost
        DealLoseRequest loseRequest = DealLoseRequest.builder()
                .reason("Test")
                .build();

        mockMvc.perform(post("/api/v1/deals/{id}/lose", dealId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loseRequest)));

        // Try to win
        mockMvc.perform(post("/api/v1/deals/{id}/win", dealId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Lose already won deal returns 400")
    void loseAlreadyWonDeal_returns400() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Test Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Mark as won
        mockMvc.perform(post("/api/v1/deals/{id}/win", dealId));

        // Try to lose
        DealLoseRequest loseRequest = DealLoseRequest.builder()
                .reason("Test")
                .build();

        mockMvc.perform(post("/api/v1/deals/{id}/lose", dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loseRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Get pipeline summary returns stage statistics")
    void getPipelineSummary_returnsStageStatistics() throws Exception {
        // Create deals at various stages
        DealCreateRequest request1 = DealCreateRequest.builder()
                .name("Deal 1")
                .amount(new BigDecimal("10000"))
                .build();
        DealCreateRequest request2 = DealCreateRequest.builder()
                .name("Deal 2")
                .amount(new BigDecimal("20000"))
                .build();
        DealCreateRequest request3 = DealCreateRequest.builder()
                .name("Deal 3")
                .amount(new BigDecimal("30000"))
                .build();
        DealCreateRequest request4 = DealCreateRequest.builder()
                .name("Deal 4")
                .amount(new BigDecimal("40000"))
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))).andReturn();
        MvcResult result2 = mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))).andReturn();
        MvcResult result3 = mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3))).andReturn();
        mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request4)));

        Long deal1Id = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        Long deal2Id = objectMapper.readTree(result2.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        Long deal3Id = objectMapper.readTree(result3.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Advance deals to different stages
        mockMvc.perform(post("/api/v1/deals/{id}/advance", deal1Id)); // QUALIFICATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", deal2Id)); // QUALIFICATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", deal3Id)); // QUALIFICATION
        mockMvc.perform(post("/api/v1/deals/{id}/advance", deal3Id)); // NEEDS_ANALYSIS

        // Get pipeline summary
        mockMvc.perform(get("/api/v1/deals/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[*].stage").exists())
                .andExpect(jsonPath("$.data[*].count").exists())
                .andExpect(jsonPath("$.data[*].totalAmount").exists());
    }

    @Test
    @DisplayName("Search deals by name returns matching results")
    void searchDeals_byName_returnsMatchingResults() throws Exception {
        DealCreateRequest request1 = DealCreateRequest.builder()
                .name("Enterprise Cloud Migration")
                .amount(new BigDecimal("500000"))
                .build();
        DealCreateRequest request2 = DealCreateRequest.builder()
                .name("Small Business Package")
                .amount(new BigDecimal("5000"))
                .build();
        DealCreateRequest request3 = DealCreateRequest.builder()
                .name("Enterprise Support Contract")
                .amount(new BigDecimal("100000"))
                .build();

        mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));
        mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));
        mockMvc.perform(post("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)));

        // Search for "Enterprise"
        mockMvc.perform(get("/api/v1/deals/search")
                        .param("q", "Enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("Update deal with valid data returns 200")
    void updateDeal_withValidData_returns200() throws Exception {
        DealCreateRequest createRequest = DealCreateRequest.builder()
                .name("Initial Deal")
                .amount(new BigDecimal("50000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        DealUpdateRequest updateRequest = DealUpdateRequest.builder()
                .name("Updated Deal Name")
                .amount(new BigDecimal("75000"))
                .description("Updated description")
                .build();

        mockMvc.perform(put("/api/v1/deals/{id}", dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Deal updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Updated Deal Name"))
                .andExpect(jsonPath("$.data.amount").value(75000));
    }

    @Test
    @DisplayName("Delete deal returns 204")
    void deleteDeal_returns204() throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name("Deal to Delete")
                .amount(new BigDecimal("10000"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        Long dealId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/deals/{id}", dealId))
                .andExpect(status().isNoContent());

        // Verify deal is soft-deleted
        mockMvc.perform(get("/api/v1/deals/{id}", dealId))
                .andExpect(status().isNotFound());
    }
}
