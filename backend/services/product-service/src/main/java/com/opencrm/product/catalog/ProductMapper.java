package com.opencrm.product.catalog;

import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "retiredAt", ignore = true)
    @Mapping(target = "retirementReason", ignore = true)
    // tenantId is never taken from a request body (DRG-418); ProductService sets it from ProductTenantResolver.
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    Product toEntity(ProductCreateRequest request);

    ProductDto toDto(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "retiredAt", ignore = true)
    @Mapping(target = "retirementReason", ignore = true)
    // Immutable identity: SKU and tenant never change on revise (DRG-418).
    @Mapping(target = "tenantId", ignore = true)
    void updateEntity(ProductUpdateRequest request, @MappingTarget Product product);

    List<ProductDto> toDtoList(List<Product> products);
}
