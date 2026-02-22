package com.opencrm.pricing.waterfall;

/**
 * Waterfall step type for pricing calculation.
 */
public enum WaterfallStepType {
    LIST_PRICE,
    BASE_PRICE,
    DISCOUNT,
    SURCHARGE,
    PARTNER_ADJUSTMENT,
    PRORATION,
    FX_CONVERSION,
    TAX,
    NET_PRICE,
    APPROVAL
}
