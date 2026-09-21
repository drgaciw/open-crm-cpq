package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.images.builder.Transferable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real broker offset retention with the production named K factory and source listener.
 * Registry failure/repair and trust are test doubles; this is not production ACL or database proof. */
@Tag("integration") @Testcontainers
class ContractOmsBrokerRetentionIntegrationTest {
    @Container static final SmallKafka kafka=new SmallKafka();
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


    @Test void fatalSourceFailureSurvivesTenAttemptsAndConsumerRestartWithoutSkipping() throws Exception {
        String topic="oms-source-retention-"+UUID.randomUUID(),group="oms-retention-"+UUID.randomUUID();
        var producer=new DefaultKafkaProducerFactory<String,String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,kafka.bootstrap(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class,
            ProducerConfig.ACKS_CONFIG,"all"));
        var template=new KafkaTemplate<String,String>(producer);
        var registry=mock(OmsSourceRegistry.class);
        var trust=mock(OmsBrokerTrust.class);
        var source=new OmsSourceConsumer(new ObjectMapper(),registry,trust);
        var allow=new AtomicBoolean();var attempts=new AtomicInteger();
        var retained=new CountDownLatch(15);var completed=new CountDownLatch(2);
        var hashes=new CopyOnWriteArrayList<String>();
        doAnswer(invocation->{
            if(!allow.get()){attempts.incrementAndGet();retained.countDown();throw new ClassCastException("Simulated registry failure classified fatal by default");}
            hashes.add(invocation.getArgument(1));completed.countDown();return null;
        }).when(registry).register(anyString(),anyString());
        var consumerFactory=new DefaultKafkaConsumerFactory<Object,Object>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,kafka.bootstrap(),
            ConsumerConfig.GROUP_ID_CONFIG,group,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,true,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class));
        var factory=new ContractOmsKafkaConfiguration().omsRetainingKafkaListenerContainerFactory(consumerFactory);
        var listener=factory.createContainer(topic);listener.getContainerProperties().setGroupId(group);
        listener.getContainerProperties().setMessageListener((MessageListener<Object,Object>)record->{
            try{source.accept((String)record.value());}catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException(failure);}
        });
        try(var admin=Admin.create(Map.of("bootstrap.servers",kafka.bootstrap()))){
            admin.createTopics(List.of(new NewTopic(topic,1,(short)1))).all().get(15,TimeUnit.SECONDS);
            template.send(topic,"quote","{\"snapshot\":{\"scopeId\":\"pilot\"},\"snapshotHash\":\"first\"}").get(15,TimeUnit.SECONDS);
            template.send(topic,"quote","{\"snapshot\":{\"scopeId\":\"pilot\"},\"snapshotHash\":\"second\"}").get(15,TimeUnit.SECONDS);
            listener.start();
            assertTrue(retained.await(40,TimeUnit.SECONDS),"Fatal failures must remain retryable beyond default ten attempts");
            assertTrue(attempts.get()>=15);assertTrue(hashes.isEmpty());
            listener.stop();
            var offsets=admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);
            assertTrue(offsets.values().stream().allMatch(offset->offset.offset()==0),"Failed source offset cannot advance even when global factory auto-commit=true");
            allow.set(true);listener.start();
            assertTrue(completed.await(20,TimeUnit.SECONDS));
            assertEquals(List.of("first","second"),hashes);
            verify(trust,atLeast(17)).requireScope("pilot");
        }finally{listener.stop();producer.destroy();}
    }
}
