package com.opencrm.crm.fields;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class FieldOptionResponse {
    Long id;
    String label;
    int position;
    Instant archivedAt;
    String createdBy;
    String updatedBy;
    long version;
}
