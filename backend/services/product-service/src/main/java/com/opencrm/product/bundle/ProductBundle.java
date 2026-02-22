package com.opencrm.product.bundle;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_bundles", indexes = {
    @Index(name = "idx_bundle_product_id", columnList = "product_id"),
    @Index(name = "idx_bundle_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductBundle extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    @Builder.Default
    private DiscountType discountType = DiscountType.NONE;

    @Column(name = "discount_value", precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "min_items")
    private Integer minItems;

    @Column(name = "max_items")
    private Integer maxItems;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<BundleItem> items = new ArrayList<>();
}
