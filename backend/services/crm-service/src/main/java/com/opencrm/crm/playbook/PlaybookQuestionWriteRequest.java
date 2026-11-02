package com.opencrm.crm.playbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookQuestionWriteRequest {

    @NotBlank(message = "Question prompt is required")
    @Size(max = 4000, message = "Question prompt must not exceed 4000 characters")
    private String prompt;

    private Integer sortOrder;
}
