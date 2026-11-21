package com.opencrm.ai.experiment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_experiment_variant", schema = "ai")
public class RuleExperimentVariant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "experiment_id", nullable = false)
    private UUID experimentId;

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "description")
    private String description;

    @Column(name = "rule_content", nullable = false, columnDefinition = "TEXT")
    private String ruleContent;

    @Column(name = "control_variant", nullable = false)
    private boolean controlVariant;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "overall_score", nullable = false)
    private double overallScore;

    @Column(name = "revenue_impact", nullable = false)
    private double revenueImpact;

    @Column(name = "conversion_rate_impact", nullable = false)
    private double conversionRateImpact;

    @Column(name = "average_execution_ms", nullable = false)
    private double averageExecutionMs;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "average_discount", nullable = false)
    private double averageDiscount;

    @Column(name = "total_revenue", nullable = false)
    private double totalRevenue;

    @Column(name = "customer_satisfaction", nullable = false)
    private double customerSatisfaction;

    @Column(name = "metrics_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metricsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RuleExperimentVariant() {
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
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

    public UUID getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(UUID experimentId) {
        this.experimentId = experimentId;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRuleContent() {
        return ruleContent;
    }

    public void setRuleContent(String ruleContent) {
        this.ruleContent = ruleContent;
    }

    public boolean isControlVariant() {
        return controlVariant;
    }

    public void setControlVariant(boolean controlVariant) {
        this.controlVariant = controlVariant;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public double getRevenueImpact() {
        return revenueImpact;
    }

    public void setRevenueImpact(double revenueImpact) {
        this.revenueImpact = revenueImpact;
    }

    public double getConversionRateImpact() {
        return conversionRateImpact;
    }

    public void setConversionRateImpact(double conversionRateImpact) {
        this.conversionRateImpact = conversionRateImpact;
    }

    public double getAverageExecutionMs() {
        return averageExecutionMs;
    }

    public void setAverageExecutionMs(double averageExecutionMs) {
        this.averageExecutionMs = averageExecutionMs;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = sampleCount;
    }

    public double getAverageDiscount() {
        return averageDiscount;
    }

    public void setAverageDiscount(double averageDiscount) {
        this.averageDiscount = averageDiscount;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getCustomerSatisfaction() {
        return customerSatisfaction;
    }

    public void setCustomerSatisfaction(double customerSatisfaction) {
        this.customerSatisfaction = customerSatisfaction;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
