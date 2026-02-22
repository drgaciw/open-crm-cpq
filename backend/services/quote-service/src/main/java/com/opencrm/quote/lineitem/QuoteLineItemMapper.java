package com.opencrm.quote.lineitem;

import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface QuoteLineItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "quote", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    @Mapping(target = "discountRate", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "netPrice", ignore = true)
    @Mapping(target = "extendedAmount", ignore = true)
    @Mapping(target = "pricingExplanationCode", ignore = true)
    @Mapping(target = "pricingWaterfallJson", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    QuoteLineItem toEntity(LineItemCreateRequest request);

    QuoteLineItemDto toDto(QuoteLineItem lineItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "quote", ignore = true)
    @Mapping(target = "productExternalId", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    @Mapping(target = "discountRate", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "netPrice", ignore = true)
    @Mapping(target = "extendedAmount", ignore = true)
    @Mapping(target = "pricingExplanationCode", ignore = true)
    @Mapping(target = "pricingWaterfallJson", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(LineItemCreateRequest request, @MappingTarget QuoteLineItem lineItem);

    List<QuoteLineItemDto> toDtoList(List<QuoteLineItem> lineItems);
}
