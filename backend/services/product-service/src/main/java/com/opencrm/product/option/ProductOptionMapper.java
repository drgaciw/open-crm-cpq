package com.opencrm.product.option;

import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductOptionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "sortOrder", expression = "java(request.getSortOrder() == null ? 0 : request.getSortOrder())")
    ProductOptionGroup toEntity(OptionGroupCreateRequest request);

    @Mapping(target = "options", source = "options")
    ProductOptionGroupDto toGroupDto(ProductOptionGroup group);

    ProductOptionDto toOptionDto(ProductOption option);

    List<ProductOptionGroupDto> toGroupDtoList(List<ProductOptionGroup> groups);
}
