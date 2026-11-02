package com.opencrm.crm.guided;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidedAnswerDto {

    private String questionKey;
    private String answer;
}
