package com.opencrm.crm.deal;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.organization.Organization;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;

/**
 * Deal entity - represents a sales opportunity.
 */
@Entity
@Table(name = "deals", indexes = {
    @Index(name = "idx_deal_stage", columnList = "stage"),
    @Index(name = "idx_deal_owner", columnList = "owner_id"),
    @Index(name = "idx_deal_close_date", columnList = "expected_close_date"),
    @Index(name = "idx_deal_org", columnList = "organization_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Deal extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_contact_id")
    private Contact primaryContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    @Builder.Default
    private DealStage stage = DealStage.PROSPECTING;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "probability")
    @Builder.Default
    private Integer probability = 10;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "actual_close_date")
    private LocalDate actualCloseDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "source_lead_id")
    private Long sourceLeadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    @Builder.Default
    private DealType type = DealType.NEW_BUSINESS;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "won_at")
    private Instant wonAt;

    @Column(name = "lost_at")
    private Instant lostAt;

    @Column(name = "next_step")
    private String nextStep;

    @Column(name = "next_step_date")
    private LocalDate nextStepDate;

    @Column(name = "competitors")
    private String competitors;

    @Column(name = "weighted_amount")
    private BigDecimal weightedAmount;

    /**
     * Calculate weighted amount based on probability.
     */
    @PrePersist
    @PreUpdate
    protected void calculateWeightedAmount() {
        if (amount != null && probability != null) {
            this.weightedAmount = amount.multiply(BigDecimal.valueOf(probability))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }

    private static final Map<DealStage, DealStage> STAGE_TRANSITIONS = Map.of(
        DealStage.PROSPECTING, DealStage.QUALIFICATION,
        DealStage.QUALIFICATION, DealStage.NEEDS_ANALYSIS,
        DealStage.NEEDS_ANALYSIS, DealStage.PROPOSAL,
        DealStage.PROPOSAL, DealStage.NEGOTIATION
    );

    /**
     * Move deal to next stage.
     */
    public void advanceStage() {
        DealStage nextStage = STAGE_TRANSITIONS.get(this.stage);
        if (nextStage == null) {
            throw new IllegalStateException("Cannot advance from stage: " + this.stage);
        }
        this.stage = nextStage;
        updateProbabilityByStage();
    }

    /**
     * Mark deal as won.
     */
    public void markWon() {
        this.stage = DealStage.WON;
        this.probability = 100;
        this.wonAt = Instant.now();
        this.actualCloseDate = LocalDate.now();
    }

    /**
     * Mark deal as lost.
     */
    public void markLost(String reason) {
        this.stage = DealStage.LOST;
        this.probability = 0;
        this.lostReason = reason;
        this.lostAt = Instant.now();
        this.actualCloseDate = LocalDate.now();
    }

    /**
     * Update probability based on stage.
     */
    private void updateProbabilityByStage() {
        this.probability = switch (this.stage) {
            case PROSPECTING -> 10;
            case QUALIFICATION -> 20;
            case NEEDS_ANALYSIS -> 40;
            case PROPOSAL -> 60;
            case NEGOTIATION -> 80;
            case WON -> 100;
            case LOST -> 0;
        };
    }
}
