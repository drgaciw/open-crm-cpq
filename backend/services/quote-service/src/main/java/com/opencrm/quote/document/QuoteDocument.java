package com.opencrm.quote.document;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "quote_documents", indexes = {
    @Index(name = "idx_document_quote_id", columnList = "quote_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuoteDocument extends BaseEntity {

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    @Builder.Default
    private DocumentType documentType = DocumentType.PROPOSAL;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 100)
    @Builder.Default
    private String contentType = "application/pdf";

    @Column(name = "content")
    private byte[] content;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "generated_by", length = 100)
    private String generatedBy;
}
