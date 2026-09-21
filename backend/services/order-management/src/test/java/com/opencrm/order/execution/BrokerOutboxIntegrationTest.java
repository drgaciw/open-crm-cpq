package com.opencrm.order.execution;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.opencrm.common.event.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.Transferable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real broker ACK and PostgreSQL lease recovery; this does not verify deployment ACLs. */
@Tag("integration")
@Testcontainers
class BrokerOutboxIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final SmallKafka KAFKA = new SmallKafka();

    // Official Apache JVM image/configuration: https://kafka.apache.org/40/getting-started/docker/
    static final class SmallKafka extends GenericContainer<SmallKafka> {
        SmallKafka() {
            super("apache/kafka:4.0.2");
            withExposedPorts(9092);
            withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx256m");
            withEnv("KAFKA_NODE_ID", "1");
            withEnv("KAFKA_PROCESS_ROLES", "broker,controller");
            withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,CONTROLLER://:9093");
            withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
            withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER");
            withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093");
            withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
            withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
            withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
            withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
            withEnv("KAFKA_LOG_SEGMENT_BYTES", "1048576");
            withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(768L * 1024 * 1024));
            withCommand("/bin/bash", "-c", "while [ ! -f /tmp/start-broker.sh ]; do sleep 0.1; done; exec /bin/bash /tmp/start-broker.sh");
            waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofSeconds(120));
        }
        @Override protected void containerIsStarting(InspectContainerResponse info) {
            String script = "export KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://" + getHost() + ":" + getMappedPort(9092) + "\nexec /etc/kafka/docker/run\n";
            copyFileToContainer(Transferable.of(script.getBytes(StandardCharsets.UTF_8)), "/tmp/start-broker.sh");
        }
        String bootstrap() { return getHost() + ":" + getMappedPort(9092); }
    }

    /** Delays delivery of an actual broker ACK to the relay, modelling a paused/crashed callback. */
    static final class HeldAcknowledgmentTemplate extends KafkaTemplate<String, String> {
        volatile CompletableFuture<SendResult<String,String>> actual;
        final CompletableFuture<Void> release = new CompletableFuture<>();
        HeldAcknowledgmentTemplate(ProducerFactory<String,String> factory) { super(factory); }
        @Override public CompletableFuture<SendResult<String,String>> send(String topic, String key, String value) {
            actual = super.send(topic, key, value);
            return actual.thenCombine(release, (result, ignored) -> result);
        }
    }

    @Test void realConsumerRetainsOffsetBeyondTenFailuresThenResumesInOrder() throws Exception {
        String topic = "oms-retention-" + UUID.randomUUID();
        String group = "retention-" + UUID.randomUUID();
        var producer = new DefaultKafkaProducerFactory<String,String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.bootstrap(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all"));
        var template = new KafkaTemplate<String,String>(producer);
        var properties = new ContainerProperties(topic);
        properties.setGroupId(group);
        properties.setAckMode(ContainerProperties.AckMode.RECORD);
        var attempts = new AtomicInteger();
        var allow = new AtomicBoolean();
        var retained = new CountDownLatch(15);
        var completed = new CountDownLatch(2);
        var received = new CopyOnWriteArrayList<String>();
        properties.setMessageListener((MessageListener<String,String>) record -> {
            if (!allow.get()) {
                attempts.incrementAndGet(); retained.countDown();
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            }
            received.add(record.value()); completed.countDown();
        });
        var factory = new DefaultKafkaConsumerFactory<String,String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.bootstrap(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false));
        var listener = new KafkaMessageListenerContainer<>(factory, properties);
        listener.setCommonErrorHandler(new KafkaDeliveryRetentionConfiguration().kafkaDeliveryRetentionErrorHandler());
        try (var admin = org.apache.kafka.clients.admin.Admin.create(Map.of("bootstrap.servers", KAFKA.bootstrap()))) {
            admin.createTopics(List.of(new org.apache.kafka.clients.admin.NewTopic(topic, 1, (short)1))).all().get(15,TimeUnit.SECONDS);
            template.send(topic, "same", "first").get(15,TimeUnit.SECONDS);
            template.send(topic, "same", "second").get(15,TimeUnit.SECONDS);
            listener.start();
            assertTrue(retained.await(40,TimeUnit.SECONDS), "must retry beyond Spring's default ten attempts");
            assertTrue(attempts.get()>=15);
            assertTrue(received.isEmpty());
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);
            assertTrue(offsets.values().stream().allMatch(offset -> offset.offset()==0), "failed record must not be committed");
            allow.set(true);
            assertTrue(completed.await(15,TimeUnit.SECONDS));
            assertEquals(List.of("first", "second"), received);
        } finally { listener.stop(); producer.destroy(); }
    }

    @Test void actualBrokerAckIsRequiredAndExpiredLeaseReplaysIdenticalEvent() throws Exception {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE outbox_events(id bigserial PRIMARY KEY,event_type text,aggregate_id text,payload text,published boolean NOT NULL DEFAULT false,published_at timestamptz,retry_count integer NOT NULL DEFAULT 0,error_message varchar(500),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz,version bigint DEFAULT 0,is_deleted boolean NOT NULL DEFAULT false)");
        new ResourceDatabasePopulator(new ClassPathResource("outbox/outbox-delivery-migration.sql")).execute(ds);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(ds));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new OutboxDeliveryStore(db));
        proxy.addAdvice(interceptor);
        var store = (OutboxDeliveryStore) proxy.getProxy();
        String topic = "oms-relay-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"schemaVersion\":1,\"eventId\":\"" + eventId + "\",\"quantity\":3}";
        db.update("INSERT INTO outbox_events(event_type,aggregate_id,payload) VALUES(?,?,?)", topic, "order-1", payload);
        var producerProperties = new HashMap<String,Object>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.bootstrap());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProperties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
        var factory = new DefaultKafkaProducerFactory<String,String>(producerProperties);
        var held = new HeldAcknowledgmentTemplate(factory);
        var normal = new KafkaTemplate<String,String>(factory);
        var consumerProperties = new HashMap<String,Object>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.bootstrap());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "verify-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<String,String>(consumerProperties)) {
            consumer.subscribe(List.of(topic));
            var firstRelay = new OutboxRelayPublisher(mock(OutboxEventRepository.class), held, store);
            firstRelay.publishPendingEvents();
            assertNotNull(held.actual.get(20, TimeUnit.SECONDS).getRecordMetadata());
            assertFalse(db.queryForObject("SELECT published FROM outbox_events", Boolean.class), "broker ACK not yet delivered to relay");
            assertEquals(payload, consume(consumer, 1).getFirst().value());
            String firstToken = db.queryForObject("SELECT lease_token FROM outbox_events", String.class);
            db.update("UPDATE outbox_events SET lease_until=clock_timestamp()-interval '1 second'");
            var secondRelay = new OutboxRelayPublisher(mock(OutboxEventRepository.class), normal, store);
            secondRelay.publishPendingEvents();
            await(() -> Boolean.TRUE.equals(db.queryForObject("SELECT published FROM outbox_events", Boolean.class)));
            var replay = consume(consumer, 1).getFirst();
            assertEquals("order-1", replay.key());
            assertEquals(payload, replay.value(), "immutable event identity and body survive expired-lease replay");
            held.release.complete(null); // Old callback must be fenced even though it has a real broker ACK.
            assertFalse(store.acknowledged(new OutboxDeliveryStore.Delivery(1, firstToken, topic, "order-1", payload, 1)));
            assertEquals(2, db.queryForObject("SELECT retry_count FROM outbox_events", Integer.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM outbox_events WHERE published", Integer.class));
        } finally { held.release.complete(null); factory.destroy(); }
    }

    private static List<ConsumerRecord<String,String>> consume(KafkaConsumer<String,String> consumer, int count) {
        var records = new ArrayList<ConsumerRecord<String,String>>();
        long until = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (records.size() < count && System.nanoTime() < until) consumer.poll(Duration.ofMillis(200)).forEach(records::add);
        assertEquals(count, records.size());
        return records;
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(50);
        assertTrue(condition.getAsBoolean());
    }
}
