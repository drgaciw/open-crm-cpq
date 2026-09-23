package com.opencrm.common.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dead-letter topic depth (ADR-018 D10): {@code opencrm.kafka.dlt.depth{topic=<topic>.DLT}}, the records on the DLT
 * of each topic this service listens to that nobody has triaged yet ({@link DeadLetterTriage}). It drops back to zero
 * once the records are replayed or skipped, so {@code depth > 0} is the alert.
 *
 * <p>The gauges are registered once the application is ready, when the listener containers, and so their topics, are
 * known. A gauge read serves the last value while it is younger than {@code depthCacheTtl} and otherwise asks the
 * brokers, with a short timeout; if they do not answer, it keeps the last value ({@code NaN} before the first).
 * Services with no listeners register nothing.
 */
@Slf4j
public class DeadLetterMetrics implements MeterBinder, ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    /** Meter name of the depth gauge. */
    public static final String DEPTH_METER = "opencrm.kafka.dlt.depth";

    private final KafkaAdmin kafkaAdmin;
    private final ObjectProvider<KafkaListenerEndpointRegistry> listeners;
    private final Duration depthCacheTtl;
    private final Duration timeout;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile MeterRegistry registry;
    private volatile Admin admin;

    public DeadLetterMetrics(KafkaAdmin kafkaAdmin, ObjectProvider<KafkaListenerEndpointRegistry> listeners,
                             Duration depthCacheTtl, Duration timeout, Clock clock) {
        this.kafkaAdmin = kafkaAdmin;
        this.listeners = listeners;
        this.depthCacheTtl = depthCacheTtl;
        this.timeout = timeout;
        this.clock = clock;
    }

    private record Cached(double depth, Instant readAt) {
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        MeterRegistry target = registry;
        if (target == null) {
            return;
        }
        for (String dlt : deadLetterTopics()) {
            Gauge.builder(DEPTH_METER, this, metrics -> metrics.depth(dlt))
                    .description("Records on a dead-letter topic that nobody has triaged yet")
                    .tag("topic", dlt)
                    .register(target);
        }
    }

    /** {@code <topic>.DLT} for every topic a listener container of this service subscribes to. */
    Set<String> deadLetterTopics() {
        Set<String> topics = new TreeSet<>();
        KafkaListenerEndpointRegistry registry = listeners.getIfAvailable();
        if (registry == null) {
            return topics;
        }
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            String[] names = container.getContainerProperties().getTopics();
            if (names != null) {
                Arrays.stream(names).map(DeadLetterTriage::deadLetterTopic).forEach(topics::add);
            }
        }
        return topics;
    }

    /** The cached depth of {@code dlt}, refreshed from the brokers once it is older than the TTL. */
    double depth(String dlt) {
        Instant now = clock.instant();
        Cached cached = cache.get(dlt);
        if (cached != null && Duration.between(cached.readAt(), now).compareTo(depthCacheTtl) < 0) {
            return cached.depth();
        }
        try {
            long depth = DeadLetterTriage.depth(admin(), dlt, timeout).values().stream()
                    .mapToLong(Long::longValue).sum();
            cache.put(dlt, new Cached(depth, now));
            return depth;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Could not read the depth of {}: {}", dlt, e.toString());
        }
        return cached == null ? Double.NaN : cached.depth();
    }

    private Admin admin() {
        Admin current = admin;
        if (current == null) {
            synchronized (this) {
                if (admin == null) {
                    admin = Admin.create(kafkaAdmin.getConfigurationProperties());
                }
                current = admin;
            }
        }
        return current;
    }

    @Override
    public void destroy() {
        Admin current = admin;
        if (current != null) {
            current.close(Duration.ofSeconds(5));
        }
    }
}
