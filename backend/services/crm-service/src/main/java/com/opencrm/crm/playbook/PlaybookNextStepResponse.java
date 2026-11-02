package com.opencrm.crm.playbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Next incomplete step for one stage of an existing playbook.
 * <p>
 * When the playbook exists and the stage has no remaining incomplete step
 * (no steps were defined, or every step id for that stage was listed in
 * {@code completedStepIds}), HTTP 200 is returned and {@code step} is JSON null.
 * A missing playbook is HTTP 404 and does not use this payload.
 * <p>
 * {@code version} is the playbook's local definition integer. BC-18 is not landed,
 * so this is not a promoted document revision. Steps are not filtered by buying role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookNextStepResponse {

    private Long playbookId;
    private Integer version;
    private String stage;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private PlaybookStepDto step;
}
