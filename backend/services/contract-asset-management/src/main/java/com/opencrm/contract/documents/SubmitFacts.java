package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.QuantitySemantics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mutable submit input so a test can flip one fact at a time. */
public final class SubmitFacts {

    public String intentName = "AMEND";
    public ActionCode action = ActionCode.ADD;
    public BigDecimal deltaQty = BigDecimal.ONE;
    public BusinessCategory category = BusinessCategory.UPSELL;
    public LocalDate actionDate = LocalDate.parse("2026-06-01");
    public LocalDate effectiveDate = LocalDate.parse("2026-06-01");
    public String backdatingReason = null;
    public LocalDate contractStart = LocalDate.parse("2026-01-01");
    public LocalDate contractEnd = LocalDate.parse("2026-12-31");
    public LocalDate lineStart = LocalDate.parse("2026-06-01");
    public LocalDate lineEnd = LocalDate.parse("2026-12-31");
    public boolean targetOnContract = true;
    public String subscriptionStatus = "ACTIVE";
    public Long observedRevision = 0L;
    public Long commercialRevision = 0L;
    public boolean sourcePresent = true;
    public boolean duplicateSource = false;
    public QuantitySemantics semantics = QuantitySemantics.DELTA;
    public BigDecimal startQty = BigDecimal.ONE;
    public BigDecimal endQty = new BigDecimal("2");
    public boolean pinsPresent = true;
    public boolean policyPresent = true;
    public boolean totalsPresent = true;
    public boolean overrideRequested = false;
    public boolean overrideComplete = true;
    public boolean removeAfterAdd = true;
    public boolean hasRemove = false;
    public boolean unitKeysUnique = true;

    public static SubmitFacts passing() {
        return new SubmitFacts();
    }
}
