package com.opencrm.pricebook;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "price_book_entries", indexes = {
    @Index(name = "idx_price_book_entry_book", columnList = "price_book_id"),
    @Index(name = "idx_price_book_entry_product", columnList = "product_external_id"),
    @Index(name = "idx_price_book_entry_resolution",
            columnList = "price_book_id, product_external_id, currency, unit_of_measure")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PriceBookEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_book_id", nullable = false, updatable = false)
    private PriceBook priceBook;

    /** The SKU. Named productExternalId for API/schema backward compatibility (pre-dates DRG-431). */
    @Column(name = "product_external_id", nullable = false)
    private String productExternalId;

    @Column(name = "list_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal listPrice;

    @Column(name = "floor_price", precision = 19, scale = 4)
    private BigDecimal floorPrice;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    /** DRG-431: entries are keyed by Sku + currency + UnitOfMeasure + EffectiveDateRange. */
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    @Builder.Default
    private String unitOfMeasure = "EACH";

    /** Inclusive. Null means open-ended (effective since the beginning of time). */
    @Column(name = "effective_start_date")
    private LocalDate effectiveStartDate;

    /** Inclusive. Null means open-ended (effective with no planned end). */
    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    public Long getPriceBookId() {
        return priceBook == null ? null : priceBook.getId();
    }

    /** The SKU this entry prices. Alias for {@link #getProductExternalId()}. */
    public String getSku() {
        return productExternalId;
    }

    public boolean isEffectiveOn(LocalDate asOfDate) {
        if (asOfDate == null) {
            return true;
        }
        return (effectiveStartDate == null || !effectiveStartDate.isAfter(asOfDate))
                && (effectiveEndDate == null || !effectiveEndDate.isBefore(asOfDate));
    }

    /** DRG-431: whether this entry's EffectiveDateRange overlaps the given range. See {@link EffectiveDateRange}. */
    public boolean overlapsRange(LocalDate otherStart, LocalDate otherEnd) {
        return EffectiveDateRange.overlaps(effectiveStartDate, effectiveEndDate, otherStart, otherEnd);
    }
}
