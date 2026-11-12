package com.opencrm.pricebook;

/**
 * Lifecycle status of a price book or cost book version (DRG-431 / BC-03-01).
 */
public enum PriceBookStatus {
    /** Mutable: entries and header fields may still be defined or changed. */
    DRAFT,
    /** Immutable: published via PublishPriceBookVersion / PublishCostBookVersion. */
    PUBLISHED
}
