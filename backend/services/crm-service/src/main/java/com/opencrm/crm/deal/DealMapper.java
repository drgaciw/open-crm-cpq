package com.opencrm.crm.deal;

import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Deal entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DealMapper {

    /**
     * Convert create request to entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "primaryContact", ignore = true)
    @Mapping(target = "stage", constant = "PROSPECTING")
    @Mapping(target = "probability", constant = "10")
    @Mapping(target = "actualCloseDate", ignore = true)
    @Mapping(target = "sourceLeadId", ignore = true)
    @Mapping(target = "lostReason", ignore = true)
    @Mapping(target = "wonAt", ignore = true)
    @Mapping(target = "lostAt", ignore = true)
    @Mapping(target = "nextStep", ignore = true)
    @Mapping(target = "nextStepDate", ignore = true)
    @Mapping(target = "competitors", ignore = true)
    @Mapping(target = "weightedAmount", ignore = true)
    @Mapping(target = "baseAmount", ignore = true)
    @Mapping(target = "baseCurrency", ignore = true)
    @Mapping(target = "baseWeightedAmount", ignore = true)
    @Mapping(target = "fxRate", ignore = true)
    @Mapping(target = "fxRateAsOf", ignore = true)
    @Mapping(target = "fxSource", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Deal toEntity(DealCreateRequest request);

    /**
     * Convert entity to DTO.
     */
    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    @Mapping(target = "primaryContactId", source = "primaryContact.id")
    @Mapping(target = "primaryContactName", expression = "java(deal.getPrimaryContact() != null ? deal.getPrimaryContact().getFullName() : null)")
    DealDto toDto(Deal deal);

    /**
     * Update entity from update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "primaryContact", ignore = true)
    @Mapping(target = "probability", ignore = true)
    @Mapping(target = "actualCloseDate", ignore = true)
    @Mapping(target = "sourceLeadId", ignore = true)
    @Mapping(target = "lostReason", ignore = true)
    @Mapping(target = "wonAt", ignore = true)
    @Mapping(target = "lostAt", ignore = true)
    @Mapping(target = "nextStep", ignore = true)
    @Mapping(target = "nextStepDate", ignore = true)
    @Mapping(target = "competitors", ignore = true)
    @Mapping(target = "weightedAmount", ignore = true)
    @Mapping(target = "baseAmount", ignore = true)
    @Mapping(target = "baseCurrency", ignore = true)
    @Mapping(target = "baseWeightedAmount", ignore = true)
    @Mapping(target = "fxRate", ignore = true)
    @Mapping(target = "fxRateAsOf", ignore = true)
    @Mapping(target = "fxSource", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(DealUpdateRequest request, @MappingTarget Deal deal);

    /**
     * Convert list of entities to list of DTOs.
     */
    List<DealDto> toDtoList(List<Deal> deals);
}
