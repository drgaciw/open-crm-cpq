package com.opencrm.crm.fields;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL coverage for the registry. The Flyway version is not allocated, so this test
 * applies {@link CustomFieldSchema} itself and deletes only the rows it creates.
 */
@Tag("integration")
@Tag("docker")
class FieldRegistryPostgresTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long organizationId;
    private long contactId;
    private long dealId;

    @BeforeEach
    void schemaAndRecords() {
        CustomFieldSchema.apply(jdbcTemplate);
        deleteFieldRows();
        organizationId = insertOrganization();
        contactId = insertContact(organizationId);
        dealId = insertDeal(organizationId);
    }

    @AfterEach
    void removeRowsThisTestCreated() {
        SecurityContextHolder.clearContext();
        deleteFieldRows();
        jdbcTemplate.update("DELETE FROM crm.deals WHERE id = ?", dealId);
        jdbcTemplate.update("DELETE FROM crm.contacts WHERE id = ?", contactId);
        jdbcTemplate.update("DELETE FROM crm.organizations WHERE id = ?", organizationId);
    }

    @Test
    void createRelabelAndArchiveAttributeTheCurrentActor() throws Exception {
        authenticatePerson("person-create");
        mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region", "spoofed-creator")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_ACTOR_FORBIDDEN"));
        String created = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdBy").value("person-create"))
                .andExpect(jsonPath("$.data.updatedBy").value("person-create"))
                .andReturn().getResponse().getContentAsString();
        long id = idOf(created);
        assertActors(id, "person-create", "person-create", false);

        authenticatePerson("person-relabel");
        mockMvc.perform(put("/api/v1/field-definitions/" + id)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Territory code\",\"version\":0,\"updatedBy\":\"spoofed-editor\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_ACTOR_FORBIDDEN"));
        assertActors(id, "person-create", "person-create", false);
        mockMvc.perform(put("/api/v1/field-definitions/" + id)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Territory code\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("region_code"))
                .andExpect(jsonPath("$.data.label").value("Territory code"))
                .andExpect(jsonPath("$.data.createdBy").value("person-create"))
                .andExpect(jsonPath("$.data.updatedBy").value("person-relabel"));
        assertActors(id, "person-create", "person-relabel", false);

        authenticatePerson("person-archive");
        mockMvc.perform(post("/api/v1/field-definitions/" + id + "/archive")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"archivedBy\":\"spoofed-archiver\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_ACTOR_FORBIDDEN"));
        assertActors(id, "person-create", "person-relabel", false);
        mockMvc.perform(post("/api/v1/field-definitions/" + id + "/archive")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdBy").value("person-create"))
                .andExpect(jsonPath("$.data.updatedBy").value("person-archive"))
                .andExpect(jsonPath("$.data.archivedAt").exists());
        assertActors(id, "person-create", "person-archive", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm.field_definition WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void tenantsAreIsolatedAndStandardFieldsCannotBeDeletedOrRetyped() throws Exception {
        authenticatePerson("person-create");
        String created = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region A", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = idOf(created);

        mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region B", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/field-definitions/" + id).param("tenant", FixtureTenants.B))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .param("entity", "ORGANIZATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='region_code')].label").value("Region A"))
                .andExpect(jsonPath("$.data[?(@.key=='name')].standard").value(true));

        String listed = mockMvc.perform(get("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .param("entity", "ORGANIZATION"))
                .andReturn().getResponse().getContentAsString();
        long nameId = standardId(listed, "name");
        mockMvc.perform(post("/api/v1/field-definitions/" + nameId + "/archive")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_STANDARD_LOCKED"));
        mockMvc.perform(put("/api/v1/field-definitions/" + nameId)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NUMBER\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_STANDARD_LOCKED"));
        mockMvc.perform(put("/api/v1/field-definitions/" + nameId)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showOnList\":false,\"position\":15,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showOnList").value(false))
                .andExpect(jsonPath("$.data.updatedBy").value("person-create"));
    }

    @Test
    void optionRenameKeepsTheValueAndAgentsCannotWriteValues() throws Exception {
        authenticatePerson("person-create");
        String created = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entity":"ORGANIZATION","key":"segment","label":"Segment","type":"SELECT","required":false,"showOnRecord":true,"showOnList":true,"position":40,"agentFilled":true,"agentBrief":"Segment the account","options":[{"label":"Red","position":1},{"label":"Blue","position":2}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long definitionId = idOf(created);
        long optionId = optionId(created, "Red");

        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"optionId\":" + optionId + ",\"createdBy\":\"spoofed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_ACTOR_FORBIDDEN"));
        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"optionId\":" + optionId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.optionId").value(optionId))
                .andExpect(jsonPath("$.data.createdBy").value("person-create"));

        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/options/" + optionId)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Crimson\",\"position\":1,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options[?(@.label=='Crimson')].id").value((int) optionId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT option_id FROM crm.field_value WHERE definition_id = ?", Long.class, definitionId))
                .isEqualTo(optionId);

        authenticateService();
        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"optionId\":" + optionId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AGENT_FIELD_VALUE_FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm.field_value WHERE definition_id = ?", Integer.class, definitionId)).isEqualTo(1);

        authenticatePerson("person-create");
        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NUMBER\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_TYPE_INCOMPATIBLE"));

        String emailField = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("billing_email", "Billing email", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long emailId = idOf(emailField);
        mockMvc.perform(put("/api/v1/field-definitions/" + emailId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"text\":\"billing@example.com\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/field-definitions/" + emailId)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EMAIL\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("EMAIL"))
                .andExpect(jsonPath("$.data.storageClass").value("TEXT"));

        mockMvc.perform(put("/api/v1/field-definitions/" + emailId + "/values")
                        .param("tenant", FixtureTenants.B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":" + contactId + ",\"text\":\"other@example.com\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/field-definitions/" + emailId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dealId\":" + dealId + ",\"text\":\"deal@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_RECORD_MISMATCH"));
    }

    @Test
    void typedColumnsAreNotJsonAndAMissingRecordIsNotCreated() throws Exception {
        List<String> types = jdbcTemplate.queryForList("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'crm'
                  AND table_name IN ('field_definition', 'field_option', 'field_value')
                """, String.class);
        assertThat(types).isNotEmpty();
        assertThat(types).noneMatch(type -> type.equalsIgnoreCase("json") || type.equalsIgnoreCase("jsonb"));
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'crm' AND table_name = 'field_value'
                """, String.class)).contains("text", "numeric", "date", "bool", "option_id", "user_id");

        authenticatePerson("person-create");
        String created = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("po_box", "PO Box", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long definitionId = idOf(created);
        mockMvc.perform(delete("/api/v1/field-definitions/" + definitionId).param("tenant", FixtureTenants.A))
                .andExpect(status().isInternalServerError());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm.field_definition WHERE id = ?", Integer.class, definitionId)).isEqualTo(1);
        mockMvc.perform(put("/api/v1/field-definitions/" + idOf(created) + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":999999999,\"text\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    private void assertActors(long id, String createdBy, String updatedBy, boolean archived) {
        String actualCreated = jdbcTemplate.queryForObject(
                "SELECT created_by FROM crm.field_definition WHERE id = ?", String.class, id);
        String actualUpdated = jdbcTemplate.queryForObject(
                "SELECT updated_by FROM crm.field_definition WHERE id = ?", String.class, id);
        Object archivedAt = jdbcTemplate.queryForObject(
                "SELECT archived_at FROM crm.field_definition WHERE id = ?", Object.class, id);
        assertThat(actualCreated).isEqualTo(createdBy);
        assertThat(actualUpdated).isEqualTo(updatedBy);
        if (archived) {
            assertThat(archivedAt).isNotNull();
        } else {
            assertThat(archivedAt).isNull();
        }
    }

    private void deleteFieldRows() {
        jdbcTemplate.update("DELETE FROM crm.field_value");
        jdbcTemplate.update("DELETE FROM crm.field_option");
        jdbcTemplate.update("DELETE FROM crm.field_definition");
    }

    private long insertOrganization() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO crm.organizations (uuid, name, created_by) VALUES (?, 'Acme', 'test') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private long insertContact(long organization) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO crm.contacts (uuid, first_name, last_name, organization_id, created_by) VALUES (?, 'Ada', 'Lovelace', ?, 'test') RETURNING id",
                Long.class, UUID.randomUUID().toString(), organization);
    }

    private long insertDeal(long organization) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO crm.deals (uuid, name, organization_id, stage, created_by) VALUES (?, 'Expansion', ?, 'PROSPECTING', 'test') RETURNING id",
                Long.class, UUID.randomUUID().toString(), organization);
    }

    private static String textField(String key, String label, String spoofedActor) {
        String actor = spoofedActor == null ? "" : ",\"createdBy\":\"" + spoofedActor + "\"";
        return "{\"entity\":\"ORGANIZATION\",\"key\":\"" + key + "\",\"label\":\"" + label
                + "\",\"type\":\"TEXT\",\"required\":false,\"showOnRecord\":true,\"showOnList\":true,\"position\":500,\"agentFilled\":true,\"agentBrief\":\"Find it\""
                + actor + "}";
    }

    private static long idOf(String json) {
        int marker = json.indexOf("\"id\":");
        int start = marker + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private static long optionId(String json, String label) {
        int labelAt = json.indexOf("\"label\":\"" + label + "\"");
        int idAt = json.lastIndexOf("\"id\":", labelAt);
        int start = idAt + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private static long standardId(String json, String key) {
        int keyAt = json.indexOf("\"key\":\"" + key + "\"");
        int idAt = json.lastIndexOf("\"id\":", keyAt);
        int start = idAt + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private static void authenticatePerson(String oid) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(oid)
                .claim("oid", oid)
                .claim("scp", "user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static void authenticateService() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("agent-client")
                .claim("idtyp", "app")
                .claim("azp", "enrichment-agent")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
