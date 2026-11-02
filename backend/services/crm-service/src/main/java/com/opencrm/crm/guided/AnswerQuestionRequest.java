package com.opencrm.crm.guided;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One needs answer. The question key and value are matched against the deterministic catalog.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerQuestionRequest {

    @NotBlank(message = "Question key is required")
    @Size(max = 64, message = "Question key is too long")
    private String questionKey;

    @NotBlank(message = "Answer is required")
    @Size(max = 255, message = "Answer is too long")
    private String answer;
}
