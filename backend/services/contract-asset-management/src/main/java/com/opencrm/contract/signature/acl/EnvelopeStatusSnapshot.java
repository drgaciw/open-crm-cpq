package com.opencrm.contract.signature.acl;

import com.opencrm.contract.signature.SignatureEnvelopeStatus;

import java.util.List;

/**
 * What GetEnvelopeStatus (poll) or a webhook delivery reports, translated into our provider-agnostic status and
 * signer evidence. {@code providerEventId} identifies this specific status report for idempotency purposes: for a
 * webhook it is the provider's delivery/event id; for a poll it is a value the adapter derives deterministically
 * from the reported state (so re-polling an unchanged envelope reports the same id and is naturally deduplicated,
 * the same as a redelivered webhook).
 */
public record EnvelopeStatusSnapshot(
        String providerEventId,
        SignatureEnvelopeStatus status,
        List<ProviderSignerEvidence> signers) {
}
