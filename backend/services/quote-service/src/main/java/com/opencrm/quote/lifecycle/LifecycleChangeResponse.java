package com.opencrm.quote.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleChangeResponse {

    private UUID id;
    private String intent;
    private String initiationTrigger;
    private String status;
    private int revision;
    private Long version;
    private LocalDate actionDate;
    private LocalDate contractEffectiveDate;
    private UUID contractId;
    private String currencyCode;
    private String outputMode;
    private String createdBy;
    private List<ChangeLineResponse> lines;
}
