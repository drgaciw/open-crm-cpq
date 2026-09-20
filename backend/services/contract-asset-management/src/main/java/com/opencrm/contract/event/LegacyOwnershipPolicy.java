package com.opencrm.contract.event;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Deploy before quote intake; cohort fencing also blocks delayed pre-marker events. */
@Component
public class LegacyOwnershipPolicy {
    private final Set<String> omsCustomers;
    public LegacyOwnershipPolicy(@Value("${oms.managed-customers:}") String customers) {
        omsCustomers = Arrays.stream(customers.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    public boolean legacyAllowed(JsonNode event) {
        String owner = event.path("fulfillmentOwner").asText("");
        if ("OMS".equals(owner)) return false;
        String customer = event.path("customerId").asText("");
        if (customer.isBlank()) customer = event.path("customer").path("customerId").asText("");
        if (customer.isBlank() || omsCustomers.contains(customer) || (!owner.isBlank() && !"LEGACY".equals(owner))) {
            throw new IllegalStateException("Quote ownership requires reconciliation before legacy activation");
        }
        return true;
    }
}
