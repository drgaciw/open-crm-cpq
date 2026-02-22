package com.opencrm.pricing.rule;

import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Rule entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RuleMapper {

    /**
     * Convert create request to entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Rule toEntity(RuleCreateRequest request);

    /**
     * Convert entity to DTO.
     */
    RuleDto toDto(Rule rule);

    /**
     * Update entity from update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "ruleSetId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(RuleUpdateRequest request, @MappingTarget Rule rule);

    /**
     * Convert list of entities to list of DTOs.
     */
    List<RuleDto> toDtoList(List<Rule> rules);
}
