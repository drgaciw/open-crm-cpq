package com.opencrm.product.bundle;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_bundle_items", indexes = {
    @Index(name = "idx_bundle_item_bundle_id", columnList = "bundle_id"),
    @Index(name = "idx_bundle_item_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BundleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    private ProductBundle bundle;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "is_optional", nullable = false)
    @Builder.Default
    private boolean optional = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
