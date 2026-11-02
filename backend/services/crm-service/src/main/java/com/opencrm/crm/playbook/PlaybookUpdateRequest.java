package com.opencrm.crm.playbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Name change. Renaming does not increment the definition version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookUpdateRequest {

    @NotBlank(message = "Playbook name is required")
    @Size(max = 255, message = "Playbook name must not exceed 255 characters")
    private String name;
}
