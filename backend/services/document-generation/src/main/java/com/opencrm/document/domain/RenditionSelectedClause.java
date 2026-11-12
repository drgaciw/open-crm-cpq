package com.opencrm.document.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** Immutable body snapshot: historical renditions never reselect or resolve mutable library data. */
@Entity @Table(name = "rendition_selected_clauses")
@Getter @Setter @NoArgsConstructor @SuperBuilder
public class RenditionSelectedClause extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100)
    private String tenantId;
    @Column(name = "rendition_id", nullable = false, updatable = false)
    private Long renditionId;
    @Column(name = "clause_id", nullable = false, updatable = false)
    private Long clauseId;
    @Column(name = "clause_version_id", nullable = false, updatable = false)
    private Long clauseVersionId;
    @Column(name = "clause_key", nullable = false, updatable = false, length = 100)
    private String clauseKey;
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
    @Column(nullable = false, updatable = false)
    private String title;
    @Column(name = "body_content", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String bodyContent;
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;
}
