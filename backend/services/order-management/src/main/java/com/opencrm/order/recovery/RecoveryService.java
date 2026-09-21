package com.opencrm.order.recovery;

import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.Command;
import com.opencrm.order.execution.ExecutionService;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static com.opencrm.order.recovery.RecoveryDtos.*;

/** Recovery records operator decisions; it never manufactures external completion evidence. */
@Service
public class RecoveryService {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final ObjectMapper json;
 private final OrderAccess access;
 private final ExecutionService execution;
 private final Set<String> scopes;
 public RecoveryService(JdbcTemplate db,PlatformTransactionManager manager,ObjectMapper json,OrderAccess access,ExecutionService execution,@Value("${oms.recovery.scopes:}") String scopes) {
  this.db=db;this.tx=new TransactionTemplate(manager);this.json=json;this.access=access;this.execution=execution;
  this.scopes=new HashSet<>();for(String scope:scopes.split(","))if(!scope.isBlank())this.scopes.add(scope.trim());
 }
 public Operation abandon(String id,String originalKey,RecoveryCommand command) {
  text(originalKey,200,"original operation key");
  if(originalKey.equals(command.operationKey()))throw OrderProblem.invalid("Recovery requires a separate operation key");
  return mutate(id,"ABANDON",originalKey,command,null,order->{
   String scope=str(order,"scope_id");
   if(count("SELECT COUNT(*) FROM task_operations WHERE operation_key=?",originalKey)>0)throw OrderProblem.conflict("External operations require evidence reconciliation, not abandonment");
   if(count("SELECT COUNT(*) FROM order_idempotency WHERE scope_id=? AND operation_key=?",scope,originalKey)>0)throw OrderProblem.conflict("Original command already has a durable receipt");
   // Non-hex prefix cannot equal any SHA-256 command digest used by domain services.
   db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)",scope,originalKey,"ABANDONED:"+OrderService.hash(originalKey).substring(0,54),id);
   db.update("INSERT INTO recovery_command_tombstones(scope_id,operation_key,order_id,recovery_operation_key,actor,reason,order_version) VALUES(?,?,?,?,?,?,?)",scope,originalKey,id,command.operationKey(),access.actor(),command.reason(),num(order,"version")+1);
  });
 }
 public Operation assign(String exceptionId,Assignment request) {
  text(request.owner(),200,"owner");
  return exceptionCommand(exceptionId,"ASSIGN",new RecoveryCommand(request.expectedVersion(),request.operationKey(),request.reason()),request.owner(),(order,exception)->{
   db.update("UPDATE order_exceptions SET owner=?,version=version+1 WHERE id=?",request.owner(),exceptionId);
  });
 }
 public Operation resolve(String exceptionId,Resolution request) {
  if(!Set.of("RECONCILE","RETRY","WAIVE").contains(Objects.toString(request.action(),"")))throw OrderProblem.invalid("Unsupported recovery action");
  return exceptionCommand(exceptionId,request.action(),new RecoveryCommand(request.expectedVersion(),request.operationKey(),request.reason()),request.action(),(order,exception)->{
   if(!"OPEN".equals(str(exception,"status")))throw OrderProblem.conflict("Exception is not open");
   String id=str(order,"id"),task=str(exception,"task_id");
   var inner=new Command(currentVersion(id),request.reason(),childKey(request.operationKey(),exceptionId,request.action()));
   switch(request.action()) {
    case "WAIVE" -> {
     if(!Set.of("REVIEW_NOTE").contains(str(exception,"category")))throw OrderProblem.conflict("This exception requires authoritative reconciliation and cannot be waived");
     db.update("UPDATE order_exceptions SET status='RESOLVED',version=version+1 WHERE id=?",exceptionId);
    }
    case "RETRY" -> {
     if(task==null)throw OrderProblem.conflict("Exception has no retryable task");
     execution.retry(id,task,inner);
     // Executor eligibility is authoritative; never use queue status to permit dispatch.
     db.update("UPDATE order_exceptions SET status='RESOLVED',version=version+1 WHERE id=? AND status='OPEN'",exceptionId);
    }
    case "RECONCILE" -> {
     execution.reconcile(id,inner);
     if(!findingStillPresent(order,exception))db.update("UPDATE order_exceptions SET status='RESOLVED',version=version+1 WHERE id=? AND status='OPEN'",exceptionId);
     else db.update("UPDATE order_exceptions SET version=version+1 WHERE id=?",exceptionId);
    }
   }
  });
 }
 public Operation noEffect(String orderId,String taskId,NoEffect request) {
  var command=new RecoveryCommand(request.expectedVersion(),request.operationKey(),request.reason());
  return mutate(orderId,"NO_EFFECT",List.of(taskId,Objects.toString(request.evidenceReference(),""),Objects.toString(request.occurredAt(),"")),command,null,order->execution.recordNoEffect(orderId,taskId,new Command(currentVersion(orderId),request.reason(),childKey(request.operationKey(),taskId,"NO_EFFECT")),request.evidenceReference(),request.occurredAt()));
 }
 public Operation recoverOutbox(String orderId,long eventId,RecoveryCommand command) {
  return mutate(orderId,"OUTBOX_REQUEUE",eventId,command,null,order->{
   int changed=db.update("UPDATE outbox_events SET exhausted_at=NULL,retry_count=0,next_attempt_at=clock_timestamp(),lease_token=NULL,lease_until=NULL,error_message=NULL,updated_at=clock_timestamp(),version=version+1 WHERE id=? AND aggregate_type='Order' AND aggregate_id=? AND published=false AND is_deleted=false AND exhausted_at IS NOT NULL AND (lease_until IS NULL OR lease_until<clock_timestamp())",eventId,orderId);
   if(changed!=1)throw OrderProblem.conflict("Event is not exhausted or has an active delivery lease");
  });
 }
 public Page<ExceptionView> exceptions(int page,int size,String status,String category,String owner) {
  bounds(page,size);var args=new ArrayList<Object>();String where=scopeWhere(args);
  if(status!=null&&!status.isBlank()){where+=" AND e.status=?";args.add(status);}
  if(category!=null&&!category.isBlank()){where+=" AND e.category=?";args.add(category);}
  if(owner!=null&&!owner.isBlank()){where+=" AND e.owner=?";args.add(owner);}
  String from=" FROM order_exceptions e JOIN orders o ON o.id=e.order_id WHERE "+where;
  long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(size);args.add((long)page*size);
  var rows=db.queryForList("SELECT e.*,o.order_number,o.customer_id"+from+" ORDER BY e.first_occurred_at,e.id LIMIT ? OFFSET ?",args.toArray());
  return new Page<>(rows.stream().map(this::view).toList(),page,size,total,(int)((total+size-1)/size));
 }
 public Page<ExhaustedEvent> exhausted(int page,int size) {
  bounds(page,size);var args=new ArrayList<Object>();String where=scopeWhere(args);
  String from=" FROM outbox_events e JOIN orders o ON e.aggregate_type='Order' AND e.aggregate_id=o.id WHERE "+where+" AND e.exhausted_at IS NOT NULL AND NOT e.published AND NOT e.is_deleted";
  long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(size);args.add((long)page*size);
  var rows=db.queryForList("SELECT e.id,e.uuid,e.aggregate_id,e.event_type,e.retry_count,e.exhausted_at"+from+" ORDER BY e.id LIMIT ? OFFSET ?",args.toArray());
  return new Page<>(rows.stream().map(r->new ExhaustedEvent(num(r,"id"),str(r,"uuid"),str(r,"aggregate_id"),str(r,"event_type"),(int)num(r,"retry_count"),instant(r,"exhausted_at"))).toList(),page,size,total,(int)((total+size-1)/size));
 }
 public Operation operation(String exceptionId,String key) {
  text(key,200,"operation key");var exception=one("SELECT * FROM order_exceptions WHERE id=?",exceptionId);var order=authorized(str(exception,"order_id"),"oms:read",false);
  var row=one("SELECT o.version,r.operation_key FROM orders o LEFT JOIN recovery_receipts r ON r.scope_id=o.scope_id AND r.order_id=o.id AND r.operation_key=? AND r.exception_id=? WHERE o.id=? AND o.scope_id=? AND o.customer_id=? AND o.is_deleted=false",key,exceptionId,str(order,"id"),str(order,"scope_id"),str(order,"customer_id"));
  return new Operation(key,str(order,"id"),row.get("operation_key")!=null?"SUCCEEDED":"NOT_FOUND",num(row,"version"));
 }
 private Operation exceptionCommand(String exceptionId,String action,RecoveryCommand command,Object detail,java.util.function.BiConsumer<Map<String,Object>,Map<String,Object>> mutation) {
  var existing=one("SELECT order_id FROM order_exceptions WHERE id=?",exceptionId);
  return mutate(str(existing,"order_id"),action,List.of(exceptionId,detail),command,exceptionId,order->{
   var exception=one("SELECT * FROM order_exceptions WHERE id=? FOR UPDATE",exceptionId);
   if(num(exception,"version")!=command.expectedVersion())throw OrderProblem.conflict("Exception version changed");
   mutation.accept(order,exception);
  });
 }
 private Operation mutate(String orderId,String action,Object detail,RecoveryCommand command,String exceptionId,Consumer<Map<String,Object>> mutation) {
  validate(command);
  try{return tx.execute(t->{
   var order=authorized(orderId,"ASSIGN".equals(action)?"oms:assign":"oms:recover",true);String scope=str(order,"scope_id");
   String hash=OrderService.hash(json.writeValueAsString(Arrays.asList("RECOVERY",orderId,action,detail,command)));
   var prior=db.queryForList("SELECT body_hash,order_id FROM order_idempotency WHERE scope_id=? AND operation_key=?",scope,command.operationKey());
   if(!prior.isEmpty()) {
    if(!hash.equals(str(prior.getFirst(),"body_hash"))||!orderId.equals(str(prior.getFirst(),"order_id")))throw OrderProblem.conflict("Operation key body conflict");
    return result(orderId,action,detail,command);
   }
   if(exceptionId==null && num(order,"version")!=command.expectedVersion())throw OrderProblem.conflict("Order version changed");
   mutation.accept(order);
   db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)",scope,command.operationKey(),hash,orderId);
   db.update("INSERT INTO recovery_receipts(scope_id,operation_key,order_id,exception_id,action,result,actor,reason) VALUES(?,?,?,?,?,?,?,?)",scope,command.operationKey(),orderId,exceptionId,action,"ABANDON".equals(action)?"ABANDONED":"SUCCEEDED",access.actor(),command.reason());
   audit(order,"RECOVERY_"+action,command.reason(),access.actor());return result(orderId,action,detail,command);
  });}catch(DataIntegrityViolationException race){throw OrderProblem.conflict("Concurrent recovery operation conflict");}
 }
 private Operation result(String id,String action,Object detail,RecoveryCommand command){return new Operation("ABANDON".equals(action)?(String)detail:command.operationKey(),id,"ABANDON".equals(action)?"ABANDONED":"SUCCEEDED",currentVersion(id));}
 private String scopeWhere(List<Object> args){String scope=access.scopeId();var customers=access.customerIds();if(customers.isEmpty())throw OrderProblem.missing();for(String customer:customers)access.require("oms:read",scope,customer);args.add(scope);args.addAll(customers);return "o.is_deleted=false AND o.scope_id=? AND o.customer_id IN ("+String.join(",",Collections.nCopies(customers.size(),"?"))+")";}
 private Map<String,Object> authorized(String id,String permission,boolean lock){var row=one("SELECT * FROM orders WHERE id=? AND is_deleted=false"+(lock?" FOR UPDATE":""),id);access.require(permission,str(row,"scope_id"),str(row,"customer_id"));return row;}
 private void audit(Map<String,Object> order,String action,String reason,String actor){String id=str(order,"id");long version=currentVersion(id)+1;db.update("UPDATE orders SET version=?,updated_at=clock_timestamp(),updated_by=? WHERE id=?",version,actor,id);db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version) VALUES(?,?,?,clock_timestamp(),?,?,?)",UUID.randomUUID().toString(),id,actor,action,reason,version);String event=UUID.randomUUID().toString();db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload) VALUES(?,'Order',?,'oms.order-updated.v1',?)",event,id,json.writeValueAsString(Map.of("eventId",event,"orderId",id,"scopeId",str(order,"scope_id"),"customerId",str(order,"customer_id"),"version",version,"action",action)));}
 private boolean findingStillPresent(Map<String,Object> order,Map<String,Object> exception){
  String id=str(order,"id"),task=str(exception,"task_id"),category=str(exception,"category");
  return switch(category){
   case "UNKNOWN_OUTCOME","ACK_DEADLINE" -> task==null
    ? count("SELECT COUNT(*) FROM task_operations WHERE order_id=? AND status IN ('UNKNOWN','WAITING_EXTERNAL')",id)>0
    : count("SELECT COUNT(*) FROM task_operations WHERE task_id=? AND status IN ('UNKNOWN','WAITING_EXTERNAL')",task)>0;
   case "ATTEMPTS_EXHAUSTED" -> task==null
    ? count("SELECT COUNT(*) FROM task_operations WHERE order_id=? AND attempt>=3 AND status NOT IN ('SUCCESS','CANCELLED')",id)>0
    : count("SELECT COUNT(*) FROM task_operations WHERE task_id=? AND attempt>=3 AND status NOT IN ('SUCCESS','CANCELLED')",task)>0;
   case "ALLOCATION_EXPIRED" -> count("SELECT COUNT(*) FROM allocation_lots WHERE order_id=? AND quantity>consumed+released AND expires_at<=clock_timestamp()",id)>0;
   case "PENDING_DELIVERY" -> count("SELECT COUNT(*) FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? AND s.status='PENDING'",id)>0;
   case "OUTBOX_EXHAUSTED" -> count("SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='Order' AND aggregate_id=? AND exhausted_at IS NOT NULL AND NOT published AND NOT is_deleted",id)>0;
   case "FINANCE_DISCREPANCY" -> count(RecoveryFacts.FINANCE_MISMATCH,id)>0;
   default -> true; // Contradictory facts and source/terminal holds need their own authoritative correction workflow.
  };
 }
 private ExceptionView view(Map<String,Object> r){return new ExceptionView(str(r,"id"),str(r,"order_id"),str(r,"order_number"),str(r,"customer_id"),str(r,"task_id"),str(r,"category"),str(r,"severity"),str(r,"status"),str(r,"owner"),str(r,"next_action"),str(r,"reason"),instant(r,"first_occurred_at"),instant(r,"last_occurred_at"),num(r,"version"));}
 private Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw OrderProblem.missing();return rows.getFirst();}
 private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
 private long currentVersion(String id){return db.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,id);}
 private static String childKey(String key,String target,String action){return "recovery-child:"+OrderService.hash(key+"|"+target+"|"+action);}
 private static void validate(RecoveryCommand c){if(c==null||c.expectedVersion()<0)throw OrderProblem.invalid("Valid expected version required");text(c.operationKey(),200,"operation key");text(c.reason(),2000,"reason");}
 private static void bounds(int page,int size){if(page<0||page>1000000||size<1||size>100)throw OrderProblem.invalid("Page must be nonnegative; size must be 1..100");}
 private static void text(String s,int max,String label){if(s==null||s.isBlank()||s.length()>max)throw OrderProblem.invalid("Invalid "+label);}
 private static String str(Map<String,Object> row,String key){return (String)row.get(key);}
 private static long num(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private static Instant instant(Map<String,Object> row,String key){return row.get(key)==null?null:((Timestamp)row.get(key)).toInstant();}
}
