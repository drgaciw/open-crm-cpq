package com.opencrm.contract.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Append-only history row. Installed base and the supersede chain are read from these rows. */
@Entity
@Table(name = "asset_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "asset_id", nullable = false, length = 36)
    private String assetId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AssetHistoryKind eventType;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd;

    @Column(name = "source_amendment_id", length = 36)
    private String sourceAmendmentId;

    @Column(name = "source_apply_unit", length = 64)
    private String sourceApplyUnit;

    @Column(name = "predecessor_asset_id", length = 36)
    private String predecessorAssetId;

    @Column(name = "successor_asset_id", length = 36)
    private String successorAssetId;

    @Column(name = "contract_line_id", length = 64)
    private String contractLineId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "product_ref")
    private String productRef;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "applied_order", nullable = false)
    private long appliedOrder;
}
