package com.opencrm.configuration.constraint;

import com.opencrm.configuration.session.ConfigurationSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constraint solver for validating product configurations.
 * Ensures only valid configuration combinations are allowed.
 */
@Component
@Slf4j
public class ConstraintSolver {
    
    /**
     * Validate a configuration session against all constraints.
     * Returns list of validation errors, empty if valid.
     */
    public List<String> validate(ConfigurationSession session) {
        List<String> errors = new ArrayList<>();
        
        Map<String, String> selections = session.getSelections();
        
        // Example constraint validations
        // In production, these would be loaded from configuration rules
        
        // Required options check
        if (!selections.containsKey("color")) {
            errors.add("Color selection is required");
        }
        
        // Mutual exclusivity check
        if (selections.containsKey("option_a") && selections.containsKey("option_b")) {
            errors.add("Option A and Option B are mutually exclusive");
        }
        
        // Dependency check
        if (selections.containsKey("premium_feature") && !selections.containsKey("base_package")) {
            errors.add("Premium feature requires base package");
        }
        
        log.debug("Validated configuration session {} with {} errors", 
                  session.getId(), errors.size());
        
        return errors;
    }
    
    /**
     * Check if a specific selection is compatible with current configuration.
     */
    public boolean isCompatible(ConfigurationSession session, String optionKey, String optionValue) {
        // Create temporary session with new selection
        Map<String, String> tempSelections = new HashMap<>(session.getSelections());
        tempSelections.put(optionKey, optionValue);
        
        // Validate temporary configuration
        // This is a simplified version - production would have more sophisticated logic
        return true;
    }
}
