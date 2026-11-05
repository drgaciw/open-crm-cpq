package com.opencrm.order.service;

import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.execution.ExecutionService;
import com.opencrm.order.fulfillment.*;
import com.opencrm.order.finance.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers @Tag("integration")
class OrderCommandJournalIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 @Test void commandCorrelationCapturesExactVersionsIncludingNestedFinanceCompletion() throws Exception {
  Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("order_management").defaultSchema("order_management").load().migrate();
  var ds=new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword());
  var db=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);var json=JsonMapper.builder().build();
  var access=mock(OrderAccess.class);when(access.actor()).thenReturn("journal-operator");when(access.scopeId()).thenReturn("pilot");when(access.customerIds()).thenReturn(Set.of("fixture-account"));
  var orders=new OrderService(db,manager,json,access,true,"pilot");var execution=new ExecutionService(db,manager,json,access,true,"pilot");
  var evidence=new EvidenceService(db,manager,json,access,execution,"pilot");var finance=new FinanceService(db,manager,json,access,orders,execution);
  String payload=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json")).replace("PHYSICAL","SERVICE");
  orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));var tree=json.readTree(payload);
  var o=orders.fromQuote(new FromQuote(tree.get("snapshotId").asText(),tree.get("quoteId").asText(),1),"convert");
  orders.submit(o.id(),new Command(0,"approved","submit"));
  String line=o.lines().getFirst().id();int quantity=o.lines().getFirst().ordered();
  evidence.record(o.id(),new EvidenceCommand(1,"service","signed acceptance",line,"SERVICE",quantity,"signed-proof",Instant.now(),null,null,null));
  long beforePrepare=orders.get(o.id()).version();
  finance.prepare(o.id(),line,new Command(beforePrepare,"send exact terms","prepare"));
  long beforeAck=orders.get(o.id()).version();
  int auditsBefore=db.queryForObject("SELECT COUNT(*) FROM order_audit",Integer.class);
  int eventsBefore=db.queryForObject("SELECT COUNT(*) FROM outbox_events",Integer.class);
  db.execute("ALTER TABLE order_command_audit ADD CONSTRAINT reject_test_operation CHECK(operation_key<>'blocked')");
  var blocked=new FinanceDtos.Acknowledgment(beforeAck,"journal must commit","blocked",line,quantity,"invoice-blocked",Instant.now(),null,"ACKNOWLEDGED");
  assertThrows(com.opencrm.order.domain.OrderProblem.class,()->finance.acknowledge(o.id(),blocked));
  assertEquals(beforeAck,orders.get(o.id()).version());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM finance_acknowledgments",Integer.class));
  assertEquals(auditsBefore,db.queryForObject("SELECT COUNT(*) FROM order_audit",Integer.class));
  assertEquals(eventsBefore,db.queryForObject("SELECT COUNT(*) FROM outbox_events",Integer.class));
  var ack=new FinanceDtos.Acknowledgment(beforeAck,"verified external ledger","ack",line,quantity,"invoice-1",Instant.now(),null,"ACKNOWLEDGED");
  finance.acknowledge(o.id(),ack);finance.acknowledge(o.id(),ack);
  var journal=db.queryForMap("SELECT * FROM order_command_audit WHERE operation_key='ack'");
  assertEquals(beforeAck,((Number)journal.get("before_version")).longValue());
  assertEquals(orders.get(o.id()).version(),((Number)journal.get("after_version")).longValue());
  assertTrue(orders.get(o.id()).version()>beforeAck+1,"Nested completion advances more than one version");
  assertEquals("journal-operator",journal.get("actor"));assertEquals("verified external ledger",journal.get("reason"));
  assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM order_command_audit",Integer.class));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE order_command_audit SET reason='tampered'"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM order_command_audit"));
 }
}
