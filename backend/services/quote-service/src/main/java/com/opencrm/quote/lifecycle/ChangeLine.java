package com.opencrm.quote.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lifecycle_change_lines")
@Getter
@Setter
@NoArgsConstructor
public class ChangeLine implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lifecycle_change_id", nullable = false)
    private LifecycleChange lifecycleChange;

    @Column(name = "tid", nullable = false, length = 64)
    private String tid;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "action_code", nullable = false, length = 32)
    private String actionCode;

    @Column(name = "quantity_semantics", nullable = false, length = 16)
    private String quantitySemantics;

    @Column(name = "business_category", nullable = false, length = 40)
    private String businessCategory;

    @Column(name = "apply_target_type", nullable = false, length = 32)
    private String applyTargetType;

    @Column(name = "target_subscription_id", length = 64)
    private String targetSubscriptionId;

    @Column(name = "source_accepted_line_uuid", length = 64)
    private String sourceAcceptedLineUuid;

    @Column(name = "observed_commercial_revision")
    private Long observedCommercialRevision;

    @Column(name = "start_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal startQty;

    @Column(name = "delta_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal deltaQty;

    @Column(name = "end_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal endQty;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "charge_type", nullable = false, length = 32)
    private String chargeType;

    @Column(name = "price_as_of", nullable = false, length = 32)
    private String priceAsOf;

    @Column(name = "policy_slot", nullable = false, length = 32)
    private String policySlot;

    @Column(name = "product_external_id")
    private String productExternalId;

    @Column(name = "amend_mode", length = 32)
    private String amendMode;

    @Column(name = "proration_method", length = 32)
    private String prorationMethod;

    @Column(name = "policy_decision_id", length = 64)
    private String policyDecisionId;

    @Column(name = "policy_id", length = 100)
    private String policyId;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    @Column(name = "pricing_trace_id", length = 64)
    private String pricingTraceId;

    @Column(name = "pricebook_version", length = 64)
    private String pricebookVersion;

    @Column(name = "configuration_hash", length = 64)
    private String configurationHash;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Transient
    private boolean newly = true;

    @Override
    public boolean isNew() {
        return newly;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newly = false;
    }
}
