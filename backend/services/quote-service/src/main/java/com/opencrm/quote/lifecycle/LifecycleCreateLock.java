package com.opencrm.quote.lifecycle;

import com.opencrm.common.commercial.CommercialDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Serializes a tenant's idempotent creates until their transaction commits or rolls back. */
@Component
public class LifecycleCreateLock {
    private final JdbcTemplate jdbc;

    public LifecycleCreateLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String tenant, String key) {
        // The query after waiting must observe the winner's commit. A joined REPEATABLE READ
        // transaction retains its older snapshot, so refuse that mode rather than mis-replay.
        String isolation = jdbc.queryForObject("SHOW transaction_isolation", String.class);
        if (!"read committed".equalsIgnoreCase(isolation)) {
            throw new IllegalStateException("Lifecycle idempotent create requires READ COMMITTED isolation");
        }
        String input = "lifecycle-create-v1|" + tenant.length() + ":" + tenant + "|" + key.length() + ":" + key;
        String digest = CommercialDigest.sha256(input.getBytes(StandardCharsets.UTF_8));
        long lockKey = ByteBuffer.wrap(HexFormat.of().parseHex(digest.substring(0, 16))).getLong();
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, lockKey);
    }
}
