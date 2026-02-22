package com.opencrm.pricingairules.ai.rl.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Represents a boolean parameter value
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BooleanParameterValue extends ParameterValue<Boolean> {
    
    /**
     * Create a new boolean parameter value
     * 
     * @param name The parameter name
     * @param description The parameter description
     * @param value The current value
     * @param optimizable Whether this parameter can be optimized
     * @param sensitivity How sensitive the system is to changes in this parameter (0-1)
     */
    public BooleanParameterValue(String name, String description, Boolean value, 
                                boolean optimizable, double sensitivity) {
        super(name, description, value, optimizable, sensitivity, false);
    }
    
    @Override
    public ParameterValue<Boolean> copy() {
        BooleanParameterValue copy = new BooleanParameterValue();
        copy.setName(getName());
        copy.setDescription(getDescription());
        copy.setValue(getValue());
        copy.setOptimizable(isOptimizable());
        copy.setSensitivity(getSensitivity());
        copy.setModified(isModified());
        return copy;
    }
    
    @Override
    public boolean isValid() {
        return getValue() != null;
    }
    
    /**
     * Toggle the boolean value
     * 
     * @return the new value
     */
    public boolean toggle() {
        boolean newValue = !getValue();
        setValue(newValue);
        setModified(true);
        return newValue;
    }
    
    /**
     * Get a random boolean value
     * 
     * @return a random boolean
     */
    public boolean randomValue() {
        boolean random = Math.random() > 0.5;
        setValue(random);
        setModified(true);
        return random;
    }
}
