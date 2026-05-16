package com.opencrm.pricing.openl;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OpenLTableValidationResult {

    boolean valid;
    OpenLTableValidationStatus status;
    String tableName;

    @Singular
    List<String> issues;
}
