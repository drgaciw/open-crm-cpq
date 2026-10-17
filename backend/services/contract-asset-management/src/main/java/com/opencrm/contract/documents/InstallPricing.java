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

public record InstallPricing(
        int version,
        UUID lifecycleChangeId,
        int lifecycleChangeRevision,
        UUID targetQuoteId,
        String inputsSnapshotRef,
        String inputDigest,
        String policyId,
        String policyVersion,
        String decisionId,
        String pricingTraceId,
        String currency,
        BigDecimal tcvDelta,
        BigDecimal arrDelta,
        BigDecimal oneTimeTotal,
        BigDecimal creditTotal,
        List<PricedLine> lines) {
}

