package com.opencrm.pricingairules.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "quote_line_item", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "quote_line_item_seq")
    @SequenceGenerator(name = "quote_line_item_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

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

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
