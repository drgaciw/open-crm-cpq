package com.opencrm.quote.quote;

import com.opencrm.quote.lineitem.QuoteLineItemDto;
import com.opencrm.quote.lineitem.QuoteLineItem;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = com.opencrm.quote.ramp.RampScheduleViews.class)
public interface QuoteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "quoteNumber", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "approvalState", constant = "NONE")
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "totalDiscount", ignore = true)
    @Mapping(target = "totalTax", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "arr", ignore = true)
    @Mapping(target = "tcv", ignore = true)
    @Mapping(target = "acv", ignore = true)
    @Mapping(target = "exitArr", ignore = true)
    @Mapping(target = "rampScheduleStale", ignore = true)
    @Mapping(target = "approvalTier", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    @Mapping(target = "pricingWaterfallJson", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Quote toEntity(QuoteCreateRequest request);

    @Mapping(target = "lineItems", source = "lineItems")
    QuoteDto toDto(Quote quote);

    @Mapping(target = "schedule", expression = "java(RampScheduleViews.toDto(lineItem))")
    @Mapping(target = "pricedPeriods", ignore = true)
    QuoteLineItemDto toLineItemDto(QuoteLineItem lineItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "quoteNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "dealType", ignore = true)
    @Mapping(target = "customerExternalId", ignore = true)
    @Mapping(target = "approvalState", ignore = true)
    @Mapping(target = "approvalTier", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "totalDiscount", ignore = true)
    @Mapping(target = "totalTax", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "arr", ignore = true)
    @Mapping(target = "tcv", ignore = true)
    @Mapping(target = "acv", ignore = true)
    @Mapping(target = "exitArr", ignore = true)
    @Mapping(target = "rampScheduleStale", ignore = true)
    @Mapping(target = "pricingWaterfallJson", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(QuoteUpdateRequest request, @MappingTarget Quote quote);

    List<QuoteDto> toDtoList(List<Quote> quotes);
}
