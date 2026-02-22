package com.opencrm.product.attribute;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_attributes", indexes = {
    @Index(name = "idx_attribute_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductAttribute extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private AttributeDataType dataType;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    @Column(name = "enum_values", columnDefinition = "TEXT")
    private String enumValues;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
