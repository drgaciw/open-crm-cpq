package com.opencrm.configuration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.configuration.session.ConfigurationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * API response DTO for configuration sessions.
 * Omits BaseEntity {@code deleted} — never leak soft-delete at the HTTP boundary.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationSessionDto {

    private Long id;
    private String uuid;
    private String productId;
    private String userId;
    private ConfigurationStatus status;
    private Map<String, String> selections;
    private boolean valid;
    private String validationErrors;
    private Instant completedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
