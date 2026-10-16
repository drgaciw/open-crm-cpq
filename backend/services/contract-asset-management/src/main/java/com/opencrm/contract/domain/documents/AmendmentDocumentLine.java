package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One change line on one document revision. */
@Entity
@Table(name = "amendment_document_lines", uniqueConstraints = @UniqueConstraint(
        name = "uq_amendment_line_triple", columnNames = {"document_id", "revision_no", "line_no"}))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AmendmentDocumentLine extends DocumentAuditedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "change_line_ref", nullable = false)
    private UUID changeLineRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_code", nullable = false, length = 40)
    private ActionCode actionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_category", nullable = false, length = 40)
    private BusinessCategory businessCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_target_type", nullable = false, length = 40)
    private ApplyTargetType applyTargetType;

    @Column(name = "target_subscription_id")
    private UUID targetSubscriptionId;

    @Column(name = "source_accepted_line_uuid", length = 255)
    private String sourceAcceptedLineUuid;

    @Column(name = "result_accepted_line_uuid", length = 255)
    private String resultAcceptedLineUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_semantics", nullable = false, length = 40)
    private QuantitySemantics quantitySemantics;

    @Column(name = "start_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal startQty;

    @Column(name = "delta_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal deltaQty;

    @Column(name = "end_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal endQty;

    @Column(name = "metric", length = 64)
    private String metric;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 40)
    private ChargeType chargeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_as_of", nullable = false, length = 40)
    private PriceAsOf priceAsOf;

    @Column(name = "product_external_id", nullable = false, length = 255)
    private String productExternalId;

    @Column(name = "pricebook_version", nullable = false, length = 64)
    private String pricebookVersion;

    @Column(name = "configuration_hash", nullable = false, length = 64)
    private String configurationHash;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_slot", nullable = false, length = 40)
    private PolicySlot policySlot;

    @Column(name = "policy_id", length = 255)
    private String policyId;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    @Column(name = "policy_decision_id", length = 255)
    private String policyDecisionId;

    @Column(name = "pricing_trace_id", length = 255)
    private String pricingTraceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segment_schedule", columnDefinition = "jsonb")
    private String segmentSchedule;

    @Column(name = "override_value", precision = 19, scale = 4)
    private BigDecimal overrideValue;

    @Column(name = "override_reason_code", length = 128)
    private String overrideReasonCode;

    @Column(name = "override_actor", length = 255)
    private String overrideActor;

    @Column(name = "application_unit_no", nullable = false)
    private Integer applicationUnitNo;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
