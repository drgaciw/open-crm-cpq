package com.opencrm.pricingairules.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_line_item", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuoteLineItem extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "bundle_id")
    private ProductBundle bundle;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "list_price", precision = 19, scale = 4)
    private BigDecimal listPrice;

    @Column(name = "discount_rate", precision = 5, scale = 4)
    private BigDecimal discountRate;

    @Column(name = "final_price", precision = 19, scale = 4)
    private BigDecimal finalPrice;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "line_item_type", nullable = false)
    private String lineItemType;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadata;
}
