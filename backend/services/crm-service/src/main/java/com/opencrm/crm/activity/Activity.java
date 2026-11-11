package com.opencrm.crm.activity;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * One row on an organization, contact, or deal timeline (DRG-520 / ADR-030).
 */
@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Activity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ActivityType type;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body")
    private String body;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "deal_id")
    private Long dealId;

    @Column(name = "author_id", length = 255)
    private String authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_kind", length = 16)
    private ActivityAuthorKind authorKind;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Reserved for mailbox ingestion (DRG-521). The note/task API leaves this null.
     */
    @Column(name = "email_thread_ref", length = 512)
    private String emailThreadRef;

    /**
     * Reserved for calendar ingestion (DRG-521). The note/task API leaves this null.
     */
    @Column(name = "calendar_event_ref", length = 512)
    private String calendarEventRef;
}
