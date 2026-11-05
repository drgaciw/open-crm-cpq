package com.opencrm.order.service;
import com.opencrm.order.operations.IntakeAdmission;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.security.OrderAccess;
import java.nio.file.*;
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
class AdmissionHookIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 @Test void pressureRejectsOnlyNewIntakeAndKeepsAcceptedReceiptsReplayable() throws Exception {
  Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("order_management").defaultSchema("order_management").load().migrate();
  var ds=new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword());var db=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);var json=JsonMapper.builder().build();
  var access=mock(OrderAccess.class);when(access.actor()).thenReturn("admission-operator");when(access.scopeId()).thenReturn("pilot");when(access.customerIds()).thenReturn(Set.of("fixture-account"));
  var normal=new OrderService(db,manager,json,access,true,"pilot");
  String original=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
  String second=original.replace("11111111-1111-4111-8111-111111111111","11111111-1111-4111-8111-111111111112").replace("22222222-2222-4222-8222-222222222222","22222222-2222-4222-8222-222222222223");
  // Generate the second identity structurally, independent of fixture UUID values.
  var secondTree=(tools.jackson.databind.node.ObjectNode)json.readTree(second);secondTree.put("snapshotId",UUID.randomUUID().toString());secondTree.put("quoteId",UUID.randomUUID().toString());second=secondTree.toString();
  normal.acceptTrustedSnapshot(original,OrderService.hash(original));normal.acceptTrustedSnapshot(second,OrderService.hash(second));
  var firstTree=json.readTree(original);var firstRequest=new FromQuote(firstTree.get("snapshotId").asText(),firstTree.get("quoteId").asText(),1);
  var first=normal.fromQuote(firstRequest,"convert");
  var bounded=new OrderService(db,manager,json,access,new IntakeAdmission(db,"pilot",1048576,1),true,"pilot");
  assertEquals(first.id(),bounded.fromQuote(firstRequest,"convert").id());
  assertEquals(first.id(),bounded.fromQuote(firstRequest,"new-alias-reference").id());
  var secondRequest=new FromQuote(secondTree.get("snapshotId").asText(),secondTree.get("quoteId").asText(),1);
  assertEquals(503,assertThrows(OrderProblem.class,()->bounded.fromQuote(secondRequest,"new-convert")).status());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_idempotency WHERE operation_key='new-convert'",Integer.class));
  var tightened=new OrderService(db,manager,json,access,new IntakeAdmission(db,"pilot",1,1),true,"pilot");
  assertDoesNotThrow(()->tightened.acceptTrustedSnapshot(original,OrderService.hash(original)));
  secondTree.put("snapshotId",UUID.randomUUID().toString());secondTree.put("quoteId",UUID.randomUUID().toString());String third=secondTree.toString();
  assertEquals(422,assertThrows(OrderProblem.class,()->tightened.acceptTrustedSnapshot(third,OrderService.hash(third))).status());
  assertEquals(503,assertThrows(OrderProblem.class,()->bounded.acceptTrustedSnapshot(third,OrderService.hash(third))).status());
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM snapshot_inbox",Integer.class));
  assertDoesNotThrow(()->bounded.submit(first.id(),new Command(0,"drain existing order","submit")));
 }
}
