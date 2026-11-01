package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published when an organization (the account of BC-12-01) is created.
 * Topic: {@code cpq.crm.organization.created}. This is the AccountCreated event.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrganizationCreatedEvent {

    private String organizationId;
    private String name;
    private String type;
    private Instant createdAt;
}
