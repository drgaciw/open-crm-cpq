package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Event published when a price book version is published (DRG-431 / BC-03-01).
 * Topic: cpq.pricebook.published
 * Publisher: pricebook-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceBookPublishedEvent {
    private Long priceBookId;
    private String priceBookUuid;
    private Integer versionNumber;
    private String currency;
    private String region;
    private Long costBookId;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private Instant publishedAt;
}
