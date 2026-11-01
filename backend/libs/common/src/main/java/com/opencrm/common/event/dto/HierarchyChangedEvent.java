package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Published when an organization's parent link is created or changed.
 * Topic: {@code cpq.crm.account.hierarchy-changed}.
 *
 * <p>{@code ancestorIds} lists account UUIDs from the immediate parent up to the root so a later
 * eligibility or pricing consumer can inherit without walking the hierarchy itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HierarchyChangedEvent {

    private String organizationId;
    private Long parentOrganizationId;
    private String parentAccountId;
    private Long previousParentOrganizationId;
    private List<String> ancestorIds;
    private Instant changedAt;
}
