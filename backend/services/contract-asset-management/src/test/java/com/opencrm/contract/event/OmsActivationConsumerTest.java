package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.order.ActivationRequest;
import com.opencrm.common.order.AcceptedOrderSnapshot.Line;
import java.time.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("integration")
class OmsActivationConsumerTest {
    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcTemplate jdbc; ObjectMapper mapper; OmsActivationConsumer consumer; TransactionTemplate tx; OmsSourceRegistry sources;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        try(var paths=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(var p:paths.sorted(Comparator.comparing(path -> org.flywaydb.core.api.MigrationVersion.fromVersion(path.getFileName().toString().split("__")[0].substring(1)))).toList()) jdbc.execute(Files.readString(p));
        }
        mapper=new ObjectMapper().findAndRegisterModules();
        sources=new OmsSourceRegistry(jdbc,mapper);
        consumer=new OmsActivationConsumer(jdbc,mapper,sources,new OmsBrokerTrust(true,true,true,"SASL_SSL","quote-principal","oms-principal","pilot"));
    }
    ActivationRequest request(String event,String op,String line) {
        return new ActivationRequest(event,op,"order","pilot","account","b70be08b-f70d-406f-82db-96c3f53410dc",1,
            new Line(line,"product","Plan","DIGITAL","RECURRING",2,"EA","10.00","2.00","1.80","19.80","MONTHLY",LocalDate.of(2026,9,20),LocalDate.of(2027,9,20)),"verified-provision",Instant.parse("2026-09-20T12:00:00Z"));
    }
    void accept(ActivationRequest r) {tx.executeWithoutResult(s -> {try{consumer.accept(mapper.writeValueAsString(r));}catch(Exception e){throw new RuntimeException(e);}});}
    void source(String... lineIds) {
        var sourceLines=Arrays.stream(lineIds).map(id -> request("source","source",id).line()).toList();
        String subtotal=java.math.BigDecimal.valueOf(20L*lineIds.length).toPlainString();
        String discount=java.math.BigDecimal.valueOf(2L*lineIds.length).toPlainString();
        String tax=new java.math.BigDecimal("1.80").multiply(java.math.BigDecimal.valueOf(lineIds.length)).toPlainString();
        String total=new java.math.BigDecimal("19.80").multiply(java.math.BigDecimal.valueOf(lineIds.length)).toPlainString();
        var snapshot=new com.opencrm.common.order.AcceptedOrderSnapshot(1,"snapshot",request("a","b","c").quoteId(),"Q-ALIAS",1,"pilot","account",Instant.parse("2026-09-20T00:00:00Z"),"USD",subtotal,discount,tax,total,"address","approval","tax","config",sourceLines);
        tx.executeWithoutResult(t -> {try {
            String payload=mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(snapshot);
            var event=mapper.createObjectNode();event.put("eventId","source-event");event.set("snapshot",mapper.readTree(payload));event.put("snapshotHash",OmsSourceRegistry.hash(payload));
            new OmsSourceConsumer(mapper,sources,new OmsBrokerTrust(true,true,true,"SASL_SSL","quote-principal","oms-principal","pilot")).accept(mapper.writeValueAsString(event));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void rejectsUnregisteredSourceAndMismatchedTerms() {
        assertThrows(RuntimeException.class,()->accept(request("a","one","line-1")));
        source("line-1");
        assertThrows(RuntimeException.class,()->accept(request("b","two","other-line")));
        assertEquals(0,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
    }
    @Test void legacyAliasSubscriptionBlocksCanonicalActivation() {
        source("line-1");
        jdbc.update("INSERT INTO subscriptions(uuid,subscription_number,customer_id,quote_id,status,plan_name,start_date,end_date,mrr,arr,currency_code,erp_sync_status,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),"SUB-Q-ALIAS","account","Q-ALIAS","ACTIVE","Legacy",LocalDate.of(2026,9,20),LocalDate.of(2027,9,20),java.math.BigDecimal.ONE,java.math.BigDecimal.TEN,"USD","NOT_REQUIRED","test");
        assertThrows(RuntimeException.class,()->accept(request("a","one","line-1")));
        assertEquals(1,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from oms_activation_operations",Integer.class));
    }
    @Test void acknowledgmentFailureRollsBackSubscriptionInboxAndOperation() {
        source("line-1");
        jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT reject_test_ack CHECK(event_type<>'oms.activation-acknowledged.v1')");
        assertThrows(RuntimeException.class,()->accept(request("a","one","line-1")));
        assertEquals(0,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from oms_activation_inbox",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from oms_activation_operations",Integer.class));
    }
    @Test void registryFencesBothLegacyIdentitiesAndIsImmutable() {
        source("line-1");
        tx.executeWithoutResult(t -> {try {
            assertFalse(sources.permitsLegacy("Q-ALIAS")); assertFalse(sources.permitsLegacy(request("a","b","c").quoteId()));
        }catch(Exception e){throw new RuntimeException(e);}});
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE oms_accepted_sources SET payload='{}'"));
        assertThrows(RuntimeException.class,()->jdbc.update("DELETE FROM oms_quote_identities"));
    }
    @Test void concurrentReplayCreatesOneSubscriptionAndAcknowledgment() throws Exception {
        source("line");
        try(var pool=Executors.newFixedThreadPool(10)) {
            var tasks=new ArrayList<Callable<Void>>();
            for(int i=0;i<30;i++){final int n=i;tasks.add(()->{accept(request("event-"+n,"op","line"));return null;});}
            for(var future:pool.invokeAll(tasks)) future.get();
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from outbox_events",Integer.class));
        assertEquals("ACTIVE",jdbc.queryForObject("select status from subscriptions",String.class));
        assertEquals("216.00",jdbc.queryForObject("select arr from subscriptions",java.math.BigDecimal.class).toPlainString());
        assertEquals("NOT_REQUIRED",jdbc.queryForObject("select erp_sync_status from subscriptions",String.class));
    }
    @Test void sameEventCannotCreateAnotherLine() {
        source("line-1","line-2");
        accept(request("same-event","one","line-1"));
        assertThrows(RuntimeException.class,()->accept(request("same-event","two","line-2")));
        assertEquals(1,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
    }
    @Test void separateLinesRemainSeparateAndOperationCollisionFails() {
        source("line-1","line-2","line-3");
        accept(request("a","one","line-1"));accept(request("b","two","line-2"));
        assertEquals(2,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
        assertThrows(RuntimeException.class,()->accept(request("c","one","line-3")));
        assertEquals(2,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
    }
}
