package com.opencrm.crm.lead;

/**
 * Projection for lead status counts.
 */
public interface LeadStatusCountProjection {
    LeadStatus getStatus();
    Long getCount();
}
