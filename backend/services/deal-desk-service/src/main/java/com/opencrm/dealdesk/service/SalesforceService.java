package com.opencrm.dealdesk.service;

import java.util.List;
import java.util.Map;

public interface SalesforceService {

    boolean isEnabled();

    String getAccessToken();

    Map<String, Object> getOpportunity(String opportunityId);

    void updateOpportunity(String opportunityId, Map<String, Object> fields);

    String createQuote(String opportunityId, Map<String, Object> quotePayload);

    void syncQuotePricing(String quoteId, Map<String, Object> pricingFields);

    List<Map<String, Object>> evaluatePriceRules(String quoteId);

    void syncPriceRules(String priceRuleId, String quoteId);

    void validatePriceRules(String quoteId);

    void applyPriceRules(String quoteId);
}
