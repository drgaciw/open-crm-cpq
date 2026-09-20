package com.opencrm.order.service;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.common.order.AcceptedOrderSnapshot.Line;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.security.OrderAccess;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderServiceTest {
 JdbcTemplate db;OrderService service;OrderAccess access;DataSourceTransactionManager manager;JsonMapper json=JsonMapper.builder().build();
 @BeforeEach void setup() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000","sa","");
  try(var connection=ds.getConnection()) {String sql=new String(new ClassPathResource("db/migration/V1__orders.sql").getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("-- POSTGRESQL_IMMUTABILITY")[0];ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
  db=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);access=mock(OrderAccess.class);when(access.actor()).thenReturn("operator");when(access.scopeId()).thenReturn("pilot");when(access.customerIds()).thenReturn(Set.of("customer"));service=new OrderService(db,manager,json,access,true,"pilot");
 }
 AcceptedOrderSnapshot snapshot() {return new AcceptedOrderSnapshot(1,"snapshot","00000000-0000-0000-0000-000000000001","Q-123",1,"pilot","customer",Instant.parse("2026-09-01T00:00:00Z"),"USD","30","0","0","30","address","approval","tax","configuration",List.of(new Line("line","product","Widget","PHYSICAL","ONE_TIME",3,"EA","10","0","0","30","ONE_TIME",null,null)));}
 void capture() {String payload=json.writeValueAsString(snapshot());service.acceptTrustedSnapshot(payload,OrderService.hash(payload));}
 FromQuote request() {return new FromQuote("snapshot",snapshot().quoteId(),1);}
 View convert() {capture();return service.fromQuote(request(),"convert");}
 @Test void hundredConcurrentConversionsCreateOneOrderAndPlan() throws Exception {
  capture();try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var start=new CountDownLatch(1);var futures=new ArrayList<Future<String>>();for(int i=0;i<100;i++) {final int n=i;futures.add(executor.submit(()->{start.await();return service.fromQuote(request(),"convert-"+n).id();}));}start.countDown();Set<String> ids=new HashSet<>();for(var f:futures) ids.add(f.get(40,TimeUnit.SECONDS));assertEquals(1,ids.size());
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM order_tasks",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_audit",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM outbox_events",Integer.class));
 }
 @Test void aliasesAndReplayHaveCanonicalIdentity() {var view=convert();assertEquals(view.id(),service.fromQuote(new FromQuote("snapshot","Q-123",1),"convert").id());assertEquals(view.id(),service.fromQuote(request(),"another-key").id());}
 @Test void disabledIntakeAllowsExistingReplayButBlocksNewOrder() {var view=convert();var disabled=new OrderService(db,manager,json,access,false,"pilot");assertEquals(view.id(),disabled.fromQuote(request(),"convert").id());}
 @Test void submitHoldVersionAndBodyConflict() {var v=convert();v=service.submit(v.id(),new Command(0,"approved","submit"));assertEquals("SUBMITTED",v.status());assertEquals(1,v.version());assertEquals(1,service.submit(v.id(),new Command(0,"approved","submit")).version());var id=v.id();assertEquals(409,assertThrows(OrderProblem.class,()->service.submit(id,new Command(1,"changed","submit"))).status());assertEquals(409,assertThrows(OrderProblem.class,()->service.hold(id,new Hold(0,"hold","hold",true))).status());assertTrue(service.hold(id,new Hold(1,"hold","hold",true)).holds().contains("MANUAL"));}
 @Test void cancellationConservesQuantityAndRejectsDispatch() {var v=convert();String line=v.lines().getFirst().id();v=service.cancel(v.id(),new Cancellation(0,"partial","cancel",line,2));assertEquals(1,v.lines().getFirst().remaining());String id=v.id();assertEquals(422,assertThrows(OrderProblem.class,()->service.cancel(id,new Cancellation(1,"excess","cancel2",line,2))).status());db.update("UPDATE order_tasks SET dispatched_at=CURRENT_TIMESTAMP WHERE line_id=?",line);assertEquals(409,assertThrows(OrderProblem.class,()->service.cancel(id,new Cancellation(1,"late","cancel3",line,1))).status());assertEquals(1,service.get(id).version());}
 @Test void fullCancellationIsTerminalAndPlanCancelled() {var v=convert();v=service.cancel(v.id(),new Cancellation(0,"all","cancel",v.lines().getFirst().id(),3));assertEquals("CANCELLED",v.status());assertTrue(service.plan(v.id()).stream().allMatch(t->t.status().equals("CANCELLED")));}
 @Test void hashTamperingAndSourceOverwriteFail() {capture();assertThrows(OrderProblem.class,()->service.acceptTrustedSnapshot("{}",OrderService.hash("different")));String changed=json.writeValueAsString(snapshot()).replace("Widget","Changed");assertEquals(409,assertThrows(OrderProblem.class,()->service.acceptTrustedSnapshot(changed,OrderService.hash(changed))).status());}
 @Test void unauthorizedAccessCannotMutateOrAudit() {var v=convert();doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(access).require("oms:cancel","pilot","customer");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.cancel(v.id(),new Cancellation(0,"all","cancel",v.lines().getFirst().id(),3)));assertEquals(1,service.timeline(v.id()).size());}

 @Test void cancelledRootTaskStaysCancelledAfterSubmit() {
  var original=snapshot();var line=original.lines().getFirst();
  var second=new Line("second","product2","Another","SERVICE","ONE_TIME",3,"EA","10","0","0","30","ONE_TIME",null,null);
  var two=new AcceptedOrderSnapshot(1,original.snapshotId(),original.quoteId(),original.quoteNumber(),1,"pilot","customer",original.acceptedAt(),"USD","60","0","0","60","address","approval","tax","configuration",List.of(line,second));
  String payload=json.writeValueAsString(two);service.acceptTrustedSnapshot(payload,OrderService.hash(payload));var v=service.fromQuote(request(),"convert");String cancelledLine=v.lines().getFirst().id();v=service.cancel(v.id(),new Cancellation(0,"remove line","cancel",cancelledLine,3));v=service.submit(v.id(),new Command(1,"go","submit"));assertTrue(service.plan(v.id()).stream().filter(t->t.lineId().equals(cancelledLine)).allMatch(t->t.status().equals("CANCELLED")));
 }
 @Test void concurrentCancellationHasOneVersionWinner() throws Exception {
  var v=convert();try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var start=new CountDownLatch(1);var futures=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++) {final int n=i;futures.add(executor.submit(()->{start.await();try {service.cancel(v.id(),new Cancellation(0,"cancel","cancel-"+n,v.lines().getFirst().id(),2));return 200;}catch(OrderProblem e){return e.status();}}));}start.countDown();List<Integer> statuses=new ArrayList<>();for(var f:futures) statuses.add(f.get(20,TimeUnit.SECONDS));assertEquals(Set.of(200,409),new HashSet<>(statuses));assertEquals(2,service.get(v.id()).lines().getFirst().cancelled());
  }
 }
 @Test void disabledIntakeRejectsNewSource() {capture();var disabled=new OrderService(db,manager,json,access,false,"pilot");assertEquals(422,assertThrows(OrderProblem.class,()->disabled.fromQuote(request(),"new")).status());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));}
 @Test void missingSourceEvidenceCannotBeClearedByManualHoldRelease() {
  String payload=json.writeValueAsString(snapshot()).replace("\"approvalReference\":\"approval\"","\"approvalReference\":null");service.acceptTrustedSnapshot(payload,OrderService.hash(payload));var v=service.fromQuote(request(),"convert");assertTrue(v.holds().contains("APPROVAL"));service.hold(v.id(),new Hold(0,"release manual","release",false));assertEquals(422,assertThrows(OrderProblem.class,()->service.submit(v.id(),new Command(1,"go","submit"))).status());
 }
 @Test void snapshotHashAndVerbatimBytesArePreserved() {String payload=json.writeValueAsString(snapshot());service.acceptTrustedSnapshot(payload,OrderService.hash(payload));var v=service.fromQuote(request(),"convert");assertEquals(payload,db.queryForObject("SELECT snapshot_json FROM orders WHERE id=?",String.class,v.id()));assertEquals(OrderService.hash(payload),db.queryForObject("SELECT snapshot_hash FROM orders WHERE id=?",String.class,v.id()));}
}
