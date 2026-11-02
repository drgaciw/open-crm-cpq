package com.opencrm.crm.playbook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookStepWriteRequest {

    @NotBlank(message = "Stage is required")
    private String stage;

    /**
     * Order within the stage. Omitted on create or add appends after the current
     * last step of that stage. Omitted on update keeps the step's current order.
     */
    private Integer sortOrder;

    @NotEmpty(message = "A playbook step needs at least one question")
    @Valid
    private List<PlaybookQuestionWriteRequest> questions;
}
