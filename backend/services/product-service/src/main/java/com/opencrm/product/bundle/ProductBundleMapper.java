package com.opencrm.product.bundle;

import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductBundleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    ProductBundle toEntity(ProductBundleCreateRequest request);

    @Mapping(target = "items", source = "items")
    ProductBundleDto toDto(ProductBundle bundle);

    @Mapping(target = "productId", source = "productId")
    BundleItemDto toItemDto(BundleItem item);

    List<ProductBundleDto> toDtoList(List<ProductBundle> bundles);
}
