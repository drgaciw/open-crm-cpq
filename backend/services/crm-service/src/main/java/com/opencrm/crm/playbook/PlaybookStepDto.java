package com.opencrm.crm.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookStepDto {

    private Long id;
    private String stage;
    private int sortOrder;
    private List<PlaybookQuestionDto> questions;
}
