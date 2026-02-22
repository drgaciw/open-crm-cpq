package com.opencrm.pricing.pricebook;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * PriceBookEntry entity - represents a product price within a price book.
 */
@Entity
@Table(name = "price_book_entries", indexes = {
    @Index(name = "idx_price_book_entry_book", columnList = "price_book_id"),
    @Index(name = "idx_price_book_entry_product", columnList = "product_external_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PriceBookEntry extends BaseEntity {

    @Column(name = "price_book_id", nullable = false)
    private Long priceBookId;

    @Column(name = "product_external_id", nullable = false)
    private String productExternalId;

    @Column(name = "list_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal listPrice;

    @Column(name = "floor_price", precision = 19, scale = 4)
    private BigDecimal floorPrice;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";
}
