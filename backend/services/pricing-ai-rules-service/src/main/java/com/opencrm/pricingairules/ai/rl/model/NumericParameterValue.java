package com.opencrm.pricingairules.ai.rl.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Represents a numerical parameter value
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NumericParameterValue extends ParameterValue<Double> {

    private double minValue;
    private double maxValue;
    private double step;
    private String unit;
    
    /**
     * Create a new numerical parameter value
     * 
     * @param name The parameter name
     * @param description The parameter description
     * @param value The current value
     * @param minValue The minimum allowed value
     * @param maxValue The maximum allowed value
     * @param step The step size for increments
     * @param unit The unit of measurement
     * @param optimizable Whether this parameter can be optimized
     * @param sensitivity How sensitive the system is to changes in this parameter (0-1)
     */
    public NumericParameterValue(String name, String description, Double value, 
                               double minValue, double maxValue, double step, String unit,
                               boolean optimizable, double sensitivity) {
        super(name, description, value, optimizable, sensitivity, false);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.unit = unit;
    }
    
    @Override
    public ParameterValue<Double> copy() {
        NumericParameterValue copy = new NumericParameterValue();
        copy.setName(getName());
        copy.setDescription(getDescription());
        copy.setValue(getValue());
        copy.setOptimizable(isOptimizable());
        copy.setSensitivity(getSensitivity());
        copy.setModified(isModified());
        copy.setMinValue(minValue);
        copy.setMaxValue(maxValue);
        copy.setStep(step);
        copy.setUnit(unit);
        return copy;
    }
    
    @Override
    public boolean isValid() {
        return getValue() != null && getValue() >= minValue && getValue() <= maxValue;
    }
    
    /**
     * Increment the value by the step size
     * 
     * @return the new value
     */
    public double increment() {
        double newValue = Math.min(maxValue, getValue() + step);
        setValue(newValue);
        setModified(true);
        return newValue;
    }
    
    /**
     * Decrement the value by the step size
     * 
     * @return the new value
     */
    public double decrement() {
        double newValue = Math.max(minValue, getValue() - step);
        setValue(newValue);
        setModified(true);
        return newValue;
    }
    
    /**
     * Set a random value within the allowed range
     * 
     * @return the new value
     */
    public double randomValue() {
        double range = maxValue - minValue;
        int steps = (int) (range / step);
        int randomSteps = (int) (Math.random() * steps);
        double random = minValue + (randomSteps * step);
        setValue(random);
        setModified(true);
        return random;
    }
}
