package com.opencrm.order.service;
import com.opencrm.order.security.OrderAccess;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers(disabledWithoutDocker=true)
class OrderPostgresIntegrationTest extends OrderServiceTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("oms");
 @BeforeAll static void migrate() {Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("order_management").defaultSchema("order_management").load().migrate();}
 @Override @BeforeEach void setup() {
  var ds=new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword());
  db=new JdbcTemplate(ds);db.execute("TRUNCATE snapshot_inbox,orders,order_lines,order_tasks,order_task_dependencies,order_holds,order_idempotency,order_audit,outbox_events CASCADE");
  manager=new DataSourceTransactionManager(ds);access=mock(OrderAccess.class);when(access.actor()).thenReturn("operator");when(access.scopeId()).thenReturn("pilot");when(access.customerIds()).thenReturn(Set.of("customer"));service=new OrderService(db,manager,json,access,true,"pilot");
 }
 @Test void databaseProtectsAuditAndSourceImmutability() {convert();assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE order_audit SET reason='tampered'"));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM snapshot_inbox"));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE orders SET snapshot_json='{}'"));}
}
