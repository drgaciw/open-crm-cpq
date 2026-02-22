package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_bundle_item", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_bundle_item_seq")
    @SequenceGenerator(name = "product_bundle_item_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "bundle_id", nullable = false)
    private ProductBundle bundle;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}
