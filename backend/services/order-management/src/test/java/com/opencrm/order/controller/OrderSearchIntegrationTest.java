package com.opencrm.order.controller;

import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @Tag("integration")
class OrderSearchIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 JdbcTemplate db;OrderService orders;MockMvc mvc;String first;
 @BeforeEach void setup() throws Exception {
  Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("order_management").defaultSchema("order_management").load().migrate();
  var ds=new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword());
  db=new JdbcTemplate(ds);db.execute("TRUNCATE orders,snapshot_inbox CASCADE");
  var access=mock(OrderAccess.class);when(access.actor()).thenReturn("search-operator");when(access.scopeId()).thenReturn("pilot");when(access.customerIds()).thenReturn(Set.of("fixture-account"));
  var json=JsonMapper.builder().build();orders=new OrderService(db,new DataSourceTransactionManager(ds),json,access,true,"pilot");
  String fixture=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
  for(int n=1;n<=3;n++) {
   var tree=(tools.jackson.databind.node.ObjectNode)json.readTree(fixture);String snapshot=UUID.randomUUID().toString(),quote=UUID.randomUUID().toString();
   tree.put("snapshotId",snapshot);tree.put("quoteId",quote);tree.put("quoteNumber","SEARCH-"+n);if(n==3)tree.put("customerId","other-account");
   String payload=tree.toString();orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));
   var order=orders.fromQuote(new FromQuote(snapshot,quote,1),"convert-"+n);
   if(n==1)first=order.id();
   if(n!=2) {
    String task=orders.plan(order.id()).getFirst().id();
    db.update("INSERT INTO order_exceptions(id,order_id,task_id,category,severity,next_action,reason) VALUES(?,?,?,'TEST','HIGH','RECONCILE','operator review')",UUID.randomUUID().toString(),order.id(),task);
    db.update("INSERT INTO fulfillment_evidence(id,order_id,line_id,scope_id,kind,quantity,external_reference,occurred_at,actor,reason,body_hash,semantic_hash) VALUES(?,?,?,'pilot','DELIVERY',1,'carrier-proof',CURRENT_TIMESTAMP,'operator','proof',?,?)",UUID.randomUUID().toString(),order.id(),order.lines().getFirst().id(),"a".repeat(64),"b".repeat(64));
   }
  }
  mvc=MockMvcBuilders.standaloneSetup(new OrderController(orders)).build();
 }
 @Test void externalReferenceAndOpenExceptionFiltersAreAppliedWithinAccountScope() throws Exception {
  mvc.perform(get("/api/v1/orders").param("externalReference","carrier-proof").param("hasExceptions","true"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalElements").value(1)).andExpect(jsonPath("$.data[0].id").value(first));
  mvc.perform(get("/api/v1/orders").param("hasExceptions","false"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalElements").value(1)).andExpect(jsonPath("$.data[0].quoteNumber").value("SEARCH-2"));
  mvc.perform(get("/api/v1/orders").param("externalReference","absent"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalElements").value(0));
 }
}
