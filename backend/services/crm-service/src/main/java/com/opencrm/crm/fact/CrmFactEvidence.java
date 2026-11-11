package com.opencrm.crm.fact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One evidence item stored with the weight the catalog assigned at observation time.
 */
@Entity
@Table(name = "crm_fact_evidence")
@Getter
@Setter
@NoArgsConstructor
public class CrmFactEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fact_id", nullable = false)
    private CrmFact fact;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 64)
    private EvidenceKind kind;

    @Column(name = "weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;

    @Column(name = "url", length = 2000)
    private String url;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "asserted_value")
    private String assertedValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
