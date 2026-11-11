package com.opencrm.document.dto;

public record SelectedClauseDto(Long clauseId, Long versionId, String clauseKey, int versionNumber,
        String title, String bodyContent, int sortOrder) {}
