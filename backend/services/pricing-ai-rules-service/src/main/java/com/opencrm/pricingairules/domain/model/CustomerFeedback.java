package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Represents customer feedback on a quote or order
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFeedback {

    @Column(name = "satisfaction_score")
    private Double satisfactionScore;
    
    @Column(name = "feedback_text", length = 1000)
    private String feedbackText;
    
    @Column(name = "feedback_date")
    private ZonedDateTime feedbackDate;
    
    @Column(name = "would_recommend")
    private Boolean wouldRecommend;
    
    @Column(name = "feedback_source", length = 50)
    private String feedbackSource;
}
