package com.opencrm.crm.contact;

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
    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    ContactDto toDto(Contact entity);

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
