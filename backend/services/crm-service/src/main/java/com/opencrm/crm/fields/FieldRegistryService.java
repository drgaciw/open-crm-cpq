package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Admin registry for custom and standard fields, plus typed values.
 * Actors come from {@link CurrentActor}. Request bodies cannot supply them.
 * Archive keeps the row. There is no hard delete.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FieldRegistryService {

    private final FieldRegistryStore store;
    private final CurrentActor currentActor;

    public List<FieldDefinitionResponse> list(String tenant, FieldEntityKind entity) {
        FixtureTenants.require(tenant);
        if (entity == null) {
            throw new BusinessException("Entity is required", HttpStatus.BAD_REQUEST, "FIELD_ENTITY_REQUIRED");
        }
        seedStandard(tenant, entity);
        return toResponses(store.listDefinitions(tenant, entity));
    }

    @Transactional(readOnly = true)
    public FieldDefinitionResponse get(String tenant, long id) {
        return toResponse(requireDefinition(tenant, id), store.listOptions(List.of(id)));
    }

    public FieldDefinitionResponse create(String tenant, FieldDefinitionCreateRequest request) {
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        String key = FieldRules.requireKey(request.getKey());
        FieldRules.rejectStandardKey(request.getEntity(), key);
        String label = FieldRules.requireLabel(request.getLabel());
        FieldRules.requirePosition(request.getPosition());
        String brief = FieldRules.normalizeBrief(request.getAgentBrief());
        List<FieldOptionWriteRequest> options = request.getOptions() == null ? List.of() : request.getOptions();
        for (FieldOptionWriteRequest option : options) {
            FieldRules.rejectClientActor(option.getCreatedBy(), option.getUpdatedBy(), option.getActor(), option.getArchivedBy());
        }
        FieldRules.requireSelectOptions(request.getType(), options.stream().map(FieldOptionWriteRequest::getLabel).toList());
        String actor = AgentFieldValueGuard.actorId(currentActor);
        FieldRows.Definition saved;
        try {
            saved = store.insertDefinition(new FieldRows.Definition(
                    null,
                    tenant,
                    request.getEntity(),
                    key,
                    label,
                    request.getType(),
                    request.getRequired(),
                    request.getShowOnRecord(),
                    request.getShowOnList(),
                    request.getPosition(),
                    request.getAgentFilled(),
                    brief,
                    null,
                    actor,
                    actor,
                    0));
            for (FieldOptionWriteRequest option : options) {
                insertOption(saved.id(), option, actor);
            }
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        log.info("Created field definition {}", saved.id());
        return get(tenant, saved.id());
    }

    public FieldDefinitionResponse update(String tenant, long id, FieldDefinitionUpdateRequest request) {
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        FieldRows.Definition current = requireActive(tenant, id);
        FieldRules.rejectKeyChange(current.key(), request.getKey());
        String label = request.getLabel() == null ? current.label() : FieldRules.requireLabel(request.getLabel());
        FieldType type = request.getType() == null ? current.type() : request.getType();
        boolean required = request.getRequired() == null ? current.required() : request.getRequired();
        boolean showOnRecord = request.getShowOnRecord() == null ? current.showOnRecord() : request.getShowOnRecord();
        boolean showOnList = request.getShowOnList() == null ? current.showOnList() : request.getShowOnList();
        int position = request.getPosition() == null ? current.position() : request.getPosition();
        FieldRules.requirePosition(position);
        boolean agentFilled = request.getAgentFilled() == null ? current.agentFilled() : request.getAgentFilled();
        String brief = request.getAgentBrief() == null ? current.agentBrief() : FieldRules.normalizeBrief(request.getAgentBrief());
        boolean standard = StandardFieldCatalog.isStandard(current.entity(), current.key());
        FieldRules.rejectStandardMutation(
                standard,
                !label.equals(current.label()),
                type != current.type(),
                required != current.required(),
                agentFilled != current.agentFilled() || !Objects.equals(brief, current.agentBrief()),
                false);
        FieldRules.rejectIncompatibleRetype(current.type(), type);
        if (type != current.type()) {
            for (String text : store.textValues(id)) {
                FieldRules.requireValue(type, FieldRules.StoredValue.text(text));
            }
        }
        String actor = AgentFieldValueGuard.actorId(currentActor);
        FieldRows.Definition next = new FieldRows.Definition(
                current.id(),
                current.tenant(),
                current.entity(),
                current.key(),
                label,
                type,
                required,
                showOnRecord,
                showOnList,
                position,
                agentFilled,
                brief,
                null,
                current.createdBy(),
                actor,
                current.version());
        try {
            requireWrite(store.updateDefinition(next, actor, request.getVersion()), tenant, id);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        log.info("Updated field definition {}", id);
        return get(tenant, id);
    }

    public FieldDefinitionResponse archive(String tenant, long id, FieldArchiveRequest request) {
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        FieldRows.Definition current = requireActive(tenant, id);
        if (StandardFieldCatalog.isStandard(current.entity(), current.key())) {
            FieldRules.rejectStandardMutation(true, false, false, false, false, true);
        }
        String actor = AgentFieldValueGuard.actorId(currentActor);
        requireWrite(store.archiveDefinition(tenant, id, actor, request.getVersion()), tenant, id);
        log.info("Archived field definition {}", id);
        return get(tenant, id);
    }

    public FieldDefinitionResponse addOption(String tenant, long id, FieldOptionWriteRequest request) {
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        FieldRows.Definition definition = requireActive(tenant, id);
        if (definition.type() != FieldType.SELECT) {
            throw new BusinessException("Options belong on select fields only", HttpStatus.BAD_REQUEST, "FIELD_OPTION_INVALID");
        }
        String actor = AgentFieldValueGuard.actorId(currentActor);
        try {
            insertOption(id, request, actor);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        return get(tenant, id);
    }

    public FieldDefinitionResponse renameOption(String tenant, long id, long optionId, FieldOptionWriteRequest request) {
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        if (request.getVersion() == null) {
            throw new BusinessException("Version is required", HttpStatus.BAD_REQUEST, "FIELD_VERSION_REQUIRED");
        }
        requireActive(tenant, id);
        FieldRows.Option option = store.findOption(id, optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Field option", optionId));
        String label = FieldRules.requireLabel(request.getLabel());
        String actor = AgentFieldValueGuard.actorId(currentActor);
        FieldRows.Option next = new FieldRows.Option(
                option.id(), option.definitionId(), label, option.position(), option.archivedAt(),
                option.createdBy(), actor, option.version());
        try {
            if (!store.updateOption(next, actor, request.getVersion())) {
                throw new BusinessException("Option version is stale", HttpStatus.CONFLICT, "FIELD_VERSION_CONFLICT");
            }
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        return get(tenant, id);
    }

    public FieldDefinitionResponse archiveOption(String tenant, long id, long optionId, FieldArchiveRequest request) {
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        requireActive(tenant, id);
        store.findOption(id, optionId).orElseThrow(() -> new ResourceNotFoundException("Field option", optionId));
        String actor = AgentFieldValueGuard.actorId(currentActor);
        if (!store.archiveOption(id, optionId, actor, request.getVersion())) {
            throw new BusinessException("Option version is stale or the option is archived", HttpStatus.CONFLICT, "FIELD_VERSION_CONFLICT");
        }
        return get(tenant, id);
    }

    public FieldValueResponse upsertValue(String tenant, long id, FieldValueWriteRequest request) {
        AgentFieldValueGuard.requireHumanWriter(currentActor);
        FixtureTenants.require(tenant);
        FieldRules.rejectClientActor(request.getCreatedBy(), request.getUpdatedBy(), request.getActor(), request.getArchivedBy());
        FieldRows.Definition definition = requireActive(tenant, id);
        if (StandardFieldCatalog.isStandard(definition.entity(), definition.key())) {
            throw new BusinessException("Standard fields are not stored as custom values", HttpStatus.BAD_REQUEST, "FIELD_STANDARD_LOCKED");
        }
        long recordId = FieldRules.requireRecord(
                definition.entity(), request.getOrganizationId(), request.getContactId(), request.getDealId());
        FieldRules.StoredValue stored = FieldRules.requireValue(definition.type(), incoming(request));
        if (definition.type() == FieldType.SELECT) {
            FieldRows.Option option = store.findOption(id, stored.optionId())
                    .orElseThrow(() -> new BusinessException("Select option is not active on this field", HttpStatus.BAD_REQUEST, "FIELD_OPTION_INVALID"));
            FieldRules.requireActiveOption(true, option.archivedAt() != null);
        }
        String actor = AgentFieldValueGuard.actorId(currentActor);
        FieldRows.Value row = new FieldRows.Value(
                null,
                id,
                request.getOrganizationId(),
                request.getContactId(),
                request.getDealId(),
                stored.text(),
                stored.numeric(),
                stored.date(),
                stored.bool(),
                stored.optionId(),
                stored.userId(),
                actor,
                actor,
                0);
        try {
            store.upsertValue(row);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        return store.findValue(id, definition.entity(), recordId)
                .map(FieldRegistryService::toValue)
                .orElseThrow(() -> new ResourceNotFoundException("Field value", id));
    }

    @Transactional(readOnly = true)
    public FieldValueResponse getValue(String tenant, long id, Long organizationId, Long contactId, Long dealId) {
        FixtureTenants.require(tenant);
        FieldRows.Definition definition = requireDefinition(tenant, id);
        long recordId = FieldRules.requireRecord(definition.entity(), organizationId, contactId, dealId);
        return store.findValue(id, definition.entity(), recordId)
                .map(FieldRegistryService::toValue)
                .orElseThrow(() -> new ResourceNotFoundException("Field value", id));
    }

    public void clearValue(String tenant, long id, Long organizationId, Long contactId, Long dealId) {
        AgentFieldValueGuard.requireHumanWriter(currentActor);
        FixtureTenants.require(tenant);
        FieldRows.Definition definition = requireActive(tenant, id);
        long recordId = FieldRules.requireRecord(definition.entity(), organizationId, contactId, dealId);
        if (definition.required()) {
            throw new BusinessException("Required field value cannot be cleared", HttpStatus.BAD_REQUEST, "FIELD_REQUIRED");
        }
        if (!store.deleteValue(id, definition.entity(), recordId)) {
            throw new ResourceNotFoundException("Field value", id);
        }
    }

    private void seedStandard(String tenant, FieldEntityKind entity) {
        String actor = AgentFieldValueGuard.actorId(currentActor);
        for (StandardFieldCatalog.StandardField field : StandardFieldCatalog.forEntity(entity)) {
            store.insertDefinitionIfAbsent(new FieldRows.Definition(
                    null,
                    tenant,
                    entity,
                    field.key(),
                    field.label(),
                    field.type(),
                    field.required(),
                    true,
                    field.showOnList(),
                    field.position(),
                    false,
                    null,
                    null,
                    actor,
                    actor,
                    0));
        }
    }

    private void insertOption(long definitionId, FieldOptionWriteRequest request, String actor) {
        String label = FieldRules.requireLabel(request.getLabel());
        FieldRules.requirePosition(request.getPosition());
        store.insertOption(new FieldRows.Option(
                null, definitionId, label, request.getPosition(), null, actor, actor, 0));
    }

    private FieldRows.Definition requireDefinition(String tenant, long id) {
        FixtureTenants.require(tenant);
        return store.findDefinition(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("Field definition", id));
    }

    private FieldRows.Definition requireActive(String tenant, long id) {
        FieldRows.Definition definition = requireDefinition(tenant, id);
        if (definition.archivedAt() != null) {
            throw new BusinessException("Field is archived", HttpStatus.CONFLICT, "FIELD_ARCHIVED");
        }
        return definition;
    }

    private void requireWrite(boolean updated, String tenant, long id) {
        if (updated) {
            return;
        }
        FieldRows.Definition current = requireDefinition(tenant, id);
        if (current.archivedAt() != null) {
            throw new BusinessException("Field is archived", HttpStatus.CONFLICT, "FIELD_ARCHIVED");
        }
        throw new BusinessException("Field version is stale", HttpStatus.CONFLICT, "FIELD_VERSION_CONFLICT");
    }

    private List<FieldDefinitionResponse> toResponses(List<FieldRows.Definition> definitions) {
        List<Long> ids = definitions.stream().map(FieldRows.Definition::id).toList();
        Map<Long, List<FieldRows.Option>> options = store.listOptions(ids).stream()
                .collect(Collectors.groupingBy(FieldRows.Option::definitionId));
        return definitions.stream()
                .map(definition -> toResponse(definition, options.getOrDefault(definition.id(), List.of())))
                .toList();
    }

    private static FieldDefinitionResponse toResponse(FieldRows.Definition row, List<FieldRows.Option> options) {
        return FieldDefinitionResponse.builder()
                .id(row.id())
                .tenant(row.tenant())
                .entity(row.entity())
                .key(row.key())
                .label(row.label())
                .type(row.type())
                .storageClass(row.type().storageClass())
                .required(row.required())
                .showOnRecord(row.showOnRecord())
                .showOnList(row.showOnList())
                .position(row.position())
                .agentFilled(row.agentFilled())
                .agentBrief(row.agentBrief())
                .archivedAt(row.archivedAt())
                .createdBy(row.createdBy())
                .updatedBy(row.updatedBy())
                .version(row.version())
                .standard(StandardFieldCatalog.isStandard(row.entity(), row.key()))
                .options(options.stream().map(FieldRegistryService::toOption).toList())
                .build();
    }

    private static FieldOptionResponse toOption(FieldRows.Option row) {
        return FieldOptionResponse.builder()
                .id(row.id())
                .label(row.label())
                .position(row.position())
                .archivedAt(row.archivedAt())
                .createdBy(row.createdBy())
                .updatedBy(row.updatedBy())
                .version(row.version())
                .build();
    }

    private static FieldValueResponse toValue(FieldRows.Value row) {
        return FieldValueResponse.builder()
                .id(row.id())
                .definitionId(row.definitionId())
                .organizationId(row.organizationId())
                .contactId(row.contactId())
                .dealId(row.dealId())
                .text(row.text())
                .numeric(row.numeric())
                .date(row.date())
                .bool(row.bool())
                .optionId(row.optionId())
                .userId(row.userId())
                .createdBy(row.createdBy())
                .updatedBy(row.updatedBy())
                .version(row.version())
                .build();
    }

    private static FieldRules.StoredValue incoming(FieldValueWriteRequest request) {
        return new FieldRules.StoredValue(
                request.getText(),
                request.getNumeric(),
                request.getDate(),
                request.getBool(),
                request.getOptionId(),
                request.getUserId());
    }

    private static RuntimeException translate(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        String text = message == null ? "" : message;
        if (text.contains("uq_field_definition_tenant_entity_key")) {
            return new BusinessException("Field key already exists", HttpStatus.CONFLICT, "FIELD_KEY_EXISTS");
        }
        if (text.contains("uq_field_option_active_label")) {
            return new BusinessException("Option label already exists", HttpStatus.CONFLICT, "FIELD_OPTION_EXISTS");
        }
        if (text.contains("fk_field_value_organization")
                || text.contains("fk_field_value_contact")
                || text.contains("fk_field_value_deal")) {
            return new ResourceNotFoundException("Field record", "missing");
        }
        if (text.contains("fk_field_value_option")) {
            return new BusinessException("Select option is not active on this field", HttpStatus.BAD_REQUEST, "FIELD_OPTION_INVALID");
        }
        return ex;
    }
}
