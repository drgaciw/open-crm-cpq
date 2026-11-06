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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * applies {@link CustomFieldSchema} itself.
 * <p>
 * Cleanup deletes only ids returned by this test's own SQL inserts and successful API calls.
 * GET-seeded standard definitions, including {@code name}, are not owned and are not deleted.
 * {@link BaseIntegrationTest} still truncates the shared CRM record tables. That is not a
 * zero-residue guarantee and not a concurrency test.
 */
@Tag("integration")
@Tag("docker")
class FieldRegistryPostgresTest extends BaseIntegrationTest {

    private static final String SENTINEL_TEXT = "sentinel-keep";
    private static final String CONTROL_TEXT = "control-keep";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<Long> primaryValueIds = new HashSet<>();
    private final Set<Long> primaryOptionIds = new HashSet<>();
    private final Set<Long> primaryDefinitionIds = new HashSet<>();

    private Long organizationId;
    private Long contactId;
    private Long dealId;
    private Long sentinelOrganizationId;
    private Long sentinelDefinitionId;
    private Long sentinelOptionId;
    private Long sentinelValueId;
    private String sentinelKey;
    private Long controlOrganizationId;
    private Long controlDefinitionId;
    private Long controlOptionId;
    private Long controlValueId;
    private String controlKey;

    @BeforeEach
    void schemaAndRecords() {
        primaryValueIds.clear();
        primaryOptionIds.clear();
        primaryDefinitionIds.clear();
        organizationId = null;
        contactId = null;
        dealId = null;
        sentinelOrganizationId = null;
        sentinelDefinitionId = null;
        sentinelOptionId = null;
        sentinelValueId = null;
        sentinelKey = null;
        controlOrganizationId = null;
        controlDefinitionId = null;
        controlOptionId = null;
        controlValueId = null;
        controlKey = null;

        CustomFieldSchema.apply(jdbcTemplate);
        organizationId = insertOrganization("Acme");
        contactId = insertContact(organizationId);
        dealId = insertDeal(organizationId);
        insertFreshSentinel();
    }

    @AfterEach
    void removeRowsThisTestCreated() {
        SecurityContextHolder.clearContext();
        Throwable assertionFailure = null;
        try {
            deleteIds("crm.field_value", primaryValueIds);
            deleteIds("crm.field_option", primaryOptionIds);
            deleteIds("crm.field_definition", primaryDefinitionIds);
            assertIdsAbsent("crm.field_value", primaryValueIds);
            assertIdsAbsent("crm.field_option", primaryOptionIds);
            assertIdsAbsent("crm.field_definition", primaryDefinitionIds);
            assertSurvived("crm.field_value", sentinelValueId);
            assertSurvived("crm.field_option", sentinelOptionId);
            assertSurvived("crm.field_definition", sentinelDefinitionId);
            assertSurvived("crm.organizations", sentinelOrganizationId);
            assertSurvived("crm.field_value", controlValueId);
            assertSurvived("crm.field_option", controlOptionId);
            assertSurvived("crm.field_definition", controlDefinitionId);
            assertSurvived("crm.organizations", controlOrganizationId);
        } catch (Throwable ex) {
            assertionFailure = ex;
        }
        Throwable cleanupFailure = null;
        try {
            disposeCaptured(controlValueId, controlOptionId, controlDefinitionId, controlOrganizationId);
            controlValueId = null;
            controlOptionId = null;
            controlDefinitionId = null;
            controlOrganizationId = null;
            disposeCaptured(sentinelValueId, sentinelOptionId, sentinelDefinitionId, sentinelOrganizationId);
            sentinelValueId = null;
            sentinelOptionId = null;
            sentinelDefinitionId = null;
            sentinelOrganizationId = null;
            deleteIfPresent("crm.deals", dealId);
            deleteIfPresent("crm.contacts", contactId);
            deleteIfPresent("crm.organizations", organizationId);
            dealId = null;
            contactId = null;
            organizationId = null;
        } catch (Throwable ex) {
            cleanupFailure = ex;
        }
        if (assertionFailure != null) {
            if (cleanupFailure != null) {
                assertionFailure.addSuppressed(cleanupFailure);
            }
            rethrow(assertionFailure);
        }
        if (cleanupFailure != null) {
            rethrow(cleanupFailure);
        }
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
        MvcResult createdResult = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region", null)))
                .andExpect(status().isCreated())
                .andReturn();
        String created = createdResult.getResponse().getContentAsString();
        long id = idOf(created);
        primaryDefinitionIds.add(id);
        insertLateControl();
        jsonPath("$.data.createdBy").value("person-create").match(createdResult);
        jsonPath("$.data.updatedBy").value("person-create").match(createdResult);
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT organization_id FROM crm.field_value WHERE id = ?", Long.class, controlValueId))
                .isEqualTo(controlOrganizationId);
    }

