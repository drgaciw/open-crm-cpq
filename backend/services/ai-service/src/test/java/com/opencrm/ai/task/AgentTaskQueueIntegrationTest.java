package com.opencrm.ai.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL, full ai-service Flyway chain (no target), AC1–AC3, AC5 and replay.
 * The audit migration test keeps its own target; this class must not.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = AgentTaskQueueIntegrationTest.QueueConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=ai",
                "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
                "spring.datasource.hikari.schema=ai",
                "spring.datasource.hikari.maximum-pool-size=4",
                "spring.flyway.schemas=ai",
                "spring.flyway.default-schema=ai",
                "ai.agent-task.dispatcher.enabled=false",
                "ai.agent-task.dispatcher.claim-limit=10",
                "ai.agent-task.dispatcher.lease=30s",
                "ai.agent-task.dispatcher.poll-interval=1h",
                "ai.agent-task.retry.initial-backoff=1s",
                "ai.agent-task.retry.multiplier=2",
                "ai.agent-task.retry.max-backoff=15m",
                "ai.agent-task.default-max-attempts=5",
                "spring.ai.azure.openai.api-key=synthetic-no-inference",
                "spring.ai.azure.openai.endpoint=https://synthetic.invalid",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.health.DataRedisReactiveHealthContributorAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
class AgentTaskQueueIntegrationTest {

