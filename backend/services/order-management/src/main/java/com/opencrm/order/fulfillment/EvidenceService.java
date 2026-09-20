package com.opencrm.order.fulfillment;

import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.execution.ExecutionService;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Records observed operator evidence. It neither promises inventory nor invokes a fulfillment provider. */
@Service
public class EvidenceService {
    public record EvidenceView(String id,String lineId,String kind,int quantity,String externalReference,
            String allocationReference,String shipmentReference,Instant occurredAt,Instant expiresAt,
            String actor,String reason,String status,String detail) {}
    public record AllocationView(String id,String lineId,String reference,int quantity,int consumed,int released,int available,Instant expiresAt) {}
    private static final Set<String> KINDS=Set.of("ALLOCATION","RELEASE","SHIPMENT","DELIVERY","PROVISION","SERVICE");
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final OrderAccess access;
    private final ExecutionService execution;
    private final Set<String> scopes;
    public EvidenceService(JdbcTemplate db,PlatformTransactionManager manager,ObjectMapper json,OrderAccess access,ExecutionService execution,
            @Value("${oms.execution.allowed-scopes:}") String scopes) {
        this.db=db;this.tx=new TransactionTemplate(manager);this.json=json;this.access=access;this.execution=execution;
        this.scopes=Set.copyOf(Arrays.stream(scopes.split(",")).map(String::trim).filter(v->!v.isBlank()).toList());
    }

    public EvidenceReceipt record(String orderId,EvidenceCommand command) {
        validate(command);
        for(int attempt=0;attempt<2;attempt++) {
            try { return tx.execute(t -> recordLocked(orderId,command)); }
            catch(DuplicateKeyException conflict) { if(attempt==1) throw OrderProblem.conflict("Concurrent evidence or operation identity conflict"); }
        }
        throw new IllegalStateException("Unreachable evidence retry state");
    }

    private EvidenceReceipt recordLocked(String orderId,EvidenceCommand c) {
        var order=order(orderId,true);
        access.require("oms:evidence",str(order,"scope_id"),str(order,"customer_id"));
        String actor=access.actor();text(actor,200,"actor");
        String body=OrderService.hash(json.writeValueAsString(List.of(orderId,c)));
        String scope=str(order,"scope_id");
        var prior=db.queryForList("SELECT body_hash FROM order_idempotency WHERE scope_id=? AND operation_key=?",scope,c.operationKey());
        if(!prior.isEmpty()) {
            if(!body.equals(prior.getFirst().get("body_hash"))) throw OrderProblem.conflict("Operation key body conflict");
            var receipts=db.queryForList("SELECT evidence_id FROM fulfillment_evidence_receipts WHERE scope_id=? AND operation_key=?",scope,c.operationKey());
            if(receipts.isEmpty()) throw OrderProblem.conflict("Operation key belongs to another command");
            return receipt(orderId,str(receipts.getFirst(),"evidence_id"));
        }
        if(number(order,"version")!=c.expectedVersion()) throw OrderProblem.conflict("Order version changed");
        var line=line(c.lineId());if(!orderId.equals(str(line,"order_id"))) throw OrderProblem.missing();
        String semantic=semantic(orderId,c);
        var identity=db.queryForList("SELECT e.id,e.semantic_hash,e.order_id FROM fulfillment_evidence_identity i JOIN fulfillment_evidence e ON e.id=i.evidence_id WHERE i.scope_id=? AND i.kind=? AND i.external_reference=?",scope,c.kind(),c.externalReference());
        if(!identity.isEmpty() && semantic.equals(identity.getFirst().get("semantic_hash"))) {
            String evidenceId=str(identity.getFirst(),"id");remember(scope,c.operationKey(),body,orderId,evidenceId);return receipt(orderId,evidenceId);
        }
        if("ALLOCATION".equals(c.kind()) && !c.expiresAt().isAfter(Instant.now())) throw OrderProblem.invalid("Allocation expiry must be in the future");
        if(!Set.of("SUBMITTED","IN_PROGRESS").contains(str(order,"status"))) throw OrderProblem.conflict("Evidence requires an active submitted order");
        validateKind(order,line,c.kind());
        db.update("UPDATE orders SET status='IN_PROGRESS' WHERE id=? AND status='SUBMITTED'",orderId);
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO fulfillment_evidence(id,order_id,line_id,scope_id,kind,quantity,external_reference,allocation_reference,shipment_reference,occurred_at,expires_at,actor,reason,body_hash,semantic_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,orderId,c.lineId(),scope,c.kind(),c.quantity(),c.externalReference(),c.allocationReference(),c.shipmentReference(),ts(c.occurredAt()),ts(c.expiresAt()),actor,c.reason(),body,semantic);
        db.update("INSERT INTO fulfillment_evidence_state(evidence_id,status) VALUES(?,'RECEIVED')",id);
        remember(scope,c.operationKey(),body,orderId,id);
        if(!identity.isEmpty()) {
            conflict(order,id,c.lineId(),c.kind(),"External reference contradicts previously recorded evidence");
            return receipt(orderId,id);
        }
        db.update("INSERT INTO fulfillment_evidence_identity(scope_id,kind,external_reference,evidence_id) VALUES(?,?,?,?)",scope,c.kind(),c.externalReference(),id);
        apply(order,evidence(id));
        if("SHIPMENT".equals(c.kind())) reconcilePendingLocked(order,c.externalReference());
        return receipt(orderId,id);
    }

