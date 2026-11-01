package com.opencrm.crm.outbox;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Reads records the CRM outbox relay has put on a topic. */
final class CrmKafkaSupport {

    private CrmKafkaSupport() {
    }

    static KafkaContainer startKafka() {
        KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");
        kafka.start();
        return kafka;
    }

    static ConsumerRecord<String, String> awaitRecord(KafkaContainer kafka, String topic, String key, Duration timeout) {
        List<ConsumerRecord<String, String>> records = recordsForKey(kafka, topic, "it-" + key, key, timeout);
        if (records.isEmpty()) {
            throw new AssertionError("no record with key " + key + " on " + topic + " within " + timeout);
        }
        return records.getFirst();
    }

    static List<ConsumerRecord<String, String>> recordsForKey(
            KafkaContainer kafka, String topic, String group, String key, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        matched.add(record);
                    }
                }
                if (!matched.isEmpty() && Instant.now().plusMillis(1000).isAfter(deadline)) {
                    break;
                }
                if (!matched.isEmpty()) {
                    // One quiet poll after the first match so a duplicate would be visible.
                    boolean extra = false;
                    for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(400))) {
                        if (key.equals(record.key())) {
                            matched.add(record);
                            extra = true;
                        }
                    }
                    if (!extra) {
                        break;
                    }
                }
            }
        }
        return matched;
    }

    static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
