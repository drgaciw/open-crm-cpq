package com.opencrm.document.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "clause_definitions", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "clause_key"}))
@Getter @Setter @NoArgsConstructor @SuperBuilder
public class ClauseDefinition extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100)
    private String tenantId;
    @Column(name = "clause_key", nullable = false, updatable = false, length = 100)
    private String clauseKey;
}
