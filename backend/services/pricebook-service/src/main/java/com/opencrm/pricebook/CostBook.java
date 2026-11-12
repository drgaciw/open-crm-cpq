package com.opencrm.pricebook;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DRG-431 / BC-03-01: a versioned, publishable set of costs, kept separate from {@link PriceBook} so list
 * prices and costs can be defined, versioned and published independently. A price book links to a cost book
 * via {@link PriceBook#getCostBook()}.
 */
@Entity
@Table(name = "cost_books", indexes = {
    @Index(name = "idx_cost_book_region", columnList = "region"),
    @Index(name = "idx_cost_book_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CostBook extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "region")
    private String region;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PriceBookStatus status = PriceBookStatus.DRAFT;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "published_at")
    private Instant publishedAt;

    public boolean isPublished() {
        return status == PriceBookStatus.PUBLISHED;
    }
}
