package com.opencrm.crm.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityDto {
    private Long id;
    private String uuid;
    private ActivityType type;
    private String title;
    private String note;
    private Long organizationId;
    private Long contactId;
    private Long dealId;
    private String reference;
    private Instant occurredAt;
    private Instant dueAt;
    private Instant completedAt;
    private String authorId;
    private String source;
    private Instant createdAt;
}
