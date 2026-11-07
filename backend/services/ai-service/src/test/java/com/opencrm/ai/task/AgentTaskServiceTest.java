package com.opencrm.ai.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    private AgentTaskRepository repository;
    private AgentTaskProperties properties;
    private AgentTaskService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(AgentTaskRepository.class);
        properties = new AgentTaskProperties();
        properties.getRetry().setInitialBackoff(Duration.ofSeconds(1));
        properties.getRetry().setMultiplier(2);
        properties.getRetry().setMaxBackoff(Duration.ofDays(7));
        properties.getDispatcher().setLease(Duration.ofSeconds(30));
        service = new AgentTaskService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper(), immediate());
    }

    @Test
    void rejectsNegativeBudgetAndDoesNotInsert() {
        assertThatThrownBy(() -> service.enqueue(request("tenant-a", "subject-1", -1)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void storesOpaqueTenantIdWithoutTrimFormatOrLengthRejection() {
        when(repository.findUnfinished(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String tenant = "  not-a-uuid / " + "t".repeat(400);

        AgentTask stored = service.enqueue(request(tenant, "subject-1", null));

        assertThat(stored.getTenantId()).isEqualTo(tenant);
        assertThat(stored.getTenantId()).isNotInstanceOf(UUID.class);
    }

    @Test
    void acceptsEmptyTenantStringBecauseTheValueIsOpaque() {
        when(repository.findUnfinished(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentTask stored = service.enqueue(request("", "subject-1", 0));

        assertThat(stored.getTenantId()).isEmpty();
    }

    @Test
    void rejectsNullTenant() {
        assertThatThrownBy(() -> service.enqueue(request(null, "subject-1", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void returnsExistingUnfinishedTaskWithoutInsert() {
        AgentTask existing = unfinished("tenant-a", "subject-1");
        when(repository.findUnfinished("tenant-a", "enrich", "contact", "subject-1")).thenReturn(Optional.of(existing));

        AgentTask result = service.enqueue(request("tenant-a", "subject-1", 4));

        assertThat(result).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void returnsWinnerWhenInsertHitsTheUnfinishedUniqueIndex() {
        AgentTask winner = unfinished("tenant-a", "subject-1");
        when(repository.findUnfinished("tenant-a", "enrich", "contact", "subject-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        AgentTask result = service.enqueue(request("tenant-a", "subject-1", 4));

        assertThat(result).isSameAs(winner);
    }

    @Test
    void insertsAgainWhenPriorTaskIsFinished() {
        when(repository.findUnfinished(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentTask created = service.enqueue(request("tenant-a", "subject-1", 4));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getFinishedAt()).isNull();
        verify(repository).saveAndFlush(any(AgentTask.class));
    }

    @Test
    void clampsClaimLimitAtTheCodeConstant() {
        when(repository.lockDue(eq(NOW), eq(AgentTaskLimits.MAX_CLAIM_LIMIT))).thenReturn(java.util.List.of());

        service.claim("worker-a", Integer.MAX_VALUE);

        verify(repository).lockDue(NOW, AgentTaskLimits.MAX_CLAIM_LIMIT);
        assertThat(AgentTaskLimits.MAX_CLAIM_LIMIT).isEqualTo(100);
        assertThat(AgentTaskLimits.DEFAULT_CLAIM_LIMIT).isEqualTo(10);
        assertThatThrownBy(() -> service.claim("worker-a", 0)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).lockDue(any(), eq(0));
    }

    @Test
    void backoffIsExponentialAndCannotExceedTheCodeCap() {
        assertThat(AgentTaskLimits.MAX_BACKOFF).isEqualTo(Duration.ofMinutes(15));
        assertThat(AgentTaskLimits.backoff(1, Duration.ofSeconds(1), 2, Duration.ofMinutes(15)))
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(AgentTaskLimits.backoff(2, Duration.ofSeconds(1), 2, Duration.ofMinutes(15)))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(AgentTaskLimits.backoff(3, Duration.ofSeconds(1), 2, Duration.ofMinutes(15)))
                .isEqualTo(Duration.ofSeconds(4));
        assertThat(AgentTaskLimits.backoff(40, Duration.ofSeconds(1), 2, Duration.ofDays(7)))
                .isEqualTo(AgentTaskLimits.MAX_BACKOFF);
        assertThat(AgentTaskLimits.backoff(3, Duration.ofSeconds(1), 2, Duration.ofSeconds(2)))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(AgentTaskLimits.backoff(2, Duration.ofSeconds(1), 100, Duration.ofMinutes(15)))
                .isEqualTo(Duration.ofSeconds(AgentTaskLimits.MAX_BACKOFF_MULTIPLIER));
    }

    @Test
    void failureTruncatesLastErrorAndParksAtMaxAttempts() {
        AgentTask task = unfinished("tenant-a", "subject-1");
        task.setMaxAttempts(2);
        task.setLeaseOwner("worker-a");
        task.setLeasedUntil(NOW.plusSeconds(30));
        when(repository.lockById(task.getId())).thenReturn(Optional.of(task));

        service.fail(task.getId(), "worker-a", "x".repeat(AgentTaskLimits.MAX_ERROR_LENGTH + 40));

        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastError()).hasSize(AgentTaskLimits.MAX_ERROR_LENGTH);
        assertThat(task.getOutcome()).isNull();
        assertThat(task.getDueAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(task.getFinishedAt()).isNull();
        assertThat(task.getLeaseOwner()).isNull();

        task.setLeaseOwner("worker-a");
        task.setLeasedUntil(NOW.plusSeconds(30));
        service.fail(task.getId(), "worker-a", "still broken");

        assertThat(task.getAttempts()).isEqualTo(2);
        assertThat(task.getOutcome()).isEqualTo(Outcome.PARKED);
        assertThat(task.getFinishedAt()).isEqualTo(NOW);
        assertThat(task.getLastError()).isEqualTo("still broken");
    }

    @Test
    void serviceDoesNotTreatTenantIdAsUuidOrLengthAuthority() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/opencrm/ai/task/AgentTaskService.java"));
        String entity = Files.readString(Path.of("src/main/java/com/opencrm/ai/task/AgentTask.java"));
        assertThat(source).doesNotContain("UUID.fromString");
        assertThat(source).doesNotContain("tenantId.length");
        assertThat(entity).contains("String tenantId");
        assertThat(entity).doesNotContain("UUID tenantId");
    }

    private static AgentTask unfinished(String tenantId, String subjectId) {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setTenantId(tenantId);
        task.setKind("enrich");
        task.setSubjectType("contact");
        task.setSubjectId(subjectId);
        task.setAttempts(0);
        task.setMaxAttempts(5);
        task.setPriority(0);
        task.setDueAt(NOW);
        task.setCreatedAt(NOW);
        return task;
    }

    private static AgentTaskEnqueue request(String tenantId, String subjectId, Integer budget) {
        return new AgentTaskEnqueue(tenantId, "enrich", "contact", subjectId, "because", "{\"a\":1}",
                1, budget, 5, NOW);
    }

    private static PlatformTransactionManager immediate() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
