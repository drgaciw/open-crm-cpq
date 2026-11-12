package com.opencrm.document.dto;

import com.opencrm.document.domain.JobStatus;
import java.util.List;

public record DocumentRenditionDto(Long id, Long jobId, String quoteId, JobStatus status,
        String documentUrl, List<SelectedClauseDto> selectedClauses) {}
