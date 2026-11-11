package com.opencrm.configuration.session;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration session entity - maintains session-safe configuration state.
 * Ensures only valid configuration combinations pass to pricing/quote flows.
 */
@Entity
@Table(name = "configuration_sessions", indexes = {
    @Index(name = "idx_config_session_product", columnList = "product_id"),
    @Index(name = "idx_config_session_user", columnList = "user_id"),
    @Index(name = "idx_config_session_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfigurationSession extends BaseEntity {
    
    @Column(name = "product_id", nullable = false)
    private String productId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
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

    /** Selection keys inserted by ApplyDefaults; explicit seller edits remove the marker. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "configuration_default_selections", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "option_key", nullable = false)
    @Builder.Default
    private Set<String> defaultedSelectionKeys = new HashSet<>();
    
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
        this.defaultedSelectionKeys.remove(optionKey);
        this.valid = false; // Invalidate until re-validated
    }
    
    public void removeSelection(String optionKey) {
        this.selections.remove(optionKey);
        this.defaultedSelectionKeys.remove(optionKey);
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
