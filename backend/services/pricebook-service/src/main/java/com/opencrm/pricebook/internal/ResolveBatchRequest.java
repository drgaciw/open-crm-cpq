package com.opencrm.pricebook.internal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveBatchRequest {

    private String region;

    @NotNull
    private List<String> productExternalIds;
}
