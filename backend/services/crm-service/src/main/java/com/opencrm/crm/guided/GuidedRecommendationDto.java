package com.opencrm.crm.guided;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One advisory option. {@code advisory} is always true. Constraints stay with the configuration engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidedRecommendationDto {

    private String productId;
    private String optionKey;
    private String optionValue;
    private String rationale;
    private boolean advisory;
}
