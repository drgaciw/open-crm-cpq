package com.opencrm.product.attribute;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_attribute_values", indexes = {
    @Index(name = "idx_attr_value_product_id", columnList = "product_id"),
    @Index(name = "idx_attr_value_attribute_id", columnList = "attribute_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductAttributeValue extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "attribute_id", nullable = false)
    private Long attributeId;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;
}
