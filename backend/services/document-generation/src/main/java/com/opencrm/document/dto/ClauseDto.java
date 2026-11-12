package com.opencrm.document.dto;

import com.opencrm.document.domain.ClauseStatus;
import java.time.Instant;

public record ClauseDto(Long clauseId, Long versionId, String clauseKey, int versionNumber,
        String title, String bodyContent, String category, ClauseStatus status,
        String approvedBy, Instant approvedAt, ClauseCondition condition, int sortOrder) {}
