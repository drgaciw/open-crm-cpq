package com.opencrm.order.operations;

import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import java.nio.file.*;
import java.sql.*;
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

@Tag("integration") @Testcontainers
class DbRoleIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void runtimeCanProcessOrdersButCannotRewriteHistoryOrBypassTriggers() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
            .schemas("order_management").defaultSchema("order_management").load().migrate();
        String url=postgres.getJdbcUrl()+"&currentSchema=order_management";
        var owner=new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword());
        var admin=new JdbcTemplate(owner);
        admin.execute("CREATE ROLE oms_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
        var runtime=new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword()) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection=super.getConnection();
                try(var statement=connection.createStatement()){statement.execute("SET ROLE oms_app");}
                catch(SQLException error){connection.close();throw error;}
                return connection;
            }
        };
        var db=new JdbcTemplate(runtime);
        assertEquals("oms_app",db.queryForObject("SELECT current_user",String.class));
        assertDenied(runtime,"SELECT count(*) FROM order_management.orders");
        // A prior broad global default must not leak UPDATE onto future append-only tables.
        admin.execute("ALTER DEFAULT PRIVILEGES GRANT ALL ON TABLES TO oms_app");
        String grants=Files.readString(Path.of("db/runtime-grants.sql"));
        admin.execute(grants);
        admin.execute(grants); // Reapplying after migrations must be safe.
        var access=mock(OrderAccess.class);
        when(access.actor()).thenReturn("role-drill");when(access.scopeId()).thenReturn("pilot");
        when(access.customerIds()).thenReturn(Set.of("fixture-account"));
        var json=JsonMapper.builder().build();
        var service=new OrderService(db,new DataSourceTransactionManager(runtime),json,access,true,"pilot");
        String payload=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
        var source=json.readTree(payload);
        service.acceptTrustedSnapshot(payload,OrderService.hash(payload));
        var order=service.fromQuote(new FromQuote(source.get("snapshotId").asText(),source.get("quoteId").asText(),1),"role-convert");
        var submitted=service.submit(order.id(),new Command(order.version(),"role drill submit","role-submit"));
        assertEquals("SUBMITTED",submitted.status());
        assertEquals(submitted.version(),service.get(order.id()).version());
        assertTrue(db.queryForObject("SELECT count(*) FROM order_audit",Integer.class)>0);
        assertTrue(db.queryForObject("SELECT count(*) FROM outbox_events",Integer.class)>0);
        Long cleanupId=db.queryForObject("SELECT min(id) FROM outbox_events",Long.class);
        db.update("UPDATE outbox_events SET published=true WHERE id=?",cleanupId);
        assertEquals(1,db.update("DELETE FROM outbox_events WHERE id=? AND published=true",cleanupId));
        for(String table:List.of("snapshot_inbox","order_audit","order_command_audit","order_idempotency",
                "execution_inbox","operation_reconciliations","fulfillment_evidence","fulfillment_evidence_identity",
                "fulfillment_evidence_receipts","finance_requests","finance_acknowledgments",
                "order_replacement_requests","order_replacement_lineage")) {
            assertFalse(admin.queryForObject("SELECT has_table_privilege('oms_app',?,'UPDATE')",Boolean.class,"order_management."+table));
            assertDenied(runtime,"DELETE FROM "+table);
            assertDenied(runtime,"TRUNCATE "+table);
        }
        assertDenied(runtime,"UPDATE order_audit SET reason='rewritten'");
        assertDenied(runtime,"UPDATE order_command_audit SET reason='rewritten'");
        assertDenied(runtime,"UPDATE snapshot_inbox SET payload='{}'");
        try(var connection=runtime.getConnection();var statement=connection.createStatement()) {
            var error=assertThrows(SQLException.class,()->statement.executeUpdate("UPDATE snapshot_inbox SET snapshot_id=snapshot_id"));
            assertEquals("P0001",error.getSQLState()); // Narrow row-lock privilege cannot rewrite even identity.
        }
        assertDenied(runtime,"ALTER TABLE snapshot_inbox DISABLE TRIGGER ALL");
        assertDenied(runtime,"ALTER TABLE order_audit DISABLE TRIGGER ALL");
        assertDenied(runtime,"ALTER TABLE orders DISABLE TRIGGER ALL");
        assertDenied(runtime,"SET session_replication_role=replica");
        assertDenied(runtime,"CREATE TABLE order_management.unapproved(id int)");
        assertDenied(runtime,"INSERT INTO flyway_schema_history(installed_rank) VALUES(999)");
        try(var connection=runtime.getConnection();var statement=connection.createStatement()) {
            var error=assertThrows(SQLException.class,()->statement.executeUpdate("UPDATE orders SET snapshot_json='{}'"));
            assertEquals("P0001",error.getSQLState()); // Existing immutable-commercial-fields trigger still applies.
        }
        assertEquals(payload,db.queryForObject("SELECT snapshot_json FROM orders WHERE id=?",String.class,order.id()));
        admin.execute("CREATE TABLE order_management.future_ledger(id int)");
        db.update("INSERT INTO future_ledger VALUES(1)");
        assertEquals(1,db.queryForObject("SELECT count(*) FROM future_ledger",Integer.class));
        assertDenied(runtime,"UPDATE future_ledger SET id=2");
        assertDenied(runtime,"DELETE FROM future_ledger");
        admin.execute("CREATE TABLE order_management.recovery_daily_runs(id int)");
        admin.execute(grants);
        db.update("INSERT INTO recovery_daily_runs VALUES(1)");
        assertEquals(1,db.update("UPDATE recovery_daily_runs SET id=2"));
    }

    private static void assertDenied(DriverManagerDataSource source,String sql) throws Exception {
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            SQLException error=assertThrows(SQLException.class,()->statement.execute(sql),sql);
            assertEquals("42501",error.getSQLState(),sql+": "+error.getMessage());
        }
    }
}
