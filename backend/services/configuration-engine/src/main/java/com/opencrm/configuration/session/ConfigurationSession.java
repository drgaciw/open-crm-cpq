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
     * Caller-supplied catalog version the session is pinned against at StartSession (DRG-424).
     * Prefer the {@code versionId} (catalog_versions.uuid) from PublishCatalog / CatalogPublished (DRG-421).
     * Recorded as given; optional remote pin resolution is via product-service
     * {@code GET /api/v1/catalog/versions/{versionId}/pin}. A blank value means the caller did not pin one.
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

    /**
     * Deterministic {@code ConfigurationHash} (DRG-428 / BC-02-05), set by {@link #freeze(String)}. Computed
     * by {@code ConfigurationHasher} over the selections, {@link #catalogVersion} and {@link #ruleSetVersion}
     * pinned on this session — same inputs always produce the same hash.
     */
    @Column(name = "configuration_hash", length = 64)
    private String configurationHash;

    /** Set by {@link #freeze(String)}; {@code null} until the configuration is frozen. */
    @Column(name = "frozen_at")
    private Instant frozenAt;

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

    /**
     * FreezeConfiguration (DRG-428 / BC-02-05): makes this configuration immutable, carrying the
     * pre-computed {@code configurationHash}. Callers must check {@link #isValid()} themselves first
     * (consistent with {@link #complete()}) since the hash must be computed over this session's state
     * before the entity is mutated.
     */
    public void freeze(String configurationHash) {
        if (!valid) {
            throw new IllegalStateException("Cannot freeze invalid configuration");
        }
        this.configurationHash = configurationHash;
        this.status = ConfigurationStatus.FROZEN;
        this.frozenAt = Instant.now();
    }
}
