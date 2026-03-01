package com.opencrm.configuration.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for product configurations.
 * Represents a configurable product with its options and rules.
 */
@Entity
@Table(name = "product_configurations", indexes = {
    @Index(name = "idx_config_product_id", columnList = "product_id"),
    @Index(name = "idx_config_code", columnList = "code"),
    @Index(name = "idx_config_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductConfiguration extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConfigurationStatus status = ConfigurationStatus.DRAFT;

    @Column(name = "max_options")
    private Integer maxOptions;

    @Column(name = "min_options")
    private Integer minOptions;

    @Column(name = "allow_duplicates", nullable = false)
    @Builder.Default
    private boolean allowDuplicates = false;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConfigurationOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConfigurationRule> rules = new ArrayList<>();

    public void addOption(ConfigurationOption option) {
        options.add(option);
        option.setConfiguration(this);
    }

    public void removeOption(ConfigurationOption option) {
        options.remove(option);
        option.setConfiguration(null);
    }

    public void addRule(ConfigurationRule rule) {
        rules.add(rule);
        rule.setConfiguration(this);
    }

    public void removeRule(ConfigurationRule rule) {
        rules.remove(rule);
        rule.setConfiguration(null);
    }

    public void activate() {
        if (this.status == ConfigurationStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot activate an archived configuration");
        }
        this.status = ConfigurationStatus.ACTIVE;
    }

    public void deprecate() {
        if (this.status != ConfigurationStatus.ACTIVE) {
            throw new IllegalStateException("Only active configurations can be deprecated");
        }
        this.status = ConfigurationStatus.DEPRECATED;
    }

    public void archive() {
        if (this.status == ConfigurationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot archive an active configuration; deprecate it first");
        }
        this.status = ConfigurationStatus.ARCHIVED;
    }
}
