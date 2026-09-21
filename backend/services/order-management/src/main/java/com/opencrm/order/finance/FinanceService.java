package com.opencrm.order.finance;

import com.opencrm.order.audit.CommandAudit;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import com.opencrm.order.execution.ExecutionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.opencrm.order.finance.FinanceDtos.*;

/** Manual finance boundary: request intent is never an external acknowledgment. */
@Service
public class FinanceService {
 private final JdbcTemplate db; private final TransactionTemplate tx; private final ObjectMapper json;
 private final OrderAccess access; private final OrderService orders; private final ExecutionService execution;
 public FinanceService(JdbcTemplate db,PlatformTransactionManager manager,ObjectMapper json,OrderAccess access,OrderService orders,ExecutionService execution){this.db=db;this.tx=new TransactionTemplate(manager);this.json=json;this.access=access;this.orders=orders;this.execution=execution;}
 public List<RequestView> list(String orderId){orders.get(orderId);return db.queryForList("SELECT * FROM finance_requests WHERE order_id=? ORDER BY line_id",orderId).stream().map(r->new RequestView(str(r,"request_key"),str(r,"line_id"),num(r,"quantity"),acknowledged(str(r,"request_key")),status(r),str(r,"payload"))).toList();}
 public View prepare(String orderId,String lineId,Command command){
  text(lineId,36,"line");
  return command(orderId,command,"FINANCE_PREPARE",List.of(lineId,command),order->{
   var line=line(orderId,lineId);var task=released(order,line);
   var prior=db.queryForList("SELECT * FROM finance_requests WHERE line_id=?",lineId);
   if(!prior.isEmpty()) {if(Set.of("UNKNOWN","REJECTED").contains(status(prior.getFirst()))) throw OrderProblem.conflict("Reconcile the existing finance request; redispatch is held");return;}
   if(Boolean.TRUE.equals(order.get("manual_hold")) || db.queryForObject("SELECT count(*) FROM order_holds WHERE order_id=?",Integer.class,orderId)>0) throw OrderProblem.conflict("Finance request held");
   if(!Set.of("SUBMITTED","IN_PROGRESS").contains(str(order,"status"))) throw OrderProblem.conflict("Order cannot create finance request");
   var snapshot=json.readValue(str(order,"snapshot_json"),AcceptedOrderSnapshot.class);
   var source=snapshot.lines().stream().filter(l->l.sourceLineId().equals(str(line,"source_line_id"))).findFirst().orElseThrow(OrderProblem::missing);
   int quantity=num(line,"ordered")-num(line,"cancelled");
   long gross=Math.multiplyExact(cents(source.unitPrice()),quantity);
   long discount=portion(cents(source.discount()),quantity,source.quantity());
   long tax=portion(cents(source.tax()),quantity,source.quantity());
   long total=Math.addExact(Math.subtractExact(gross,discount),tax);
   String key=OrderService.hash(json.writeValueAsString(List.of(str(order,"scope_id"),str(order,"quote_id"),num(order,"accepted_revision"),source.sourceLineId(),"FINANCE")));
   var payload=new LinkedHashMap<String,Object>();payload.put("schemaVersion",1);payload.put("requestKey",key);payload.put("orderId",orderId);payload.put("lineId",lineId);payload.put("scopeId",str(order,"scope_id"));payload.put("customerId",str(order,"customer_id"));payload.put("quoteId",snapshot.quoteId());payload.put("acceptedRevision",snapshot.acceptedRevision());payload.put("snapshotHash",str(order,"snapshot_hash"));payload.put("sourceLine",source);payload.put("quantity",quantity);payload.put("currency",snapshot.currency());payload.put("grossCents",gross);payload.put("discountCents",discount);payload.put("taxCents",tax);payload.put("totalCents",total);payload.put("taxEvidence",snapshot.taxEvidence());payload.put("milestones",db.queryForList("SELECT t.kind,op.evidence_reference,op.completed_at FROM order_tasks t JOIN task_operations op ON op.task_id=t.id WHERE t.line_id=? AND t.kind<>'FINANCE_ACK' AND t.status='SUCCESS' ORDER BY t.kind",lineId));
   String frozen=json.writeValueAsString(payload);
   db.update("INSERT INTO finance_requests(request_key,order_id,line_id,scope_id,quantity,gross_cents,discount_cents,tax_cents,total_cents,payload) VALUES(?,?,?,?,?,?,?,?,?,?)",key,orderId,lineId,str(order,"scope_id"),quantity,gross,discount,tax,total,frozen);
   String event=UUID.randomUUID().toString();payload.put("eventId",event);outbox(event,orderId,"oms.finance-requested.v1",json.writeValueAsString(payload));
   db.update("UPDATE orders SET finance_status=CASE WHEN finance_status='PENDING' THEN 'REQUESTED' ELSE finance_status END,status='IN_PROGRESS' WHERE id=?",orderId);
  });
 }
 public View acknowledge(String orderId,Acknowledgment a){
  if(a==null) throw OrderProblem.invalid("Acknowledgment required");
  text(a.lineId(),36,"line");text(a.externalReference(),500,"external reference");
  if(a.quantity()<1 || a.occurredAt()==null || a.occurredAt().isAfter(Instant.now().plusSeconds(300)) || !Set.of("ACKNOWLEDGED","REJECTED","UNKNOWN").contains(a.outcome()==null?"":a.outcome())) throw OrderProblem.invalid("Valid quantity, timestamp and explicit outcome required");
  return command(orderId,new Command(a.expectedVersion(),a.reason(),a.operationKey()),"FINANCE_EVIDENCE",a,order->{
   var line=line(orderId,a.lineId());var task=released(order,line);
   var request=one("SELECT * FROM finance_requests WHERE order_id=? AND line_id=?",orderId,a.lineId());String key=str(request,"request_key");
   if(a.requestKey()!=null && !key.equals(a.requestKey())) throw OrderProblem.conflict("Finance request does not match line");
   String evidenceHash=OrderService.hash(json.writeValueAsString(List.of(key,a.quantity(),a.externalReference(),a.occurredAt(),a.outcome())));
   var prior=db.queryForList("SELECT body_hash FROM finance_acknowledgments WHERE scope_id=? AND external_reference=?",str(order,"scope_id"),a.externalReference());
   if(!prior.isEmpty()){if(!evidenceHash.equals(prior.getFirst().get("body_hash")))throw OrderProblem.conflict("External finance reference body conflict");return;}
   int before=acknowledged(key),target=num(request,"quantity");
   if(a.quantity()>target || ("ACKNOWLEDGED".equals(a.outcome()) && a.quantity()>target-before))throw OrderProblem.conflict("Finance quantity exceeds frozen obligation");
   long gross=0,discount=0,tax=0;
   if("ACKNOWLEDGED".equals(a.outcome())){gross=delta(request,"gross_cents",before,a.quantity());discount=delta(request,"discount_cents",before,a.quantity());tax=delta(request,"tax_cents",before,a.quantity());}
   db.update("INSERT INTO finance_acknowledgments(id,request_key,scope_id,external_reference,body_hash,quantity,outcome,gross_cents,discount_cents,tax_cents,total_cents,occurred_at,actor,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),key,str(order,"scope_id"),a.externalReference(),evidenceHash,a.quantity(),a.outcome(),gross,discount,tax,gross-discount+tax,Timestamp.from(a.occurredAt()),access.actor(),a.reason());
   if(!"ACKNOWLEDGED".equals(a.outcome())){
    if(before<target){db.update("UPDATE order_tasks SET status='UNKNOWN' WHERE id=? AND status<>'SUCCESS'",str(task,"id"));exception(order,task,a.outcome(),a.reason());}
    else exception(order,task,"CONTRADICTORY",a.reason());
   }
   refreshStatus(orderId);
   if("ACKNOWLEDGED".equals(a.outcome()) && before+a.quantity()==target){
    execution.completeManualTask(orderId,a.lineId(),"FINANCE_ACK",a.externalReference(),a.occurredAt());
    db.update("UPDATE order_exceptions SET status='RESOLVED',version=version+1,last_occurred_at=CURRENT_TIMESTAMP WHERE task_id=? AND category IN ('FINANCE_UNKNOWN','FINANCE_REJECTED') AND status='OPEN'",str(task,"id"));
   }
  });
 }
 private View command(String id,Command c,String action,Object body,java.util.function.Consumer<Map<String,Object>> mutation){
  if(c==null || c.expectedVersion()<0)throw OrderProblem.invalid("Valid command required");text(c.reason(),2000,"reason");text(c.operationKey(),200,"operation key");
  try{return tx.execute(t->{
   var order=one("SELECT * FROM orders WHERE id=? AND is_deleted=false FOR UPDATE",id);String scope=str(order,"scope_id");access.require("oms:finance",scope,str(order,"customer_id"));
   String hash=OrderService.hash(json.writeValueAsString(List.of(id,action,body)));
   var prior=db.queryForList("SELECT body_hash FROM order_idempotency WHERE scope_id=? AND operation_key=?",scope,c.operationKey());
   if(!prior.isEmpty()){if(!hash.equals(prior.getFirst().get("body_hash")))throw OrderProblem.conflict("Operation key body conflict");return orders.get(id);}
   if(((Number)order.get("version")).longValue()!=c.expectedVersion())throw OrderProblem.conflict("Order version changed");
   mutation.accept(order);
   db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)",scope,c.operationKey(),hash,id);
   long version=db.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,id)+1;
   db.update("UPDATE orders SET version=?,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?",version,access.actor(),id);
   db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,?)",UUID.randomUUID().toString(),id,access.actor(),action,c.reason(),version);
   String event=UUID.randomUUID().toString();outbox(event,id,"oms.order-updated.v1",json.writeValueAsString(Map.of("eventId",event,"orderId",id,"scopeId",scope,"customerId",str(order,"customer_id"),"version",version,"action",action)));
   CommandAudit.record(db,id,c.operationKey(),access.actor(),c.reason(),c.expectedVersion());
   return orders.get(id);
  });}catch(DataIntegrityViolationException e){throw OrderProblem.conflict("Concurrent finance identity conflict");}
 }
 private Map<String,Object> released(Map<String,Object> order,Map<String,Object> line){
  var task=one("SELECT * FROM order_tasks WHERE order_id=? AND line_id=? AND kind='FINANCE_ACK'",str(order,"id"),str(line,"id"));
  int due=num(line,"ordered")-num(line,"cancelled");
  if(due<=0 || num(line,"fulfilled")<due || num(line,"delivered")<num(line,"shipped") || Set.of("BLOCKED","CANCELLED").contains(str(task,"status")) || db.queryForObject("SELECT count(*) FROM order_task_dependencies d JOIN order_tasks p ON p.id=d.predecessor_id WHERE d.task_id=? AND p.status NOT IN ('SUCCESS','CANCELLED')",Integer.class,str(task,"id"))>0)throw OrderProblem.conflict("Finance obligation is not released and fulfilled");
  return task;
 }
 private void refreshStatus(String orderId){
  var requests=db.queryForList("SELECT * FROM finance_requests WHERE order_id=?",orderId);long acknowledged=0;boolean complete=true,unknown=false,rejected=false;
  for(var r:requests){String status=status(r);acknowledged+=acknowledged(str(r,"request_key"));complete&="ACKNOWLEDGED".equals(status);unknown|="UNKNOWN".equals(status);rejected|="REJECTED".equals(status);}
  int due=db.queryForObject("SELECT count(*) FROM order_lines WHERE order_id=? AND ordered>cancelled",Integer.class,orderId);
  String status=complete && requests.size()==due?"ACKNOWLEDGED":unknown?"UNKNOWN":rejected?"REJECTED":acknowledged>0?"PARTIAL":"PENDING";
  db.update("UPDATE orders SET finance_status=? WHERE id=?",status,orderId);
 }
 private String status(Map<String,Object> request){String key=str(request,"request_key");int count=acknowledged(key);if(count==num(request,"quantity"))return "ACKNOWLEDGED";
  var outcomes=db.queryForList("SELECT DISTINCT outcome FROM finance_acknowledgments WHERE request_key=?",String.class,key);
  if(outcomes.contains("UNKNOWN"))return "UNKNOWN";if(outcomes.contains("REJECTED"))return "REJECTED";return count>0?"PARTIAL":"PENDING";}
 private void exception(Map<String,Object> o,Map<String,Object> task,String outcome,String reason){db.update("INSERT INTO order_exceptions(id,order_id,task_id,category,severity,next_action,reason) VALUES(?,?,?,?, 'HIGH','RECONCILE',?) ON CONFLICT(order_id,task_id,category) DO UPDATE SET status='OPEN',reason=EXCLUDED.reason,last_occurred_at=CURRENT_TIMESTAMP,version=order_exceptions.version+1",UUID.randomUUID().toString(),str(o,"id"),str(task,"id"),"FINANCE_"+outcome,reason);}
 private int acknowledged(String key){return db.queryForObject("SELECT COALESCE(sum(quantity),0) FROM finance_acknowledgments WHERE request_key=? AND outcome='ACKNOWLEDGED'",Integer.class,key);}
 private long delta(Map<String,Object> request,String amount,int before,int quantity){long total=((Number)request.get(amount)).longValue();return portion(total,before+quantity,num(request,"quantity"))-portion(total,before,num(request,"quantity"));}
 static long portion(long total,int quantity,int denominator){return BigInteger.valueOf(total).multiply(BigInteger.valueOf(quantity)).divide(BigInteger.valueOf(denominator)).longValueExact();}
 private static long cents(String value){return new BigDecimal(value).movePointRight(2).longValueExact();}
 private void outbox(String event,String orderId,String type,String payload){db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES(?,'Order',?,?,?,CURRENT_TIMESTAMP)",event,orderId,type,payload);}
 private Map<String,Object> line(String orderId,String lineId){return one("SELECT * FROM order_lines WHERE id=? AND order_id=?",lineId,orderId);}
 private Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw OrderProblem.missing();return rows.getFirst();}
 private static String str(Map<String,Object> r,String k){return (String)r.get(k);}private static int num(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
 private static void text(String value,int max,String label){if(value==null || value.isBlank() || value.length()>max)throw OrderProblem.invalid("Invalid "+label);}
}
