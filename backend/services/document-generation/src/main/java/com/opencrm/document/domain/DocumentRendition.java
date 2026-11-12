package com.opencrm.document.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity @Table(name = "document_renditions")
@Getter @Setter @NoArgsConstructor @SuperBuilder
public class DocumentRendition extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100)
    private String tenantId;
    @Column(name = "job_id", nullable = false, unique = true, updatable = false)
    private Long jobId;
    @Column(name = "selection_context", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String selectionContext;
}
