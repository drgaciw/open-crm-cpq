package com.opencrm.dealdesk.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectDealRequestDto {

    @NotBlank(message = "rejection notes are required")
    private String notes;
}
