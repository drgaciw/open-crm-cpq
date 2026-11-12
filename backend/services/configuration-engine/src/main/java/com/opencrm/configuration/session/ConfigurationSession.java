package com.opencrm.configuration.session;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration session entity - maintains session-safe configuration state.
 * Ensures only valid configuration combinations pass to pricing/quote flows.
 */
@Entity
@Table(name = "configuration_sessions", indexes = {
    @Index(name = "idx_config_session_product", columnList = "product_id"),
    @Index(name = "idx_config_session_user", columnList = "user_id"),
    @Index(name = "idx_config_session_status", columnList = "status"),
    @Index(name = "idx_config_session_bundle", columnList = "bundle_id"),
    @Index(name = "idx_config_session_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfigurationSession extends BaseEntity {

    /** Exactly one of {@code productId}/{@code bundleId} is set (DRG-424 StartSession; DB CHECK enforces it). */
    @Column(name = "product_id")
    private String productId;

    /** Set instead of {@code productId} when the session is started against a bundle. */
    @Column(name = "bundle_id")
    private String bundleId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * Caller-supplied catalog version the session is pinned against at StartSession (DRG-424). Recorded as
     * given; there is no landed catalog-versioning authority yet (DRG-421 is not landed) to validate it
     * against, so a blank value simply means the caller did not pin one.
     */
    @Column(name = "catalog_version")
    private String catalogVersion;

    /** Compiled configuration schema version ({@link com.opencrm.configuration.model.ConfigurationModel#schemaVersion()}) in effect when the session started. */
    @Column(name = "rule_set_version")
    private String ruleSetVersion;

    /** Entra {@code tid} of the caller who started the session, if any (same convention as {@code QuoteTenantResolver}). */
    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConfigurationStatus status = ConfigurationStatus.IN_PROGRESS;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "configuration_selections", 
                     joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "option_key")
    @Column(name = "option_value")
    @Builder.Default
    private Map<String, String> selections = new HashMap<>();
    
    @Column(name = "is_valid", nullable = false)
    @Builder.Default
    private boolean valid = false;
    
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    public void addSelection(String optionKey, String optionValue) {
        this.selections.put(optionKey, optionValue);
        this.valid = false; // Invalidate until re-validated
    }
    
    public void removeSelection(String optionKey) {
        this.selections.remove(optionKey);
        this.valid = false;
    }
    
    public void markAsValid() {
        this.valid = true;
        this.validationErrors = null;
    }
    
    public void markAsInvalid(String errors) {
        this.valid = false;
        this.validationErrors = errors;
    }
    
    public void complete() {
        if (!valid) {
            throw new IllegalStateException("Cannot complete invalid configuration");
        }
        this.status = ConfigurationStatus.COMPLETED;
        this.completedAt = Instant.now();
    }
}
