package com.opencrm.quote.client;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads unmerged CAM renewal policy rungs. There is no tenant argument: the service token carries it.
 * This client is not wired into quote continuation.
 */
public interface RenewalPolicyClient {

    List<RenewalPolicyView> fetchRungs(UUID accountId, UUID agreementId, UUID lineId, LocalDate asOf);
}
