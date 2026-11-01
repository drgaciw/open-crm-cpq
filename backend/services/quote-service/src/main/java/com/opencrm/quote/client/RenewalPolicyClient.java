package com.opencrm.quote.client;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads unmerged CAM renewal policy rungs. There is no tenant argument: the service token carries it.
 * From-active-assets renewals apply these rungs through {@code QuoteContinuationPolicy}.
 */
public interface RenewalPolicyClient {

    List<RenewalPolicyView> fetchRungs(UUID accountId, UUID agreementId, UUID lineId, LocalDate asOf);
}
