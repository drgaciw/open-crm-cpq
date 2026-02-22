package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a goal with an associated weight for multi-objective optimization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightedGoal {
    
    /**
     * The optimization goal
     */
    private OptimizationGoal goal;
    
    /**
     * The weight of this goal (0.0 - 1.0)
     */
    private double weight;
}
