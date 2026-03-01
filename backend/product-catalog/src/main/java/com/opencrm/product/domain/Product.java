package com.opencrm.product.domain;

import com.opencrm.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "base_price_amount")),
        @AttributeOverride(name = "currencyCode", column = @Column(name = "base_price_currency"))
    })
    private Money basePrice;

    private Instant createdAt;
    private Instant updatedAt;
    
    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = ProductStatus.DRAFT;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
