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

public record CreateAmendment(
        UUID lifecycleChangeId,
        int lifecycleChangeRevision,
        UUID targetQuoteId,
        String inputsSnapshotRef,
        SignaturePolicy signaturePolicy,
        LocalDate actionDate,
        LocalDate contractEffectiveDate,
        String backdatingReasonCode,
        String currency,
        List<LineCommand> lines) {

    public CreateAmendment withCurrency(String currency) {
        return new CreateAmendment(lifecycleChangeId, lifecycleChangeRevision, targetQuoteId, inputsSnapshotRef,
                signaturePolicy, actionDate, contractEffectiveDate, backdatingReasonCode, currency, lines);
    }

    public CreateAmendment withDates(LocalDate actionDate, LocalDate contractEffectiveDate, String backdatingReasonCode) {
        return new CreateAmendment(lifecycleChangeId, lifecycleChangeRevision, targetQuoteId, inputsSnapshotRef,
                signaturePolicy, actionDate, contractEffectiveDate, backdatingReasonCode, currency, lines);
    }

    public CreateAmendment withLifecycleChangeId(UUID lifecycleChangeId) {
        return new CreateAmendment(lifecycleChangeId, lifecycleChangeRevision, targetQuoteId, inputsSnapshotRef,
                signaturePolicy, actionDate, contractEffectiveDate, backdatingReasonCode, currency, lines);
    }
}

