package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explicit CRM opportunity type to quote {@link DealType} table, applied by
 * {@link QuoteService#create} at the opportunity-to-quote handoff.
 *
 * <p>Keys are exactly the names of {@code com.opencrm.crm.deal.DealType}.
 * crm-service {@code DealTypeTest} locks that catalog to {@link #crmNames()}.
 * A CRM constant added without a row here is rejected by {@link #toQuoteDealType(String)}
 * ({@code CRM_DEAL_TYPE_UNMAPPED}). This method has no default: null and blank fail
 * with {@code CRM_DEAL_TYPE_REQUIRED}. {@code QuoteService#create} applies
 * {@link DealType#NEW} only when the request omits both the CRM type and a quote type.
 *
 * <p>Targets stay inside the quote enum persisted by {@code chk_quotes_deal_type}
 * ({@code NEW}, {@code AMENDMENT}, {@code RENEWAL}, {@code EXPANSION}). No schema change.
 *
 * <table>
 *   <caption>Handoff table</caption>
 *   <tr><th>CRM DealType</th><th>Quote DealType</th><th>Commercial meaning</th></tr>
 *   <tr><td>NEW_BUSINESS</td><td>NEW</td><td>First sale. Renewal uplift and amendment proration do not apply.</td></tr>
 *   <tr><td>EXISTING_BUSINESS</td><td>AMENDMENT</td><td>Existing relationship that is not a renewal or a growth motion. Amendment proration keys on AMENDMENT.</td></tr>
 *   <tr><td>RENEWAL</td><td>RENEWAL</td><td>Term renewal. Renewal uplift keys on RENEWAL.</td></tr>
 *   <tr><td>UPSELL</td><td>EXPANSION</td><td>Growth on products the customer already has. The quote enum has no UPSELL.</td></tr>
 *   <tr><td>CROSS_SELL</td><td>EXPANSION</td><td>Growth by adding a product. The quote enum has no CROSS_SELL. UPSELL and CROSS_SELL are not distinguished after the handoff.</td></tr>
 * </table>
 *
 * <p>Quote-only names ({@code NEW}, {@code AMENDMENT}, {@code EXPANSION}) and continuation
 * operation {@code EXTENSION} are not CRM types and are rejected. EXTENSION versus RENEWAL
 * on a continuation quote is DRG-546 and is not decided here.
 */
public final class CrmQuoteDealTypeMapping {

    private CrmQuoteDealTypeMapping() {
    }

    /**
     * Closed copy of the CRM catalog. A new constant that is missing from the
     * constructor call fails compilation, so the table cannot gain a key without a target.
     */
    private enum Source {
        NEW_BUSINESS(DealType.NEW),
        EXISTING_BUSINESS(DealType.AMENDMENT),
        RENEWAL(DealType.RENEWAL),
        UPSELL(DealType.EXPANSION),
        CROSS_SELL(DealType.EXPANSION);

        private final DealType quoteDealType;

        Source(DealType quoteDealType) {
            this.quoteDealType = quoteDealType;
        }
    }

    private static final Map<String, DealType> TABLE = table();

    private static Map<String, DealType> table() {
        Map<String, DealType> rows = new LinkedHashMap<>();
        for (Source source : Source.values()) {
            rows.put(source.name(), source.quoteDealType);
        }
        return Map.copyOf(rows);
    }

    /**
     * @param crmDealType CRM {@code DealType} name; surrounding whitespace is ignored and the
     *                    value is matched case-insensitively in {@link Locale#ROOT}
     * @return the quote deal type for that CRM value
     * @throws BusinessException {@code CRM_DEAL_TYPE_REQUIRED} when null or blank;
     *                           {@code CRM_DEAL_TYPE_UNMAPPED} when the name is not in the table
     */
    public static DealType toQuoteDealType(String crmDealType) {
        if (crmDealType == null) {
            throw required("CRM deal type is required at the opportunity-to-quote handoff");
        }
        String normalized = crmDealType.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw required("CRM deal type is blank");
        }
        DealType mapped = TABLE.get(normalized);
        if (mapped == null) {
            throw new BusinessException(
                    "Unmapped CRM deal type " + safeToken(normalized),
                    "CRM_DEAL_TYPE_UNMAPPED");
        }
        return mapped;
    }

    /** CRM names this table accepts. Same set as {@code com.opencrm.crm.deal.DealType}. */
    public static Set<String> crmNames() {
        return TABLE.keySet();
    }

    private static BusinessException required(String message) {
        return new BusinessException(message, "CRM_DEAL_TYPE_REQUIRED");
    }

    /** Echo only a short enum-shaped token so a rejected value cannot carry markup or a huge payload. */
    private static String safeToken(String normalized) {
        if (normalized.length() <= 64 && normalized.chars().allMatch(CrmQuoteDealTypeMapping::isEnumChar)) {
            return normalized;
        }
        return "(rejected)";
    }

    private static boolean isEnumChar(int ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_';
    }
}
