package com.opencrm.crm.organization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Organization response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {

    private Long id;
    private String uuid;
    private String name;
    private String website;
    private String websiteDomain;
    private String industry;
    private Integer employeeCount;
    private Long annualRevenue;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String description;
    private String linkedinUrl;
    private OrganizationType type;
    private String ownerId;
    private String territory;
    private Integer contactCount;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
