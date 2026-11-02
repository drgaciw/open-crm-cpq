package com.opencrm.crm.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookQuestionDto {

    private Long id;
    private String prompt;
    private int sortOrder;
}
