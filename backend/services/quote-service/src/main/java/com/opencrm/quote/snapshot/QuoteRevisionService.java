package com.opencrm.quote.snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.quote.quote.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.*;

@Service @RequiredArgsConstructor
public class QuoteRevisionService {
    private final JdbcTemplate jdbc;
    private final QuoteRepository quotes;
    private final OmsQuoteProperties properties;
    private final ObjectMapper mapper;

    @Transactional
    public void reopen(QuoteRevisionRequest request) {
        validate(request);
        String hash;
        try {
            ObjectNode body=mapper.valueToTree(request);body.remove("eventId");
            hash=AcceptedSnapshotService.sha256(mapper.writeValueAsString(body));
        } catch(Exception e) { throw new IllegalArgumentException("Invalid revision command",e); }
        // Different quotes using one operation key must serialize before either writes state.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 7619))",request.operationKey());
        var events=jdbc.queryForList("SELECT request_hash FROM quote_revision_inbox WHERE event_id=?",request.eventId());
        if(!events.isEmpty()) { assertHash(hash,events.getFirst().get("request_hash"));return; }
        var operations=jdbc.queryForList("SELECT request_hash FROM quote_revision_operations WHERE operation_key=?",request.operationKey());
        if(!operations.isEmpty()) {
            assertHash(hash,operations.getFirst().get("request_hash"));receipt(request,hash);return;
        }
        var locked=jdbc.queryForList("SELECT id FROM quotes WHERE uuid=? AND is_deleted=false FOR UPDATE",request.quoteId());
        if(locked.size()!=1) throw new IllegalStateException("Source quote unavailable");
        var quote=quotes.findByUuidAndDeletedFalse(request.quoteId()).orElseThrow();
        if(!"OMS".equals(quote.getFulfillmentOwner()) || quote.getStatus()!=QuoteStatus.ACCEPTED
            || quote.getAcceptedRevision()!=request.acceptedRevision() || !request.customerId().equals(quote.getCustomerExternalId()))
            throw new IllegalStateException("Only the exact accepted OMS revision may be corrected");
        if(jdbc.queryForObject("SELECT count(*) FROM accepted_order_snapshots WHERE quote_id=? AND accepted_revision=? AND scope_id=? AND customer_id=?",Integer.class,
                request.quoteId(),request.acceptedRevision(),request.scopeId(),request.customerId())!=1)
            throw new IllegalStateException("Accepted source ownership unavailable");
        jdbc.update("INSERT INTO quote_revision_operations(operation_key,request_hash,quote_id,accepted_revision,order_id,scope_id,customer_id,reason) VALUES (?,?,?,?,?,?,?,?)",
            request.operationKey(),hash,request.quoteId(),request.acceptedRevision(),request.orderId(),request.scopeId(),request.customerId(),request.reason());
        receipt(request,hash);
        quote.setStatus(QuoteStatus.DRAFT);quote.setAcceptedAt(null);
        quote.setApprovalState(ApprovalState.NONE);quote.setApprovalTier(null);quote.setOmsApprovalReference(null);
        quote.setOmsCorrectionOperationKey(request.operationKey());
        quotes.saveAndFlush(quote);
    }
    private void receipt(QuoteRevisionRequest r,String hash) {
        jdbc.update("INSERT INTO quote_revision_inbox(event_id,operation_key,request_hash) VALUES (?,?,?)",r.eventId(),r.operationKey(),hash);
    }
    private void assertHash(String actual,Object expected) {
        if(!actual.equals(expected)) throw new IllegalStateException("Revision event or operation identity conflict");
    }
    private void validate(QuoteRevisionRequest r) {
        if(r==null || r.schemaVersion()!=1 || r.acceptedRevision()<1 || !Objects.equals(properties.getScopeId(),r.scopeId()))
            throw new IllegalArgumentException("Untrusted revision scope or schema");
        for(String value:new String[]{r.eventId(),r.operationKey(),r.orderId(),r.quoteId(),r.scopeId(),r.customerId()})
            if(value==null || value.isBlank() || value.length()>255) throw new IllegalArgumentException("Revision identity required");
        if(r.reason()==null || r.reason().isBlank() || r.reason().length()>2000 || !UUID.fromString(r.quoteId()).toString().equals(r.quoteId()))
            throw new IllegalArgumentException("Canonical quote identity and correction reason required");
    }
}
