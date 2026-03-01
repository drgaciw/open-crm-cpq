package com.opencrm.configuration.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Reusable configuration template for quick product setup.
 */
@Entity
@Table(name = "configuration_templates", indexes = {
    @Index(name = "idx_cfg_tmpl_code", columnList = "template_code"),
    @Index(name = "idx_cfg_tmpl_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfigurationTemplate extends BaseEntity {

    @Column(name = "template_code", nullable = false, unique = true, length = 100)
    private String templateCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConfigurationStatus status = ConfigurationStatus.DRAFT;

    /** JSON blob containing the template definition (options, rules, defaults) */
    @Column(name = "template_data", columnDefinition = "TEXT", nullable = false)
    private String templateData;

    @Column(name = "source_configuration_id")
    private Long sourceConfigurationId;
}
