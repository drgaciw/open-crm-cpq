package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published when {@code AssignRole} (DRG-307 / BC-19-03) gives a principal a role.
 * Topic: {@code cpq.authorization.role.assigned}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleAssignedEvent {

    private String principalId;
    private String roleName;
    private String assignedBy;
    private Instant assignedAt;
}
