package com.opencrm.contract.documents;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.ApplyTargetType;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.ChargeType;
import com.opencrm.contract.domain.documents.PolicySlot;
import com.opencrm.contract.domain.documents.PriceAsOf;
import com.opencrm.contract.domain.documents.QuantitySemantics;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LineCommand(
        UUID changeLineRef,
        ActionCode action,
        BusinessCategory category,
        ApplyTargetType targetType,
        UUID targetSubscriptionId,
        String sourceAcceptedLineUuid,
        QuantitySemantics semantics,
        BigDecimal startQty,
        BigDecimal deltaQty,
        BigDecimal endQty,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        ChargeType chargeType,
        PriceAsOf priceAsOf,
        PolicySlot policySlot,
        int applicationUnitNo,
        long observedCommercialRevision) {
}