    @Test
    void tenantsAreIsolatedAndStandardFieldsCannotBeDeletedOrRetyped() throws Exception {
        authenticatePerson("person-create");
        MvcResult createdResult = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region A", null)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(createdResult.getResponse().getContentAsString());
        primaryDefinitionIds.add(id);

        MvcResult otherTenant = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("region_code", "Region B", null)))
                .andExpect(status().isCreated())
                .andReturn();
        primaryDefinitionIds.add(idOf(otherTenant.getResponse().getContentAsString()));
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
        MvcResult createdResult = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entity":"ORGANIZATION","key":"segment","label":"Segment","type":"SELECT","required":false,"showOnRecord":true,"showOnList":true,"position":40,"agentFilled":true,"agentBrief":"Segment the account","options":[{"label":"Red","position":1},{"label":"Blue","position":2}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String created = createdResult.getResponse().getContentAsString();
        long definitionId = idOf(created);
        primaryDefinitionIds.add(definitionId);
        long redOptionId = optionId(created, "Red");
        long blueOptionId = optionId(created, "Blue");
        primaryOptionIds.add(redOptionId);
        primaryOptionIds.add(blueOptionId);

        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"optionId\":" + redOptionId + ",\"createdBy\":\"spoofed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_ACTOR_FORBIDDEN"));
        MvcResult valueResult = putOwnedValue(definitionId,
                "{\"organizationId\":" + organizationId + ",\"optionId\":" + redOptionId + "}");
        jsonPath("$.data.optionId").value((int) redOptionId).match(valueResult);
        jsonPath("$.data.createdBy").value("person-create").match(valueResult);

        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/options/" + redOptionId)
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Crimson\",\"position\":1,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options[?(@.label=='Crimson')].id").value((int) redOptionId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT option_id FROM crm.field_value WHERE definition_id = ?", Long.class, definitionId))
                .isEqualTo(redOptionId);

        authenticateService();
        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"optionId\":" + redOptionId + "}"))
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

        MvcResult emailResult = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("billing_email", "Billing email", null)))
                .andExpect(status().isCreated())
                .andReturn();
        long emailId = idOf(emailResult.getResponse().getContentAsString());
        primaryDefinitionIds.add(emailId);
        putOwnedValue(emailId, "{\"organizationId\":" + organizationId + ",\"text\":\"billing@example.com\"}");
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
    void numericOverflowIsRejectedBeforeARowIsWritten() throws Exception {
        authenticatePerson("person-create");
        MvcResult createdResult = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entity":"ORGANIZATION","key":"headcount","label":"Headcount","type":"NUMBER","required":false,"showOnRecord":true,"showOnList":false,"position":40,"agentFilled":false}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long definitionId = idOf(createdResult.getResponse().getContentAsString());
        primaryDefinitionIds.add(definitionId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT numeric_precision FROM information_schema.columns
                WHERE table_schema = 'crm' AND table_name = 'field_value' AND column_name = 'numeric'
                """, Integer.class)).isEqualTo(24);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT numeric_scale FROM information_schema.columns
                WHERE table_schema = 'crm' AND table_name = 'field_value' AND column_name = 'numeric'
                """, Integer.class)).isEqualTo(4);

        rejectNumber(definitionId, "1e+20");
        rejectNumber(definitionId, "-1e+20");
        rejectNumber(definitionId, "123456789012345678901");
        rejectNumber(definitionId, "1.23456");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm.field_value WHERE definition_id = ?", Integer.class, definitionId)).isZero();

        MvcResult zero = putOwnedValue(definitionId,
                "{\"organizationId\":" + organizationId + ",\"numeric\":0}");
        long valueId = idOf(zero.getResponse().getContentAsString());
        assertThat(storedNumeric(definitionId)).isEqualByComparingTo("0");
        MvcResult positiveExponent = putOwnedValue(definitionId,
                "{\"organizationId\":" + organizationId + ",\"numeric\":1e+19}");
        assertThat(idOf(positiveExponent.getResponse().getContentAsString())).isEqualTo(valueId);
        assertThat(storedNumeric(definitionId)).isEqualByComparingTo("1E+19");
        MvcResult positiveBoundary = putOwnedValue(definitionId,
                "{\"organizationId\":" + organizationId + ",\"numeric\":99999999999999999999.9999}");
        assertThat(idOf(positiveBoundary.getResponse().getContentAsString())).isEqualTo(valueId);
        assertThat(storedNumeric(definitionId)).isEqualByComparingTo("99999999999999999999.9999");
        MvcResult negativeBoundary = putOwnedValue(definitionId,
                "{\"organizationId\":" + organizationId + ",\"numeric\":-99999999999999999999.9999}");
        assertThat(idOf(negativeBoundary.getResponse().getContentAsString())).isEqualTo(valueId);
        assertThat(storedNumeric(definitionId)).isEqualByComparingTo("-99999999999999999999.9999");
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
        MvcResult createdResult = mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField("po_box", "PO Box", null)))
                .andExpect(status().isCreated())
                .andReturn();
        long definitionId = idOf(createdResult.getResponse().getContentAsString());
        primaryDefinitionIds.add(definitionId);
        mockMvc.perform(delete("/api/v1/field-definitions/" + definitionId).param("tenant", FixtureTenants.A))
                .andExpect(status().isInternalServerError());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm.field_definition WHERE id = ?", Integer.class, definitionId)).isEqualTo(1);
        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":999999999,\"text\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    private void insertFreshSentinel() {
        sentinelKey = freshKey("s_");
        sentinelOrganizationId = insertOrganization("Sentinel " + sentinelKey);
        sentinelDefinitionId = insertDefinition(FixtureTenants.B, sentinelKey, "Unrelated", SENTINEL_TEXT);
        sentinelOptionId = insertOption(sentinelDefinitionId, "Sentinel option");
        sentinelValueId = insertTextValue(sentinelDefinitionId, sentinelOrganizationId, SENTINEL_TEXT);
    }

    private void insertLateControl() {
        controlKey = freshKey("c_");
        controlOrganizationId = insertOrganization("Control " + controlKey);
        controlDefinitionId = insertDefinition(FixtureTenants.A, controlKey, "Late control", CONTROL_TEXT);
        controlOptionId = insertOption(controlDefinitionId, "Control option");
        controlValueId = insertTextValue(controlDefinitionId, controlOrganizationId, CONTROL_TEXT);
    }

    private long insertDefinition(String tenant, String key, String label, String brief) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO crm.field_definition (
                    tenant, entity, "key", label, type, required, show_on_record, show_on_list, position,
                    agent_filled, agent_brief, created_by, updated_by
                ) VALUES (?, 'organization', ?, ?, 'text', false, true, false, 900, false, ?, 'sentinel', 'sentinel')
                RETURNING id
                """, Long.class, tenant, key, label, brief);
    }

    private long insertOption(long definitionId, String label) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO crm.field_option (definition_id, label, position, created_by, updated_by)
                VALUES (?, ?, 1, 'sentinel', 'sentinel') RETURNING id
                """, Long.class, definitionId, label);
    }

    private long insertTextValue(long definitionId, long ownerOrganizationId, String text) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO crm.field_value (definition_id, organization_id, "text", created_by, updated_by)
                VALUES (?, ?, ?, 'sentinel', 'sentinel') RETURNING id
                """, Long.class, definitionId, ownerOrganizationId, text);
    }

    private MvcResult putOwnedValue(long definitionId, String content) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andReturn();
        primaryValueIds.add(idOf(result.getResponse().getContentAsString()));
        return result;
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

    private void rejectNumber(long definitionId, String numeric) throws Exception {
        mockMvc.perform(put("/api/v1/field-definitions/" + definitionId + "/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizationId + ",\"numeric\":" + numeric + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_VALUE_INVALID"));
    }

    private void assertSurvived(String table, Long id) {
        if (id == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    private void assertIdsAbsent(String table, Set<Long> createdIds) {
        if (createdIds == null || createdIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(createdIds.size(), "?"));
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id IN (" + placeholders + ")",
                Integer.class,
                createdIds.toArray());
        assertThat(remaining).isZero();
    }

    private void disposeCaptured(Long valueId, Long optionId, Long definitionId, Long ownerOrganizationId) {
        deleteIfPresent("crm.field_value", valueId);
        deleteIfPresent("crm.field_option", optionId);
        deleteIfPresent("crm.field_definition", definitionId);
        deleteIfPresent("crm.organizations", ownerOrganizationId);
    }

    private void deleteIds(String table, Set<Long> createdIds) {
        if (createdIds == null || createdIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(createdIds.size(), "?"));
        jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE id IN (" + placeholders + ")",
                createdIds.toArray());
    }

    private void deleteIfPresent(String table, Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", id);
    }

    private BigDecimal storedNumeric(long definitionId) {
        return jdbcTemplate.queryForObject(
                "SELECT \"numeric\" FROM crm.field_value WHERE definition_id = ?",
                BigDecimal.class,
                definitionId);
    }

    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO crm.organizations (uuid, name, created_by) VALUES (?, ?, 'test') RETURNING id",
                Long.class, UUID.randomUUID().toString(), name);
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

    private static String freshKey(String prefix) {
        String key = prefix + UUID.randomUUID().toString().replace("-", "");
        if (!key.matches("^[a-z][a-z0-9_]{0,62}$")) {
            throw new IllegalStateException("Generated field key is not allowed");
        }
        return key;
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

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }
}
