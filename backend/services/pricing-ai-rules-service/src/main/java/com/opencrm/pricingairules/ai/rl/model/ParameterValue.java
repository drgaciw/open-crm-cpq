package com.opencrm.pricingairules.ai.rl.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for all parameter value types
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = NumericalParameterValue.class, name = "numerical"),
    @JsonSubTypes.Type(value = CategoricalParameterValue.class, name = "categorical"),
    @JsonSubTypes.Type(value = BooleanParameterValue.class, name = "boolean")
})
public abstract class ParameterValue<T> {
    
    private String name;
    private String description;
    private T value;
    
    // For tracking optimization
    private boolean optimizable;
    private double sensitivity;
    private boolean modified;
    
    /**
     * Create a copy of this parameter value
     * @return a new copy of this parameter value
     */
    public abstract ParameterValue<T> copy();
    
    /**
     * Check if this parameter value is valid
     * @return true if the parameter value is valid
     */
    public abstract boolean isValid();
    
    /**
     * Get the importance/sensitivity of this parameter
     * @return the importance value (0.0 to 1.0)
     */
    public double getImportance() {
        return this.sensitivity;
    }
}
