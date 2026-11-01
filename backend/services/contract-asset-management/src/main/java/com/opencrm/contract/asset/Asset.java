package com.opencrm.contract.asset;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Installed-base asset for one activated contract line (DRG-653, DRG-654).
 * Lives in contract-asset-management until placement versus CAM (DRG-561) is decided.
 */
@Entity
@Table(name = "assets", uniqueConstraints = @UniqueConstraint(
        name = "uq_asset_subscription_contract_line", columnNames = {"subscription_id", "contract_line_id"}),
        indexes = {
                @Index(name = "idx_asset_account", columnList = "account_id"),
                @Index(name = "idx_asset_contract_line", columnList = "contract_line_id"),
                @Index(name = "idx_asset_subscription", columnList = "subscription_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Asset extends BaseEntity {

    /**
     * QuoteAcceptedEvent.customerId (CustomerSnapshot). Same key CAM subscriptions store as customer_id.
     * There is no customer_id column on this table.
     */
    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "contract_line_id", nullable = false, length = 64)
    private String contractLineId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    /** Null when no entitlement exists to link. Follow-up: DRG-700. */
    @Column(name = "entitlement_id", length = 64)
    private String entitlementId;

    @Column(name = "product_ref")
    private String productRef;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    @Column(name = "effective_start", nullable = false)
    private LocalDate effectiveStart;

    @Column(name = "effective_end", nullable = false)
    private LocalDate effectiveEnd;

    @Column(name = "superseded_by", length = 36)
    private String supersededBy;

    @Column(length = 36)
    private String supersedes;
}
