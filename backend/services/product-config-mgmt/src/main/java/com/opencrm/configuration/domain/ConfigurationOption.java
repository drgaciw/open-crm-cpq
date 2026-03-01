package com.opencrm.configuration.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Value object representing a selectable option within a product configuration.
 */
@Entity
@Table(name = "configuration_options", indexes = {
    @Index(name = "idx_cfg_opt_config_id", columnList = "configuration_id"),
    @Index(name = "idx_cfg_opt_option_code", columnList = "option_code"),
    @Index(name = "idx_cfg_opt_product_id", columnList = "referenced_product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfigurationOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", nullable = false)
    private ProductConfiguration configuration;

    @Column(name = "option_code", nullable = false, length = 100)
    private String optionCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "referenced_product_id")
    private Long referencedProductId;

    @Column(name = "price_adjustment", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Column(name = "adjustment_type", length = 20)
    @Builder.Default
    private String adjustmentType = "ABSOLUTE";

    @Column(name = "min_quantity")
    @Builder.Default
    private int minQuantity = 0;

    @Column(name = "max_quantity")
    @Builder.Default
    private int maxQuantity = 1;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultOption = false;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "group_name", length = 100)
    private String groupName;
}
