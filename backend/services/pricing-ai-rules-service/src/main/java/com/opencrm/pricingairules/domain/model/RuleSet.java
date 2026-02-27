package com.opencrm.pricingairules.domain.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule_set", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RuleSet extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "effective_date")
    private ZonedDateTime effectiveDate;

    @Column(name = "expiration_date")
    private ZonedDateTime expirationDate;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Rule> rules = new ArrayList<>();
}
