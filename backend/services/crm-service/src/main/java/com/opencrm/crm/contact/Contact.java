package com.opencrm.crm.contact;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.crm.organization.Organization;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Contact entity - represents an individual person.
 */
@Entity
@Table(name = "contacts", indexes = {
    @Index(name = "idx_contact_email", columnList = "email"),
    @Index(name = "idx_contact_org", columnList = "organization_id"),
    @Index(name = "idx_contact_name", columnList = "first_name, last_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Contact extends BaseEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "department")
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

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

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_primary")
    private boolean primary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ContactStatus status = ContactStatus.ACTIVE;

    @Column(name = "owner_id")
    private String ownerId;

    /**
     * Get full name of the contact.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
