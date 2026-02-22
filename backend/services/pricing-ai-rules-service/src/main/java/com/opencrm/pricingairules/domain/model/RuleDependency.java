package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "rule_dependency", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_dependency_seq")
    @SequenceGenerator(name = "rule_dependency_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @ManyToOne
    @JoinColumn(name = "dependent_rule_id", nullable = false)
    private Rule dependentRule;

    @Column(name = "dependency_type", nullable = false)
    private String dependencyType;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}
