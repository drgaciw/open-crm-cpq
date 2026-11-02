package com.opencrm.crm.buyingrole;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.deal.Deal;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * One contact's buying role on one deal (DRG-518). The pair is unique.
 * A contact may have a role on many deals. The deal aggregate is not modified.
 */
@Entity
@Table(name = "deal_buying_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_deal_buying_roles_deal_contact", columnNames = {"deal_id", "contact_id"})
}, indexes = {
        @Index(name = "idx_deal_buying_roles_contact", columnList = "contact_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DealBuyingRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Convert(converter = BuyingRoleConverter.class)
    @Column(name = "role", nullable = false, length = 32)
    private BuyingRole role;
}
