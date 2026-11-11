package com.opencrm.pricebook;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "price_books", indexes = {
    @Index(name = "idx_price_book_region", columnList = "region"),
    @Index(name = "idx_price_book_default", columnList = "is_default"),
    @Index(name = "idx_price_book_status", columnList = "status"),
    @Index(name = "idx_price_book_cost_book", columnList = "cost_book_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PriceBook extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "region")
    private String region;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    /** DRG-431: explicit DRAFT/PUBLISHED lifecycle, set by PublishPriceBookVersion. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PriceBookStatus status = PriceBookStatus.DRAFT;

    /** DRG-431: business version number, distinct from BaseEntity's optimistic-lock "version". */
    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * DRG-431: the cost book that supplies this price book's costs, so list prices (this aggregate's own entries)
     * and costs (the linked cost book's entries) stay versioned and publishable separately.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_book_id")
    private CostBook costBook;

    public Long getCostBookId() {
        return costBook == null ? null : costBook.getId();
    }

    public boolean isPublished() {
        return status == PriceBookStatus.PUBLISHED;
    }
}
