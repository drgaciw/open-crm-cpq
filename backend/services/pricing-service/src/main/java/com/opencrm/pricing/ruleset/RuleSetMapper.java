package com.opencrm.pricing.ruleset;

import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for RuleSet entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RuleSetMapper {

    /**
     * Convert create request to entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "ruleSetVersion", constant = "1")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    RuleSet toEntity(RuleSetCreateRequest request);

    /**
     * Convert entity to DTO.
     */
    RuleSetDto toDto(RuleSet ruleSet);

    /**
     * Update entity from update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ruleSetVersion", ignore = true)
    @Mapping(target = "engineType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(RuleSetUpdateRequest request, @MappingTarget RuleSet ruleSet);

    /**
     * Convert list of entities to list of DTOs.
     */
    List<RuleSetDto> toDtoList(List<RuleSet> ruleSets);
}
