package com.opencrm.product.option;

import com.opencrm.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Validates option-group min/max selection constraints at write time.
 */
@Component
public class OptionGroupConstraintValidator {

    public void validateGroupDefinition(int minSelections, Integer maxSelections) {
        if (minSelections < 0) {
            throw new BusinessException("Minimum selections cannot be negative", "INVALID_OPTION_GROUP_MIN");
        }
        if (maxSelections != null && maxSelections < minSelections) {
            throw new BusinessException(
                    "Maximum selections cannot be less than minimum selections",
                    "INVALID_OPTION_GROUP_MAX"
            );
        }
    }

    public void validateOptionCount(ProductOptionGroup group) {
        int optionCount = group.getOptions().size();
        if (group.getMaxSelections() != null && optionCount > group.getMaxSelections()) {
            throw new BusinessException(
                    "Option group allows at most " + group.getMaxSelections() + " options",
                    "OPTION_GROUP_MAX_OPTIONS_EXCEEDED"
            );
        }
    }
}
