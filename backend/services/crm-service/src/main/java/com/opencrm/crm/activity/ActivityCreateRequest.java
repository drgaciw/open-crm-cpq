package com.opencrm.crm.activity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Create a note or a task. Email and meeting values are rejected until mailbox
 * ingestion (DRG-521). The author is not a field here; it comes from the caller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCreateRequest {

    @NotNull(message = "Activity type is required")
    private ActivityType type;

    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    @Size(max = 8000, message = "Body must not exceed 8000 characters")
    private String body;

    private Long organizationId;

    private Long contactId;

    private Long dealId;

    private Instant dueAt;

    private Instant occurredAt;
}
