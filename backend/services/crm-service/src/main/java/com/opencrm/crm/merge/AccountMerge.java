package com.opencrm.crm.merge;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.crm.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Audit row for one account merge (DRG-484). The losing organization stays in
 * {@code organizations} and is closed with {@link com.opencrm.common.entity.BaseEntity#softDelete()}.
 */
@Entity
@Table(name = "account_merges", indexes = {
        @Index(name = "idx_account_merges_survivor", columnList = "survivor_organization_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_account_merges_merged_away", columnNames = "merged_away_organization_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AccountMerge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survivor_organization_id", nullable = false)
    private Organization survivor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merged_away_organization_id", nullable = false)
    private Organization mergedAway;

    @Column(name = "decided_by", nullable = false, length = 255)
    private String decidedBy;
}
