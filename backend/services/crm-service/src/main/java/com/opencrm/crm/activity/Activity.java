package com.opencrm.crm.activity;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Manually recorded timeline metadata. Never contains mailbox bodies or credentials. */
@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor
public class Activity extends BaseEntity {
    @Column(nullable = false, updatable = false, length = 128)
    private String workforceTenantId;
    @Column(nullable = false, updatable = false)
    private String authorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ActivityType type;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "text")
    private String note;
    private Long organizationId;
    private Long contactId;
    private Long dealId;
    @Column(name = "external_reference", length = 512)
    private String reference;
    @Column(nullable = false)
    private Instant occurredAt;
    private Instant dueAt;
    private Instant completedAt;
    /** Provenance must not be confused with verified synced evidence (DRG-521). */
    public String getSource() { return "MANUAL"; }
}
