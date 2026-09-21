package com.opencrm.order.operations;

import com.opencrm.order.domain.OrderProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** Admission only: already durable receipts and existing-order work must bypass the backlog check. */
@Component
public class IntakeAdmission {
    /** Retryable pressure, not malformed commercial input. Broker handlers must retain this delivery. */
    public static final class Saturated extends OrderProblem {
        public Saturated(String message) { super(503,message); }
    }
    private final JdbcTemplate db;
    private final String scope;
    private final int maxPayloadBytes;
    private final int maxPendingOutbox;
    public IntakeAdmission(JdbcTemplate db,
            @Value("${oms.scope-id:}") String scope,
            @Value("${oms.admission.max-payload-bytes:1048576}") int maxPayloadBytes,
            @Value("${oms.admission.max-pending-outbox:10000}") int maxPendingOutbox) {
        if(maxPayloadBytes<1 || maxPayloadBytes>16777216 || maxPendingOutbox<1 || maxPendingOutbox>1000000)
            throw new IllegalArgumentException("Invalid bounded admission policy");
        this.db=db;this.scope=scope;this.maxPayloadBytes=maxPayloadBytes;this.maxPendingOutbox=maxPendingOutbox;
    }
    public void checkPayload(String payload) {
        if(payload==null || payload.isBlank()) throw OrderProblem.invalid("Accepted snapshot payload is required");
        // The character precheck avoids allocating a second oversized buffer for an obvious rejection.
        if(payload.length()>maxPayloadBytes || payload.getBytes(StandardCharsets.UTF_8).length>maxPayloadBytes)
            throw OrderProblem.invalid("Accepted snapshot payload exceeds byte limit");
    }
    public void checkNewIntake() {
        if(scope==null || scope.isBlank()) throw new Saturated("Intake deployment scope is not configured");
        int pending=db.query(connection->{
            var statement=connection.prepareStatement("""
                SELECT count(*) FROM (
                    SELECT 1 FROM outbox_events e JOIN orders o ON o.id=e.aggregate_id
                    WHERE e.aggregate_type='Order' AND e.published=false AND e.is_deleted=false AND o.scope_id=?
                    LIMIT ?
                ) pending
                """);
            statement.setQueryTimeout(5);statement.setString(1,scope);statement.setInt(2,maxPendingOutbox);return statement;
        },result->{result.next();return result.getInt(1);});
        if(pending>=maxPendingOutbox) throw new Saturated("Pending delivery backlog is at the intake high-water mark");
    }
}
