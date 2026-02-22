package com.opencrm.quote.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteDocumentDto {

    private Long id;
    private String uuid;
    private Long quoteId;
    private DocumentType documentType;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String generatedBy;
    private Instant createdAt;
}
