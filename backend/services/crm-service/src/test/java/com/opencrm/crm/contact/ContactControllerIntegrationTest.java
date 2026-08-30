package com.opencrm.crm.contact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.BaseIntegrationTest;
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
 * Integration tests for ContactController.
 * Tests CRUD operations and validation using MockMvc and TestContainers.
 */
@DisplayName("Contact Controller Integration Tests")
@Tag("integration")
class ContactControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContactRepository contactRepository;

    @BeforeEach
    void setUp() {
        // BaseIntegrationTest cleanup runs before each test
    }

    @Test
    @DisplayName("Create contact with valid data returns 201")
    void createContact_withValidData_returns201() throws Exception {
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phone("+1-555-0100")
                .jobTitle("Sales Manager")
                .department("Sales")
                .build();

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Contact created successfully"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"))
                .andExpect(jsonPath("$.data.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.data.phone").value("+1-555-0100"))
                .andExpect(jsonPath("$.data.jobTitle").value("Sales Manager"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Create contact with blank first name returns 400")
    void createContact_withBlankFirstName_returns400() throws Exception {
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Create contact with blank last name returns 400")
    void createContact_withBlankLastName_returns400() throws Exception {
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("")
                .email("john.doe@example.com")
                .build();

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Create contact with invalid email returns 400")
    void createContact_withInvalidEmail_returns400() throws Exception {
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("not-an-email")
                .build();

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Get all contacts returns 200 with list")
    void getContacts_returns200() throws Exception {
        // Create test data
        ContactCreateRequest request1 = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        ContactCreateRequest request2 = ContactCreateRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .build();

        mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));

        mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));

        // Get all contacts
        mockMvc.perform(get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }

    @Test
    @DisplayName("Get contact by existing ID returns 200")
    void getContact_withExistingId_returns200() throws Exception {
        // Create a contact
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long contactId = objectMapper.readTree(responseBody).get("data").get("id").asLong();

        // Get the contact by ID
        mockMvc.perform(get("/api/v1/contacts/{id}", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(contactId))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"));
    }

    @Test
    @DisplayName("Get contact with non-existent ID returns 404")
    void getContact_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/contacts/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Update contact with valid data returns 200")
    void updateContact_withValidData_returns200() throws Exception {
        // Create a contact
        ContactCreateRequest createRequest = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .jobTitle("Sales Associate")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long contactId = objectMapper.readTree(responseBody).get("data").get("id").asLong();

        // Update the contact
        ContactUpdateRequest updateRequest = ContactUpdateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe.updated@example.com")
                .jobTitle("Senior Sales Manager")
                .phone("+1-555-9999")
                .build();

        mockMvc.perform(put("/api/v1/contacts/{id}", contactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Contact updated successfully"))
                .andExpect(jsonPath("$.data.id").value(contactId))
                .andExpect(jsonPath("$.data.email").value("john.doe.updated@example.com"))
                .andExpect(jsonPath("$.data.jobTitle").value("Senior Sales Manager"))
                .andExpect(jsonPath("$.data.phone").value("+1-555-9999"));
    }

    @Test
    @DisplayName("Update contact with non-existent ID returns 404")
    void updateContact_withNonExistentId_returns404() throws Exception {
        ContactUpdateRequest updateRequest = ContactUpdateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        mockMvc.perform(put("/api/v1/contacts/{id}", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Delete contact returns 204")
    void deleteContact_returns204() throws Exception {
        // Create a contact
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long contactId = objectMapper.readTree(responseBody).get("data").get("id").asLong();

        // Delete the contact
        mockMvc.perform(delete("/api/v1/contacts/{id}", contactId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Delete contact twice returns 404 on second attempt")
    void deleteContact_twice_returns404OnSecondAttempt() throws Exception {
        // Create a contact
        ContactCreateRequest request = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long contactId = objectMapper.readTree(responseBody).get("data").get("id").asLong();

        // Delete the contact first time
        mockMvc.perform(delete("/api/v1/contacts/{id}", contactId))
                .andExpect(status().isOk());

        // Try to delete again - should return 404
        mockMvc.perform(delete("/api/v1/contacts/{id}", contactId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Search contacts by name returns matching results")
    void searchContacts_byName_returnsMatchingResults() throws Exception {
        // Create test contacts
        ContactCreateRequest request1 = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Smith")
                .email("john.smith@example.com")
                .build();

        ContactCreateRequest request2 = ContactCreateRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .build();

        ContactCreateRequest request3 = ContactCreateRequest.builder()
                .firstName("Bob")
                .lastName("Johnson")
                .email("bob.johnson@example.com")
                .build();

        mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));

        mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));

        mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)));

        // Search for "Smith" — data is T[] + pagination (same contract as list)
        mockMvc.perform(get("/api/v1/contacts/search")
                        .param("q", "Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }
}
