package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

/** Thin contract header. Richer fields stay with DRG-458. Precedence flag stays with DRG-592. */
@Entity
@Table(name = "contracts", uniqueConstraints = @UniqueConstraint(
        name = "uq_contracts_tenant_number", columnNames = {"tenant_id", "contract_number"}))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ContractHeader extends DocumentAuditedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "contract_number", nullable = false, length = 128)
    private String contractNumber;

    @Column(name = "customer_account_id", nullable = false)
    private UUID customerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_model", nullable = false, length = 40)
    private TermModel termModel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "governing_agreement_ref", length = 255)
    private String governingAgreementRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ContractStatus status;

    @Column(name = "last_executed_seq", nullable = false)
    private Integer lastExecutedSeq;

    @Column(name = "prior_contract_id")
    private UUID priorContractId;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (lastExecutedSeq == null) {
            lastExecutedSeq = 0;
        }
    }
}
