package com.opencrm.quote.lineitem;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.quote.quote.Quote;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_line_items", indexes = {
    @Index(name = "idx_line_item_quote_id", columnList = "quote_id"),
    @Index(name = "idx_line_item_product", columnList = "product_external_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuoteLineItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "product_external_id", nullable = false)
    private String productExternalId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(name = "line_item_type", length = 50)
    private String lineItemType;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(name = "list_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_rate", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "net_price", precision = 19, scale = 2)
    private BigDecimal netPrice;

    @Column(name = "extended_amount", precision = 19, scale = 2)
    private BigDecimal extendedAmount;

    @Column(name = "billing_frequency", length = 20)
    private String billingFrequency;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "pricing_explanation_code", length = 100)
    private String pricingExplanationCode;

    @Column(name = "pricing_waterfall_json", columnDefinition = "TEXT")
    private String pricingWaterfallJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
