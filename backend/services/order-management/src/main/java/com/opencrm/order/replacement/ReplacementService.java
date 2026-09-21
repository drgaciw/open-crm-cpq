package com.opencrm.order.replacement;

import com.opencrm.order.audit.CommandAudit;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.Command;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Cancels untouched obligations and records the quote correction request in one transaction. */
@Service
public class ReplacementService {
 public record Receipt(String operationKey,String orderId,String eventId,long orderVersion,String status) {}
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final ObjectMapper json;
 private final OrderAccess access;
 public ReplacementService(JdbcTemplate db,PlatformTransactionManager manager,ObjectMapper json,OrderAccess access) {
  this.db=db;this.tx=new TransactionTemplate(manager);this.json=json;this.access=access;
 }
 public Receipt request(String id,Command command) {
  if(command==null || command.expectedVersion()<0 || invalid(command.operationKey(),200) || invalid(command.reason(),2000))
   throw OrderProblem.invalid("Expected version, operation key and reason are required");
  try {return tx.execute(status->replace(id,command));}
  catch(DuplicateKeyException e) {throw OrderProblem.conflict("Operation key already used by a concurrent command");}
 }
 private Receipt replace(String id,Command c) {
  var rows=db.queryForList("SELECT * FROM orders WHERE id=? AND is_deleted=false FOR UPDATE",id);
  if(rows.isEmpty()) throw OrderProblem.missing();
  var order=rows.getFirst();String scope=(String)order.get("scope_id"),customer=(String)order.get("customer_id");
  access.require("oms:submit",scope,customer);access.require("oms:cancel",scope,customer);
  String body=OrderService.hash(json.writeValueAsString(List.of(id,"REPLACEMENT",c)));
  var replay=db.queryForList("SELECT body_hash,order_id FROM order_idempotency WHERE scope_id=? AND operation_key=?",scope,c.operationKey());
  if(!replay.isEmpty()) {
   if(!body.equals(replay.getFirst().get("body_hash")) || !id.equals(replay.getFirst().get("order_id"))) throw OrderProblem.conflict("Operation key body conflict");
   return receipt(id,c.operationKey());
  }
  long version=((Number)order.get("version")).longValue();
  if(version!=c.expectedVersion()) throw OrderProblem.conflict("Order version changed");
  if(!Set.of("DRAFT","SUBMITTED").contains(order.get("status")) || !"PENDING".equals(order.get("finance_status")))
   throw OrderProblem.conflict("Only wholly unexecuted draft or submitted orders may be replaced");
  if(count("SELECT COUNT(*) FROM order_lines WHERE order_id=? AND (allocated<>0 OR shipped<>0 OR delivered<>0 OR fulfilled<>0)",id)>0
    || count("SELECT COUNT(*) FROM order_tasks WHERE order_id=? AND (dispatched_at IS NOT NULL OR status NOT IN ('BLOCKED','READY','CANCELLED'))",id)>0
    || count("SELECT COUNT(*) FROM task_operations WHERE order_id=? AND (status NOT IN ('RETRYABLE','CANCELLED') OR dispatch_generation<>0 OR last_dispatched_at IS NOT NULL OR completed_at IS NOT NULL OR subscription_id IS NOT NULL OR evidence_reference IS NOT NULL)",id)>0)
   throw OrderProblem.conflict("Execution or unresolved work prevents commercial replacement");
  String requestId=UUID.randomUUID().toString(),eventId=UUID.randomUUID().toString(),actor=access.actor();
  // Every execution writer locks this order first. Clearing leases fences any undispatched operation.
  db.update("UPDATE task_operations SET status='CANCELLED',lease_token=NULL,lease_until=NULL,worker_id=NULL,next_attempt_at=NULL,deadline_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",id);
  db.update("UPDATE order_lines SET cancelled=ordered,status='CANCELLED' WHERE order_id=?",id);
  db.update("UPDATE order_tasks SET status='CANCELLED' WHERE order_id=?",id);
  db.update("UPDATE orders SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?",actor,id);
  db.update("""
   INSERT INTO order_replacement_requests(id,order_id,scope_id,customer_id,quote_id,accepted_revision,operation_key,event_id,reason,actor,order_version)
   VALUES(?,?,?,?,?,?,?,?,?,?,?)
   """,requestId,id,scope,customer,order.get("quote_id"),order.get("accepted_revision"),c.operationKey(),eventId,c.reason(),actor,version+1);
  db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)",scope,c.operationKey(),body,id);
  db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version,evidence_reference) VALUES(?,?,?,CURRENT_TIMESTAMP,'REPLACEMENT_REQUESTED',?,?,?)",UUID.randomUUID().toString(),id,actor,c.reason(),version+1,eventId);
  String payload=json.writeValueAsString(Map.of("schemaVersion",1,"eventId",eventId,"operationKey",c.operationKey(),"orderId",id,"quoteId",order.get("quote_id"),"acceptedRevision",order.get("accepted_revision"),"scopeId",scope,"customerId",customer,"reason",c.reason()));
  db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES(?,'Order',?,'oms.quote-revision-requested.v1',?,CURRENT_TIMESTAMP)",eventId,id,payload);
  CommandAudit.record(db,id,c.operationKey(),actor,c.reason(),version);
  return new Receipt(c.operationKey(),id,eventId,version+1,"SUCCEEDED");
 }
 private Receipt receipt(String id,String key) {
  var r=db.queryForMap("SELECT event_id,order_version FROM order_replacement_requests WHERE order_id=? AND operation_key=?",id,key);
  return new Receipt(key,id,(String)r.get("event_id"),((Number)r.get("order_version")).longValue(),"SUCCEEDED");
 }
 private int count(String sql,String id) {return db.queryForObject(sql,Integer.class,id);}
 private static boolean invalid(String value,int max) {return value==null || value.isBlank() || value.length()>max;}
}
