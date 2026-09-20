package com.opencrm.order.replacement;

import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Testcontainers
class ReplacementIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 JdbcTemplate db; OrderService orders; OrderAccess access; DataSourceTransactionManager manager;
 JsonMapper json=JsonMapper.builder().build();
 @BeforeAll static void migrate() {Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("order_management").defaultSchema("order_management").load().migrate();}
 @BeforeEach void setup() {
  var ds=new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword());
  db=new JdbcTemplate(ds);db.execute("TRUNCATE snapshot_inbox,outbox_events,execution_inbox CASCADE");manager=new DataSourceTransactionManager(ds);
  access=mock(OrderAccess.class);when(access.actor()).thenReturn("operator");when(access.scopeId()).thenReturn("pilot");when(access.customerIds()).thenReturn(Set.of("customer"));
  orders=new OrderService(db,manager,json,access,true,"pilot");
 }
 AcceptedOrderSnapshot snapshot(int revision) {return new AcceptedOrderSnapshot(1,"snapshot-"+revision,"00000000-0000-0000-0000-000000000001","Q-123",revision,"pilot","customer",Instant.parse("2026-09-01T00:00:00Z"),"USD","30","0","0","30","address","approval","tax","configuration",List.of(new AcceptedOrderSnapshot.Line("line","product","Widget","PHYSICAL","ONE_TIME",3,"EA","10","0","0","30","ONE_TIME",null,null)));}
 void capture(int revision) {String payload=json.writeValueAsString(snapshot(revision));orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));}
 View convert(int revision) {capture(revision);return orders.fromQuote(new FromQuote("snapshot-"+revision,snapshot(revision).quoteId(),revision),"convert-"+revision);}
 ReplacementService replacements() {return new ReplacementService(db,manager,json,access);}
 Command command(long version,String key) {return new Command(version,"Correct commercial terms",key);}
 @Test void acceptedRevisionCannotBypassReplacementAuthorization() {
  convert(1);capture(2);
  assertEquals(409,assertThrows(OrderProblem.class,()->orders.fromQuote(new FromQuote("snapshot-2",snapshot(2).quoteId(),2),"convert-2")).status());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
 }
 @Test void replacementCancelsObligationsAndEmitsExactDurableEventWithoutChangingSource() {
  var original=convert(1);var before=db.queryForMap("SELECT snapshot_json,snapshot_hash,snapshot_id,quote_id,accepted_revision FROM orders WHERE id=?",original.id());
  var receipt=replacements().request(original.id(),command(0,"replace"));
  assertEquals("SUCCEEDED",receipt.status());assertEquals(1,receipt.orderVersion());
  var cancelled=orders.get(original.id());assertEquals("CANCELLED",cancelled.status());assertEquals(3,cancelled.lines().getFirst().cancelled());assertEquals(original.snapshot(),cancelled.snapshot());
  assertTrue(orders.plan(original.id()).stream().allMatch(t->t.status().equals("CANCELLED")));
  assertEquals(before,db.queryForMap("SELECT snapshot_json,snapshot_hash,snapshot_id,quote_id,accepted_revision FROM orders WHERE id=?",original.id()));
  var payload=json.readTree(db.queryForObject("SELECT payload FROM outbox_events WHERE event_type='oms.quote-revision-requested.v1'",String.class));
  assertEquals(json.valueToTree(Map.of("schemaVersion",1,"eventId",receipt.eventId(),"operationKey","replace","orderId",original.id(),"quoteId",snapshot(1).quoteId(),"acceptedRevision",1,"scopeId","pilot","customerId","customer","reason","Correct commercial terms")),payload);
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM order_audit",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_idempotency WHERE operation_key='replace'",Integer.class));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE order_replacement_requests SET reason='tamper'"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM order_replacement_requests"));
 }
 @Test void nextRevisionLinksExactlyOnePredecessorAndCanBeReplayed() {
  var old=convert(1);replacements().request(old.id(),command(0,"replace"));var successor=convert(2);
  assertEquals(Map.of("predecessor_order_id",old.id(),"successor_order_id",successor.id()),db.queryForMap("SELECT predecessor_order_id,successor_order_id FROM order_replacement_lineage"));
  assertEquals(successor.id(),orders.fromQuote(new FromQuote("snapshot-2",snapshot(2).quoteId(),2),"another-convert").id());
  capture(3);assertEquals(409,assertThrows(OrderProblem.class,()->orders.fromQuote(new FromQuote("snapshot-3",snapshot(3).quoteId(),3),"skip-permit")).status());
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM order_replacement_lineage"));
 }
 @Test void ordinaryCancellationIsNotAReplacementPermit() {
  var old=convert(1);orders.cancel(old.id(),new Cancellation(0,"cancel","cancel",old.lines().getFirst().id(),3));
  assertEquals(409,assertThrows(OrderProblem.class,()->convert(2)).status());
 }
 @Test void replayIsBodyBoundAndStaleVersionHasNoSideEffects() {
  var old=convert(1);var service=replacements();assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),command(8,"stale"))).status());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_replacement_requests",Integer.class));assertEquals(0,orders.get(old.id()).version());
  var first=service.request(old.id(),command(0,"replace"));assertEquals(first,service.request(old.id(),command(0,"replace")));
  assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),new Command(0,"different","replace"))).status());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_replacement_requests",Integer.class));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM outbox_events",Integer.class));
 }
 @Test void concurrentReplayCreatesOneRequestAndConcurrentDifferentKeysHaveOneWinner() throws Exception {
  var old=convert(1);var service=replacements();
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<ReplacementService.Receipt>>();
   for(int i=0;i<8;i++) tasks.add(executor.submit(()->{gate.await();return service.request(old.id(),command(0,"replace"));}));
   gate.countDown();Set<String> events=new HashSet<>();for(var task:tasks) events.add(task.get(20,TimeUnit.SECONDS).eventId());assertEquals(1,events.size());
  }
  var next=convert(2);
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<Integer>>();
   for(int i=0;i<2;i++) {String key="replace-next-"+i;tasks.add(executor.submit(()->{gate.await();try {service.request(next.id(),command(0,key));return 202;}catch(OrderProblem e) {return e.status();}}));}
   gate.countDown();Set<Integer> outcomes=new HashSet<>();for(var task:tasks) outcomes.add(task.get(20,TimeUnit.SECONDS));assertEquals(Set.of(202,409),outcomes);
  }
 }
 @Test void submittedUndispatchedOrderIsEligibleAndRetryableOperationIsFenced() {
  var old=convert(1);orders.submit(old.id(),command(0,"submit"));insertOperation(old.id(),"RETRYABLE");
  replacements().request(old.id(),command(1,"replace"));assertEquals("CANCELLED",db.queryForObject("SELECT status FROM task_operations",String.class));
  assertNull(db.queryForObject("SELECT lease_token FROM task_operations",String.class));
 }
 @Test void crossOrderOperationKeyRaceRollsBackLosingCancellation() throws Exception {
  var first=convert(1);String otherQuote="00000000-0000-0000-0000-000000000002";
  String payload=json.writeValueAsString(snapshot(1)).replace("snapshot-1","other-snapshot").replace(snapshot(1).quoteId(),otherQuote);
  orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));
  var second=orders.fromQuote(new FromQuote("other-snapshot",otherQuote,1),"other-convert");
  var bothPassedIdempotency=new CountDownLatch(2);
  when(access.actor()).thenAnswer(invocation->{bothPassedIdempotency.countDown();assertTrue(bothPassedIdempotency.await(10,TimeUnit.SECONDS));return "operator";});
  var service=replacements();
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var calls=new ArrayList<Future<Integer>>();
   for(String id:List.of(first.id(),second.id())) calls.add(executor.submit(()->{try {service.request(id,command(0,"shared-key"));return 202;}catch(OrderProblem e) {return e.status();}}));
   var outcomes=new HashSet<Integer>();for(var call:calls) outcomes.add(call.get(20,TimeUnit.SECONDS));assertEquals(Set.of(202,409),outcomes);
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM orders WHERE status='CANCELLED'",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_replacement_requests",Integer.class));
  assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM order_audit",Integer.class));
  assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM outbox_events",Integer.class));
 }
 @Test void outboxFailureRollsBackEveryReplacementWrite() {
  var original=convert(1);
  db.execute("ALTER TABLE outbox_events ADD CONSTRAINT replacement_test_fail CHECK(event_type<>'oms.quote-revision-requested.v1')");
  try {
   assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->replacements().request(original.id(),command(0,"replace")));
   assertEquals("DRAFT",orders.get(original.id()).status());assertEquals(0,orders.get(original.id()).version());
   assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_replacement_requests",Integer.class));
   assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_idempotency WHERE operation_key='replace'",Integer.class));
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_audit",Integer.class));
  } finally {db.execute("ALTER TABLE outbox_events DROP CONSTRAINT replacement_test_fail");}
 }
 void insertOperation(String id,String status) {
  String task=db.queryForObject("SELECT id FROM order_tasks WHERE order_id=? AND kind='ALLOCATION'",String.class,id);
  db.update("INSERT INTO task_operations(operation_key,task_id,order_id,body_hash,status,lease_token,lease_until) VALUES('operation',?,?,?,?,'old-token',CURRENT_TIMESTAMP)",task,id,"a".repeat(64),status);
 }
 @Test void claimedAndUnknownOperationsBlockWithoutMutations() {
  var old=convert(1);insertOperation(old.id(),"CLAIMED");var service=replacements();
  for(String status:List.of("CLAIMED","UNKNOWN","EXHAUSTED","WAITING_EXTERNAL","SUCCEEDED")) {
   db.update("UPDATE task_operations SET status=?",status);
   assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),command(0,"replace"))).status());
   assertEquals("DRAFT",orders.get(old.id()).status());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_replacement_requests",Integer.class));
  }
 }
 @Test void historicalDispatchAndQuantitiesPreventReplacement() {
  var old=convert(1);var service=replacements();
  db.update("UPDATE order_tasks SET dispatched_at=CURRENT_TIMESTAMP WHERE order_id=?",old.id());
  assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),command(0,"replace"))).status());
  db.update("UPDATE order_tasks SET dispatched_at=NULL WHERE order_id=?",old.id());
  for(String column:List.of("allocated","shipped","fulfilled")) {
   db.update("UPDATE order_lines SET "+column+"=1 WHERE order_id=?",old.id());
   assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),command(0,"replace"))).status());
   db.update("UPDATE order_lines SET "+column+"=0 WHERE order_id=?",old.id());
  }
  db.update("UPDATE orders SET status='IN_PROGRESS' WHERE id=?",old.id());
  assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),command(0,"replace"))).status());
  db.update("UPDATE orders SET status='DRAFT',finance_status='REQUESTED' WHERE id=?",old.id());
  assertEquals(409,assertThrows(OrderProblem.class,()->service.request(old.id(),command(0,"replace"))).status());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_replacement_requests",Integer.class));
 }
 @Test void bothPermissionsAndVerifiedScopeAccountAreRequired() {
  var old=convert(1);var guarded=new ReplacementService(db,manager,json,new OrderAccess("pilot"));
  try {
   for(var permissions:List.of(List.of("oms:submit"),List.of("oms:cancel"))) {
    authenticate("pilot","customer",permissions);
    assertThrows(AccessDeniedException.class,()->guarded.request(old.id(),command(0,"replace")));
   }
   authenticate("pilot","other",List.of("oms:submit","oms:cancel"));
   assertEquals(404,assertThrows(ResponseStatusException.class,()->guarded.request(old.id(),command(0,"replace"))).getStatusCode().value());
   authenticate("other","customer",List.of("oms:submit","oms:cancel"));
   assertThrows(AccessDeniedException.class,()->guarded.request(old.id(),command(0,"replace")));
   SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("actor","password","oms:submit","oms:cancel"));
   assertThrows(AccessDeniedException.class,()->guarded.request(old.id(),command(0,"replace")));
   assertEquals(0,orders.get(old.id()).version());
   authenticate("pilot","customer",List.of("oms:submit","oms:cancel"));assertEquals("SUCCEEDED",guarded.request(old.id(),command(0,"replace")).status());
  } finally {SecurityContextHolder.clearContext();}
 }
 void authenticate(String scope,String customer,List<String> permissions) {
  var jwt=Jwt.withTokenValue("verified-by-filter").header("alg","RS256").subject("operator").claim("scope_id",scope).claim("account_ids",List.of(customer)).claim("permissions",permissions).build();
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
 }
}
