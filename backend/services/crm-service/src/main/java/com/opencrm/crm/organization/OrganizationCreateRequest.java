package com.opencrm.crm.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationCreateRequest {

    @NotBlank(message = "Organization name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Website must not exceed 500 characters")
    private String website;

    @Size(max = 255, message = "Website domain must not exceed 255 characters")
    private String websiteDomain;

    @Size(max = 255, message = "Industry must not exceed 255 characters")
    private String industry;

    private Integer employeeCount;

    private Long annualRevenue;

    @Size(max = 50, message = "Phone must not exceed 50 characters")
    private String phone;

    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    private String addressLine2;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    private String description;

    @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
    private String linkedinUrl;

    private OrganizationType type;

    private String ownerId;

    @Size(max = 100, message = "Territory must not exceed 100 characters")
    private String territory;

    /**
     * Optional parent organization id. Applied by {@link AccountHierarchyService} after insert.
     * Null leaves the new organization as a root.
     */
    @Positive(message = "Parent organization id must be positive")
    private Long parentOrganizationId;
}
