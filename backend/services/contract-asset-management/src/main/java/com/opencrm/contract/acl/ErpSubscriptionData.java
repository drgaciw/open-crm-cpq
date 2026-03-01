package com.opencrm.contract.acl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ERP subscription data transfer object.
 * Part of ACL to isolate ERP data model from domain model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErpSubscriptionData {
    private String erpSubscriptionId;
    private String status;
    private String startDate;
    private String endDate;
}
