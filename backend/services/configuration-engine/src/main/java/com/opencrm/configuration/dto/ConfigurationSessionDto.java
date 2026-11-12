package com.opencrm.configuration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.configuration.session.ConfigurationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
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
    /** Set instead of {@code productId} when the session was started against a bundle (DRG-424). */
    private String bundleId;
    private String userId;
    /** Catalog version the session was pinned against at StartSession, if the caller supplied one (DRG-424). */
    private String catalogVersion;
    /** Compiled configuration schema version in effect when the session started (DRG-424). */
    private String ruleSetVersion;
    /** Entra {@code tid} of the caller who started the session, if any (DRG-424). */
    private String tenantId;
    private ConfigurationStatus status;
    private Map<String, String> selections;
    private boolean valid;
    private String validationErrors;
    /**
     * Structured open violations as of the moment the session state was read (GetSessionState, DRG-424).
     * Computed live from the current selections; not persisted. {@code validationErrors} remains the
     * flattened legacy summary.
     */
    private List<ConfigurationViolation> openViolations;
    /**
     * Candidate option (product) ids not yet selected and not excluded by current selections
     * (GetSessionState, DRG-424). Computed live; not persisted.
     */
    private List<Long> availableOptionIds;
    private Instant completedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
