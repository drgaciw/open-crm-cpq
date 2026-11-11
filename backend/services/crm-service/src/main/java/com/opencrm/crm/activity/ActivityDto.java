package com.opencrm.crm.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDto {

    private Long id;
    private String uuid;
    private ActivityType type;
    private String subject;
    private String body;
    private Long organizationId;
    private Long contactId;
    private Long dealId;
    private String authorId;
    private ActivityAuthorKind authorKind;
    private Instant dueAt;
    private Instant completedAt;
    private Instant occurredAt;
    private String emailThreadRef;
    private String calendarEventRef;
    private Instant createdAt;
    private Instant updatedAt;
}
