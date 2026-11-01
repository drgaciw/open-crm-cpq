package com.opencrm.crm.contact;

import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.organization.Organization;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Contact entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ContactMapper {

    /**
     * Convert ContactCreateRequest to Contact entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Contact toEntity(ContactCreateRequest request);

    /**
     * Convert Contact entity to ContactDto.
     */
    @Mapping(target = "fullName", expression = "java(entity.getFullName())")
    @Mapping(target = "organizationId", source = "organization", qualifiedByName = "organizationAccountId")
    @Mapping(target = "organizationName", source = "organization.name")
    ContactDto toDto(Contact entity);

    /**
     * Shared-kernel identifier for the contact's account. Null when the contact has no organization.
     */
    default AccountId accountId(Organization organization) {
        if (organization == null || organization.getId() == null) {
            return null;
        }
        return AccountId.of(organization.getId());
    }

    @Named("organizationAccountId")
    default Long organizationAccountId(Organization organization) {
        AccountId accountId = accountId(organization);
        return accountId == null ? null : accountId.value();
    }

    /**
     * Update Contact entity from ContactUpdateRequest.
     * Only updates non-null fields.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(ContactUpdateRequest request, @MappingTarget Contact entity);

    /**
     * Convert list of Contact entities to list of ContactDto.
     */
    List<ContactDto> toDtoList(List<Contact> entities);
}