    private static final Instant OPEN = Instant.parse("2026-11-06T00:00:00Z");

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = AgentTaskService.class)
    @EntityScan(basePackageClasses = AgentTask.class)
    @EnableJpaRepositories(basePackageClasses = AgentTaskRepository.class)
    static class QueueConfiguration {
        @Bean
        MutableClock agentTaskClock() {
            return new MutableClock(OPEN);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AgentTaskService service;
    @Autowired AgentTaskRepository repository;
    @Autowired AgentTaskProperties properties;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;

    @BeforeEach
    void resetQueue() {
        clock.set(OPEN);
        jdbc.update("DELETE FROM ai.agent_task");
    }

    @Test
    void fullFlywayChainCreatesAdrColumnsWithOpaqueTextTenant() throws Exception {
        List<String> scripts = jdbc.queryForList(
                "SELECT script FROM ai.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        long files = Files.list(Path.of("src/main/resources/db/migration")).filter(path -> path.toString().endsWith(".sql")).count();
        assertThat(scripts).contains("V1__create_ai_schema.sql", "V2__remove_assumed_audit_model.sql");
        assertThat(scripts).anyMatch(script -> script.contains("agent_task"));
        assertThat(scripts).hasSize((int) files);

        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'ai' AND table_name = 'agent_task' ORDER BY column_name",
                String.class);
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "tenant_id", "kind", "subject_type", "subject_id", "reason", "payload", "priority", "budget",
                "attempts", "max_attempts", "due_at", "leased_until", "lease_owner", "started_at", "finished_at",
                "outcome", "last_error", "created_at");
        var tenant = jdbc.queryForMap(
                "SELECT data_type, is_nullable, character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = 'ai' AND table_name = 'agent_task' AND column_name = 'tenant_id'");
        assertThat(tenant).containsEntry("data_type", "text").containsEntry("is_nullable", "NO");
        assertThat(tenant.get("character_maximum_length")).isNull();

        String indexes = String.join("\n", jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'ai' AND tablename = 'agent_task'", String.class));
        assertThat(indexes).contains("UNIQUE").contains("finished_at IS NULL").contains("due_at");
    }

    @Test
    void enqueueSameUnfinishedWorkReturnsTheExistingRow() {
        AgentTaskEnqueue request = request("tenant-a", "contact-1", 3, 5, OPEN);
        AgentTask first = service.enqueue(request);
        AgentTask second = service.enqueue(new AgentTaskEnqueue(
                "tenant-a", "enrich", "contact", "contact-1", "other reason", "{\"other\":true}", 9, 1, 9, OPEN.plusSeconds(30)));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(countForTenant("tenant-a")).isEqualTo(1);
        assertThat(repository.findById(first.getId()).orElseThrow().getReason()).isEqualTo("because");
    }

    @Test
    void enqueueAfterFinishCreatesANewRow() {
        AgentTask first = service.enqueue(request("tenant-a", "contact-1", 3, 5, OPEN));
        service.claim("worker-a", 10);
        service.complete(first.getId(), "worker-a");

        AgentTask second = service.enqueue(request("tenant-a", "contact-1", 3, 5, OPEN));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(countForTenant("tenant-a")).isEqualTo(2);
    }

    @Test
    void opaqueTenantIdRoundTripsWithoutUuidOrLengthRejection() {
        String weird = "  not-a-uuid / tenant\t" + "x".repeat(2000);
        AgentTask stored = service.enqueue(request(weird, "contact-1", null, 5, OPEN));

        assertThat(repository.findById(stored.getId()).orElseThrow().getTenantId()).isEqualTo(weird);
        assertThat(service.enqueue(request("", "blank-tenant", 0, 5, OPEN)).getTenantId()).isEmpty();
    }

    @Test
    void concurrentClaimsAreDisjointAndCoverEveryDueRow() throws Exception {
        List<UUID> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add(service.enqueue(request("tenant-a", "row-" + i, 1, 5, OPEN)).getId());
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch firstLeased = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<List<UUID>> first = executor.submit(() -> tx.execute(status -> {
                List<UUID> leased = ids(service.claim("worker-a", 8));
                firstLeased.countDown();
                try {
                    assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return leased;
            }));
            assertThat(firstLeased.await(20, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            List<UUID> second = tx.execute(status -> ids(service.claim("worker-b", 20)));
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            release.countDown();
            List<UUID> firstIds = first.get(20, TimeUnit.SECONDS);

            assertThat(firstIds).hasSize(8);
            assertThat(second).hasSize(12).doesNotContainAnyElementsOf(firstIds);
            Set<UUID> union = new HashSet<>(firstIds);
            union.addAll(second);
            assertThat(union).containsExactlyInAnyOrderElementsOf(all);
            assertThat(waitedMillis).isLessThan(5_000);
            assertThat(owners(firstIds)).containsOnly("worker-a");
            assertThat(owners(second)).containsOnly("worker-b");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void claimOrdersByPriorityThenDueAndCapsTheLimit() {
        service.enqueue(request("tenant-a", "low", 1, 5, OPEN));
        service.enqueue(request("tenant-a", "early-high", 1, 5, OPEN.minusSeconds(9)));
        service.enqueue(request("tenant-a", "late-high", 1, 5, OPEN.minusSeconds(5)));
        jdbc.update("UPDATE ai.agent_task SET priority = 5 WHERE subject_id IN ('early-high', 'late-high')");
        jdbc.update("UPDATE ai.agent_task SET priority = 1 WHERE subject_id = 'low'");

        List<AgentTask> claimed = service.claim("worker-a", 2);

        assertThat(claimed).extracting(AgentTask::getSubjectId).containsExactly("early-high", "late-high");

        jdbc.update("DELETE FROM ai.agent_task");
        for (int i = 0; i < AgentTaskLimits.MAX_CLAIM_LIMIT + 1; i++) {
            service.enqueue(request("tenant-b", "cap-" + i, 1, 5, OPEN));
        }
        assertThat(service.claim("worker-b", 100_000)).hasSize(AgentTaskLimits.MAX_CLAIM_LIMIT);
    }

    @Test
    void expiredLeaseIsClaimableLiveLeaseIsNotAndOnlyTheOwnerExtends() {
        AgentTask task = service.enqueue(request("tenant-a", "contact-1", 2, 5, OPEN));
        assertThat(service.claim("worker-a", 10)).extracting(AgentTask::getId).containsExactly(task.getId());
        assertThat(service.claim("worker-b", 10)).isEmpty();

        service.extendLease(task.getId(), "worker-a", Duration.ofSeconds(10));
        Instant extended = repository.findById(task.getId()).orElseThrow().getLeasedUntil();
        assertThat(extended).isEqualTo(OPEN.plusSeconds(40));
        assertThatThrownBy(() -> service.extendLease(task.getId(), "worker-b", Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(task.getId()).orElseThrow().getLeasedUntil()).isEqualTo(extended);

        clock.set(extended.plusMillis(1));
        assertThatThrownBy(() -> service.extendLease(task.getId(), "worker-a", Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.claim("worker-b", 10)).extracting(AgentTask::getId).containsExactly(task.getId());
        assertThat(repository.findById(task.getId()).orElseThrow().getLeaseOwner()).isEqualTo("worker-b");
    }

    @Test
    void failureRetriesThenParksWithTruncatedErrorAndSuccessFinishes() {
        AgentTask retry = service.enqueue(request("tenant-a", "retry-me", 4, 3, OPEN));
        service.claim("worker-a", 10);
        String longError = "e".repeat(AgentTaskLimits.MAX_ERROR_LENGTH + 25);
        service.fail(retry.getId(), "worker-a", longError);
        AgentTask once = repository.findById(retry.getId()).orElseThrow();
        assertThat(once.getAttempts()).isEqualTo(1);
        assertThat(once.getLastError()).hasSize(AgentTaskLimits.MAX_ERROR_LENGTH);
        assertThat(once.getDueAt()).isEqualTo(OPEN.plusSeconds(1));
        assertThat(once.getFinishedAt()).isNull();
        assertThat(once.getOutcome()).isNull();

        clock.set(OPEN.plusSeconds(1));
        service.claim("worker-a", 10);
        service.fail(retry.getId(), "worker-a", "second");
        clock.set(OPEN.plusSeconds(1).plusSeconds(2));
        service.claim("worker-a", 10);
        service.fail(retry.getId(), "worker-a", "parked now");
        AgentTask parked = repository.findById(retry.getId()).orElseThrow();
        assertThat(parked.getAttempts()).isEqualTo(3);
        assertThat(parked.getOutcome()).isEqualTo(Outcome.PARKED);
        assertThat(parked.getOutcome().wire()).isEqualTo("parked");
        assertThat(parked.getFinishedAt()).isEqualTo(clock.instant());
        assertThat(parked.getLastError()).isEqualTo("parked now");
        assertThat(service.claim("worker-a", 10)).isEmpty();

        AgentTask done = service.enqueue(request("tenant-a", "done", 4, 5, clock.instant()));
        RecordingHandler handler = new RecordingHandler("enrich", false);
        properties.getDispatcher().setLeaseOwner("dispatcher-success");
        new AgentTaskDispatcher(service, properties, List.of(handler)).poll();
        AgentTask succeeded = repository.findById(done.getId()).orElseThrow();
        assertThat(handler.runs).isEqualTo(1);
        assertThat(handler.budgetSeen).isEqualTo(4);
        assertThat(succeeded.getOutcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(succeeded.getOutcome().wire()).isEqualTo("succeeded");
        assertThat(succeeded.getFinishedAt()).isNotNull();
    }

    @Test
    void replayedEnqueueRunsTheHandlerOnce() {
        AgentTaskEnqueue request = request("tenant-a", "contact-9", 6, 5, OPEN);
        AgentTask first = service.enqueue(request);
        assertThat(service.enqueue(request).getId()).isEqualTo(first.getId());
        assertThat(countForTenant("tenant-a")).isEqualTo(1);
        RecordingHandler handler = new RecordingHandler("enrich", false);
        properties.getDispatcher().setLeaseOwner("dispatcher-replay");
        new AgentTaskDispatcher(service, properties, List.of(handler)).poll();

        assertThat(handler.runs).isEqualTo(1);
        assertThat(handler.budgetSeen).isEqualTo(6);
        assertThat(repository.findById(first.getId()).orElseThrow().getOutcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(countForTenant("tenant-a")).isEqualTo(1);
    }

    @Test
    void crashMidRunIsClaimedOnceMoreWithoutDuplicateSuccess() {
        AgentTask task = service.enqueue(request("tenant-a", "crash", 2, 5, OPEN));
        assertThat(service.claim("worker-a", 10)).extracting(AgentTask::getId).containsExactly(task.getId());
        clock.set(OPEN.plusSeconds(31));
        assertThatThrownBy(() -> service.complete(task.getId(), "worker-a")).isInstanceOf(IllegalStateException.class);

        assertThat(service.claim("worker-b", 10)).extracting(AgentTask::getId).containsExactly(task.getId());
        assertThat(service.claim("worker-c", 10)).isEmpty();
        service.complete(task.getId(), "worker-b");
        assertThatThrownBy(() -> service.complete(task.getId(), "worker-b")).isInstanceOf(IllegalStateException.class);

        AgentTask finished = repository.findById(task.getId()).orElseThrow();
        assertThat(finished.getOutcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(finished.getFinishedAt()).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ai.agent_task WHERE subject_id = 'crash' AND outcome = 'succeeded'", Integer.class))
                .isEqualTo(1);
    }

    private AgentTaskEnqueue request(String tenantId, String subjectId, Integer budget, int maxAttempts, Instant dueAt) {
        return new AgentTaskEnqueue(tenantId, "enrich", "contact", subjectId, "because", "{\"a\":1}",
                0, budget, maxAttempts, dueAt);
    }

    private int countForTenant(String tenantId) {
        return jdbc.queryForObject("SELECT count(*) FROM ai.agent_task WHERE tenant_id = ?", Integer.class, tenantId);
    }

    private static List<UUID> ids(List<AgentTask> tasks) {
        return tasks.stream().map(AgentTask::getId).toList();
    }

    private List<String> owners(List<UUID> ids) {
        List<String> owners = new ArrayList<>();
        for (UUID id : ids) {
            owners.add(jdbc.queryForObject("SELECT lease_owner FROM ai.agent_task WHERE id = ?", String.class, id));
        }
        return owners;
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class RecordingHandler implements AgentTaskHandler {
        private final String kind;
        private final boolean fail;
        private int runs;
        private Integer budgetSeen;

        private RecordingHandler(String kind, boolean fail) {
            this.kind = kind;
            this.fail = fail;
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public void handle(AgentTask task) {
            runs++;
            budgetSeen = task.getBudget();
            if (fail) {
                throw new IllegalStateException("scripted failure");
            }
        }
    }
}
