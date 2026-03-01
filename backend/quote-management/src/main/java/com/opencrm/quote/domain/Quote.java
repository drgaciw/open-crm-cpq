package com.opencrm.quote.domain;

import com.opencrm.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuoteLine> lines = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "total_amount")),
        @AttributeOverride(name = "currencyCode", column = @Column(name = "currency_code"))
    })
    private Money totalAmount;

    private Instant createdAt;
    private Instant acceptedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = QuoteStatus.DRAFT;
        if (totalAmount == null) totalAmount = Money.zero("USD");
    }
    
    public void addLine(QuoteLine line) {
        lines.add(line);
        line.setQuote(this);
    }
    
    public void calculateTotal() {
        if (lines.isEmpty()) {
            totalAmount = Money.zero("USD");
            return;
        }
        String currency = lines.get(0).getLineTotal().getCurrencyCode();
        Money total = Money.zero(currency);
        for (QuoteLine line : lines) {
            if (line.getLineTotal() != null) {
                total = total.add(line.getLineTotal());
            }
        }
        totalAmount = total;
    }
}
