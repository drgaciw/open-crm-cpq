package com.opencrm.crm.guided;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Guided selling session as returned by the API. Recommendations, when present, are advisory.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidedSessionDto {

    private Long id;
    private String uuid;
    private Long dealId;
    private GuidedSessionStatus status;
    private String sellerActor;
    private String productId;
    private Long configurationSessionId;
    private boolean recordedLocally;
    private boolean advisory;
    private String constraintNote;
    private String rationale;
    private Instant derivedAt;
    private List<GuidedAnswerDto> answers;
    private Map<String, String> selections;
    private List<GuidedRecommendationDto> recommendations;
}
