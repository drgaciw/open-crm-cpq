package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_bundle", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_bundle_seq")
    @SequenceGenerator(name = "product_bundle_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "bundle_type", nullable = false)
    private String bundleType;

    @Column(name = "status", nullable = false)
    private String status;

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductBundleItem> bundleItems = new ArrayList<>();

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
