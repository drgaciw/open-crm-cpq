package com.opencrm.pricebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PriceBookDto {

    private Long id;
    private String uuid;
    private String name;
    private String region;
    private String currency;
    private Boolean isDefault;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private PriceBookStatus status;
    private Integer versionNumber;
    private Instant publishedAt;
    private Long costBookId;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
