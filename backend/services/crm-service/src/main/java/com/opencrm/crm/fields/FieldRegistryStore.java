package com.opencrm.crm.fields;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the field registry. Implementations write {@code created_by} and {@code updated_by}
 * from the actor string the service took from {@link com.opencrm.security.CurrentActor}.
 */
public interface FieldRegistryStore {

    FieldRows.Definition insertDefinition(FieldRows.Definition row);

    int insertDefinitionIfAbsent(FieldRows.Definition row);

    Optional<FieldRows.Definition> findDefinition(String tenant, long id);

    List<FieldRows.Definition> listDefinitions(String tenant, FieldEntityKind entity);

    boolean updateDefinition(FieldRows.Definition row, String actor, long expectedVersion);

    boolean archiveDefinition(String tenant, long id, String actor, long expectedVersion);

    FieldRows.Option insertOption(FieldRows.Option row);

    List<FieldRows.Option> listOptions(Collection<Long> definitionIds);

    Optional<FieldRows.Option> findOption(long definitionId, long optionId);

    boolean updateOption(FieldRows.Option row, String actor, long expectedVersion);

    boolean archiveOption(long definitionId, long optionId, String actor, long expectedVersion);

    List<String> textValues(long definitionId);

    void upsertValue(FieldRows.Value row);

    Optional<FieldRows.Value> findValue(long definitionId, FieldEntityKind entity, long recordId);

    boolean deleteValue(long definitionId, FieldEntityKind entity, long recordId);
}
