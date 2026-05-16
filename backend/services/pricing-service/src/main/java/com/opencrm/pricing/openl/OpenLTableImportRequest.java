package com.opencrm.pricing.openl;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OpenLTableImportRequest {

    String tableName;

    @NotBlank
    String content;

    String contentType;
}
