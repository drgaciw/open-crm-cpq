package com.opencrm.crm.guided;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidedQuestionDto {

    private String key;
    private String prompt;
    private List<String> allowedValues;
}
