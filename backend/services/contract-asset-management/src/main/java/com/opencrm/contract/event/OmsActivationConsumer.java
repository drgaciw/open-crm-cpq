package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.order.ActivationRequest;
import com.opencrm.common.order.ActivationAcknowledgment;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Sole OMS activation writer; no simulated ERP call or processing-date defaults. */
@Component
@ConditionalOnProperty(name="oms.activation-consumer-enabled",havingValue="true")
public class OmsActivationConsumer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Set<String> scopes;
    public OmsActivationConsumer(JdbcTemplate jdbc,ObjectMapper mapper,@Value("${oms.trusted-source-scopes:}") String scopes) {
        this.jdbc=jdbc;this.mapper=mapper;
        this.scopes=Set.copyOf(Arrays.stream(scopes.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());
    }
    @Transactional
    @KafkaListener(topics="oms.activation-requested.v1",groupId="contract-oms-activation-v1")
    public void accept(String payload) throws Exception {
        ActivationRequest r=mapper.readValue(payload,ActivationRequest.class);
        if (!scopes.contains(r.scopeId())) throw new IllegalArgumentException("Untrusted activation scope");
        var arr=ActivationTerms.arr(r);
        String business=hash(mapper.writeValueAsString(List.of(r.scopeId(),r.quoteId(),r.acceptedRevision(),r.line().sourceLineId())));
        var normalized=mapper.valueToTree(r);((com.fasterxml.jackson.databind.node.ObjectNode)normalized).remove("eventId");
        String requestHash=hash(mapper.writeValueAsString(normalized));
        jdbc.execute("SELECT pg_advisory_xact_lock(" + Long.parseUnsignedLong(business.substring(0,15),16) + ")");
        int receipt=jdbc.update("INSERT INTO oms_activation_inbox(event_id,request_hash) VALUES (?,?) ON CONFLICT (event_id) DO NOTHING",r.eventId(),requestHash);
        if(receipt==0) {
            String prior=jdbc.queryForObject("SELECT request_hash FROM oms_activation_inbox WHERE event_id=?",String.class,r.eventId());
            if(!requestHash.equals(prior)) throw new IllegalStateException("Activation event identity conflict");
            return;
        }
        var existing=jdbc.queryForList("SELECT request_hash FROM oms_activation_operations WHERE business_key=? OR operation_key=?",business,r.operationKey());
        if (!existing.isEmpty()) {
            if (existing.size()!=1 || !requestHash.equals(existing.getFirst().get("request_hash"))) throw new IllegalStateException("Activation identity conflict");
            return;
        }
        if (jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE quote_id=? AND subscription_number NOT LIKE 'OMS-%'",Integer.class,r.quoteId())>0) {
            throw new IllegalStateException("Legacy subscription requires reconciliation before activation");
        }
        String subscription=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO subscriptions(uuid,subscription_number,customer_id,quote_id,status,plan_name,start_date,end_date,mrr,arr,currency_code,erp_sync_status,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            subscription,"OMS-"+business,r.customerId(),r.quoteId(),"ACTIVE",r.line().description(),r.line().startDate(),r.line().endDate(),
            arr.divide(java.math.BigDecimal.valueOf(12),2,RoundingMode.HALF_EVEN),arr,"USD","NOT_REQUIRED","oms-activation");
        var ack=new ActivationAcknowledgment(UUID.randomUUID().toString(),r.operationKey(),r.orderId(),r.scopeId(),subscription,"ACKNOWLEDGED",r.evidenceReference(),Instant.now());
        String ackJson=mapper.writeValueAsString(ack);
        jdbc.update("INSERT INTO oms_activation_operations(operation_key,business_key,request_hash,scope_id,quote_id,source_line_id,accepted_revision,subscription_id,acknowledgment_json) VALUES (?,?,?,?,?,?,?,?,?)",
            r.operationKey(),business,requestHash,r.scopeId(),r.quoteId(),r.line().sourceLineId(),r.acceptedRevision(),subscription,ackJson);
        jdbc.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,created_by) VALUES (?,?,?,?,?,?)",
            ack.eventId(),"Order",r.orderId(),"oms.activation-acknowledged.v1",ackJson,"oms-activation");
    }
    private static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
