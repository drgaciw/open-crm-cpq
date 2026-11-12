package com.opencrm.crm.fact;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One machine-written attribute (ADR-028). A newer fact supersedes the older one.
 * Agents do not update contact or organization columns except through this ledger.
 */
@Entity
@Table(name = "crm_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CrmFact extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    private FactSubjectType subjectType;

    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    @Column(name = "field_key", nullable = false, length = 128)
    private String fieldKey;

    @Column(name = "fact_value", nullable = false)
    private String factValue;

    @Column(name = "score", nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "band", length = 16)
    private FactBand band;

    @Column(name = "method", nullable = false, length = 64)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FactStatus status;

    @Column(name = "weight_catalog_version", nullable = false, length = 32)
    private String weightCatalogVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_kind", nullable = false, length = 16)
    private WriterKind writerKind;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "contradicting", nullable = false)
    private boolean contradicting;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @OneToMany(mappedBy = "fact", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<CrmFactEvidence> evidence = new ArrayList<>();

    public void addEvidence(CrmFactEvidence item) {
        if (evidence == null) {
            evidence = new ArrayList<>();
        }
        item.setFact(this);
        evidence.add(item);
    }
}
