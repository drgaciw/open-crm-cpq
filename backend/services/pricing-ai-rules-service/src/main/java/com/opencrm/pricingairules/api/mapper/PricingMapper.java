package com.opencrm.pricingairules.api.mapper;

import com.opencrm.pricingairules.api.dto.*;
import com.opencrm.pricingairules.domain.model.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PricingMapper {
    
    // Quote mappings
    @Mapping(target = "arr", source = "ARR")
    @Mapping(target = "tcv", source = "TCV")
    @Mapping(target = "acv", source = "ACV")
    @Mapping(target = "clv", source = "CLV")
    QuoteDTO toQuoteDTO(Quote quote);

    @Mapping(target = "ARR", source = "arr")
    @Mapping(target = "TCV", source = "tcv")
    @Mapping(target = "ACV", source = "acv")
    @Mapping(target = "CLV", source = "clv")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    Quote toQuote(QuoteDTO quoteDTO);
    
    // Customer mappings
    CustomerDTO toCustomerDTO(Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toCustomer(CustomerDTO customerDTO);
    
    // Product mappings
    ProductDTO toProductDTO(Product product);

    @Mapping(target = "id", ignore = true)
    Product toProduct(ProductDTO productDTO);
    
    // Product Bundle mappings
    ProductBundleDTO toProductBundleDTO(ProductBundle productBundle);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductBundle toProductBundle(ProductBundleDTO productBundleDTO);
    
    // Product Bundle Item mappings
    ProductBundleItemDTO toProductBundleItemDTO(ProductBundleItem productBundleItem);

    ProductBundleItem toProductBundleItem(ProductBundleItemDTO productBundleItemDTO);
    
    // Quote Line Item mappings
    QuoteLineItemDTO toQuoteLineItemDTO(QuoteLineItem quoteLineItem);

    QuoteLineItem toQuoteLineItem(QuoteLineItemDTO quoteLineItemDTO);
    
    // Rule mappings
    RuleDTO toRuleDTO(Rule rule);

    @Mapping(target = "ruleSet", ignore = true)
    @Mapping(target = "dependencies", ignore = true)
    @Mapping(target = "dependents", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Rule toRule(RuleDTO ruleDTO);
    
    // Rule Set mappings
    RuleSetDTO toRuleSetDTO(RuleSet ruleSet);

    @Mapping(target = "rules", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    RuleSet toRuleSet(RuleSetDTO ruleSetDTO);
    
    // Update existing entities
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalId", source = "externalId")
    @Mapping(target = "quoteName", source = "quoteName")
    @Mapping(target = "subscriptionType", source = "subscriptionType")
    @Mapping(target = "durationInMonths", source = "durationInMonths")
    @Mapping(target = "monthlyPrice", source = "monthlyPrice")
    @Mapping(target = "basePrice", source = "basePrice")
    @Mapping(target = "finalPrice", source = "finalPrice")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "discountRate", source = "discountRate")
    @Mapping(target = "priceMultiplier", source = "priceMultiplier")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "ARR", source = "arr")
    @Mapping(target = "TCV", source = "tcv")
    @Mapping(target = "ACV", source = "acv")
    @Mapping(target = "CLV", source = "clv")
    @Mapping(target = "userCount", source = "userCount")
    @Mapping(target = "effectiveDate", source = "effectiveDate")
    @Mapping(target = "expirationDate", source = "expirationDate")
    @Mapping(target = "expectedDuration", source = "expectedDuration")
    @Mapping(target = "isMultiRegion", source = "isMultiRegion")
    @Mapping(target = "hasCustomSupport", source = "hasCustomSupport")
    @Mapping(target = "churnAdjustment", source = "churnAdjustment")
    @Mapping(target = "metadata", source = "metadata")
    void updateQuote(@MappingTarget Quote quote, QuoteDTO quoteDTO);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "segment", source = "segment")
    @Mapping(target = "region", source = "region")
    @Mapping(target = "industry", source = "industry")
    @Mapping(target = "churnScore", source = "churnScore")
    void updateCustomer(@MappingTarget Customer customer, CustomerDTO customerDTO);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalId", source = "externalId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "productType", source = "productType")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "listPrice", source = "listPrice")
    @Mapping(target = "currency", source = "currency")
    void updateProduct(@MappingTarget Product product, ProductDTO productDTO);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "bundleType", source = "bundleType")
    @Mapping(target = "status", source = "status")
    void updateProductBundle(@MappingTarget ProductBundle productBundle, ProductBundleDTO productBundleDTO);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "ruleType", source = "ruleType")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "status", source = "status")
    void updateRule(@MappingTarget Rule rule, RuleDTO ruleDTO);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "effectiveDate", source = "effectiveDate")
    @Mapping(target = "expirationDate", source = "expirationDate")
    void updateRuleSet(@MappingTarget RuleSet ruleSet, RuleSetDTO ruleSetDTO);
}

