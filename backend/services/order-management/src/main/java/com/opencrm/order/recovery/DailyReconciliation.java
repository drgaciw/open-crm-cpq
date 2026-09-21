package com.opencrm.order.recovery;

import com.opencrm.order.domain.OrderProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Bounded, resumable daily invariant scan. Findings never stand in for fulfillment proof. */
@Service
public class DailyReconciliation {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final SortedSet<String> scopes=new TreeSet<>();
 private final int healthQueryTimeoutSeconds;
 public DailyReconciliation(JdbcTemplate db,PlatformTransactionManager manager,String scopes){this(db,manager,scopes,5);}
 @Autowired
 public DailyReconciliation(JdbcTemplate db,PlatformTransactionManager manager,@Value("${oms.recovery.scopes:}") String configuredScopes,@Value("${oms.recovery.metrics-query-timeout-seconds:5}") int timeoutSeconds){
  if(timeoutSeconds<1||timeoutSeconds>30)throw new IllegalArgumentException("Reconciliation health timeout must be 1..30 seconds");
  this.db=db;this.tx=new TransactionTemplate(manager);this.healthQueryTimeoutSeconds=timeoutSeconds;
  for(String scope:configuredScopes.split(","))if(!scope.isBlank())scopes.add(scope.trim());
 }
 public int scan(int limit){
  if(limit<1||limit>100)throw OrderProblem.invalid("Batch size must be 1..100");
  int scanned=0;
  for(String scope:scopes){int remaining=limit-scanned;if(remaining==0)break;scanned+=tx.execute(t->scanScope(scope,remaining));}
  return scanned;
 }
 private int scanScope(String scope,int limit){
  db.update("INSERT INTO recovery_daily_runs(scope_id,run_date,upper_bound) VALUES(?,(clock_timestamp() AT TIME ZONE 'UTC')::date,clock_timestamp()) ON CONFLICT DO NOTHING",scope);
  // Extend only the current day's completed window. Its immutable checkpoint remains unchanged.
  db.update("UPDATE recovery_daily_runs r SET upper_bound=clock_timestamp(),completed_at=NULL WHERE r.scope_id=? AND r.run_date=(clock_timestamp() AT TIME ZONE 'UTC')::date AND r.completed_at IS NOT NULL AND EXISTS(SELECT 1 FROM orders o WHERE o.scope_id=r.scope_id AND o.is_deleted=false AND (o.created_at,o.id)>(r.cursor_created_at,r.cursor_id))",scope);
  // The row lock is the worker fence. Cursor, findings and audits commit together; crashes release it.
  var runs=db.queryForList("SELECT * FROM recovery_daily_runs WHERE scope_id=? AND completed_at IS NULL ORDER BY run_date LIMIT 1 FOR UPDATE SKIP LOCKED",scope);
  if(runs.isEmpty())return 0;var run=runs.getFirst();Object day=run.get("run_date");
  var orders=db.queryForList("SELECT id,created_at FROM orders WHERE scope_id=? AND is_deleted=false AND created_at<=? AND (created_at,id)>(?,?) ORDER BY created_at,id LIMIT ?",scope,run.get("upper_bound"),run.get("cursor_created_at"),run.get("cursor_id"),limit);
  for(var candidate:orders){
   var order=db.queryForMap("SELECT * FROM orders WHERE id=? AND scope_id=? FOR UPDATE",candidate.get("id"),scope);String id=(String)order.get("id");
   var findings=inspect(id);
   for(var finding:findings.entrySet())record(scope,day,order,finding.getKey(),finding.getValue());
   db.update("UPDATE recovery_daily_runs SET cursor_created_at=?,cursor_id=?,scanned=scanned+1 WHERE scope_id=? AND run_date=?",candidate.get("created_at"),id,scope,day);
  }
  if(orders.size()<limit){
   db.update("UPDATE recovery_daily_runs SET completed_at=clock_timestamp() WHERE scope_id=? AND run_date=?",scope,day);
   db.update("INSERT INTO recovery_daily_checkpoints(scope_id,run_date,upper_bound,cursor_created_at,cursor_id,scanned,completed_at) SELECT scope_id,run_date,upper_bound,cursor_created_at,cursor_id,scanned,completed_at FROM recovery_daily_runs WHERE scope_id=? AND run_date=? ON CONFLICT DO NOTHING",scope,day);
  }
  return orders.size();
 }
 public record ScanHealth(long incompleteRuns,double oldestRunAgeSeconds,double secondsSinceCompletion) {}
 public ScanHealth health(){
  long incomplete=0;double oldest=0,since=scopes.isEmpty()?Double.POSITIVE_INFINITY:0;
  for(String scope:scopes){
   ScanHealth current=db.query(connection->{
    var statement=connection.prepareStatement("SELECT COUNT(*) FILTER(WHERE completed_at IS NULL) AS incomplete,COALESCE(MAX(EXTRACT(EPOCH FROM clock_timestamp()-upper_bound)) FILTER(WHERE completed_at IS NULL),0) AS age,EXTRACT(EPOCH FROM clock_timestamp()-MAX(completed_at)) AS since FROM recovery_daily_runs WHERE scope_id=?");
    statement.setString(1,scope);statement.setQueryTimeout(healthQueryTimeoutSeconds);return statement;
   },result->{result.next();return new ScanHealth(result.getLong("incomplete"),result.getDouble("age"),result.getObject("since")==null?Double.POSITIVE_INFINITY:result.getDouble("since"));});
   incomplete+=current.incompleteRuns();oldest=Math.max(oldest,current.oldestRunAgeSeconds());since=Math.max(since,current.secondsSinceCompletion());
  }
  return new ScanHealth(incomplete,oldest,since);
 }
 private Map<String,String> inspect(String id){
  var result=new LinkedHashMap<String,String>();
  check(result,id,"UNKNOWN_OUTCOME","SELECT COUNT(*) FROM task_operations WHERE order_id=? AND status='UNKNOWN'","Unknown external outcome requires authoritative reconciliation");
  check(result,id,"ACK_DEADLINE","SELECT COUNT(*) FROM task_operations WHERE order_id=? AND status='WAITING_EXTERNAL' AND deadline_at<clock_timestamp()","External acknowledgment deadline exceeded");
  check(result,id,"ATTEMPTS_EXHAUSTED","SELECT COUNT(*) FROM task_operations WHERE order_id=? AND attempt>=3 AND (status='RETRYABLE' OR (status='CLAIMED' AND lease_until<clock_timestamp()))","Operation attempts exhausted");
  check(result,id,"ALLOCATION_EXPIRED","SELECT COUNT(*) FROM allocation_lots WHERE order_id=? AND quantity>consumed+released AND expires_at<=clock_timestamp()","Expired allocation requires explicit release or reconciled shipment proof");
  check(result,id,"PENDING_DELIVERY","SELECT COUNT(*) FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON e.id=s.evidence_id WHERE e.order_id=? AND s.status='PENDING'","Delivery awaits referenced shipment evidence");
  check(result,id,"CONTRADICTORY_EVIDENCE",RecoveryFacts.UNADJUDICATED_CONFLICTS,"Conflicting fulfillment evidence remains preserved");
  check(result,id,"OUTBOX_EXHAUSTED","SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='Order' AND aggregate_id=? AND exhausted_at IS NOT NULL AND NOT published AND NOT is_deleted","Event delivery exhausted; reconcile broker state before requeue");
  check(result,id,"FINANCE_DISCREPANCY",RecoveryFacts.FINANCE_MISMATCH,"Finance handoff lacks an exact cumulative authoritative acknowledgment");
  check(result,id,"FINANCE_CONTRADICTORY","SELECT COUNT(*) FROM order_exceptions WHERE order_id=? AND category='FINANCE_CONTRADICTORY' AND status='OPEN'","Late contradictory finance outcome requires authoritative review");
  check(result,id,"TERMINAL_INCONSISTENCY","SELECT COUNT(*) FROM orders o WHERE o.id=? AND o.status IN ('COMPLETED','PARTIALLY_COMPLETED') AND (o.finance_status<>'ACKNOWLEDGED' OR EXISTS(SELECT 1 FROM order_lines l WHERE l.order_id=o.id AND (l.fulfilled+l.cancelled<>l.ordered OR l.delivered<>l.shipped)) OR EXISTS(SELECT 1 FROM order_tasks t WHERE t.order_id=o.id AND t.status NOT IN ('SUCCESS','CANCELLED')))","Terminal order contradicts durable quantities, task or finance state");
  check(result,id,"DEPENDENCY_INCONSISTENCY","SELECT COUNT(*) FROM order_tasks t WHERE t.order_id=? AND t.status='SUCCESS' AND t.kind='ACTIVATION' AND EXISTS(SELECT 1 FROM order_task_dependencies d JOIN order_tasks p ON p.id=d.predecessor_id WHERE d.task_id=t.id AND p.status NOT IN ('SUCCESS','CANCELLED'))","Activation success has an incomplete prerequisite");
  return result;
 }
 private void check(Map<String,String> findings,String id,String category,String sql,String detail){if(db.queryForObject(sql,Long.class,id)>0)findings.put(category,detail);}
 private void record(String scope,Object day,Map<String,Object> order,String category,String detail){
  String id=(String)order.get("id");
  int added=db.update("INSERT INTO recovery_findings(scope_id,run_date,order_id,category,detail) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",scope,day,id,category,detail);
  if(added==0)return;
  // Order lock prevents duplicate nullable-task exceptions across parallel runs.
  if(db.queryForObject("SELECT COUNT(*) FROM order_exceptions WHERE order_id=? AND category=? AND status='OPEN'",Long.class,id,category)==0)
   db.update("INSERT INTO order_exceptions(id,order_id,category,severity,next_action,reason) VALUES(?,?,?,'HIGH','RECONCILE',?)",UUID.randomUUID().toString(),id,category,detail);
  long version=db.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,id)+1;
  db.update("UPDATE orders SET version=?,updated_at=clock_timestamp(),updated_by='oms-reconciler' WHERE id=?",version,id);
  db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version) VALUES(?,?,'oms-reconciler',clock_timestamp(),'DAILY_FINDING',?,?)",UUID.randomUUID().toString(),id,category+": "+detail,version);
  String event=UUID.randomUUID().toString();
  db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload) VALUES(?,'Order',?,'oms.order-updated.v1',json_build_object('eventId',?::text,'orderId',?::text,'scopeId',?::text,'customerId',?::text,'version',?::bigint,'action','DAILY_FINDING')::text)",event,id,event,id,scope,order.get("customer_id"),version);
 }
}
