package com.opencrm.crm.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Playbook read model. {@code version} is {@link Playbook#getDefinitionVersion()},
 * the local integer. It is not the optimistic-lock column and not a BC-18 revision.
 * Steps are ordered by pipeline stage, then {@code sortOrder}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookDto {

    private Long id;
    private String uuid;
    private String name;
    private Integer version;
    private List<PlaybookStepDto> steps;
    private Instant createdAt;
    private Instant updatedAt;
}
