package com.opencrm.pricing.customer;

import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Customer entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerMapper {

    /**
     * Convert create request to entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Customer toEntity(CustomerCreateRequest request);

    /**
     * Convert entity to DTO.
     */
    CustomerDto toDto(Customer customer);

    /**
     * Update entity from update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(CustomerUpdateRequest request, @MappingTarget Customer customer);

    /**
     * Convert list of entities to list of DTOs.
     */
    List<CustomerDto> toDtoList(List<Customer> customers);
}
