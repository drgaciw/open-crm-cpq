package com.opencrm.pricebook;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DRG-431 / BC-03-01: a cost book entry, keyed by Sku + currency + UnitOfMeasure + EffectiveDateRange,
 * mirroring {@link PriceBookEntry}'s keying.
 */
@Entity
@Table(name = "cost_book_entries", indexes = {
    @Index(name = "idx_cost_book_entry_book", columnList = "cost_book_id"),
    @Index(name = "idx_cost_book_entry_resolution", columnList = "cost_book_id, sku, currency, unit_of_measure")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CostBookEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cost_book_id", nullable = false, updatable = false)
    private CostBook costBook;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal cost;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    @Builder.Default
    private String unitOfMeasure = "EACH";

    @Column(name = "effective_start_date")
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    public Long getCostBookId() {
        return costBook == null ? null : costBook.getId();
    }

    public boolean isEffectiveOn(LocalDate asOfDate) {
        if (asOfDate == null) {
            return true;
        }
        return (effectiveStartDate == null || !effectiveStartDate.isAfter(asOfDate))
                && (effectiveEndDate == null || !effectiveEndDate.isBefore(asOfDate));
    }
}
