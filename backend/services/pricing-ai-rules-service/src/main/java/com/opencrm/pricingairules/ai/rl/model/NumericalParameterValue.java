package com.opencrm.pricingairules.ai.rl.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Represents a numerical parameter value with constraints
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NumericalParameterValue extends ParameterValue<Double> {
    
    private double minValue;
    private double maxValue;
    private double stepSize;
    private String unit;
    
    /**
     * Create a new numerical parameter value
     * 
     * @param name The parameter name
     * @param description The parameter description
     * @param value The current value
     * @param minValue The minimum allowed value
     * @param maxValue The maximum allowed value
     * @param stepSize The step size for optimization
     * @param unit The unit of measurement
     * @param optimizable Whether this parameter can be optimized
     * @param sensitivity How sensitive the system is to changes in this parameter (0-1)
     */
    public NumericalParameterValue(String name, String description, Double value, 
                                  double minValue, double maxValue, double stepSize, 
                                  String unit, boolean optimizable, double sensitivity) {
        super(name, description, value, optimizable, sensitivity, false);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.stepSize = stepSize;
        this.unit = unit;
    }
    
    @Override
    public ParameterValue<Double> copy() {
        NumericalParameterValue copy = new NumericalParameterValue();
        copy.setName(getName());
        copy.setDescription(getDescription());
        copy.setValue(getValue());
        copy.setMinValue(minValue);
        copy.setMaxValue(maxValue);
        copy.setStepSize(stepSize);
        copy.setUnit(unit);
        copy.setOptimizable(isOptimizable());
        copy.setSensitivity(getSensitivity());
        copy.setModified(isModified());
        return copy;
    }
    
    @Override
    public boolean isValid() {
        Double value = getValue();
        return value != null && value >= minValue && value <= maxValue;
    }
    
    /**
     * Adjust the parameter value by the given delta, ensuring it stays within bounds
     * 
     * @param delta The amount to adjust by
     * @return the new value
     */
    public double adjust(double delta) {
        double newValue = getValue() + delta;
        newValue = Math.max(minValue, Math.min(maxValue, newValue));
        setValue(newValue);
        setModified(true);
        return newValue;
    }
    
    /**
     * Get a random value within the parameter's range
     * 
     * @return a random value within the allowed range
     */
    public double randomValue() {
        double range = maxValue - minValue;
        double random = Math.random() * range + minValue;
        
        // If we have a step size, round to the nearest step
        if (stepSize > 0) {
            random = Math.round(random / stepSize) * stepSize;
        }
        
        setValue(random);
        setModified(true);
        return random;
    }
}
