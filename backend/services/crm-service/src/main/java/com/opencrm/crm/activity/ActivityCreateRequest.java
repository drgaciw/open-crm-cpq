package com.opencrm.crm.activity;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.Instant;

@Data
public class ActivityCreateRequest {
    @NotNull private ActivityType type;
    @NotBlank @Size(max = 255) private String title;
    @Size(max = 10000) private String note;
    @Positive private Long organizationId;
    @Positive private Long contactId;
    @Positive private Long dealId;
    @Size(max = 512) private String reference;
    @NotNull private Instant occurredAt;
    private Instant dueAt;
}
