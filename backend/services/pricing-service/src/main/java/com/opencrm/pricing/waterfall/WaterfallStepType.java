package com.opencrm.pricing.waterfall;

/**
 * Waterfall step type for pricing calculation.
 */
public enum WaterfallStepType {
    PERIOD_SELECTION,
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
