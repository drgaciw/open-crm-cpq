package com.opencrm.contract.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyOwnershipPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LegacyOwnershipPolicy policy = new LegacyOwnershipPolicy("pilot-account");
    @Test void omsMarkerNeverUsesLegacyWriter() throws Exception {
        assertFalse(policy.legacyAllowed(mapper.readTree("{\"fulfillmentOwner\":\"OMS\",\"customerId\":\"a\"}")));
    }
    @Test void cohortWithoutOwnershipFailsClosedIncludingOldEvents() throws Exception {
        assertThrows(IllegalStateException.class, () -> policy.legacyAllowed(mapper.readTree("{\"customerId\":\"pilot-account\"}")));
        assertThrows(IllegalStateException.class, () -> policy.legacyAllowed(mapper.readTree("{\"fulfillmentOwner\":\"LEGACY\",\"customerId\":\"pilot-account\"}")));
    }
    @Test void legacyCustomersRetainExistingBehavior() throws Exception {
        assertTrue(policy.legacyAllowed(mapper.readTree("{\"customerId\":\"old-account\"}")));
    }
    @Test void unknownOwnershipAndCustomerAreNotLegacy() throws Exception {
        assertThrows(IllegalStateException.class, () -> policy.legacyAllowed(mapper.readTree("{\"fulfillmentOwner\":\"MYSTERY\",\"customerId\":\"a\"}")));
        assertThrows(IllegalStateException.class, () -> policy.legacyAllowed(mapper.readTree("{}")));
    }
}
