package com.opencrm.common.event;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Database boundary for relay claims and fenced acknowledgments. */
@Component
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class OutboxDeliveryStore {
    public record Delivery(long id, String token, String topic, String aggregateId, String payload, int attempt) {}
    public record Exhausted(long id, String topic, int attempts, String error, Instant exhaustedAt) {}
    private final JdbcTemplate jdbc;
    public OutboxDeliveryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Claim commits before network I/O. A crash consumes an attempt; an expired lease is reclaimable. */
    public List<Delivery> claim(int limit, int maxAttempts, Duration lease) {
        validateLimit(limit);
        if (maxAttempts < 1 || maxAttempts > 100 || lease.isNegative() || lease.isZero()
                || lease.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Invalid relay policy");
        // Include records exhausted by a process crash and records exhausted by the previous relay.
        jdbc.update("""
            UPDATE outbox_events SET exhausted_at=now(), lease_token=NULL, lease_until=NULL,
                error_message=COALESCE(error_message,'Attempt limit reached after lease expiry'),
                updated_at=now(), version=version+1
            WHERE id IN (SELECT id FROM outbox_events WHERE published=false AND is_deleted=false
                AND exhausted_at IS NULL AND retry_count>=? AND (lease_until IS NULL OR lease_until<=now())
                ORDER BY created_at,id LIMIT ? FOR UPDATE SKIP LOCKED)
            """, maxAttempts, limit);
        return jdbc.query("""
            UPDATE outbox_events SET lease_token=?, lease_until=now()+(? * interval '1 millisecond'),
                retry_count=retry_count+1, updated_at=now(), version=version+1
            WHERE id IN (SELECT id FROM outbox_events WHERE published=false AND is_deleted=false
                AND exhausted_at IS NULL AND retry_count<?
                AND (lease_until IS NULL OR lease_until<=now())
                AND (next_attempt_at IS NULL OR next_attempt_at<=now())
                ORDER BY created_at,id LIMIT ? FOR UPDATE SKIP LOCKED)
            RETURNING id,lease_token,event_type,aggregate_id,payload,retry_count
            """, (rs, n) -> new Delivery(rs.getLong("id"),rs.getString("lease_token"),
                rs.getString("event_type"),rs.getString("aggregate_id"),rs.getString("payload"),rs.getInt("retry_count")),
            UUID.randomUUID().toString(),lease.toMillis(),maxAttempts,limit);
    }

    public boolean acknowledged(Delivery delivery) {
        return jdbc.update("""
            UPDATE outbox_events SET published=true,published_at=now(),lease_token=NULL,lease_until=NULL,
                next_attempt_at=NULL,error_message=NULL,updated_at=now(),version=version+1
            WHERE id=? AND lease_token=? AND lease_until>clock_timestamp() AND published=false AND is_deleted=false
            """,delivery.id(),delivery.token()) == 1;
    }

    public boolean failed(Delivery delivery, int maxAttempts, String error) {
        if (maxAttempts < 1 || maxAttempts > 100) throw new IllegalArgumentException("Invalid attempt limit");
        String safeError = error == null ? "Delivery failed" : error.substring(0,Math.min(error.length(),500));
        return jdbc.update("""
            UPDATE outbox_events SET lease_token=NULL,lease_until=NULL,error_message=?,
                exhausted_at=CASE WHEN retry_count>=? THEN now() ELSE NULL END,
                next_attempt_at=now()+(LEAST(300,power(2,LEAST(retry_count-1,6))*5) * interval '1 second'),
                updated_at=now(),version=version+1
            WHERE id=? AND lease_token=? AND lease_until>clock_timestamp() AND published=false AND is_deleted=false
            """,safeError,maxAttempts,delivery.id(),delivery.token()) == 1;
    }

    /** Bounded operational projection; deliberately excludes the commercial payload. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Exhausted> exhausted(int limit) {
        validateLimit(limit);
        return jdbc.query("""
            SELECT id,event_type,retry_count,error_message,exhausted_at FROM outbox_events
            WHERE published=false AND exhausted_at IS NOT NULL AND is_deleted=false ORDER BY exhausted_at,id LIMIT ?
            """,(rs,n) -> new Exhausted(rs.getLong("id"),rs.getString("event_type"),rs.getInt("retry_count"),
                rs.getString("error_message"),rs.getTimestamp("exhausted_at").toInstant()),limit);
    }

    /** Authorized operator boundary only: callers must authorize and audit the actor/reason before invoking. */
    public boolean recover(long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length()>450) throw new IllegalArgumentException("Recovery reason required (max 450 characters)");
        return jdbc.update("""
            UPDATE outbox_events SET exhausted_at=NULL,retry_count=0,next_attempt_at=now(),
                lease_token=NULL,lease_until=NULL,error_message=?,updated_at=now(),version=version+1
            WHERE id=? AND published=false AND is_deleted=false AND exhausted_at IS NOT NULL
                AND (lease_until IS NULL OR lease_until<=now())
            ""","Recovery: "+reason,id) == 1;
    }

    private static void validateLimit(int limit) {
        if (limit<1 || limit>100) throw new IllegalArgumentException("Batch size must be 1..100");
    }
}
