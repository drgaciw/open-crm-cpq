package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AmendmentAuditEventTest {
    @Test void nullableAuditFieldsSurviveAndUnknownAdditionsAreIgnored() throws Exception {
        var json = new ObjectMapper();
        var id = UUID.randomUUID();
        var event = new AmendmentAuditEvent(id, id, null, null, "hash", "business-correlation", id);
        String wire = json.writeValueAsString(event);
        assertThat(json.readTree(wire).size()).isEqualTo(7);
        assertThat(wire).contains("\"amendmentSequence\":null", "\"amendmentNumber\":null");
        assertThat(json.readValue(wire.substring(0, wire.length()-1) + ",\"futureField\":true}",
                AmendmentAuditEvent.class)).isEqualTo(event);
        assertThat(EventContractRegistry.AMENDMENT_AUDIT_RECORDED_V1.payloadClass())
                .isEqualTo(AmendmentAuditEvent.class);
    }
}
