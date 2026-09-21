package com.opencrm.order.service;

import com.opencrm.order.audit.CommandAudit;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.order.domain.*;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.security.OrderAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class OrderService {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final ObjectMapper json;
 private final OrderAccess access;
 private final boolean intakeEnabled;
 private final Set<String> trustedScopes;
 private final com.opencrm.order.operations.IntakeAdmission admission;
 private final SnapshotValidator validator=new SnapshotValidator();
 public OrderService(JdbcTemplate db,PlatformTransactionManager manager,ObjectMapper json,OrderAccess access,
   boolean intakeEnabled,String trustedScopes) {
  this(db,manager,json,access,new com.opencrm.order.operations.IntakeAdmission(db,trustedScopes.split(",")[0],1048576,10000),intakeEnabled,trustedScopes);
 }
 @org.springframework.beans.factory.annotation.Autowired
 public OrderService(JdbcTemplate db,PlatformTransactionManager manager,ObjectMapper json,OrderAccess access,
   com.opencrm.order.operations.IntakeAdmission admission,
   @Value("${oms.intake-enabled:false}") boolean intakeEnabled,@Value("${oms.trusted-source-scopes:}") String trustedScopes) {
  this.db=db;this.tx=new TransactionTemplate(manager);this.json=json;this.access=access;this.intakeEnabled=intakeEnabled;
  this.admission=admission;
  this.trustedScopes=new HashSet<>(Arrays.asList(trustedScopes.split(",")));
 }
 /** Called only by the configured, authenticated Kafka consumer; never exposed as an HTTP operation. */
 public void acceptTrustedSnapshot(String payload,String expectedHash) {
  if(payload==null) throw OrderProblem.invalid("Accepted snapshot required");
  if(!hash(payload).equals(expectedHash)) throw OrderProblem.invalid("Snapshot hash mismatch");
  // A tightened admission policy must not turn an already acknowledged receipt into a poison replay.
  var accepted=db.queryForList("SELECT scope_id,payload FROM snapshot_inbox WHERE payload_hash=?",expectedHash);
  if(!accepted.isEmpty()) {
   if(!trustedScopes.contains(str(accepted.getFirst(),"scope_id"))) throw new OrderProblem(403,"Source scope denied");
   if(!payload.equals(str(accepted.getFirst(),"payload"))) throw OrderProblem.conflict("Snapshot hash collision");
   return;
  }
  admission.checkPayload(payload);
  var s=decode(payload); validator.validate(s);
  if(!trustedScopes.contains(s.scopeId()) || s.scopeId().isBlank()) throw new OrderProblem(403,"Source scope denied");
  try {
   tx.executeWithoutResult(t -> {
    var rows=db.queryForList("SELECT payload_hash FROM snapshot_inbox WHERE snapshot_id=?",s.snapshotId());
    if(!rows.isEmpty()) { if(!expectedHash.equals(rows.getFirst().get("payload_hash"))) throw OrderProblem.conflict("Snapshot identity reused");return; }
    admission.checkNewIntake();
    db.update("INSERT INTO snapshot_inbox(snapshot_id,scope_id,customer_id,quote_id,accepted_revision,payload,payload_hash,received_at) VALUES(?,?,?,?,?,?,?,?)",s.snapshotId(),s.scopeId(),s.customerId(),s.quoteId(),s.acceptedRevision(),payload,expectedHash,Timestamp.from(Instant.now()));
   });
  } catch(DataIntegrityViolationException e) {
   var rows=db.queryForList("SELECT payload_hash FROM snapshot_inbox WHERE snapshot_id=?",s.snapshotId());
   if(rows.isEmpty() || !expectedHash.equals(rows.getFirst().get("payload_hash"))) throw OrderProblem.conflict("Source revision already captured");
  }
 }
 public View fromQuote(FromQuote request,String operationKey) {
  checkText(operationKey,"Idempotency-Key",200);
  try { return tx.execute(t -> convert(request,operationKey)); }
  catch(DataIntegrityViolationException e) {
   // Concurrent requests for different sources can contend on a caller's idempotency key.
   return tx.execute(t -> convert(request,operationKey));
  }
 }
 private View convert(FromQuote r,String key) {
  var sources=db.queryForList("SELECT * FROM snapshot_inbox WHERE snapshot_id=? FOR UPDATE",r.snapshotId());
  if(sources.isEmpty()) throw OrderProblem.missing();
  var source=sources.getFirst();var s=decode((String)source.get("payload"));
  access.require("oms:submit",s.scopeId(),s.customerId());
  if((!s.quoteId().equals(r.quoteId()) && !s.quoteNumber().equals(r.quoteId())) || s.acceptedRevision()!=r.acceptedRevision()) throw OrderProblem.missing();
  String body=hash("CONVERT|"+s.snapshotId()+"|"+s.quoteId()+"|"+s.acceptedRevision());
  var replay=idempotency(s.scopeId(),key,body);if(replay!=null) return view(replay);
  var existing=db.queryForList("SELECT id FROM orders WHERE scope_id=? AND quote_id=? AND accepted_revision=?",s.scopeId(),s.quoteId(),s.acceptedRevision());
  if(!existing.isEmpty()) { String id=(String)existing.getFirst().get("id");long before=((Number)row(id,true).get("version")).longValue();remember(s.scopeId(),key,body,id);CommandAudit.record(db,id,key,access.actor(),"Existing accepted order referenced",before);return view(id); }
  if(!intakeEnabled) throw new OrderProblem(422,"New order intake is disabled");
  admission.checkNewIntake();
  var replacement=s.acceptedRevision()>1 ? com.opencrm.order.replacement.ReplacementLineage.requirePermit(db,s) : null;
  var holds=validator.validate(s);String id=UUID.randomUUID().toString();Instant now=Instant.now();String actor=access.actor();
  db.update("INSERT INTO orders(id,order_number,scope_id,customer_id,quote_id,quote_number,accepted_revision,snapshot_id,snapshot_json,snapshot_hash,status,created_at,updated_at,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,"ORD-"+id,s.scopeId(),s.customerId(),s.quoteId(),s.quoteNumber(),s.acceptedRevision(),s.snapshotId(),source.get("payload"),source.get("payload_hash"),"DRAFT",Timestamp.from(now),Timestamp.from(now),actor,actor);
  for(var line:s.lines()) {
   String lineId=UUID.randomUUID().toString();db.update("INSERT INTO order_lines(id,order_id,source_line_id,description,ordered) VALUES(?,?,?,?,?)",lineId,id,line.sourceLineId(),line.description()==null?"[Missing source description]":line.description(),line.quantity());
   if(holds.contains("MAPPING:"+line.sourceLineId())) continue;
   List<String> kinds=new ArrayList<>(switch(line.fulfillmentType()) { case "PHYSICAL" -> List.of("ALLOCATION","SHIPMENT","DELIVERY");case "DIGITAL" -> List.of("PROVISION");case "SERVICE" -> List.of("SERVICE");default -> throw OrderProblem.invalid("Unsupported fulfillment mapping"); });
   if("RECURRING".equals(line.chargeType())) kinds.add("ACTIVATION");kinds.add("FINANCE_ACK");
   String predecessor=null;
   for(String kind:kinds) {
    String taskId=UUID.randomUUID().toString();
    db.update("INSERT INTO order_tasks(id,order_id,line_id,kind,template_version,status) VALUES(?,?,?,?,1,'BLOCKED')",taskId,id,lineId,kind);
    if(predecessor!=null) db.update("INSERT INTO order_task_dependencies(task_id,predecessor_id) VALUES(?,?)",taskId,predecessor);
    predecessor=taskId;
   }
  }
  for(String hold:holds) db.update("INSERT INTO order_holds(order_id,code) VALUES(?,?)",id,hold);
  if(replacement!=null) com.opencrm.order.replacement.ReplacementLineage.link(db,replacement,id);
  remember(s.scopeId(),key,body,id);audit(id,"CREATED","Accepted snapshot captured",0,null);CommandAudit.record(db,id,key,actor,"Accepted snapshot captured",0);return view(id);
 }
 public View get(String id) { return tx.execute(t -> {var row=row(id,false);access.require("oms:read",str(row,"scope_id"),str(row,"customer_id"));return view(id);}); }
 public record Search(List<View> orders,long total) {}
 public Search list(int page,int size,String query,String status,String customerId) {
  return list(page,size,query,status,customerId,null,null);
 }
 public Search list(int page,int size,String query,String status,String customerId,String externalReference,Boolean hasExceptions) {
  if(page<0 || size<1 || size>100 || page>100000) throw OrderProblem.invalid("Invalid pagination");
  String scope=access.scopeId();Set<String> accounts=access.customerIds();
  if(accounts.isEmpty()) throw new OrderProblem(403,"Account claims required");
  for(String account:accounts) access.require("oms:read",scope,account);
  if(customerId!=null && !accounts.contains(customerId)) throw OrderProblem.missing();
  var parameters=new ArrayList<Object>();parameters.add(scope);parameters.addAll(accounts);
  String sql="SELECT id FROM orders WHERE is_deleted=false AND scope_id=? AND customer_id IN ("+String.join(",",Collections.nCopies(accounts.size(),"?"))+")";
  if(customerId!=null) {sql+=" AND customer_id=?";parameters.add(customerId);}
  if(status!=null && !status.isBlank()) {sql+=" AND status=?";parameters.add(status);}
  if(query!=null && !query.isBlank()) {sql+=" AND (LOWER(order_number) LIKE ? OR LOWER(quote_number) LIKE ?)";String q="%"+query.toLowerCase(Locale.ROOT)+"%";parameters.add(q);parameters.add(q);}
  if(externalReference!=null && !externalReference.isBlank()) {
   checkText(externalReference,"external reference",500);
   sql+=" AND (EXISTS (SELECT 1 FROM fulfillment_evidence e WHERE e.order_id=orders.id AND (e.external_reference=? OR e.allocation_reference=? OR e.shipment_reference=?)) OR EXISTS (SELECT 1 FROM finance_acknowledgments f JOIN finance_requests r ON r.request_key=f.request_key WHERE r.order_id=orders.id AND f.external_reference=?) OR EXISTS (SELECT 1 FROM order_audit a WHERE a.order_id=orders.id AND a.evidence_reference=?))";
   for(int i=0;i<5;i++)parameters.add(externalReference);
  }
  if(hasExceptions!=null)sql+=" AND "+(hasExceptions?"":"NOT ")+"EXISTS (SELECT 1 FROM order_exceptions x WHERE x.order_id=orders.id AND x.status='OPEN')";
  long total=db.queryForObject(sql.replace("SELECT id FROM orders","SELECT COUNT(*) FROM orders"),Long.class,parameters.toArray());
  sql+=" ORDER BY created_at DESC,id LIMIT ? OFFSET ?";parameters.add(size);parameters.add((long)page*size);
  return new Search(db.queryForList(sql,parameters.toArray()).stream().map(r->view(str(r,"id"))).toList(),total);
 }
 public List<Task> plan(String id) {get(id);return db.query("SELECT * FROM order_tasks WHERE order_id=? ORDER BY line_id,kind",(r,n)->new Task(r.getString("id"),r.getString("line_id"),r.getString("kind"),r.getInt("template_version"),r.getString("status"),r.getTimestamp("dispatched_at")==null?null:r.getTimestamp("dispatched_at").toInstant()),id);}
 public List<Timeline> timeline(String id) {get(id);return db.query("SELECT * FROM order_audit WHERE order_id=? ORDER BY version,occurred_at,id",(r,n)->new Timeline(r.getString("actor"),r.getTimestamp("occurred_at").toInstant(),r.getString("action"),r.getString("reason"),r.getLong("version"),r.getString("evidence_reference")),id);}
 public View submit(String id,Command command) {return mutate(id,"SUBMIT","oms:submit",command,()->{
  var r=row(id,false);if(!"DRAFT".equals(str(r,"status"))) throw OrderProblem.conflict("Only draft orders may submit");
  if(hasHolds(id,r)) throw OrderProblem.invalid("Order has unresolved holds");
  validator.validate(decode(str(r,"snapshot_json")));
  db.update("UPDATE orders SET status='SUBMITTED' WHERE id=?",id);
  db.update("UPDATE order_tasks SET status='READY' WHERE order_id=? AND status='BLOCKED' AND id NOT IN (SELECT task_id FROM order_task_dependencies)",id);
 });}
 public View hold(String id,Hold command) {return mutate(id,"HOLD:"+command.active(),"oms:hold",new Command(command.expectedVersion(),command.reason(),command.operationKey()),()->{
  var r=row(id,false);if(Set.of("COMPLETED","CANCELLED").contains(str(r,"status"))) throw OrderProblem.conflict("Terminal order");
  db.update("UPDATE orders SET manual_hold=? WHERE id=?",command.active(),id);
 });}
 public View cancel(String id,Cancellation c) {return mutate(id,"CANCEL:"+c.lineId()+":"+c.quantity(),"oms:cancel",new Command(c.expectedVersion(),c.reason(),c.operationKey()),()->{
  var r=row(id,false);if(Set.of("COMPLETED","CANCELLED").contains(str(r,"status"))) throw OrderProblem.conflict("Terminal order");
  if(c.quantity()<1) throw OrderProblem.invalid("Positive cancellation quantity required");
  var lines=db.queryForList("SELECT * FROM order_lines WHERE id=? AND order_id=?",c.lineId(),id);if(lines.isEmpty()) throw OrderProblem.missing();var line=lines.getFirst();
  if(c.quantity()>num(line,"ordered")-num(line,"cancelled")-Math.max(num(line,"fulfilled"),num(line,"shipped"))) throw OrderProblem.invalid("Cancellation exceeds remaining quantity");
  if(c.quantity()>num(line,"ordered")-num(line,"cancelled")-num(line,"fulfilled")-num(line,"allocated")) throw OrderProblem.conflict("Allocated quantity requires explicit release before cancellation");
  int uncertain=db.queryForObject("SELECT COUNT(*) FROM order_tasks t LEFT JOIN task_operations op ON op.task_id=t.id WHERE t.order_id=? AND t.line_id=? AND (t.status='UNKNOWN' OR op.status='UNKNOWN' OR (op.status='CLAIMED' AND op.lease_until>clock_timestamp()) OR (op.status='WAITING_EXTERNAL' AND op.evidence_reference IS NULL) OR (t.dispatched_at IS NOT NULL AND op.operation_key IS NULL))",Integer.class,id,c.lineId());
  uncertain+=db.queryForObject("SELECT COUNT(*) FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? AND e.line_id=? AND s.status IN ('PENDING','CONFLICT')",Integer.class,id,c.lineId());
  if(uncertain>0) throw OrderProblem.conflict("Unreconciled or active work prevents cancellation");
  db.update("UPDATE order_lines SET cancelled=cancelled+?,status=CASE WHEN cancelled+?=ordered THEN 'CANCELLED' ELSE status END WHERE id=?",c.quantity(),c.quantity(),c.lineId());
  if(num(line,"cancelled")+c.quantity()==num(line,"ordered")) {
   db.update("UPDATE order_tasks SET status='CANCELLED' WHERE line_id=?",c.lineId());
   db.update("UPDATE task_operations SET status='CANCELLED',lease_token=NULL,lease_until=NULL WHERE task_id IN (SELECT id FROM order_tasks WHERE line_id=?)",c.lineId());
  } else {
   int target=num(line,"ordered")-num(line,"cancelled")-c.quantity();
   var proofs=db.queryForList("SELECT t.id,t.kind FROM order_tasks t JOIN task_operations op ON op.task_id=t.id WHERE t.line_id=? AND t.status='WAITING_EXTERNAL' AND op.evidence_reference IS NOT NULL AND op.status<>'UNKNOWN'",c.lineId());
   for(var proof:proofs) {
    boolean met=switch(str(proof,"kind")) {case "ALLOCATION" -> num(line,"allocated")+num(line,"shipped")>=target;case "SHIPMENT" -> num(line,"shipped")>=target;case "DELIVERY" -> num(line,"delivered")>=target;case "SERVICE","PROVISION" -> num(line,"fulfilled")>=target;default -> false;};
    if(met) {db.update("UPDATE order_tasks SET status='SUCCESS' WHERE id=?",str(proof,"id"));db.update("UPDATE task_operations SET status='SUCCESS',lease_token=NULL,lease_until=NULL WHERE task_id=?",str(proof,"id"));}
   }
   db.update("UPDATE order_tasks SET status='READY' WHERE line_id=? AND status='BLOCKED' AND NOT EXISTS (SELECT 1 FROM order_task_dependencies d JOIN order_tasks p ON p.id=d.predecessor_id WHERE d.task_id=order_tasks.id AND p.status NOT IN ('SUCCESS','CANCELLED'))",c.lineId());
  }
  if(db.queryForObject("SELECT COUNT(*) FROM order_lines WHERE order_id=? AND cancelled<>ordered",Integer.class,id)==0) db.update("UPDATE orders SET status='CANCELLED' WHERE id=?",id);
 });}
 private View mutate(String id,String action,String permission,Command c,Runnable change) {
  checkText(c.operationKey(),"operationKey",200);checkText(c.reason(),"reason",2000);
  try { return tx.execute(t->{var r=row(id,true);String scope=str(r,"scope_id");access.require(permission,scope,str(r,"customer_id"));
   String body=hash(json.writeValueAsString(List.of(id,action,c)));String replay=idempotency(scope,c.operationKey(),body);if(replay!=null) return view(replay);
   long version=((Number)r.get("version")).longValue();if(version!=c.expectedVersion()) throw OrderProblem.conflict("Order version changed");
   change.run();db.update("UPDATE orders SET version=version+1,updated_at=?,updated_by=? WHERE id=?",Timestamp.from(Instant.now()),access.actor(),id);
   remember(scope,c.operationKey(),body,id);audit(id,action.split(":")[0],c.reason(),version+1,null);CommandAudit.record(db,id,c.operationKey(),access.actor(),c.reason(),version);return view(id);
  }); } catch(org.springframework.dao.DuplicateKeyException e) { throw OrderProblem.conflict("Operation key already used by a concurrent command"); }
 }
 private Map<String,Object> row(String id,boolean lock) {var rows=db.queryForList("SELECT * FROM orders WHERE id=? AND is_deleted=false"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty()) throw OrderProblem.missing();return rows.getFirst();}
 private View view(String id) {var r=row(id,false);List<Line> lines=db.query("SELECT * FROM order_lines WHERE order_id=? ORDER BY source_line_id",(l,n)->new Line(l.getString("id"),l.getString("description"),l.getInt("ordered"),l.getInt("cancelled"),l.getInt("shipped"),l.getInt("delivered"),l.getInt("ordered")-l.getInt("cancelled")-l.getInt("fulfilled"),l.getInt("allocated"),l.getString("status")),id);List<String> holds=new ArrayList<>(db.queryForList("SELECT code FROM order_holds WHERE order_id=? ORDER BY code",String.class,id));if(Boolean.TRUE.equals(r.get("manual_hold"))) holds.add("MANUAL");return new View(id,str(r,"order_number"),str(r,"quote_id"),str(r,"quote_number"),str(r,"customer_id"),str(r,"scope_id"),str(r,"status"),((Number)r.get("version")).longValue(),decode(str(r,"snapshot_json")),lines,List.copyOf(holds),str(r,"finance_status"));}
 private boolean hasHolds(String id,Map<String,Object> r) {return Boolean.TRUE.equals(r.get("manual_hold")) || db.queryForObject("SELECT COUNT(*) FROM order_holds WHERE order_id=?",Integer.class,id)>0;}
 private String idempotency(String scope,String key,String hash) {var rows=db.queryForList("SELECT body_hash,order_id FROM order_idempotency WHERE scope_id=? AND operation_key=?",scope,key);if(rows.isEmpty()) return null;if(!hash.equals(rows.getFirst().get("body_hash"))) throw OrderProblem.conflict("Operation key body conflict");return str(rows.getFirst(),"order_id");}
 private void remember(String scope,String key,String hash,String id) {db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)",scope,key,hash,id);}
 private void audit(String id,String action,String reason,long version,String reference) {db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version,evidence_reference) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,access.actor(),Timestamp.from(Instant.now()),action,reason,version,reference);
  var order=row(id,false);String eventId=UUID.randomUUID().toString();
  String payload=json.writeValueAsString(Map.of("eventId",eventId,"orderId",id,"scopeId",str(order,"scope_id"),"customerId",str(order,"customer_id"),"version",version,"action",action));
  db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES(?,'Order',?,'oms.order-updated.v1',?,?)",eventId,id,payload,Timestamp.from(Instant.now()));
 }
 private AcceptedOrderSnapshot decode(String payload) {try {return json.readValue(payload,AcceptedOrderSnapshot.class);}catch(Exception e) {throw OrderProblem.invalid("Invalid accepted snapshot payload");}}
 public static String hash(String body) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));}catch(Exception e) {throw new IllegalStateException(e);}}
 private static String str(Map<String,Object> r,String key) {return (String)r.get(key);}
 private static int num(Map<String,Object> r,String key) {return ((Number)r.get(key)).intValue();}
 private static void checkText(String s,String field,int max) {if(s==null || s.isBlank() || s.length()>max) throw OrderProblem.invalid("Invalid "+field);}
}
