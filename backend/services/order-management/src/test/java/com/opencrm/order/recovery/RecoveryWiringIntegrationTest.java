package com.opencrm.order.recovery;
import com.opencrm.order.controller.OrderOperationController;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.execution.ExecutionService;
import com.opencrm.order.fulfillment.*;
import com.opencrm.order.recovery.RecoveryDtos.*;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers @Tag("integration")
class RecoveryWiringIntegrationTest {
 @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 JdbcTemplate db;OrderService orders;RecoveryService recovery;EvidenceService evidence;View order;
 @BeforeAll static void migrate(){Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).schemas("order_management").defaultSchema("order_management").load().migrate();}
 @BeforeEach void setup()throws Exception{
  var ds=new DriverManagerDataSource(pg.getJdbcUrl()+"&currentSchema=order_management",pg.getUsername(),pg.getPassword());db=new JdbcTemplate(ds);db.execute("TRUNCATE snapshot_inbox,outbox_events CASCADE");
  var manager=new DataSourceTransactionManager(ds);var access=mock(OrderAccess.class);when(access.actor()).thenReturn("integration-operator");var json=JsonMapper.builder().build();
  orders=new OrderService(db,manager,json,access,true,"pilot");var execution=new ExecutionService(db,manager,json,access,true,"pilot");evidence=new EvidenceService(db,manager,json,access,execution,"pilot");recovery=new RecoveryService(db,manager,json,access,execution,"pilot");
  String payload=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));order=orders.fromQuote(new FromQuote("11111111-1111-4111-8111-111111111111","Q-OMS-001",1),"conversion");order=orders.submit(order.id(),new Command(order.version(),"pilot","submit"));
 }
 @Test void lookupReportsAbandonmentAndJournalUsesOrderVersion(){
  long before=order.version();recovery.abandon(order.id(),"never-received",new RecoveryCommand(before,"abandon","verified absent"));
  var result=new OrderOperationController(orders,db).get(order.id(),"never-received").getData();
  assertEquals("ABANDONED",result.status());assertEquals(orders.get(order.id()).version(),result.orderVersion());
  var audit=db.queryForMap("SELECT * FROM order_command_audit WHERE operation_key='abandon'");assertEquals(before,((Number)audit.get("before_version")).longValue());assertEquals(before+1,((Number)audit.get("after_version")).longValue());
  assertEquals(409,assertThrows(OrderProblem.class,()->orders.hold(order.id(),new Hold(orders.get(order.id()).version(),"late","never-received",true))).status());
 }
 @Test void exceptionVersionDoesNotReplaceAggregateVersionInJournal(){
  db.update("INSERT INTO order_exceptions(id,order_id,category,severity,next_action,reason) VALUES('ex',?,'REVIEW_NOTE','LOW','REVIEW','test')",order.id());
  var command=new Assignment(0,"assign","triage","owner");recovery.assign("ex",command);recovery.assign("ex",command);
  var audit=db.queryForMap("SELECT * FROM order_command_audit WHERE operation_key='assign'");assertEquals(order.version(),((Number)audit.get("before_version")).longValue());assertEquals(order.version()+1,((Number)audit.get("after_version")).longValue());assertEquals(1,db.queryForObject("SELECT count(*) FROM order_command_audit WHERE operation_key='assign'",Integer.class));
 }
 @Test void adjudicatedConflictAllowsEligibleCancellationButPendingDeliveryStillBlocks(){
  String line=order.lines().getFirst().id();var when=Instant.now().minusSeconds(30);
  fact("allocate","ALLOCATION",2,"lot",null,null,when);
  var conflict=fact("conflict","ALLOCATION",3,"lot",null,null,when);
  fact("release","RELEASE",2,"release","lot",null,when.plusSeconds(1));
  assertEquals(409,assertThrows(OrderProblem.class,()->orders.cancel(order.id(),new Cancellation(version(),"cancel","blocked",line,1))).status());
  recovery.adjudicate(order.id(),conflict.evidenceId(),new Adjudication(version(),"disavow","authority repudiated erroneous observation","authority-ref",Instant.now()));
  assertEquals(1,orders.cancel(order.id(),new Cancellation(version(),"eligible cancellation","cancel",line,1)).lines().getFirst().cancelled());
  assertEquals("CONFLICT",db.queryForObject("SELECT status FROM fulfillment_evidence_state WHERE evidence_id=?",String.class,conflict.evidenceId()));
  fact("pending","DELIVERY",1,"delivery",null,"unseen-shipment",when.plusSeconds(2));
  assertEquals(409,assertThrows(OrderProblem.class,()->orders.cancel(order.id(),new Cancellation(version(),"still uncertain","pending-block",line,1))).status());
 }
 long version(){return orders.get(order.id()).version();}
 EvidenceReceipt fact(String key,String kind,int quantity,String ref,String allocation,String shipment,Instant at){return evidence.record(order.id(),new EvidenceCommand(version(),key,"synthetic authority",order.lines().getFirst().id(),kind,quantity,ref,at,allocation,shipment,kind.equals("ALLOCATION")?Instant.now().plusSeconds(3600):null));}
}
