package com.opencrm.crm.fields;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class FieldDefinitionResponse {
    Long id;
    String tenant;
    FieldEntityKind entity;
    String key;
    String label;
    FieldType type;
    StorageClass storageClass;
    boolean required;
    boolean showOnRecord;
    boolean showOnList;
    int position;
    boolean agentFilled;
    String agentBrief;
    Instant archivedAt;
    String createdBy;
    String updatedBy;
    long version;
    boolean standard;
    List<FieldOptionResponse> options;
}
