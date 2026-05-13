package com.opencrm.configuration.session;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of a valid configuration after it is committed.
 */
@Entity
@Table(name = "committed_configurations", indexes = {
    @Index(name = "idx_committed_config_session", columnList = "session_id"),
    @Index(name = "idx_committed_config_product", columnList = "product_id"),
    @Index(name = "idx_committed_config_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CommittedConfiguration extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "committed_configuration_selections",
            joinColumns = @JoinColumn(name = "configuration_id"))
    @MapKeyColumn(name = "option_key")
    @Column(name = "option_value")
    @Builder.Default
    private Map<String, String> selections = new HashMap<>();

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    public static CommittedConfiguration fromSession(ConfigurationSession session) {
        return CommittedConfiguration.builder()
                .sessionId(session.getId())
                .productId(session.getProductId())
                .userId(session.getUserId())
                .selections(new HashMap<>(session.getSelections()))
                .committedAt(session.getCompletedAt())
                .build();
    }
}
