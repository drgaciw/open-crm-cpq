package com.opencrm.crm.organization;

import com.opencrm.common.valueobject.AccountId;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Organization entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrganizationMapper {

    /**
     * Convert OrganizationCreateRequest to Organization entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "contactCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Organization toEntity(OrganizationCreateRequest request);

    /**
     * Convert Organization entity to OrganizationDto.
     * {@code id} stays a {@code Long} on the wire and is read from {@link AccountId}.
     */
    @Mapping(target = "id", source = ".", qualifiedByName = "accountIdValue")
    @Mapping(target = "contactCount", expression = "java(entity.getContactCount() != null ? entity.getContactCount() : 0)")
    OrganizationDto toDto(Organization entity);

    /**
     * Shared-kernel identifier for this organization. Null until the row has an id.
     */
    default AccountId accountId(Organization entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return AccountId.of(entity.getId());
    }

    @Named("accountIdValue")
    default Long accountIdValue(Organization entity) {
        AccountId accountId = accountId(entity);
        return accountId == null ? null : accountId.value();
    }

    /**
     * Update Organization entity from OrganizationUpdateRequest.
     * Only updates non-null fields.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "contactCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(OrganizationUpdateRequest request, @MappingTarget Organization entity);

    /**
     * Convert list of Organization entities to list of OrganizationDto.
     */
    List<OrganizationDto> toDtoList(List<Organization> entities);
}
