package com.opencrm.order.advisory;

import com.opencrm.jev.JevResult;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.order.recovery.DailyReconciliation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("integration")
class OrderAdvisoryPostgresIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate db;
    static DataSourceTransactionManager manager;
    OrderAdvisoryRepository repository;
    String orderId, exceptionId;

    @BeforeAll static void migrate() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management",postgres.getUsername(),postgres.getPassword());
        Flyway.configure().dataSource(source).schemas("order_management").defaultSchema("order_management").load().migrate();
        db=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source);
    }

    @BeforeEach void setup() {
        db.execute("TRUNCATE snapshot_inbox,jev_decision_log,order_advisory_claims,recovery_daily_runs CASCADE");
        repository=new OrderAdvisoryRepository(db,manager);
        orderId=UUID.randomUUID().toString(); exceptionId=UUID.randomUUID().toString();
        String snapshot=UUID.randomUUID().toString(), quote=UUID.randomUUID().toString();
        db.update("INSERT INTO snapshot_inbox VALUES(?,'pilot','account',?,1,'{}',?,clock_timestamp())",snapshot,quote,"a".repeat(64));
        db.update("""
                INSERT INTO orders(id,order_number,scope_id,customer_id,quote_id,quote_number,accepted_revision,
                  snapshot_id,snapshot_json,snapshot_hash,status,version,created_at,updated_at,created_by,updated_by)
                VALUES(?,'ORDER-1','pilot','account',?,'QUOTE-1',1,?,'{}',?,'DRAFT',1,clock_timestamp(),clock_timestamp(),'test','test')
                """,orderId,quote,snapshot,"a".repeat(64));
        db.update("INSERT INTO order_exceptions(id,order_id,category,severity,next_action,reason) VALUES(?,?,'UNKNOWN_OUTCOME','HIGH','RECONCILE','private evidence reference must stay local')",exceptionId,orderId);
    }

    private OrderAdvisorySnapshot snapshot() { return repository.snapshots(orderId,"pilot",Instant.now()).getFirst(); }
    private boolean claim(UUID id, String key, OrderAdvisorySnapshot s) {
        return repository.claim(id,key,s,OrderAdvisoryFeatures.state(s.facts()),"test-pinned-v1",OrderAdvisoryQuestions.RUBRIC,s.capturedAt().plusSeconds(900));
    }
    private JevDecisionRecord record(UUID id) {
        var result=new JevResult(JevResult.Status.TIMEOUT,Map.of(),0,5,1,null,"Timeout","test-pinned-v1",OrderAdvisoryQuestions.RUBRIC,"fake","none");
        return JevDecisionRecord.of(OrderAdvisoryQuestions.USE_CASE,"OMS_EXCEPTION_EVALUATION",id.toString(),OrderAdvisoryQuestions.triage(),
                OrderAdvisoryFeatures.state(snapshot().facts()),result,null,JevDecisionRecord.AuthoritativeSource.NONE,true);
    }

    @Test void committedSnapshotsAreScopedBoundedAndExcludeRawEvidence() {
        assertTrue(repository.snapshots(orderId,"foreign",Instant.now()).isEmpty());
        var s=snapshot(); assertEquals("account",s.customerId()); assertEquals(1,s.orderVersion());
        String state=OrderAdvisoryFeatures.state(s.facts()).canonicalJson();
        assertFalse(state.contains("account")); assertFalse(state.contains(orderId)); assertFalse(state.contains("private evidence"));
        for(int i=0;i<10;i++)db.update("INSERT INTO order_exceptions(id,order_id,category,severity,next_action,reason) VALUES(?,?,'REVIEW_NOTE','LOW','REVIEW','local')",UUID.randomUUID().toString(),orderId);
        assertEquals(5,repository.snapshots(orderId,"pilot",Instant.now()).size());
        db.update("UPDATE order_exceptions SET status='RESOLVED' WHERE order_id=?",orderId);
        assertTrue(repository.snapshots(orderId,"pilot",Instant.now()).isEmpty());
    }

    @Test void staleVersionAndClosedExceptionCannotClaim() {
        var s=snapshot(); db.update("UPDATE orders SET version=2 WHERE id=?",orderId);
        assertFalse(claim(UUID.randomUUID(),"a".repeat(64),s));
        s=snapshot(); db.update("UPDATE order_exceptions SET status='RESOLVED',version=version+1 WHERE id=?",exceptionId);
        assertFalse(claim(UUID.randomUUID(),"b".repeat(64),s));
    }

    @Test void financeUnknownAndEvidenceFactsReflectCurrentTaskState() {
        String line=UUID.randomUUID().toString(), finance=UUID.randomUUID().toString(), activation=UUID.randomUUID().toString();
        db.update("INSERT INTO order_lines(id,order_id,source_line_id,description,ordered) VALUES(?,?,'line','private description',1)",line,orderId);
        db.update("INSERT INTO order_tasks(id,order_id,line_id,kind,template_version,status) VALUES(?,?,?,'FINANCE_ACK',1,'UNKNOWN'),(?,?,?,'ACTIVATION',1,'WAITING')",finance,orderId,line,activation,orderId,line);
        db.update("UPDATE order_exceptions SET category='FINANCE_UNKNOWN' WHERE id=?",exceptionId);
        String evidence=UUID.randomUUID().toString();
        db.update("""
                INSERT INTO fulfillment_evidence(id,order_id,line_id,scope_id,kind,quantity,external_reference,
                  occurred_at,actor,reason,body_hash,semantic_hash)
                VALUES(?,?,?,'pilot','DELIVERY',1,'private-reference',clock_timestamp(),'test','private reason',?,?)
                """,evidence,orderId,line,"f".repeat(64),"e".repeat(64));
        db.update("INSERT INTO fulfillment_evidence_state(evidence_id,status) VALUES(?,'PENDING')",evidence);
        var facts=snapshot().facts();
        assertEquals(OrderAdvisoryFeatures.Flag.YES,facts.unknownOutcome());
        assertEquals(OrderAdvisoryFeatures.Flag.YES,facts.pendingEvidence());
        assertEquals(OrderAdvisoryFeatures.Flag.YES,facts.activationPending());
        db.update("UPDATE order_tasks SET status='SUCCESS' WHERE order_id=?",orderId);
        db.update("UPDATE fulfillment_evidence_state SET status='CONFLICT' WHERE evidence_id=?",evidence);
        facts=snapshot().facts();
        assertEquals(OrderAdvisoryFeatures.Flag.NO,facts.unknownOutcome());
        assertEquals(OrderAdvisoryFeatures.Flag.NO,facts.activationPending());
        assertEquals(OrderAdvisoryFeatures.Flag.NO,facts.pendingEvidence());
        assertEquals(OrderAdvisoryFeatures.Flag.YES,facts.conflictingEvidence());
    }

    @Test void optionalHookFailureCannotRollbackOperatorRecovery() {
        var access=org.mockito.Mockito.mock(com.opencrm.order.security.OrderAccess.class);
        org.mockito.Mockito.when(access.actor()).thenReturn("test-operator");
        var recovery=new com.opencrm.order.recovery.RecoveryService(db,manager,tools.jackson.databind.json.JsonMapper.builder().build(),
                access,org.mockito.Mockito.mock(com.opencrm.order.execution.ExecutionService.class),"pilot");
        recovery.setAdvisory(id->{throw new IllegalStateException("optional adapter broken");});
        var result=recovery.assign(exceptionId,new com.opencrm.order.recovery.RecoveryDtos.Assignment(0,"assign-advisory-test","operator chose owner","operator"));
        assertEquals("SUCCEEDED",result.status());
        assertEquals("operator",db.queryForObject("SELECT owner FROM order_exceptions WHERE id=?",String.class,exceptionId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM recovery_receipts",Integer.class));
    }

    @Test void concurrentReplicasClaimOnceAndPreserveExactBinding() throws Exception {
        var s=snapshot();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<12;i++)tasks.add(pool.submit(()->claim(UUID.randomUUID(),"c".repeat(64),s)));
            int winners=0; for(var task:tasks)if(task.get())winners++;
            assertEquals(1,winners);
        }
        var binding=db.queryForMap("SELECT * FROM order_advisory_claims");
        assertEquals(s.exceptionId(),binding.get("exception_id")); assertEquals(s.orderVersion(),((Number)binding.get("order_version")).longValue());
        assertEquals("account",binding.get("customer_id"));
        assertThrows(RuntimeException.class,()->db.update("UPDATE order_advisory_claims SET customer_id='foreign'"));
    }

    @Test void decisionAndBindingAreAtomicEvenWhenLinkInsertFails() {
        UUID id=UUID.randomUUID(); assertTrue(claim(id,"d".repeat(64),snapshot()));
        repository.complete(id,record(id));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM jev_decision_log",Integer.class));
        assertThrows(RuntimeException.class,()->repository.complete(id,record(id)));
        assertThrows(RuntimeException.class,()->repository.complete(UUID.randomUUID(),record(id)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM jev_decision_log",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_advisory_decisions",Integer.class));
        assertNull(db.queryForMap("SELECT authoritative_outcome FROM jev_decision_log").get("authoritative_outcome"));
    }

    @Test void throwingOptionalAdvisoryCannotRollbackReconciliation() {
        db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,exhausted_at) VALUES(?,'Order',?,'test','{}',clock_timestamp())",UUID.randomUUID().toString(),orderId);
        var daily=new DailyReconciliation(db,manager,"pilot");
        daily.setAdvisory(id->{throw new IllegalStateException("advisory unavailable");});
        assertEquals(1,daily.scan(10));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_exceptions WHERE category='OUTBOX_EXHAUSTED'",Integer.class));
        assertTrue(db.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,orderId)>1);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_advisory_claims",Integer.class));
    }

    @Test void runtimeGrantsAllowAdvisoryWritesWithoutHistoryMutation() throws Exception {
        db.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='oms_app') THEN CREATE ROLE oms_app LOGIN NOINHERIT PASSWORD 'synthetic-test-only'; END IF; END $$");
        try(var connection=db.getDataSource().getConnection();var statement=connection.createStatement()) {
            statement.execute(Files.readString(Path.of("db/runtime-grants.sql")));
        }
        var runtimeSource=new DriverManagerDataSource(postgres.getJdbcUrl()+"&currentSchema=order_management","oms_app","synthetic-test-only");
        var runtime=new JdbcTemplate(runtimeSource);
        var repo=new OrderAdvisoryRepository(runtime,new DataSourceTransactionManager(runtimeSource));
        var s=repo.snapshots(orderId,"pilot",Instant.now()).getFirst(); var id=UUID.randomUUID();
        assertTrue(repo.claim(id,"e".repeat(64),s,OrderAdvisoryFeatures.state(s.facts()),"test-pinned-v1",OrderAdvisoryQuestions.RUBRIC,s.capturedAt().plusSeconds(900)));
        repo.complete(id,record(id));
        assertThrows(RuntimeException.class,()->runtime.update("UPDATE jev_decision_log SET answer_value='RETRY'"));
        assertThrows(RuntimeException.class,()->runtime.update("DELETE FROM order_advisory_claims"));
        assertThrows(RuntimeException.class,()->runtime.execute("TRUNCATE order_audit"));
    }

    @Test void realAsynchronousHookSeesCommittedVersionAndLogsOnSeparateTransaction() throws Exception {
        var properties=new com.opencrm.jev.JevProperties();
        properties.setEnabled(true); properties.setModel("offline-test-v1");
        properties.getOmsExceptionTriage().setEnabled(true);
        var fake=new com.opencrm.jev.testing.FakeJevTransport().fail(new IllegalStateException("offline provider failure"));
        var completion=new java.util.concurrent.CountDownLatch(1);
        var observed=org.mockito.Mockito.spy(repository);
        org.mockito.Mockito.doAnswer(invocation->{
            try{return invocation.callRealMethod();}finally{completion.countDown();}
        }).when(observed).complete(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        try(var runner=new com.opencrm.jev.advisory.JevAdvisoryRunner(1)) {
            var service=new OrderAdvisoryService(new com.opencrm.jev.JevClient(properties,fake),properties,runner,observed,
                    "pilot",java.time.Duration.ofMinutes(15),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
            new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status->{
                db.update("UPDATE order_exceptions SET version=7 WHERE id=?",exceptionId);
                service.afterCommit(orderId);
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_advisory_claims",Integer.class));
                assertTrue(fake.requests().isEmpty());
            });
            assertTrue(completion.await(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(7,db.queryForObject("SELECT exception_version FROM order_advisory_claims",Long.class));
            assertEquals("ERROR",db.queryForObject("SELECT status FROM jev_decision_log",String.class));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_advisory_decisions",Integer.class));
        }
    }
}
