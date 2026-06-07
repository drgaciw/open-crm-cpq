package com.opencrm.crm.lead;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.organization.Organization;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lead entity - represents a potential sales opportunity.
 */
@Entity
@Table(name = "leads", indexes = {
    @Index(name = "idx_lead_status", columnList = "status"),
    @Index(name = "idx_lead_owner", columnList = "owner_id"),
    @Index(name = "idx_lead_source", columnList = "source"),
    @Index(name = "idx_lead_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Lead extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "source")
    private String source;

    @Column(name = "source_detail")
    private String sourceDetail;

    @Column(name = "estimated_value")
    private BigDecimal estimatedValue;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "score")
    @Builder.Default
    private Integer score = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "first_contacted_at")
    private Instant firstContactedAt;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(name = "qualified_at")
    private Instant qualifiedAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "converted_deal_id")
    private Long convertedDealId;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "lost_at")
    private Instant lostAt;

    @Column(name = "campaign")
    private String campaign;

    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LeadPriority priority = LeadPriority.MEDIUM;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "next_action_date")
    private Instant nextActionDate;

    /**
     * Mark lead as contacted.
     */
    public void markContacted() {
        Instant now = Instant.now();
        if (this.firstContactedAt == null) {
            this.firstContactedAt = now;
        }
        this.lastContactedAt = now;
        if (this.status == LeadStatus.NEW) {
            this.status = LeadStatus.CONTACTED;
        }
    }

    /**
     * Qualify the lead.
     */
    public void qualify() {
        this.status = LeadStatus.QUALIFIED;
        this.qualifiedAt = Instant.now();
    }

    /**
     * Convert lead to a deal.
     */
    public void convert(Long dealId) {
        this.status = LeadStatus.CONVERTED;
        this.convertedAt = Instant.now();
        this.convertedDealId = dealId;
    }

    /**
     * Mark lead as lost.
     */
    public void markLost(String reason) {
        this.status = LeadStatus.LOST;
        this.lostReason = reason;
        this.lostAt = Instant.now();
    }
}
