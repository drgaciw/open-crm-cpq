package com.opencrm.crm.playbook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookCreateRequest {

    @NotBlank(message = "Playbook name is required")
    @Size(max = 255, message = "Playbook name must not exceed 255 characters")
    private String name;

    /**
     * Initial steps. The playbook is still definition version 1 after they are
     * stored. Later step or question changes increment the version.
     */
    @Valid
    private List<PlaybookStepWriteRequest> steps;
}
