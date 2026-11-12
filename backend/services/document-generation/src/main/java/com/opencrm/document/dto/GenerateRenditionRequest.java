package com.opencrm.document.dto;

import com.opencrm.document.domain.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record GenerateRenditionRequest(@NotBlank @Size(max = 255) String quoteId,
        @NotBlank @Size(max = 255) String customerId, @NotNull DocumentType documentType,
        @NotNull @Valid ClauseSelectionContext context) {}
