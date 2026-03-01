package com.opencrm.quote.domain;

import com.opencrm.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quote_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(nullable = false)
    private String productId;
    
    private int quantity;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price")),
        @AttributeOverride(name = "currencyCode", column = @Column(name = "unit_price_currency"))
    })
    private Money unitPrice;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "line_total")),
        @AttributeOverride(name = "currencyCode", column = @Column(name = "line_total_currency"))
    })
    private Money lineTotal;
    
    @PrePersist
    @PreUpdate
    public void calculateLineTotal() {
        if (unitPrice != null && quantity > 0) {
            lineTotal = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
        }
    }
}
