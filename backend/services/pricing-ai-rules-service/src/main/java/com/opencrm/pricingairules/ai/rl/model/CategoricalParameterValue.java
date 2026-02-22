package com.opencrm.pricingairules.ai.rl.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a categorical parameter value with a set of possible values
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CategoricalParameterValue extends ParameterValue<String> {
    
    private List<String> possibleValues;
    
    /**
     * Create a new categorical parameter value
     * 
     * @param name The parameter name
     * @param description The parameter description
     * @param value The current value
     * @param possibleValues The list of possible values
     * @param optimizable Whether this parameter can be optimized
     * @param sensitivity How sensitive the system is to changes in this parameter (0-1)
     */
    public CategoricalParameterValue(String name, String description, String value, 
                                    List<String> possibleValues, boolean optimizable, 
                                    double sensitivity) {
        super(name, description, value, optimizable, sensitivity, false);
        this.possibleValues = new ArrayList<>(possibleValues);
    }
    
    @Override
    public ParameterValue<String> copy() {
        CategoricalParameterValue copy = new CategoricalParameterValue();
        copy.setName(getName());
        copy.setDescription(getDescription());
        copy.setValue(getValue());
        copy.setPossibleValues(new ArrayList<>(possibleValues));
        copy.setOptimizable(isOptimizable());
        copy.setSensitivity(getSensitivity());
        copy.setModified(isModified());
        return copy;
    }
    
    @Override
    public boolean isValid() {
        String value = getValue();
        return value != null && possibleValues.contains(value);
    }
    
    /**
     * Get a random value from the possible values
     * 
     * @return a randomly selected value from the possible values
     */
    public String randomValue() {
        if (possibleValues == null || possibleValues.isEmpty()) {
            return null;
        }
        
        int index = (int) (Math.random() * possibleValues.size());
        String random = possibleValues.get(index);
        setValue(random);
        setModified(true);
        return random;
    }
    
    /**
     * Cycle to the next value in the list of possible values
     * 
     * @return the next value in the list
     */
    public String nextValue() {
        if (possibleValues == null || possibleValues.isEmpty()) {
            return null;
        }
        
        String currentValue = getValue();
        int currentIndex = possibleValues.indexOf(currentValue);
        int nextIndex = (currentIndex + 1) % possibleValues.size();
        
        String next = possibleValues.get(nextIndex);
        setValue(next);
        setModified(true);
        return next;
    }
    
    /**
     * Get the allowed values for this categorical parameter
     * @return the list of allowed values
     */
    public List<String> getAllowedValues() {
        return this.possibleValues;
    }
}
