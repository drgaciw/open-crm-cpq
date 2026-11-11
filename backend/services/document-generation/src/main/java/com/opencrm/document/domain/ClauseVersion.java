package com.opencrm.document.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.Instant;

@Entity
@Table(name = "clause_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"clause_id", "version_number"}))
@Getter @Setter @NoArgsConstructor @SuperBuilder
public class ClauseVersion extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100)
    private String tenantId;
    @Column(name = "clause_id", nullable = false, updatable = false)
    private Long clauseId;
    @Column(name = "clause_key", nullable = false, updatable = false, length = 100)
    private String clauseKey;
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
    @Column(nullable = false, updatable = false)
    private String title;
    @Column(name = "body_content", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String bodyContent;
    @Column(nullable = false, updatable = false, length = 100)
    private String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ClauseStatus status;
    @Column(name = "approved_by", length = 255)
    private String approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "condition_definition", updatable = false, columnDefinition = "TEXT")
    private String conditionDefinition;
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;
}
