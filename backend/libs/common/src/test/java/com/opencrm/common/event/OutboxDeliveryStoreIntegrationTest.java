package com.opencrm.common.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class OutboxDeliveryStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcTemplate jdbc;
    OutboxDeliveryStore store;
    TransactionTemplate transaction;

    @BeforeEach void setup() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        var manager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(manager);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new OutboxDeliveryStore(jdbc));
        proxy.addAdvice(interceptor);
        store = (OutboxDeliveryStore) proxy.getProxy();
        jdbc.execute("DROP TABLE IF EXISTS outbox_events");
        jdbc.execute("""
            CREATE TABLE outbox_events (id bigserial PRIMARY KEY, event_type text NOT NULL,
            aggregate_id text NOT NULL, payload text NOT NULL, published boolean DEFAULT false,
            published_at timestamptz, retry_count int NOT NULL DEFAULT 0, error_message varchar(500),
            created_at timestamptz DEFAULT now(), updated_at timestamptz, version bigint DEFAULT 0,
            is_deleted boolean DEFAULT false)
            """);
        new ResourceDatabasePopulator(new ClassPathResource("outbox/outbox-delivery-migration.sql")).execute(dataSource);
    }
    void insert(int count) {
        jdbc.update("INSERT INTO outbox_events(event_type,aggregate_id,payload) SELECT 'topic', 'key', '{}' FROM generate_series(1,?)", count);
    }
    void expire() { jdbc.update("UPDATE outbox_events SET lease_until=now()-interval '1 second', next_attempt_at=now()-interval '1 second'"); }
    List<OutboxDeliveryStore.Delivery> claim(int count) { return store.claim(count, 5, Duration.ofSeconds(60)); }

    OutboxDeliveryStore.Delivery one() { var rows = claim(1); assertEquals(1, rows.size()); return rows.getFirst(); }
    @Test void concurrentWorkersClaimDisjointBoundedBatches() throws Exception {
        insert(40);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<List<OutboxDeliveryStore.Delivery>>> futures = new ArrayList<>();
            for (int i=0;i<8;i++) futures.add(executor.submit(() -> claim(5)));
            var ids = new HashSet<Long>();
            for (var future : futures) {
                var batch = future.get();
                assertEquals(5, batch.size());
                for (var delivery : batch) assertTrue(ids.add(delivery.id()));
            }
            assertEquals(40, ids.size());
            assertTrue(claim(5).isEmpty());
        }
    }
    @Test void expiredWorkerCannotCommitAfterAnotherWorkerReclaims() {
        insert(1);
        var old = one();
        expire();
        assertFalse(store.acknowledged(old));
        var current = one();
        assertNotEquals(old.token(), current.token());
        assertEquals(2,current.attempt());
        assertFalse(store.acknowledged(old));
        assertFalse(store.failed(old,5,"old failure"));
        assertTrue(store.acknowledged(current));
        assertTrue(jdbc.queryForObject("SELECT published FROM outbox_events",Boolean.class));
        assertTrue(claim(1).isEmpty());
    }
    @Test void failuresBackOffExhaustAndRequireExplicitRecovery() {
        insert(1);
        for (int i=1;i<=5;i++) {
            var delivery=one();
            assertEquals(i,delivery.attempt());
            assertTrue(store.failed(delivery,5,"BrokerUnavailable"));
            assertTrue(claim(1).isEmpty());
            if(i<5) expire();
        }
        var exhausted=store.exhausted(10);
        assertEquals(1,exhausted.size());
        assertEquals(5,exhausted.getFirst().attempts());
        assertFalse(jdbc.queryForObject("SELECT published FROM outbox_events",Boolean.class));
        assertThrows(IllegalArgumentException.class,()->store.recover(exhausted.getFirst().id()," "));
        assertTrue(store.recover(exhausted.getFirst().id(),"Broker recovered; operator ticket OPS-1"));
        assertFalse(store.recover(exhausted.getFirst().id(),"Already recovered"));
        assertEquals(1,one().attempt());
    }
    @Test void crashDuringLastAttemptRemainsVisibleUntilRecovery() {
        insert(1);
        jdbc.update("UPDATE outbox_events SET retry_count=4");
        one();
        expire();
        assertTrue(claim(1).isEmpty());
        assertEquals(1,store.exhausted(10).size());
    }
    @Test void successfulAckPreservesPayloadAndClearsLease() {
        insert(1);
        var delivery=one();
        assertTrue(store.acknowledged(delivery));
        assertFalse(store.failed(delivery,5,"Late failure"));
        assertEquals("{}",jdbc.queryForObject("SELECT payload FROM outbox_events",String.class));
        assertNull(jdbc.queryForObject("SELECT lease_token FROM outbox_events",String.class));
        assertNotNull(jdbc.queryForObject("SELECT published_at FROM outbox_events",java.sql.Timestamp.class));
    }
    @Test void claimAndAcknowledgmentCommitIndependentlyOfCallerRollback() {
        insert(1);
        var delivery = transaction.execute(status -> { var row = one(); status.setRollbackOnly(); return row; });
        assertTrue(claim(1).isEmpty());
        transaction.executeWithoutResult(status -> { assertTrue(store.acknowledged(delivery)); status.setRollbackOnly(); });
        assertTrue(jdbc.queryForObject("SELECT published FROM outbox_events",Boolean.class));
    }
}
