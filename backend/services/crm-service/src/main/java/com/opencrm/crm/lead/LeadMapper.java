package com.opencrm.crm.lead;

import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Lead entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface LeadMapper {

    /**
     * Convert create request to entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "contact", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "status", constant = "NEW")
    @Mapping(target = "score", constant = "0")
    @Mapping(target = "assignedAt", ignore = true)
    @Mapping(target = "firstContactedAt", ignore = true)
    @Mapping(target = "lastContactedAt", ignore = true)
    @Mapping(target = "qualifiedAt", ignore = true)
    @Mapping(target = "convertedAt", ignore = true)
    @Mapping(target = "convertedDealId", ignore = true)
    @Mapping(target = "lostReason", ignore = true)
    @Mapping(target = "lostAt", ignore = true)
    @Mapping(target = "nextAction", ignore = true)
    @Mapping(target = "nextActionDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Lead toEntity(LeadCreateRequest request);

    /**
     * Convert entity to DTO.
     */
    @Mapping(target = "contactId", source = "contact.id")
    @Mapping(target = "contactName", expression = "java(lead.getContact() != null ? lead.getContact().getFullName() : null)")
    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    LeadDto toDto(Lead lead);

    /**
     * Update entity from update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "contact", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "assignedAt", ignore = true)
    @Mapping(target = "firstContactedAt", ignore = true)
    @Mapping(target = "lastContactedAt", ignore = true)
    @Mapping(target = "qualifiedAt", ignore = true)
    @Mapping(target = "convertedAt", ignore = true)
    @Mapping(target = "convertedDealId", ignore = true)
    @Mapping(target = "lostReason", ignore = true)
    @Mapping(target = "lostAt", ignore = true)
    @Mapping(target = "nextAction", ignore = true)
    @Mapping(target = "nextActionDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(LeadUpdateRequest request, @MappingTarget Lead lead);

    /**
     * Convert list of entities to list of DTOs.
     */
    List<LeadDto> toDtoList(List<Lead> leads);
}
