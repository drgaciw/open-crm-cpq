package com.opencrm.crm.organization;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

/** An explicit null unlinks; a missing field is not an unlink request. */
public class LinkToParentRequest {
    @Positive(message = "Parent organization id must be positive")
    private Long parentOrganizationId;
    private boolean supplied;

    public Long getParentOrganizationId() { return parentOrganizationId; }

    @JsonSetter("parentOrganizationId")
    @JsonAlias("parentId")
    public void setParentOrganizationId(Long id) { this.parentOrganizationId = id; this.supplied = true; }

    @JsonIgnore
    @AssertTrue(message = "parentId must be supplied; use null to unlink")
    public boolean isParentSpecified() { return supplied; }
}
