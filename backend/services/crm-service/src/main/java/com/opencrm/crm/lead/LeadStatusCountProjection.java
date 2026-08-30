package com.opencrm.crm.lead;

/**
 * Spring Data JPA interface projection for lead counts grouped by status.
 */
public interface LeadStatusCountProjection {
    LeadStatus getStatus();

    Long getCount();
}
