package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldRegistryServiceTest {

    private final MemoryStore store = new MemoryStore();
    private final CurrentActor actor = new CurrentActor("service:crm-service", false);
    private final FieldRegistryService service = new FieldRegistryService(store, actor);

    @BeforeEach
    void person() {
        authenticate("person-create", false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createArchiveAndRelabelRecordTheCurrentActorNotTheClient() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        assertThat(created.getCreatedBy()).isEqualTo("person-create");
        assertThat(created.getUpdatedBy()).isEqualTo("person-create");

        authenticate("person-relabel", false);
        FieldDefinitionResponse relabeled = service.update(FixtureTenants.A, created.getId(), FieldDefinitionUpdateRequest.builder()
                .label("Territory code")
                .version(created.getVersion())
                .build());
        assertThat(relabeled.getKey()).isEqualTo("region_code");
        assertThat(relabeled.getLabel()).isEqualTo("Territory code");
        assertThat(relabeled.getCreatedBy()).isEqualTo("person-create");
        assertThat(relabeled.getUpdatedBy()).isEqualTo("person-relabel");

        authenticate("person-archive", false);
        FieldDefinitionResponse archived = service.archive(FixtureTenants.A, created.getId(), FieldArchiveRequest.builder()
                .version(relabeled.getVersion())
                .build());
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getCreatedBy()).isEqualTo("person-create");
        assertThat(archived.getUpdatedBy()).isEqualTo("person-archive");
        assertThat(store.definitions).hasSize(1);
    }

    @Test
    void aUserFieldStoresTheReferencedUserAndRecordsTheCaller() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, FieldDefinitionCreateRequest.builder()
                .entity(FieldEntityKind.ORGANIZATION)
                .key("account_owner")
                .label("Account owner")
                .type(FieldType.USER)
                .required(false)
                .showOnRecord(true)
                .showOnList(false)
                .position(40)
                .agentFilled(false)
                .build());
        FieldValueResponse saved = service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .referencedUser("person-owner")
                .build());
        assertThat(saved.getUserId()).isEqualTo("person-owner");
        assertThat(saved.getCreatedBy()).isEqualTo("person-create");
    }

    @Test
    void standardFieldsCanBeHiddenButNotRelabeledRetypedOrArchived() {
        FieldDefinitionResponse name = service.list(FixtureTenants.A, FieldEntityKind.ORGANIZATION).stream()
                .filter(field -> field.getKey().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(name.isStandard()).isTrue();

        FieldDefinitionResponse hidden = service.update(FixtureTenants.A, name.getId(), FieldDefinitionUpdateRequest.builder()
                .showOnList(false)
                .position(15)
                .version(name.getVersion())
                .build());
        assertThat(hidden.isShowOnList()).isFalse();
        assertThat(hidden.getPosition()).isEqualTo(15);
        assertThat(hidden.getLabel()).isEqualTo("Name");

        assertThatThrownBy(() -> service.update(FixtureTenants.A, name.getId(), FieldDefinitionUpdateRequest.builder()
                .label("Legal name")
                .version(hidden.getVersion())
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_STANDARD_LOCKED"));
        assertThatThrownBy(() -> service.archive(FixtureTenants.A, name.getId(), FieldArchiveRequest.builder()
                .version(hidden.getVersion())
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_STANDARD_LOCKED"));
    }

    @Test
    void retypeStaysInsideTheStorageClassAndOptionRenameKeepsTheValue() {
        FieldDefinitionResponse select = service.create(FixtureTenants.A, FieldDefinitionCreateRequest.builder()
                .entity(FieldEntityKind.ORGANIZATION)
                .key("segment")
                .label("Segment")
                .type(FieldType.SELECT)
                .required(false)
                .showOnRecord(true)
                .showOnList(true)
                .position(30)
                .agentFilled(false)
                .options(List.of(FieldOptionWriteRequest.builder().label("Red").position(1).build()))
                .build());
        long optionId = select.getOptions().get(0).getId();
        authenticate("person-value", false);
        FieldValueResponse value = service.upsertValue(FixtureTenants.A, select.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .optionId(optionId)
                .build());
        assertThat(value.getOptionId()).isEqualTo(optionId);

        FieldDefinitionResponse renamed = service.renameOption(FixtureTenants.A, select.getId(), optionId,
                FieldOptionWriteRequest.builder().label("Crimson").position(1).version(0L).build());
        assertThat(renamed.getOptions().get(0).getId()).isEqualTo(optionId);
        assertThat(renamed.getOptions().get(0).getLabel()).isEqualTo("Crimson");
        assertThat(store.values.get(0).optionId()).isEqualTo(optionId);

        FieldDefinitionResponse text = service.create(FixtureTenants.A, createRequest());
        assertThatThrownBy(() -> service.update(FixtureTenants.A, text.getId(), FieldDefinitionUpdateRequest.builder()
                .type(FieldType.NUMBER)
                .version(text.getVersion())
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_TYPE_INCOMPATIBLE"));
    }

    @Test
    void anOverflowingNumberIsRejectedBeforeTheValueIsWritten() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, FieldDefinitionCreateRequest.builder()
                .entity(FieldEntityKind.ORGANIZATION)
                .key("headcount")
                .label("Headcount")
                .type(FieldType.NUMBER)
                .required(false)
                .showOnRecord(true)
                .showOnList(false)
                .position(40)
                .agentFilled(false)
                .build());

        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .numeric(new BigDecimal("1E+20"))
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VALUE_INVALID"));
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .numeric(new BigDecimal("-1E+20"))
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VALUE_INVALID"));
        assertThat(store.values).isEmpty();

        FieldValueResponse saved = service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .numeric(new BigDecimal("1E+19"))
                .build());
        assertThat(saved.getNumeric()).isEqualByComparingTo("1E+19");
        assertThat(store.values).hasSize(1);
    }

    @Test
    void aStaleVersionDoesNotReplaceTheLatestActor() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        authenticate("person-relabel", false);
        service.update(FixtureTenants.A, created.getId(), FieldDefinitionUpdateRequest.builder()
                .label("Territory code")
                .version(created.getVersion())
                .build());

        authenticate("person-stale", false);
        assertThatThrownBy(() -> service.update(FixtureTenants.A, created.getId(), FieldDefinitionUpdateRequest.builder()
                .label("Lost update")
                .version(created.getVersion())
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VERSION_CONFLICT"));

        FieldDefinitionResponse current = service.get(FixtureTenants.A, created.getId());
        assertThat(current.getLabel()).isEqualTo("Territory code");
        assertThat(current.getCreatedBy()).isEqualTo("person-create");
        assertThat(current.getUpdatedBy()).isEqualTo("person-relabel");
    }

    @Test
    void aServiceCallerCannotWriteAValueAndTenantsDoNotSeeEachOther() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        authenticate("enrichment-agent", true);
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("west")
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("AGENT_FIELD_VALUE_FORBIDDEN");
                });
        assertThat(store.values).isEmpty();

        assertThatThrownBy(() -> service.get(FixtureTenants.B, created.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.list("production-tenant", FieldEntityKind.ORGANIZATION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addOptionAppendsASelectLabelAndRejectsATextField() {
        FieldDefinitionResponse select = selectField("segment");
        FieldDefinitionResponse added = service.addOption(FixtureTenants.A, select.getId(), FieldOptionWriteRequest.builder()
                .label("Blue")
                .position(2)
                .build());
        assertThat(added.getOptions()).extracting(FieldOptionResponse::getLabel).containsExactly("Red", "Blue");
        assertThat(added.getOptions()).extracting(FieldOptionResponse::getCreatedBy).containsOnly("person-create");

        FieldDefinitionResponse text = service.create(FixtureTenants.A, createRequest());
        assertThatThrownBy(() -> service.addOption(FixtureTenants.A, text.getId(), FieldOptionWriteRequest.builder()
                .label("Nope")
                .position(1)
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_INVALID"));
        assertThat(store.options).extracting(FieldRows.Option::label).containsExactly("Red", "Blue");
    }

    @Test
    void archiveOptionSucceedsAndRejectsAMissingOrStaleOption() {
        FieldDefinitionResponse select = selectField("segment");
        long optionId = select.getOptions().get(0).getId();

        assertThatThrownBy(() -> service.archiveOption(FixtureTenants.A, select.getId(), 9_999L,
                FieldArchiveRequest.builder().version(0L).build()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> service.archiveOption(FixtureTenants.A, select.getId(), optionId,
                FieldArchiveRequest.builder().version(4L).build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VERSION_CONFLICT"));
        assertThat(service.get(FixtureTenants.A, select.getId()).getOptions().get(0).getArchivedAt()).isNull();

        FieldDefinitionResponse archived = service.archiveOption(FixtureTenants.A, select.getId(), optionId,
                FieldArchiveRequest.builder().version(0L).build());
        assertThat(archived.getOptions().get(0).getArchivedAt()).isNotNull();
        assertThat(archived.getOptions().get(0).getVersion()).isEqualTo(1L);
        assertThat(archived.getOptions().get(0).getUpdatedBy()).isEqualTo("person-create");
        assertThat(archived.getOptions().get(0).getLabel()).isEqualTo("Red");
    }

    @Test
    void getValueReturnsAStoredHitAndMissesWhenNothingIsStored() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        assertThatThrownBy(() -> service.getValue(FixtureTenants.A, created.getId(), 9L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);

        service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("west")
                .build());
        FieldValueResponse loaded = service.getValue(FixtureTenants.A, created.getId(), 9L, null, null);
        assertThat(loaded.getText()).isEqualTo("west");
        assertThat(loaded.getOrganizationId()).isEqualTo(9L);
        assertThat(loaded.getCreatedBy()).isEqualTo("person-create");
    }

    @Test
    void clearValueRemovesAnOptionalValue() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("west")
                .build());

        service.clearValue(FixtureTenants.A, created.getId(), 9L, null, null);

        assertThat(store.values).isEmpty();
        assertThatThrownBy(() -> service.getValue(FixtureTenants.A, created.getId(), 9L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearValueRejectsARequiredDefinition() {
        FieldDefinitionResponse created = textOn(FieldEntityKind.ORGANIZATION, "priority", true);
        service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("high")
                .build());

        assertThatThrownBy(() -> service.clearValue(FixtureTenants.A, created.getId(), 9L, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_REQUIRED"));
        assertThat(service.getValue(FixtureTenants.A, created.getId(), 9L, null, null).getText()).isEqualTo("high");
    }

    @Test
    void clearValueMissesWhenNothingIsStored() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());

        assertThatThrownBy(() -> service.clearValue(FixtureTenants.A, created.getId(), 9L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aServiceOrAgentCallerCannotClearAValue() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("west")
                .build());
        authenticate("enrichment-agent", true);

        assertThatThrownBy(() -> service.clearValue(FixtureTenants.A, created.getId(), 9L, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("AGENT_FIELD_VALUE_FORBIDDEN");
                });
        assertThat(service.getValue(FixtureTenants.A, created.getId(), 9L, null, null).getText()).isEqualTo("west");
    }

    @Test
    void upsertValueRejectsStandardFieldsArchivedOptionsAndArchivedDefinitions() {
        FieldDefinitionResponse name = service.list(FixtureTenants.A, FieldEntityKind.ORGANIZATION).stream()
                .filter(field -> field.getKey().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, name.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("Acme")
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_STANDARD_LOCKED"));

        FieldDefinitionResponse select = selectField("segment");
        long optionId = select.getOptions().get(0).getId();
        service.archiveOption(FixtureTenants.A, select.getId(), optionId,
                FieldArchiveRequest.builder().version(0L).build());
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, select.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .optionId(optionId)
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_INVALID"));

        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        service.archive(FixtureTenants.A, created.getId(), FieldArchiveRequest.builder()
                .version(created.getVersion())
                .build());
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .text("west")
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_ARCHIVED"));
        assertThat(store.values).isEmpty();
    }

    @Test
    void aDuplicateFieldKeyOnCreateMapsToFieldKeyExists() {
        store.failInsertDefinition(constraintViolation("uq_field_definition_tenant_entity_key"));

        assertThatThrownBy(() -> service.create(FixtureTenants.A, createRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_KEY_EXISTS"));
        assertThat(store.definitions).isEmpty();
    }

    @Test
    void anUnknownConstraintOnUpdateIsRethrown() {
        FieldDefinitionResponse created = service.create(FixtureTenants.A, createRequest());
        DataIntegrityViolationException failure = constraintViolation("chk_field_unknown");
        store.failUpdateDefinition(failure);

        assertThatThrownBy(() -> service.update(FixtureTenants.A, created.getId(), FieldDefinitionUpdateRequest.builder()
                .label("Territory code")
                .version(created.getVersion())
                .build()))
                .isSameAs(failure);
        assertThat(service.get(FixtureTenants.A, created.getId()).getLabel()).isEqualTo("Region");
    }

    @Test
    void aDuplicateOptionLabelOnRenameMapsToFieldOptionExists() {
        FieldDefinitionResponse select = selectField("segment");
        long optionId = select.getOptions().get(0).getId();
        store.failUpdateOption(constraintViolation("uq_field_option_active_label"));

        assertThatThrownBy(() -> service.renameOption(FixtureTenants.A, select.getId(), optionId,
                FieldOptionWriteRequest.builder().label("Crimson").position(1).version(0L).build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_EXISTS"));
        assertThat(service.get(FixtureTenants.A, select.getId()).getOptions().get(0).getLabel()).isEqualTo("Red");
    }

    @Test
    void valueForeignKeysMapToNotFoundOrAnInvalidOption() {
        assertUpsertConstraint(FieldEntityKind.ORGANIZATION, "org_note", 11L, null, null, "fk_field_value_organization");
        assertUpsertConstraint(FieldEntityKind.CONTACT, "contact_note", null, 12L, null, "fk_field_value_contact");
        assertUpsertConstraint(FieldEntityKind.DEAL, "deal_note", null, null, 13L, "fk_field_value_deal");

        FieldDefinitionResponse select = selectField("segment");
        long optionId = select.getOptions().get(0).getId();
        store.failUpsert(constraintViolation("fk_field_value_option"));
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, select.getId(), FieldValueWriteRequest.builder()
                .organizationId(9L)
                .optionId(optionId)
                .build()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_INVALID"));
        assertThat(store.values).isEmpty();
    }

    private void assertUpsertConstraint(FieldEntityKind entity, String key, Long organizationId, Long contactId,
                                        Long dealId, String constraint) {
        FieldDefinitionResponse created = textOn(entity, key, false);
        store.failUpsert(constraintViolation(constraint));
        assertThatThrownBy(() -> service.upsertValue(FixtureTenants.A, created.getId(), FieldValueWriteRequest.builder()
                .organizationId(organizationId)
                .contactId(contactId)
                .dealId(dealId)
                .text("west")
                .build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Field record");
    }

    private static DataIntegrityViolationException constraintViolation(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("violates constraint \"" + constraint + "\""));
    }

    private FieldDefinitionResponse selectField(String key) {
        return service.create(FixtureTenants.A, FieldDefinitionCreateRequest.builder()
                .entity(FieldEntityKind.ORGANIZATION)
                .key(key)
                .label("Segment")
                .type(FieldType.SELECT)
                .required(false)
                .showOnRecord(true)
                .showOnList(true)
                .position(30)
                .agentFilled(false)
                .options(List.of(FieldOptionWriteRequest.builder().label("Red").position(1).build()))
                .build());
    }

    private FieldDefinitionResponse textOn(FieldEntityKind entity, String key, boolean required) {
        return service.create(FixtureTenants.A, FieldDefinitionCreateRequest.builder()
                .entity(entity)
                .key(key)
                .label("Note")
                .type(FieldType.TEXT)
                .required(required)
                .showOnRecord(true)
                .showOnList(false)
                .position(40)
                .agentFilled(false)
                .build());
    }

    private static FieldDefinitionCreateRequest createRequest() {
        return FieldDefinitionCreateRequest.builder()
                .entity(FieldEntityKind.ORGANIZATION)
                .key("region_code")
                .label("Region")
                .type(FieldType.TEXT)
                .required(false)
                .showOnRecord(true)
                .showOnList(true)
                .position(500)
                .agentFilled(true)
                .agentBrief("Find the region")
                .build();
    }

    private static void authenticate(String id, boolean service) {
        Jwt.Builder builder = Jwt.withTokenValue("test").header("alg", "none").subject(id);
        if (service) {
            builder.claim("idtyp", "app").claim("azp", id);
        } else {
            builder.claim("oid", id).claim("scp", "user");
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
    }

    private static final class MemoryStore implements FieldRegistryStore {
        private final AtomicLong ids = new AtomicLong(1);
        private final List<FieldRows.Definition> definitions = new ArrayList<>();
        private final List<FieldRows.Option> options = new ArrayList<>();
        private final List<FieldRows.Value> values = new ArrayList<>();
        private DataIntegrityViolationException insertDefinitionFailure;
        private DataIntegrityViolationException updateDefinitionFailure;
        private DataIntegrityViolationException updateOptionFailure;
        private DataIntegrityViolationException upsertFailure;

        void failInsertDefinition(DataIntegrityViolationException failure) {
            this.insertDefinitionFailure = failure;
        }

        void failUpdateDefinition(DataIntegrityViolationException failure) {
            this.updateDefinitionFailure = failure;
        }

        void failUpdateOption(DataIntegrityViolationException failure) {
            this.updateOptionFailure = failure;
        }

        void failUpsert(DataIntegrityViolationException failure) {
            this.upsertFailure = failure;
        }

        @Override
        public FieldRows.Definition insertDefinition(FieldRows.Definition row) {
            throwIfPending(insertDefinitionFailure);
            insertDefinitionFailure = null;
            FieldRows.Definition saved = copy(ids.getAndIncrement(), row, 0, null);
            definitions.add(saved);
            return saved;
        }

        @Override
        public int insertDefinitionIfAbsent(FieldRows.Definition row) {
            boolean exists = definitions.stream().anyMatch(existing ->
                    existing.tenant().equals(row.tenant())
                            && existing.entity() == row.entity()
                            && existing.key().equals(row.key()));
            if (exists) {
                return 0;
            }
            insertDefinition(row);
            return 1;
        }

        @Override
        public Optional<FieldRows.Definition> findDefinition(String tenant, long id) {
            return definitions.stream()
                    .filter(row -> row.id() == id && row.tenant().equals(tenant))
                    .findFirst();
        }

        @Override
        public List<FieldRows.Definition> listDefinitions(String tenant, FieldEntityKind entity) {
            return definitions.stream()
                    .filter(row -> row.tenant().equals(tenant) && row.entity() == entity)
                    .toList();
        }

        @Override
        public boolean updateDefinition(FieldRows.Definition row, String actor, long expectedVersion) {
            throwIfPending(updateDefinitionFailure);
            updateDefinitionFailure = null;
            for (int i = 0; i < definitions.size(); i++) {
                FieldRows.Definition current = definitions.get(i);
                if (current.id().equals(row.id()) && current.tenant().equals(row.tenant())
                        && current.version() == expectedVersion && current.archivedAt() == null) {
                    definitions.set(i, new FieldRows.Definition(
                            current.id(), current.tenant(), current.entity(), current.key(), row.label(), row.type(),
                            row.required(), row.showOnRecord(), row.showOnList(), row.position(), row.agentFilled(),
                            row.agentBrief(), null, current.createdBy(), actor, current.version() + 1));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean archiveDefinition(String tenant, long id, String actor, long expectedVersion) {
            for (int i = 0; i < definitions.size(); i++) {
                FieldRows.Definition current = definitions.get(i);
                if (current.id() == id && current.tenant().equals(tenant)
                        && current.version() == expectedVersion && current.archivedAt() == null) {
                    definitions.set(i, new FieldRows.Definition(
                            current.id(), current.tenant(), current.entity(), current.key(), current.label(),
                            current.type(), current.required(), current.showOnRecord(), current.showOnList(),
                            current.position(), current.agentFilled(), current.agentBrief(), Instant.parse("2026-11-06T00:00:00Z"),
                            current.createdBy(), actor, current.version() + 1));
                    return true;
                }
            }
            return false;
        }

        @Override
        public FieldRows.Option insertOption(FieldRows.Option row) {
            FieldRows.Option saved = new FieldRows.Option(
                    ids.getAndIncrement(), row.definitionId(), row.label(), row.position(), null,
                    row.createdBy(), row.updatedBy(), 0);
            options.add(saved);
            return saved;
        }

        @Override
        public List<FieldRows.Option> listOptions(Collection<Long> definitionIds) {
            return options.stream().filter(option -> definitionIds.contains(option.definitionId())).toList();
        }

        @Override
        public Optional<FieldRows.Option> findOption(long definitionId, long optionId) {
            return options.stream()
                    .filter(option -> option.definitionId() == definitionId && option.id() == optionId)
                    .findFirst();
        }

        @Override
        public boolean updateOption(FieldRows.Option row, String actor, long expectedVersion) {
            throwIfPending(updateOptionFailure);
            updateOptionFailure = null;
            for (int i = 0; i < options.size(); i++) {
                FieldRows.Option current = options.get(i);
                if (current.id().equals(row.id()) && current.definitionId() == row.definitionId()
                        && current.version() == expectedVersion) {
                    options.set(i, new FieldRows.Option(
                            current.id(), current.definitionId(), row.label(), current.position(), current.archivedAt(),
                            current.createdBy(), actor, current.version() + 1));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean archiveOption(long definitionId, long optionId, String actor, long expectedVersion) {
            for (int i = 0; i < options.size(); i++) {
                FieldRows.Option current = options.get(i);
                if (current.id() != null && current.id() == optionId && current.definitionId() == definitionId
                        && current.version() == expectedVersion && current.archivedAt() == null) {
                    options.set(i, new FieldRows.Option(
                            current.id(), current.definitionId(), current.label(), current.position(),
                            Instant.parse("2026-11-06T00:00:00Z"),
                            current.createdBy(), actor, current.version() + 1));
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<String> textValues(long definitionId) {
            return values.stream()
                    .filter(value -> value.definitionId() == definitionId && value.text() != null)
                    .map(FieldRows.Value::text)
                    .toList();
        }

        @Override
        public void upsertValue(FieldRows.Value row) {
            throwIfPending(upsertFailure);
            upsertFailure = null;
            values.add(row);
        }

        private static void throwIfPending(DataIntegrityViolationException failure) {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public Optional<FieldRows.Value> findValue(long definitionId, FieldEntityKind entity, long recordId) {
            return values.stream().filter(value -> value.definitionId() == definitionId).findFirst();
        }

        @Override
        public boolean deleteValue(long definitionId, FieldEntityKind entity, long recordId) {
            return values.removeIf(value -> value.definitionId() == definitionId);
        }

        private static FieldRows.Definition copy(long id, FieldRows.Definition row, long version, Instant archivedAt) {
            return new FieldRows.Definition(
                    id, row.tenant(), row.entity(), row.key(), row.label(), row.type(), row.required(),
                    row.showOnRecord(), row.showOnList(), row.position(), row.agentFilled(), row.agentBrief(),
                    archivedAt, row.createdBy(), row.updatedBy(), version);
        }
    }
}
