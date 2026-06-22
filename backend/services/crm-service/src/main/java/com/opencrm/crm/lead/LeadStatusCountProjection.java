package com.opencrm.crm.lead;

public interface LeadStatusCountProjection {
    LeadStatus getStatus();
    Long getCount();
}
