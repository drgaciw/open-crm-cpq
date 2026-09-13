package com.opencrm.crm.contact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Jackson 3 rejects null/missing values for primitive {@code boolean}.
 * The Angular CRM create form omits {@code primary} and may send {@code organizationId: null}.
 */
@Tag("unit")
@DisplayName("ContactCreateRequest Jackson deserialization")
class ContactCreateRequestJacksonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("deserializes SPA payload without primary field")
    void deserializesSpaPayloadWithoutPrimary() throws Exception {
        String json = """
                {
                  "firstName": "UAT",
                  "lastName": "Contact",
                  "email": "",
                  "phone": "",
                  "jobTitle": "",
                  "department": "",
                  "notes": "",
                  "organizationId": null
                }
                """;

        ContactCreateRequest request = objectMapper.readValue(json, ContactCreateRequest.class);

        assertEquals("UAT", request.getFirstName());
        assertEquals("Contact", request.getLastName());
        assertFalse(Boolean.TRUE.equals(request.getPrimary()));
        assertNull(request.getOrganizationId());
    }

    @Test
    @DisplayName("deserializes minimal firstName/lastName/email payload")
    void deserializesMinimalPayload() throws Exception {
        String json = """
                {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com"}
                """;

        ContactCreateRequest request = objectMapper.readValue(json, ContactCreateRequest.class);

        assertEquals("Ada", request.getFirstName());
        assertEquals("ada@example.com", request.getEmail());
        assertFalse(Boolean.TRUE.equals(request.getPrimary()));
    }
}
