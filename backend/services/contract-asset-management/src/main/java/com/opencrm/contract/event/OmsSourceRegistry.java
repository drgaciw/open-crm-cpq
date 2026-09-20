package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.common.order.ActivationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable source authority and cross-path ownership fence, independent of intake enablement. */
@Service
public class OmsSourceRegistry {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OmsSourceRegistry(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper; }

    @Transactional
    public void register(String payload,String expectedHash) throws Exception {
        if (!hash(payload).equals(expectedHash)) throw new IllegalArgumentException("Source hash mismatch");
        AcceptedOrderSnapshot s=mapper.readValue(payload,AcceptedOrderSnapshot.class);
        if(s.schemaVersion()!=1 || !"USD".equals(s.currency()) || s.acceptedRevision()<1 || s.acceptedAt()==null
            || s.scopeId()==null || s.scopeId().isBlank() || s.customerId()==null || s.customerId().isBlank()
            || s.snapshotId()==null || s.snapshotId().isBlank() || s.quoteNumber()==null || s.quoteNumber().isBlank()
            || !UUID.fromString(s.quoteId()).toString().equals(s.quoteId())) throw new IllegalArgumentException("Invalid accepted source");
        Set<String> ids=new HashSet<>();
        if(s.lines().isEmpty()) throw new IllegalArgumentException("Missing source lines");
        for(var line:s.lines()) if(line.sourceLineId()==null || !ids.add(line.sourceLineId())) throw new IllegalArgumentException("Invalid source line identity");
        lockIdentities(s.quoteId(),s.quoteNumber());
        var identities=jdbc.queryForList("SELECT quote_id FROM oms_quote_identities WHERE identity IN (?,?)",s.quoteId(),s.quoteNumber());
        if(identities.stream().anyMatch(row -> !s.quoteId().equals(row.get("quote_id")))) throw new IllegalStateException("Source alias identity conflict");
        jdbc.update("INSERT INTO oms_quote_identities(identity,quote_id) VALUES (?,?) ON CONFLICT DO NOTHING",s.quoteId(),s.quoteId());
        jdbc.update("INSERT INTO oms_quote_identities(identity,quote_id) VALUES (?,?) ON CONFLICT DO NOTHING",s.quoteNumber(),s.quoteId());
        int inserted=jdbc.update("INSERT INTO oms_accepted_sources(scope_id,quote_id,quote_number,accepted_revision,snapshot_id,customer_id,payload,payload_hash) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
            s.scopeId(),s.quoteId(),s.quoteNumber(),s.acceptedRevision(),s.snapshotId(),s.customerId(),payload,expectedHash);
        if(inserted==0) {
            var existing=jdbc.queryForList("SELECT payload_hash FROM oms_accepted_sources WHERE scope_id=? AND quote_id=? AND accepted_revision=?",s.scopeId(),s.quoteId(),s.acceptedRevision());
            if(existing.size()!=1 || !expectedHash.equals(existing.getFirst().get("payload_hash"))) throw new IllegalStateException("Accepted source revision conflict");
        }
    }

    public AcceptedOrderSnapshot requireSource(ActivationRequest r) throws Exception {
        var rows=jdbc.queryForList("SELECT payload FROM oms_accepted_sources WHERE scope_id=? AND quote_id=? AND accepted_revision=?",r.scopeId(),r.quoteId(),r.acceptedRevision());
        if(rows.size()!=1) throw new IllegalStateException("Accepted source ownership is not registered");
        var s=mapper.readValue((String)rows.getFirst().get("payload"),AcceptedOrderSnapshot.class);
        lockIdentities(s.quoteId(),s.quoteNumber());
        if(!Objects.equals(s.customerId(),r.customerId()) || s.lines().stream().noneMatch(line -> line.equals(r.line())))
            throw new IllegalStateException("Activation differs from accepted source line");
        if(s.address()==null || s.address().isBlank() || s.approvalReference()==null || s.approvalReference().isBlank() || s.taxEvidence()==null || s.taxEvidence().isBlank()
            || s.configurationReference()==null || s.configurationReference().isBlank()) throw new IllegalStateException("Accepted source evidence missing");
        if(jdbc.queryForObject("SELECT count(*) FROM subscriptions s WHERE s.quote_id IN (SELECT identity FROM oms_quote_identities WHERE quote_id=?) AND NOT EXISTS (SELECT 1 FROM oms_activation_operations a WHERE a.subscription_id=s.uuid)",Integer.class,s.quoteId())>0)
            throw new IllegalStateException("Legacy subscription requires reconciliation before activation");
        if(jdbc.queryForObject("SELECT count(*) FROM oms_activation_operations WHERE scope_id=? AND quote_id=? AND accepted_revision<>?",Integer.class,r.scopeId(),r.quoteId(),r.acceptedRevision())>0)
            throw new IllegalStateException("Commercial revision requires explicit subscription reconciliation");
        return s;
    }

    /** Call inside the legacy subscription transaction, before any legacy side effect. */
    public boolean permitsLegacy(String identity) throws Exception {
        if(identity==null || identity.isBlank()) throw new IllegalStateException("Legacy quote identity missing");
        lockIdentities(identity);
        return jdbc.queryForObject("SELECT count(*) FROM oms_quote_identities WHERE identity=?",Integer.class,identity)==0;
    }
    private void lockIdentities(String... identities) throws Exception {
        for(String identity:Arrays.stream(identities).distinct().sorted().toList())
            jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",Long.parseUnsignedLong(hash(identity).substring(0,15),16));
    }
    static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
