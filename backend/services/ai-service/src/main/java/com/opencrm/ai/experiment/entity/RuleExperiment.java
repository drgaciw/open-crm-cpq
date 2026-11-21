package com.opencrm.ai.experiment.entity;

import com.opencrm.ai.experiment.RuleExperimentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_experiment", schema = "ai")
public class RuleExperiment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "rule_set_id")
    private Long ruleSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RuleExperimentStatus status;

    @Column(name = "control_traffic_pct", nullable = false)
    private double controlTrafficPct = 0.20;

    @Column(name = "winning_variant_id")
    private UUID winningVariantId;

    @Column(name = "conclusion")
    private String conclusion;

    @Column(name = "applied_draft_rule_id")
    private Long appliedDraftRuleId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_by")
    private String appliedBy;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RuleExperiment() {
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = RuleExperimentStatus.CREATED;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Long getRuleSetId() {
        return ruleSetId;
    }

    public void setRuleSetId(Long ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    public RuleExperimentStatus getStatus() {
        return status;
    }

    public void setStatus(RuleExperimentStatus status) {
        this.status = status;
    }

    public double getControlTrafficPct() {
        return controlTrafficPct;
    }

    public void setControlTrafficPct(double controlTrafficPct) {
        this.controlTrafficPct = controlTrafficPct;
    }

    public UUID getWinningVariantId() {
        return winningVariantId;
    }

    public void setWinningVariantId(UUID winningVariantId) {
        this.winningVariantId = winningVariantId;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public Long getAppliedDraftRuleId() {
        return appliedDraftRuleId;
    }

    public void setAppliedDraftRuleId(Long appliedDraftRuleId) {
        this.appliedDraftRuleId = appliedDraftRuleId;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public void setAppliedBy(String appliedBy) {
        this.appliedBy = appliedBy;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
