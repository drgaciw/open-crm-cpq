package com.opencrm.pricebook;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PriceBookMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "versionNumber", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "costBook", ignore = true)
    PriceBook toEntity(PriceBookCreateRequest request);

    @Mapping(target = "costBookId", source = "costBookId")
    PriceBookDto toDto(PriceBook priceBook);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "versionNumber", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "costBook", ignore = true)
    void updateEntity(PriceBookUpdateRequest request, @MappingTarget PriceBook priceBook);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "priceBook", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    PriceBookEntry toEntryEntity(PriceBookEntryCreateRequest request);

    @Mapping(target = "priceBookId", source = "priceBookId")
    PriceBookEntryDto toEntryDto(PriceBookEntry entry);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "priceBook", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntryEntity(PriceBookEntryUpdateRequest request, @MappingTarget PriceBookEntry entry);
}