    private void apply(Map<String,Object> order,Map<String,Object> e) {
        String id=str(e,"id"),orderId=str(order,"id"),lineId=str(e,"line_id"),kind=str(e,"kind");
        var line=line(lineId);int quantity=integer(e,"quantity"),remaining=integer(line,"ordered")-integer(line,"cancelled")-integer(line,"fulfilled");
        String issue=null;
        if("ALLOCATION".equals(kind)) {
            if(Boolean.TRUE.equals(order.get("manual_hold")) || db.queryForObject("SELECT COUNT(*) FROM order_holds WHERE order_id=?",Integer.class,orderId)>0) issue="Held order cannot allocate new work";
            else if(quantity>remaining-integer(line,"allocated")) issue="Allocation exceeds unallocated remaining quantity";
            else if(e.get("expires_at")!=null && !instant(e,"expires_at").isAfter(Instant.now())) issue="Allocation evidence is already expired";
            if(issue==null) {
                db.update("INSERT INTO allocation_lots(id,order_id,line_id,evidence_id,external_reference,quantity,occurred_at,expires_at) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),orderId,lineId,id,str(e,"external_reference"),quantity,e.get("occurred_at"),e.get("expires_at"));
                db.update("UPDATE order_lines SET allocated=allocated+? WHERE id=?",quantity,lineId);
            }
        } else if("SHIPMENT".equals(kind) || "RELEASE".equals(kind)) {
            var lots=db.queryForList("SELECT * FROM allocation_lots WHERE order_id=? AND line_id=? AND external_reference=?",orderId,lineId,str(e,"allocation_reference"));
            if(lots.isEmpty()) issue="Referenced allocation does not exist";
            else {
                var lot=lots.getFirst();int available=integer(lot,"quantity")-integer(lot,"consumed")-integer(lot,"released");
                if(quantity>available || quantity>integer(line,"allocated")) issue="Evidence exceeds remaining allocated quantity";
                else if(instant(e,"occurred_at").isBefore(instant(lot,"occurred_at"))) issue="Evidence predates allocation";
                else if("SHIPMENT".equals(kind) && lot.get("expires_at")!=null && !instant(e,"occurred_at").isBefore(instant(lot,"expires_at"))) issue="Shipment occurred after allocation expiry";
                else if("SHIPMENT".equals(kind) && quantity>remaining) issue="Shipment exceeds uncancelled remaining quantity";
                if(issue==null) {
                    if("SHIPMENT".equals(kind)) {
                        db.update("UPDATE allocation_lots SET consumed=consumed+? WHERE id=?",quantity,str(lot,"id"));
                        db.update("UPDATE order_lines SET allocated=allocated-?,shipped=shipped+?,fulfilled=fulfilled+? WHERE id=?",quantity,quantity,quantity,lineId);
                    } else {
                        db.update("UPDATE allocation_lots SET released=released+? WHERE id=?",quantity,str(lot,"id"));
                        db.update("UPDATE order_lines SET allocated=allocated-? WHERE id=?",quantity,lineId);
                    }
                }
            }
        } else if("DELIVERY".equals(kind)) {
            var shipments=db.queryForList("SELECT e.* FROM fulfillment_evidence_identity i JOIN fulfillment_evidence e ON e.id=i.evidence_id JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE i.scope_id=? AND i.kind='SHIPMENT' AND i.external_reference=? AND e.order_id=? AND e.line_id=? AND s.status='APPLIED'",str(order,"scope_id"),str(e,"shipment_reference"),orderId,lineId);
            if(shipments.isEmpty()) {
                db.update("UPDATE fulfillment_evidence_state SET status='PENDING',detail='Awaiting referenced shipment' WHERE evidence_id=?",id);
                exception(order,lineId,kind,"PENDING_DELIVERY","RECONCILE","Delivery retained pending referenced shipment");
                audit(order,"EVIDENCE_PENDING",str(e,"reason"),str(e,"external_reference"),true,access.actor());return;
            }
            var shipment=shipments.getFirst();
            int already=db.queryForObject("SELECT COALESCE(SUM(e.quantity),0) FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? AND e.line_id=? AND e.kind='DELIVERY' AND e.shipment_reference=? AND s.status='APPLIED'",Integer.class,orderId,lineId,str(e,"shipment_reference"));
            if(quantity>integer(shipment,"quantity")-already || quantity>integer(line,"shipped")-integer(line,"delivered")) issue="Delivery exceeds referenced shipment quantity";
            else if(instant(e,"occurred_at").isBefore(instant(shipment,"occurred_at"))) issue="Delivery predates referenced shipment";
            if(issue==null) db.update("UPDATE order_lines SET delivered=delivered+? WHERE id=?",quantity,lineId);
        } else {
            if(quantity>remaining) issue="Fulfillment exceeds uncancelled remaining quantity";
            if(issue==null) db.update("UPDATE order_lines SET fulfilled=fulfilled+? WHERE id=?",quantity,lineId);
        }
        if(issue!=null) {conflict(order,id,lineId,kind,issue);return;}
        db.update("UPDATE fulfillment_evidence_state SET status='APPLIED',detail=NULL,applied_at=clock_timestamp() WHERE evidence_id=?",id);
        db.update("UPDATE order_lines SET status=CASE WHEN fulfilled+cancelled=ordered THEN 'FULFILLED' WHEN fulfilled>0 THEN 'PARTIALLY_FULFILLED' ELSE 'PENDING' END WHERE id=?",lineId);
        if("RELEASE".equals(kind)) execution.refreshLineReadiness(orderId,lineId,str(e,"external_reference"),instant(e,"occurred_at"));
        else execution.completeManualTask(orderId,lineId,kind,str(e,"external_reference"),instant(e,"occurred_at"));
        audit(order,"FULFILLMENT_EVIDENCE",str(e,"reason"),str(e,"external_reference"),false,access.actor());
        if(Set.of("ALLOCATION","RELEASE","SHIPMENT","DELIVERY").contains(kind) && db.queryForObject("SELECT COUNT(*) FROM allocation_lots WHERE order_id=? AND line_id=? AND expires_at<=clock_timestamp() AND quantity>consumed+released",Integer.class,orderId,lineId)==0)
            resolve(orderId,lineId,"ALLOCATION","ALLOCATION_EXPIRED");
    }

    public void reconcilePending(String orderId) {
        tx.executeWithoutResult(t->{var order=order(orderId,true);access.require("oms:evidence",str(order,"scope_id"),str(order,"customer_id"));reconcilePendingLocked(order,null);});
    }
    private void reconcilePendingLocked(Map<String,Object> order,String shipmentReference) {
        String query="SELECT e.* FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? AND e.kind='DELIVERY' AND s.status='PENDING'";
        var args=new ArrayList<Object>();args.add(str(order,"id"));
        if(shipmentReference!=null){query+=" AND e.shipment_reference=?";args.add(shipmentReference);}query+=" ORDER BY e.recorded_at,e.id";
        for(var pending:db.queryForList(query,args.toArray())) {
            var known=db.queryForList("SELECT i.evidence_id FROM fulfillment_evidence_identity i JOIN fulfillment_evidence_state s ON s.evidence_id=i.evidence_id JOIN fulfillment_evidence e ON e.id=i.evidence_id WHERE i.scope_id=? AND i.kind='SHIPMENT' AND i.external_reference=? AND e.order_id=? AND e.line_id=? AND s.status='APPLIED'",str(order,"scope_id"),str(pending,"shipment_reference"),str(order,"id"),str(pending,"line_id"));
            if(!known.isEmpty()) apply(order,pending);
            if(db.queryForObject("SELECT COUNT(*) FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? AND e.line_id=? AND e.kind='DELIVERY' AND s.status='PENDING'",Integer.class,str(order,"id"),str(pending,"line_id"))==0)
                resolve(str(order,"id"),str(pending,"line_id"),"DELIVERY","PENDING_DELIVERY");
        }
    }

    /** Expiry only creates an operator exception. It never fabricates a release or fulfillment event. */
    public void detectExpiredAllocations(int limit) {
        if(limit<1 || limit>100) throw OrderProblem.invalid("Batch size must be 1..100");if(scopes.isEmpty()) return;
        var args=new ArrayList<Object>(scopes);args.add(limit);
        String placeholders=String.join(",",Collections.nCopies(scopes.size(),"?"));
        var lots=db.queryForList("SELECT a.* FROM allocation_lots a JOIN orders o ON o.id=a.order_id WHERE o.scope_id IN ("+placeholders+") AND a.expires_at<=clock_timestamp() AND a.quantity>a.consumed+a.released AND NOT EXISTS (SELECT 1 FROM order_exceptions x JOIN order_tasks t ON t.id=x.task_id WHERE x.order_id=o.id AND t.line_id=a.line_id AND x.category='ALLOCATION_EXPIRED' AND x.status='OPEN') ORDER BY a.expires_at,a.id LIMIT ?",args.toArray());
        for(var lot:lots) tx.executeWithoutResult(t->{
            var order=order(str(lot,"order_id"),true);
            int stillExpired=db.queryForObject("SELECT COUNT(*) FROM allocation_lots WHERE id=? AND expires_at<=clock_timestamp() AND quantity>consumed+released",Integer.class,str(lot,"id"));if(stillExpired==0)return;
            String taskId=taskId(str(order,"id"),str(lot,"line_id"),"ALLOCATION");
            int existing=db.queryForObject("SELECT COUNT(*) FROM order_exceptions WHERE task_id=? AND category='ALLOCATION_EXPIRED' AND status='OPEN'",Integer.class,taskId);
            exception(order,str(lot,"line_id"),"ALLOCATION","ALLOCATION_EXPIRED","RELEASE","Expired allocation requires explicit operator release");
            if(existing==0)audit(order,"ALLOCATION_EXPIRED","Expired allocation requires explicit operator release",str(lot,"external_reference"),true,"oms-expiry-monitor");
        });
    }

    public List<EvidenceView> ledger(String orderId) {
        var order=order(orderId,false);access.require("oms:read",str(order,"scope_id"),str(order,"customer_id"));
        return db.query("SELECT e.*,s.status,s.detail FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? ORDER BY e.recorded_at,e.id",(r,n)->new EvidenceView(r.getString("id"),r.getString("line_id"),r.getString("kind"),r.getInt("quantity"),r.getString("external_reference"),r.getString("allocation_reference"),r.getString("shipment_reference"),r.getTimestamp("occurred_at").toInstant(),r.getTimestamp("expires_at")==null?null:r.getTimestamp("expires_at").toInstant(),r.getString("actor"),r.getString("reason"),r.getString("status"),r.getString("detail")),orderId);
    }
    public List<AllocationView> allocations(String orderId) {
        var order=order(orderId,false);access.require("oms:read",str(order,"scope_id"),str(order,"customer_id"));
        return db.query("SELECT * FROM allocation_lots WHERE order_id=? ORDER BY occurred_at,id",(r,n)->new AllocationView(r.getString("id"),r.getString("line_id"),r.getString("external_reference"),r.getInt("quantity"),r.getInt("consumed"),r.getInt("released"),r.getInt("quantity")-r.getInt("consumed")-r.getInt("released"),r.getTimestamp("expires_at")==null?null:r.getTimestamp("expires_at").toInstant()),orderId);
    }

    private void conflict(Map<String,Object> order,String id,String lineId,String kind,String detail) {
        db.update("UPDATE fulfillment_evidence_state SET status='CONFLICT',detail=? WHERE evidence_id=?",detail,id);
        exception(order,lineId,kind,"CONTRADICTORY_EVIDENCE","RECONCILE",detail);
        audit(order,"EVIDENCE_CONFLICT",detail,str(evidence(id),"external_reference"),true,access.actor());
    }
    private void exception(Map<String,Object> order,String lineId,String kind,String category,String next,String reason) {
        db.update("INSERT INTO order_exceptions(id,order_id,task_id,category,severity,next_action,reason) VALUES(?,?,?,?,'HIGH',?,?) ON CONFLICT(order_id,task_id,category) DO UPDATE SET status='OPEN',last_occurred_at=clock_timestamp(),version=order_exceptions.version+1,reason=EXCLUDED.reason",UUID.randomUUID().toString(),str(order,"id"),taskId(str(order,"id"),lineId,kind),category,next,reason);
    }
    private void resolve(String orderId,String lineId,String kind,String category) {
        db.update("UPDATE order_exceptions SET status='RESOLVED',last_occurred_at=clock_timestamp(),version=version+1 WHERE task_id=? AND category=? AND status='OPEN'",taskId(orderId,lineId,kind),category);
    }
    private String taskId(String orderId,String lineId,String kind) {
        var tasks=db.queryForList("SELECT id FROM order_tasks WHERE order_id=? AND line_id=? AND kind=?",orderId,lineId,"RELEASE".equals(kind)?"ALLOCATION":kind);
        if(tasks.isEmpty()) throw OrderProblem.missing();return str(tasks.getFirst(),"id");
    }
    private void remember(String scope,String key,String body,String orderId,String evidenceId) {
        db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)",scope,key,body,orderId);
        db.update("INSERT INTO fulfillment_evidence_receipts(scope_id,operation_key,evidence_id) VALUES(?,?,?)",scope,key,evidenceId);
    }
    private EvidenceReceipt receipt(String orderId,String evidenceId) {
        String status=db.queryForObject("SELECT status FROM fulfillment_evidence_state WHERE evidence_id=?",String.class,evidenceId);
        long version=db.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,orderId);
        return new EvidenceReceipt(orderId,evidenceId,status,version);
    }
    private void audit(Map<String,Object> order,String action,String reason,String reference,boolean bump,String actor) {
        String orderId=str(order,"id");
        if(bump) db.update("UPDATE orders SET version=version+1,updated_at=clock_timestamp(),updated_by=? WHERE id=?",actor,orderId);
        long version=db.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,orderId);
        db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version,evidence_reference) VALUES(?,?,?,clock_timestamp(),?,?,?,?)",UUID.randomUUID().toString(),orderId,actor,action,reason,version,reference);
        if(bump) {String eventId=UUID.randomUUID().toString();String payload=json.writeValueAsString(Map.of("eventId",eventId,"orderId",orderId,"scopeId",str(order,"scope_id"),"customerId",str(order,"customer_id"),"version",version,"action",action));db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES(?,'Order',?,'oms.order-updated.v1',?,clock_timestamp())",eventId,orderId,payload);}
    }
    private void validateKind(Map<String,Object> order,Map<String,Object> line,String kind) {
        var snapshot=json.readValue(str(order,"snapshot_json"),AcceptedOrderSnapshot.class);
        String type=snapshot.lines().stream().filter(l->l.sourceLineId().equals(str(line,"source_line_id"))).findFirst().orElseThrow().fulfillmentType();
        if((Set.of("ALLOCATION","RELEASE","SHIPMENT","DELIVERY").contains(kind) && !"PHYSICAL".equals(type)) || ("PROVISION".equals(kind) && !"DIGITAL".equals(type)) || ("SERVICE".equals(kind) && !"SERVICE".equals(type))) throw OrderProblem.invalid("Evidence kind does not match immutable fulfillment type");
    }
    private static void validate(EvidenceCommand c) {
        if(c==null)throw OrderProblem.invalid("Evidence required");text(c.operationKey(),200,"operation key");text(c.reason(),2000,"reason");text(c.lineId(),36,"line id");text(c.externalReference(),500,"external reference");
        if(!KINDS.contains(c.kind()==null?"":c.kind()) || c.quantity()<1 || c.expectedVersion()<0 || c.occurredAt()==null || c.occurredAt().isAfter(Instant.now().plusSeconds(300))) throw OrderProblem.invalid("Invalid evidence kind, quantity, version or timestamp");
        if(Set.of("SHIPMENT","RELEASE").contains(c.kind())) text(c.allocationReference(),500,"allocation reference");
        if("DELIVERY".equals(c.kind())) text(c.shipmentReference(),500,"shipment reference");
        if(c.allocationReference()!=null) text(c.allocationReference(),500,"allocation reference");
        if(c.shipmentReference()!=null) text(c.shipmentReference(),500,"shipment reference");
        if("ALLOCATION".equals(c.kind()) && c.expiresAt()==null) throw OrderProblem.invalid("Allocation expiry is required");
        if(c.expiresAt()!=null && (!"ALLOCATION".equals(c.kind()) || !c.expiresAt().isAfter(c.occurredAt()))) throw OrderProblem.invalid("Invalid allocation expiry");
    }
    private String semantic(String orderId,EvidenceCommand c) {
        return OrderService.hash(json.writeValueAsString(Arrays.asList(orderId,c.lineId(),c.kind(),c.quantity(),c.externalReference(),c.occurredAt(),c.allocationReference(),c.shipmentReference(),c.expiresAt())));
    }
    private Map<String,Object> order(String id,boolean lock) {return one("SELECT * FROM orders WHERE id=? AND is_deleted=false"+(lock?" FOR UPDATE":""),id);}
    private Map<String,Object> line(String id) {return one("SELECT * FROM order_lines WHERE id=?",id);}
    private Map<String,Object> evidence(String id) {return one("SELECT * FROM fulfillment_evidence WHERE id=?",id);}
    private Map<String,Object> one(String query,Object...args) {var rows=db.queryForList(query,args);if(rows.isEmpty())throw OrderProblem.missing();return rows.getFirst();}
    private static String str(Map<String,Object> r,String key){return (String)r.get(key);}
    private static int integer(Map<String,Object> r,String key){return ((Number)r.get(key)).intValue();}
    private static long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
    private static Instant instant(Map<String,Object> r,String key){Object value=r.get(key);return value==null?null:((Timestamp)value).toInstant();}
    private static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}
    private static void text(String value,int max,String field){if(value==null || value.isBlank() || value.length()>max)throw OrderProblem.invalid("Invalid "+field);}
}
