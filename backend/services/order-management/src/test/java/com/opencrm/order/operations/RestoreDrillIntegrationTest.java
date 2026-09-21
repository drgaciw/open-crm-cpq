package com.opencrm.order.operations;

import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.execution.ExecutionService;
import com.opencrm.order.fulfillment.*;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local functional backup/restore exercise. No broker or fulfillment provider is connected. */
@Tag("integration") @Testcontainers
class RestoreDrillIntegrationTest {
    private static final String RESTORED_DATABASE="oms_restored";
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("oms_original");
    private final JsonMapper json=JsonMapper.builder().build();
    private record Services(JdbcTemplate db,OrderService orders,ExecutionService execution,EvidenceService evidence) {}

    private Services services(String database,boolean intakeEnabled) {
        String url=postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/"+database)+"&currentSchema=order_management";
        var source=new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword());
        var db=new JdbcTemplate(source);
        var manager=new DataSourceTransactionManager(source);
        var access=mock(OrderAccess.class);
        when(access.actor()).thenReturn("fictional-drill-operator");
        when(access.scopeId()).thenReturn("pilot");
        when(access.customerIds()).thenReturn(Set.of("fixture-account"));
        var orders=new OrderService(db,manager,json,access,intakeEnabled,"pilot");
        var execution=new ExecutionService(db,manager,json,access,true,"pilot");
        return new Services(db,orders,execution,new EvidenceService(db,manager,json,access,execution,"pilot"));
    }
    private void command(String... arguments)throws Exception {
        var result=postgres.execInContainer(arguments);
        assertEquals(0,result.getExitCode(),()->"PostgreSQL drill command failed: "+String.join(" ",arguments)+"\n"+result.getStderr());
    }
    private List<Map<String,Object>> rows(JdbcTemplate db,String table,String key) {
        return db.queryForList("SELECT * FROM "+table+" ORDER BY "+key);
    }

    @Test void restoreRetainsAcknowledgedLocalStateAndDoesNotRedispatchUnknownWork()throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .schemas("order_management").defaultSchema("order_management").load().migrate();
        var original=services(postgres.getDatabaseName(),true);
        String payload=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
        String hash=OrderService.hash(payload);
        original.orders().acceptTrustedSnapshot(payload,hash);
        var tree=json.readTree(payload);
        var source=new FromQuote(tree.get("snapshotId").asText(),tree.get("quoteId").asText(),1);
        var converted=original.orders().fromQuote(source,"drill-convert");
        var submit=new Command(converted.version(),"fictional pilot accepted","drill-submit");
        original.orders().submit(converted.id(),submit);
        String orderId=converted.id(),lineId=converted.lines().getFirst().id();
        // This is a fictional manual allocation fact, never a remote acknowledgment or claimed delivery.
        original.evidence().record(orderId,new EvidenceCommand(original.orders().get(orderId).version(),
                "drill-allocation","fictional allocation evidence",lineId,"ALLOCATION",4,
                "fictional-allocation",Instant.now(),null,null,Instant.now().plusSeconds(3600)));
        String shipment=original.db().queryForObject("SELECT id FROM order_tasks WHERE order_id=? AND kind='SHIPMENT'",String.class,orderId);
        var lease=original.execution().claim(shipment,"drill-worker",Duration.ofSeconds(30)).orElseThrow();
        original.execution().dispatch(lease);
        original.execution().recordUnknown(lease,"fictional provider response unavailable; no success asserted");
        var before=original.orders().get(orderId);
        assertEquals(4,before.lines().getFirst().allocated());
        assertEquals(0,before.lines().getFirst().shipped());
        assertEquals("PENDING",before.financeStatus());

        Map<String,String> tables=new LinkedHashMap<>();
        for(String table:List.of("orders","order_lines","order_tasks","order_audit","outbox_events",
                "fulfillment_evidence","allocation_lots","order_exceptions"))tables.put(table,"id");
        tables.put("snapshot_inbox","snapshot_id");tables.put("order_idempotency","scope_id,operation_key");
        tables.put("task_operations","operation_key");tables.put("fulfillment_evidence_state","evidence_id");
        tables.put("fulfillment_evidence_identity","scope_id,kind,external_reference");
        tables.put("fulfillment_evidence_receipts","scope_id,operation_key");
        tables.put("order_task_dependencies","task_id,predecessor_id");
        tables.put("finance_requests","request_key");tables.put("finance_acknowledgments","id");
        // The coordinator's additive command journal may be present when this test is merged.
        if(original.db().queryForObject("SELECT to_regclass('order_management.order_command_audit')::text",String.class)!=null)
            tables.put("order_command_audit","scope_id,operation_key");
        var expected=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var table:tables.entrySet())expected.put(table.getKey(),rows(original.db(),table.getKey(),table.getValue()));
        assertTrue(expected.get("outbox_events").stream().allMatch(row->Boolean.FALSE.equals(row.get("published"))));
        Instant backupStarted=Instant.now();
        command("pg_dump","--username",postgres.getUsername(),"--format=custom","--file=/tmp/oms-drill.dump",postgres.getDatabaseName());
        Instant backupFinished=Instant.now();
        // Prove isolation and the backup cutoff: this acknowledged post-backup mutation stays only in source.
        original.orders().hold(orderId,new Hold(before.version(),"post-backup source-only marker","source-only-marker",true));
        command("createdb","--username",postgres.getUsername(),RESTORED_DATABASE);
        Instant restoreStarted=Instant.now();
        long restoreNanos=System.nanoTime();
        command("pg_restore","--username",postgres.getUsername(),"--exit-on-error","--dbname",RESTORED_DATABASE,"/tmp/oms-drill.dump");
        long restoreMillis=Duration.ofNanos(System.nanoTime()-restoreNanos).toMillis();
        // New datasource, transaction manager and service instances simulate process reconstruction.
        var restored=services(RESTORED_DATABASE,false);
        assertEquals(RESTORED_DATABASE,restored.db().queryForObject("SELECT current_database()",String.class));
        assertNotEquals(original.db().getDataSource(),restored.db().getDataSource());
        for(var table:tables.entrySet())assertEquals(expected.get(table.getKey()),rows(restored.db(),table.getKey(),table.getValue()),table.getKey()+" must match the backup exactly");
        String restoredPayload=restored.db().queryForObject("SELECT payload FROM snapshot_inbox",String.class);
        assertEquals(payload,restoredPayload);assertEquals(hash,OrderService.hash(restoredPayload));
        assertEquals(hash,restored.db().queryForObject("SELECT snapshot_hash FROM orders WHERE id=?",String.class,orderId));
        assertEquals(before,restored.orders().get(orderId));
        assertEquals(0,restored.db().queryForObject("SELECT count(*) FROM order_idempotency WHERE operation_key='source-only-marker'",Integer.class));
        assertEquals(1,original.db().queryForObject("SELECT count(*) FROM order_idempotency WHERE operation_key='source-only-marker'",Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class,()->restored.db().update("UPDATE snapshot_inbox SET payload='{}'"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->restored.db().update("DELETE FROM order_audit"));

        assertEquals(orderId,restored.orders().fromQuote(source,"drill-convert").id());
        assertEquals(orderId,restored.orders().fromQuote(source,"drill-convert-alias").id());
        assertEquals(before.version(),restored.orders().submit(orderId,submit).version());
        var newSource=(tools.jackson.databind.node.ObjectNode)json.readTree(payload);
        newSource.put("snapshotId",UUID.randomUUID().toString());newSource.put("quoteId",UUID.randomUUID().toString());newSource.put("quoteNumber","Q-FICTIONAL-NEW");
        String newPayload=newSource.toString();restored.orders().acceptTrustedSnapshot(newPayload,OrderService.hash(newPayload));
        assertEquals(422,assertThrows(OrderProblem.class,()->restored.orders().fromQuote(new FromQuote(newSource.get("snapshotId").asText(),newSource.get("quoteId").asText(),1),"new-disabled")).status());
        assertEquals("UNKNOWN",restored.db().queryForObject("SELECT status FROM order_tasks WHERE id=?",String.class,shipment));
        assertTrue(restored.execution().claim(shipment,"restarted-worker",Duration.ofSeconds(30)).isEmpty());
        assertEquals(409,assertThrows(OrderProblem.class,()->restored.execution().retry(orderId,shipment,
                new Command(before.version(),"retry without no-effect proof","unsafe-retry"))).status());
        assertEquals(409,assertThrows(OrderProblem.class,()->restored.execution().dispatch(lease)).status());
        assertEquals(expected.get("outbox_events"),rows(restored.db(),"outbox_events","id"));
        assertEquals(0,restored.orders().get(orderId).lines().getFirst().shipped());

        var artifact=new LinkedHashMap<String,Object>();
        artifact.put("evidenceClass","LOCAL_FUNCTIONAL_DRILL_NOT_PRODUCTION_RPO_RTO");
        artifact.put("backupStartedAt",backupStarted.toString());artifact.put("backupFinishedAt",backupFinished.toString());
        artifact.put("restoreStartedAt",restoreStarted.toString());artifact.put("backupAgeAtRestoreMillis",Duration.between(backupFinished,restoreStarted).toMillis());
        artifact.put("restoreElapsedMillis",restoreMillis);artifact.put("restoredDatabaseIsSeparate",true);
        artifact.put("snapshotsRetained",expected.get("snapshot_inbox").size());artifact.put("receiptsRetained",expected.get("order_idempotency").size());
        artifact.put("auditRowsRetained",expected.get("order_audit").size());artifact.put("pendingOutboxRowsRetained",expected.get("outbox_events").size());
        artifact.put("unknownRedispatchBlocked",true);artifact.put("postBackupSourceOnlyCommandIntentionallyExcluded",true);
        Path output=Path.of("target/restore-drill-report.json");Files.createDirectories(output.getParent());
        Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(artifact));
    }
}
