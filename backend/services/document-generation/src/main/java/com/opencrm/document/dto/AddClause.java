package com.opencrm.document.dto;

import jakarta.validation.constraints.*;

public record AddClause(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_.-]+") String clauseKey,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100000) String bodyContent,
        @NotBlank @Size(max = 100) String category,
        ClauseCondition condition,
        int sortOrder) {}
