package com.opencrm.quote.lifecycle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateLifecycleChangeRequest {

    @NotBlank
    private String intent;

    @NotBlank
    private String initiationTrigger;

    private String requestIdentifier;

    /**
     * Ignored. The tenant is the caller's token {@code tid}. A value here is stored only in the
     * rejection inputs so an audit can show what the body claimed.
     */
    private String tid;

    @NotNull
    private LocalDate actionDate;

    @NotNull
    private LocalDate contractEffectiveDate;

    @NotNull
    private UUID contractId;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    private String outputMode;

    @NotEmpty
    private List<@Valid ChangeLineWrite> lines;
}
