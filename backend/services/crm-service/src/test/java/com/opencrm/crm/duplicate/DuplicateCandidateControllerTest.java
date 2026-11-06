package com.opencrm.crm.duplicate;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.config.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-517 duplicate candidate contract")
class DuplicateCandidateControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private DuplicateCandidateService duplicateCandidateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DuplicateCandidateController controller = new DuplicateCandidateController(duplicateCandidateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("AC5: publishedCandidateFieldsAreExact")
    void publishedCandidateFieldsAreExact() throws Exception {
        when(duplicateCandidateService.organizationCandidates(7L)).thenReturn(List.of(DuplicateCandidateDto.builder()
                .candidateId(8L)
                .candidateType(DuplicateCandidateType.ORGANIZATION)
                .reason(DuplicateMatchReason.DOMAIN)
                .matchedValue("acme.com")
                .build()));
        when(duplicateCandidateService.contactCandidates(4L)).thenReturn(List.of(DuplicateCandidateDto.builder()
                .candidateId(5L)
                .candidateType(DuplicateCandidateType.CONTACT)
                .reason(DuplicateMatchReason.EMAIL)
                .matchedValue("ann@acme.com")
                .build()));

        JsonNode organization = data(mockMvc.perform(get("/api/v1/organizations/7/duplicate-candidates")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(organization).isNotEmpty();
        assertThat(fieldNames(organization.get(0))).containsExactly(
                "candidateId", "candidateType", "reason", "matchedValue");
        assertThat(organization.get(0).get("candidateId").asLong()).isEqualTo(8L);
        assertThat(organization.get(0).get("candidateType").asString()).isEqualTo("ORGANIZATION");
        assertThat(organization.get(0).get("reason").asString()).isEqualTo("DOMAIN");
        assertThat(organization.get(0).get("matchedValue").asString()).isEqualTo("acme.com");

        JsonNode contact = data(mockMvc.perform(get("/api/v1/contacts/4/duplicate-candidates"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(contact).isNotEmpty();
        assertThat(fieldNames(contact.get(0))).containsExactly(
                "candidateId", "candidateType", "reason", "matchedValue");
        assertThat(contact.get(0).get("candidateType").asString()).isEqualTo("CONTACT");
        assertThat(contact.get(0).get("reason").asString()).isEqualTo("EMAIL");
        verify(duplicateCandidateService).organizationCandidates(7L);
        verify(duplicateCandidateService).contactCandidates(4L);
    }

    @Test
    @DisplayName("AC5: noCandidatesReturnsEmptyList")
    void noCandidatesReturnsEmptyList() throws Exception {
        when(duplicateCandidateService.organizationCandidates(8L)).thenReturn(List.of());
        String body = mockMvc.perform(get("/api/v1/organizations/8/duplicate-candidates"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = JSON.readTree(body);
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.get("data").isArray()).isTrue();
        assertThat(root.get("data").isEmpty()).isTrue();
        verify(duplicateCandidateService).organizationCandidates(8L);
    }

    @Test
    @DisplayName("AC5: unknownOrganizationAndContactReturn404")
    void unknownOrganizationAndContactReturn404() throws Exception {
        when(duplicateCandidateService.organizationCandidates(99L))
                .thenThrow(new ResourceNotFoundException("Organization", 99L));
        when(duplicateCandidateService.contactCandidates(98L))
                .thenThrow(new ResourceNotFoundException("Contact", 98L));

        mockMvc.perform(get("/api/v1/organizations/99/duplicate-candidates"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/contacts/98/duplicate-candidates"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC6: operationsStateSuggestionsMergePathAndFreeMailRule")
    void operationsStateSuggestionsMergePathAndFreeMailRule() throws Exception {
        assertSuggestionCopy(DuplicateCandidateController.class.getMethod("organizationCandidates", Long.class));
        assertSuggestionCopy(DuplicateCandidateController.class.getMethod("contactCandidates", Long.class));
    }

    @Test
    @DisplayName("AC6: openHostDocumentStatesSuggestionsMergeAndFreeMail")
    void openHostDocumentStatesSuggestionsMergeAndFreeMail() throws Exception {
        Path doc = Path.of("docs/api/duplicate-candidates.md");
        if (!Files.exists(doc)) {
            doc = Path.of("../../../docs/api/duplicate-candidates.md");
        }
        if (!Files.exists(doc)) {
            doc = Path.of("../docs/api/duplicate-candidates.md");
        }
        assertThat(doc).exists();
        String text = Files.readString(doc);
        assertThat(text).contains("GET /api/v1/organizations/{id}/duplicate-candidates");
        assertThat(text).contains("GET /api/v1/contacts/{id}/duplicate-candidates");
        assertThat(text).contains("POST /api/v1/organizations/merge");
        assertThat(text).contains("BC-12-04");
        assertThat(text.toLowerCase()).contains("suggestion");
        assertThat(text.toLowerCase()).contains("free-mail");
        assertThat(text).contains("is_deleted");
        assertThat(text).contains("gmail.com");
        assertThat(text).contains("sbcglobal.net");
    }

    private static void assertSuggestionCopy(Method method) {
        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        String description = operation.description().toLowerCase();
        assertThat(description).contains("suggestion");
        assertThat(operation.description()).contains("POST /api/v1/organizations/merge");
        assertThat(operation.description()).contains("BC-12-04");
        assertThat(description).contains("free-mail");
        assertThat(description).contains("explicitly by a user");
    }

    private static JsonNode data(String body) {
        JsonNode root = JSON.readTree(body);
        assertThat(root.get("success").asBoolean()).isTrue();
        return root.get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }
}
