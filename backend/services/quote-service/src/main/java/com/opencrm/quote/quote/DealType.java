package com.opencrm.quote.quote;

/**
 * Commercial type stored on a quote.
 *
 * <p>Not the CRM opportunity type ({@code com.opencrm.crm.deal.DealType}).
 * CRM values are translated by {@link CrmQuoteDealTypeMapping} in {@link QuoteService#create}.
 * Persisted values are {@code NEW}, {@code AMENDMENT}, {@code RENEWAL}, and {@code EXPANSION}.
 */
public enum DealType {
    NEW,
    AMENDMENT,
    RENEWAL,
    EXPANSION
}
