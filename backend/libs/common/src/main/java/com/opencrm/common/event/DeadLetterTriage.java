package com.opencrm.common.event;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Triage of dead-letter topics (ADR-018 D7). It uses only {@code kafka-clients}, so it runs with or without a Spring
 * context: {@link DeadLetterTool} wraps it as an admin process, and {@link DeadLetterMetrics} uses {@link #depth} for
 * the {@code opencrm.kafka.dlt.depth} gauge.
 *
 * <p>The model:
 * <ul>
 *   <li>A consumer group, {@value #TRIAGE_GROUP}, holds one cursor per DLT. Everything behind it has been triaged;
 *       everything from it to the end of the topic is the DLT's <em>depth</em>, the number that should alert.</li>
 *   <li>{@link #replay} re-publishes the next records to the topic they came from, with the original key, value and
 *       headers (including {@code eventId}) minus Spring Kafka's {@code kafka_dlt-*} headers, then moves the cursor.
 *       Every consumer of a topic is idempotent (D6), so a group that already processed an event ignores the replayed
 *       copy. That is why replay needs no filter on {@code kafka_dlt-original-consumer-group}: the record reaches the
 *       group it failed for, and only that group acts on it.</li>
 *   <li>{@link #skip} moves the cursor without re-publishing, for records that must not be processed.</li>
 *   <li>{@link #peek} reads without moving the cursor.</li>
 * </ul>
 * Replay only helps once the cause is fixed: a record that fails again is dead-lettered again, and the cursor has moved
 * past the earlier copy. Run one triage at a time per DLT.
 */
public class DeadLetterTriage {

    /** Consumer group whose committed offsets are the triage cursor of every DLT. */
    public static final String TRIAGE_GROUP = "opencrm-dlt-triage";

    /** Suffix of a dead-letter topic ({@code KafkaConsumerErrorHandlingAutoConfiguration.DLT_SUFFIX}). */
    public static final String DLT_SUFFIX = ".DLT";

    // Headers written by Spring Kafka's DeadLetterPublishingRecoverer (KafkaHeaders.DLT_*), spelled out so that this
    // class needs only kafka-clients.
    static final String DLT_HEADER_PREFIX = "kafka_dlt-";
    static final String ORIGINAL_TOPIC = "kafka_dlt-original-topic";
    static final String ORIGINAL_GROUP = "kafka_dlt-original-consumer-group";
    static final String EXCEPTION_CAUSE_FQCN = "kafka_dlt-exception-cause-fqcn";
    static final String EXCEPTION_FQCN = "kafka_dlt-exception-fqcn";
    static final String EXCEPTION_MESSAGE = "kafka_dlt-exception-message";

    private static final Duration POLL = Duration.ofMillis(500);
    private static final int PREVIEW = 200;

    private final Map<String, Object> clientConfig;
    private final Duration timeout;

    /**
     * @param clientConfig Kafka client settings shared by the consumer and producer, at least
     *                     {@code bootstrap.servers}; security settings go here too
     */
    public DeadLetterTriage(Map<String, Object> clientConfig, Duration timeout) {
        this.clientConfig = Map.copyOf(clientConfig);
        this.timeout = timeout;
    }

    /** One dead-lettered record, as shown by {@link #peek}. */
    public record DeadLetter(int partition, long offset, String key, String originalTopic, String originalGroup,
                             String exception, String exceptionMessage, String eventId, String valuePreview) {
    }

    /** What a {@link #replay} or {@link #skip} did, and how many records remain untriaged. */
    public record Outcome(List<DeadLetter> records, long remaining) {
    }

    /** The DLT for {@code topic}: {@code topic} itself if it already ends in {@value #DLT_SUFFIX}. */
    public static String deadLetterTopic(String topic) {
        return topic.endsWith(DLT_SUFFIX) ? topic : topic + DLT_SUFFIX;
    }

    /**
     * Untriaged records per partition of {@code dltTopic}: the end offset minus the triage cursor, or minus the start
     * offset where the partition was never triaged (records past retention are gone and do not count). A DLT that
     * does not exist yet has depth zero.
     */
    public static Map<Integer, Long> depth(Admin admin, String dltTopic, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        TopicDescription description;
        try {
            description = admin.describeTopics(List.of(dltTopic)).allTopicNames()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS).get(dltTopic);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                return Map.of();
            }
            throw e;
        }
        List<TopicPartition> partitions = description.partitions().stream()
                .map(partition -> new TopicPartition(dltTopic, partition.partition()))
                .toList();
        Map<TopicPartition, ListOffsetsResultInfo> end = offsets(admin, partitions, OffsetSpec.latest(), timeout);
        Map<TopicPartition, ListOffsetsResultInfo> start = offsets(admin, partitions, OffsetSpec.earliest(), timeout);
        Map<TopicPartition, OffsetAndMetadata> cursor = admin.listConsumerGroupOffsets(TRIAGE_GROUP)
                .partitionsToOffsetAndMetadata().get(timeout.toMillis(), TimeUnit.MILLISECONDS);

        Map<Integer, Long> depth = new TreeMap<>();
        for (TopicPartition partition : partitions) {
            long from = start.get(partition).offset();
            OffsetAndMetadata committed = cursor.get(partition);
            if (committed != null) {
                from = Math.max(from, committed.offset());
            }
            depth.put(partition.partition(), Math.max(0, end.get(partition).offset() - from));
        }
        return depth;
    }

    private static Map<TopicPartition, ListOffsetsResultInfo> offsets(Admin admin, List<TopicPartition> partitions,
            OffsetSpec spec, Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        partitions.forEach(partition -> request.put(partition, spec));
        return admin.listOffsets(request).all().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** {@link #depth(Admin, String, Duration)} with an Admin client of its own. */
    public Map<Integer, Long> depth(String dltTopic) throws InterruptedException, ExecutionException, TimeoutException {
        try (Admin admin = Admin.create(clientConfig)) {
            return depth(admin, dltTopic, timeout);
        }
    }

    /** Up to {@code max} untriaged records, oldest first per partition. Moves nothing. */
    public List<DeadLetter> peek(String dltTopic, int max) {
        List<DeadLetter> result = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer = consumer(max)) {
            for (ConsumerRecord<byte[], byte[]> record : next(consumer, dltTopic, max)) {
                result.add(describe(record));
            }
        }
        return result;
    }

    /**
     * Re-publishes up to {@code max} untriaged records to their original topic and moves the cursor past each one
     * once the broker has acknowledged it. With {@code dryRun}, reports what it would do and changes nothing.
     */
    public Outcome replay(String dltTopic, int max, boolean dryRun)
            throws InterruptedException, ExecutionException, TimeoutException {
        List<DeadLetter> done = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer = consumer(max);
             KafkaProducer<byte[], byte[]> producer = dryRun ? null : producer()) {
            for (ConsumerRecord<byte[], byte[]> record : next(consumer, dltTopic, max)) {
                DeadLetter letter = describe(record);
                if (!dryRun) {
                    producer.send(new ProducerRecord<>(letter.originalTopic(), null, record.key(), record.value(),
                            replayHeaders(record))).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    commit(consumer, record);
                }
                done.add(letter);
            }
        }
        return new Outcome(done, remaining(dltTopic));
    }

    /** Moves the cursor past up to {@code max} untriaged records without re-publishing them. */
    public Outcome skip(String dltTopic, int max) throws InterruptedException, ExecutionException, TimeoutException {
        List<DeadLetter> done = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer = consumer(max)) {
            for (ConsumerRecord<byte[], byte[]> record : next(consumer, dltTopic, max)) {
                commit(consumer, record);
                done.add(describe(record));
            }
        }
        return new Outcome(done, remaining(dltTopic));
    }

    private long remaining(String dltTopic) throws InterruptedException, ExecutionException, TimeoutException {
        return depth(dltTopic).values().stream().mapToLong(Long::longValue).sum();
    }

    /** The next {@code max} records after the cursor, in offset order per partition, up to the current end. */
    private List<ConsumerRecord<byte[], byte[]>> next(KafkaConsumer<byte[], byte[]> consumer, String dltTopic,
                                                      int max) {
        List<PartitionInfo> infos = consumer.partitionsFor(dltTopic, timeout);
        if (infos == null || infos.isEmpty()) {
            return List.of();
        }
        List<TopicPartition> partitions = infos.stream()
                .map(info -> new TopicPartition(dltTopic, info.partition()))
                .sorted(Comparator.comparingInt(TopicPartition::partition))
                .toList();
        consumer.assign(partitions);
        Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(Set.copyOf(partitions), timeout);
        for (TopicPartition partition : partitions) {
            OffsetAndMetadata cursor = committed.get(partition);
            if (cursor != null) {
                consumer.seek(partition, cursor.offset());
            } else {
                consumer.seekToBeginning(List.of(partition));
            }
        }
        Map<TopicPartition, Long> end = consumer.endOffsets(partitions, timeout);

        List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (records.size() < max && !caughtUp(consumer, partitions, end) && System.nanoTime() < deadline) {
            for (ConsumerRecord<byte[], byte[]> record : consumer.poll(POLL)) {
                TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                if (records.size() < max && record.offset() < end.get(partition)) {
                    records.add(record);
                }
            }
        }
        // Records of one partition must be handled in order; the cursor only ever moves forward over them.
        records.sort(Comparator.<ConsumerRecord<byte[], byte[]>>comparingInt(ConsumerRecord::partition)
                .thenComparingLong(ConsumerRecord::offset));
        return records;
    }

    private static boolean caughtUp(KafkaConsumer<byte[], byte[]> consumer, List<TopicPartition> partitions,
                                    Map<TopicPartition, Long> end) {
        return partitions.stream().allMatch(partition -> consumer.position(partition) >= end.get(partition));
    }

    private void commit(KafkaConsumer<byte[], byte[]> consumer, ConsumerRecord<byte[], byte[]> record) {
        consumer.commitSync(Map.of(new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)), timeout);
    }

    /** The record's headers minus Spring Kafka's {@code kafka_dlt-*} ones. */
    static RecordHeaders replayHeaders(ConsumerRecord<byte[], byte[]> record) {
        RecordHeaders headers = new RecordHeaders();
        for (Header header : record.headers()) {
            if (!header.key().startsWith(DLT_HEADER_PREFIX)) {
                headers.add(header);
            }
        }
        return headers;
    }

    static DeadLetter describe(ConsumerRecord<byte[], byte[]> record) {
        String originalTopic = text(record, ORIGINAL_TOPIC);
        if (originalTopic == null) {
            originalTopic = record.topic().endsWith(DLT_SUFFIX)
                    ? record.topic().substring(0, record.topic().length() - DLT_SUFFIX.length())
                    : record.topic();
        }
        String exception = text(record, EXCEPTION_CAUSE_FQCN);
        if (exception == null) {
            exception = text(record, EXCEPTION_FQCN);
        }
        return new DeadLetter(record.partition(), record.offset(), utf8(record.key()), originalTopic,
                text(record, ORIGINAL_GROUP), exception, truncate(text(record, EXCEPTION_MESSAGE)),
                text(record, EventHeaders.EVENT_ID), truncate(utf8(record.value())));
    }

    private static String text(ConsumerRecord<byte[], byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : utf8(header.value());
    }

    private static String utf8(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= PREVIEW ? value : value.substring(0, PREVIEW) + "...";
    }

    private KafkaConsumer<byte[], byte[]> consumer(int max) {
        Map<String, Object> config = new HashMap<>(clientConfig);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, TRIAGE_GROUP);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Peeking at a DLT that does not exist yet must not create it.
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.max(1, Math.min(max, 500)));
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, "opencrm-dlt-triage");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(config);
    }

    private KafkaProducer<byte[], byte[]> producer() {
        Map<String, Object> config = new HashMap<>(clientConfig);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "opencrm-dlt-replay");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(config);
    }
}
