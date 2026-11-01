package com.opencrm.quote.lifecycle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceLifecycleChangeRequest {

    @NotNull
    @Min(0)
    private Long version;
}
