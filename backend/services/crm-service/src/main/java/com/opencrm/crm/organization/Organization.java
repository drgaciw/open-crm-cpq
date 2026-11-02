package com.opencrm.crm.organization;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.crm.contact.Contact;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Organization entity - represents a company or account.
 */
@Entity
@Table(name = "organizations", indexes = {
    @Index(name = "idx_org_name", columnList = "name"),
    @Index(name = "idx_org_domain", columnList = "website_domain"),
    @Index(name = "idx_org_industry", columnList = "industry"),
    @Index(name = "idx_org_parent", columnList = "parent_organization_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "website")
    private String website;

    @Column(name = "website_domain")
    private String websiteDomain;

    @Column(name = "industry")
    private String industry;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "annual_revenue")
    private Long annualRevenue;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country")
    private String country;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    @Builder.Default
    private OrganizationType type = OrganizationType.PROSPECT;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "territory")
    private String territory;

    /**
     * Parent organization row id. Null is a root account. Written only by {@link AccountHierarchyService}
     * so a self-parent or an ancestor loop is rejected before the row changes.
     */
    @Column(name = "parent_organization_id")
    private Long parentOrganizationId;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<Contact> contacts = new ArrayList<>();

    /**
     * Count of non-deleted contacts (computed by DB, avoids N+1 on lazy contacts collection).
     */
    @Formula("(SELECT COUNT(*) FROM crm.contacts c WHERE c.organization_id = id AND c.is_deleted = false)")
    private Integer contactCount;

    /**
     * Add a contact to this organization.
     */
    public void addContact(Contact contact) {
        contacts.add(contact);
        contact.setOrganization(this);
    }

    /**
     * Remove a contact from this organization.
     */
    public void removeContact(Contact contact) {
        contacts.remove(contact);
        contact.setOrganization(null);
    }
}
