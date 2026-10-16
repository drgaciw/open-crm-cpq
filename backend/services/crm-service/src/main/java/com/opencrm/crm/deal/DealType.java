package com.opencrm.crm.deal;

/**
 * Type of CRM opportunity (deal).
 *
 * <p>These names are not quote deal types. quote-service {@code CrmQuoteDealTypeMapping}
 * is the opportunity-to-quote table, applied in {@code QuoteService#create}. Add a constant
 * only together with a row in that table; {@code DealTypeTest} locks this catalog to those keys.
 * An unmapped name fails at the handoff ({@code CRM_DEAL_TYPE_UNMAPPED}) rather than guessing.
 */
public enum DealType {
    NEW_BUSINESS,
    EXISTING_BUSINESS,
    RENEWAL,
    UPSELL,
    CROSS_SELL
}
