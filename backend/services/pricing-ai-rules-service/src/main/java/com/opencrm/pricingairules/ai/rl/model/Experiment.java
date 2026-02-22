package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents an experiment with multiple rule variants
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experiment {
    
    private String id;
    private String name;
    private String description;
    private String ruleId;
    private String ruleName;
    
    // Variants being tested
    private List<Variant> variants;
    
    // Experiment configuration
    private ExplorationStrategy explorationStrategy;
    private ExperimentConfig config;
    
    // Experiment status
    private String status; // DRAFT, RUNNING, PAUSED, COMPLETED, FAILED
    private Instant startDate;
    private Instant endDate;
    private Instant lastUpdated;
    private String createdBy;
    
    // Results
    private Variant winningVariant;
    private String conclusion;
    private List<String> insights;
    
    /**
     * Generate a new ID for this experiment
     * @return this instance for chaining
     */
    public Experiment generateId() {
        this.id = UUID.randomUUID().toString();
        return this;
    }
    
    /**
     * Get the experiment ID (alias for getId for compatibility)
     * @return the experiment ID
     */
    public String getExperimentId() {
        return this.id;
    }
    
    /**
     * Get the variant count
     * @return the number of variants in this experiment
     */
    public int getVariantCount() {
        return variants != null ? variants.size() : 0;
    }
    
    /**
     * Get the winning variant ID
     * @return the ID of the winning variant, or null if none exists
     */
    public String getWinningVariantId() {
        return winningVariant != null ? winningVariant.getId() : null;
    }
    
    /**
     * Add a variant to this experiment
     * 
     * @param variant The variant to add
     * @return this instance for chaining
     */
    public Experiment addVariant(Variant variant) {
        if (variants == null) {
            variants = new ArrayList<>();
        }
        variant.setExperimentId(this.id);
        variants.add(variant);
        return this;
    }
    
    /**
     * Set one variant as the control variant
     * 
     * @param variantId The ID of the variant to set as control
     * @return this instance for chaining
     */
    public Experiment setControlVariant(String variantId) {
        if (variants != null) {
            for (Variant variant : variants) {
                variant.setControl(variant.getId().equals(variantId));
            }
        }
        return this;
    }
    
    /**
     * Get the control variant
     * 
     * @return the control variant, or null if none exists
     */
    public Variant getControlVariant() {
        if (variants != null) {
            return variants.stream()
                    .filter(Variant::isControl)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
    
    /**
     * Get active variants
     * 
     * @return a list of active variants
     */
    public List<Variant> getActiveVariants() {
        if (variants != null) {
            return variants.stream()
                    .filter(v -> "ACTIVE".equals(v.getStatus()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
    
    /**
     * Get the best performing variant
     * 
     * @return the variant with the highest overall score
     */
    public Variant getBestVariant() {
        if (variants != null && !variants.isEmpty()) {
            return variants.stream()
                    .max((v1, v2) -> Double.compare(v1.getOverallScore(), v2.getOverallScore()))
                    .orElse(null);
        }
        return null;
    }
    
    /**
     * Start the experiment
     * 
     * @return this instance for chaining
     */
    public Experiment start() {
        this.startDate = Instant.now();
        this.status = "RUNNING";
        this.lastUpdated = Instant.now();
        return this;
    }
    
    /**
     * Pause the experiment
     * 
     * @return this instance for chaining
     */
    public Experiment pause() {
        this.status = "PAUSED";
        this.lastUpdated = Instant.now();
        return this;
    }
    
    /**
     * Resume the experiment
     * 
     * @return this instance for chaining
     */
    public Experiment resume() {
        this.status = "RUNNING";
        this.lastUpdated = Instant.now();
        return this;
    }
    
    /**
     * Complete the experiment and declare a winner
     * 
     * @param conclusion The conclusion text
     * @param insights List of insights gained
     * @return this instance for chaining
     */
    public Experiment complete(String conclusion, List<String> insights) {
        this.endDate = Instant.now();
        this.status = "COMPLETED";
        this.winningVariant = getBestVariant();
        this.conclusion = conclusion;
        this.insights = insights;
        this.lastUpdated = Instant.now();
        return this;
    }
}
