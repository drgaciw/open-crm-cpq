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
    JdbcTemplate jdbc; ObjectMapper mapper; OmsActivationConsumer consumer; TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        try(var paths=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(var p:paths.sorted().toList()) jdbc.execute(Files.readString(p));
        }
        mapper=new ObjectMapper().findAndRegisterModules();
        consumer=new OmsActivationConsumer(jdbc,mapper,"pilot");
    }
    ActivationRequest request(String event,String op,String line) {
        return new ActivationRequest(event,op,"order","pilot","account","quote",1,
            new Line(line,"product","Plan","DIGITAL","RECURRING",2,"EA","10.00","2.00","1.80","19.80","MONTHLY",LocalDate.of(2026,9,20),LocalDate.of(2027,9,20)),"verified-provision",Instant.parse("2026-09-20T12:00:00Z"));
    }
    void accept(ActivationRequest r) {tx.executeWithoutResult(s -> {try{consumer.accept(mapper.writeValueAsString(r));}catch(Exception e){throw new RuntimeException(e);}});}
    @Test void concurrentReplayCreatesOneSubscriptionAndAcknowledgment() throws Exception {
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
        accept(request("same-event","one","line-1"));
        assertThrows(RuntimeException.class,()->accept(request("same-event","two","line-2")));
        assertEquals(1,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
    }
    @Test void separateLinesRemainSeparateAndOperationCollisionFails() {
        accept(request("a","one","line-1"));accept(request("b","two","line-2"));
        assertEquals(2,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
        assertThrows(RuntimeException.class,()->accept(request("c","one","line-3")));
        assertEquals(2,jdbc.queryForObject("select count(*) from subscriptions",Integer.class));
    }
}
