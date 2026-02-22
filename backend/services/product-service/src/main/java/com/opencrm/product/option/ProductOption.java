package com.opencrm.product.option;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "product_options", indexes = {
    @Index(name = "idx_option_group_id", columnList = "option_group_id"),
    @Index(name = "idx_option_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_group_id", nullable = false)
    private ProductOptionGroup optionGroup;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_adjustment", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", length = 20)
    @Builder.Default
    private AdjustmentType adjustmentType = AdjustmentType.ABSOLUTE;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultOption = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
