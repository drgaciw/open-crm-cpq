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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Canonical commercial line on a contract (DRG-665).
 * {@code tenantId} is always stamped from the parent {@link ContractHeader}, never from a request body.
 */
@Entity
@Table(name = "contract_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uq_contract_lines_contract_line_no", columnNames = {"contract_id", "line_no"}),
        @UniqueConstraint(name = "uq_contract_lines_tenant_accepted_line",
                columnNames = {"tenant_id", "accepted_line_uuid"})
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ContractLine extends DocumentAuditedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /** Quote-line provenance; interim identity used by assets until they FK here. */
    @Column(name = "accepted_line_uuid", nullable = false, length = 64)
    private String acceptedLineUuid;

    @Column(name = "quote_id")
    private String quoteId;

    @Column(name = "product_external_id", nullable = false)
    private String productExternalId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ContractLineStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 40)
    private ChargeType chargeType;

    @PrePersist
    void assignDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ContractLineStatus.PENDING;
        }
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
    }
}
