package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.security.CurrentActor;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SLA escalation against PostgreSQL after the Flyway schema, with a pinned clock.
 */
@Tag("integration")
class ApprovalEscalationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory emf;
    private static TransactionTemplate tx;
    private static ApprovalRequestRepository requests;

    private OutboxEventPublisher publisher;
    private ApprovalService approvals;

    @BeforeAll
    static void database() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        DataSource data = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(data)
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(data);
        factory.setPackagesToScan("com.opencrm.approval.domain", "com.opencrm.common.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.show_sql", "false"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        requests = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
                .getRepository(ApprovalRequestRepository.class);
    }

    @AfterAll
    static void stop() {
        if (emf != null) {
            emf.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void service() {
        publisher = mock(OutboxEventPublisher.class);
        ApprovalRequestMapper mapper = mock(ApprovalRequestMapper.class);
        when(mapper.toDto(any())).thenAnswer(invocation -> null);
        approvals = new ApprovalService(requests, publisher, mapper, new CurrentActor("service:approval-routing", true));
        approvals.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        approvals.setSlaWindow(Duration.ofHours(24));
    }

    @Test
    void overdueRequestEscalatesOnceAfterTheSlaWindow() {
        Long id = tx.execute(status -> {
            ApprovalRequest request = ApprovalRequest.builder()
                    .quoteId("42")
                    .approvalType(ApprovalType.QUOTE)
                    .status(ApprovalStatus.PENDING)
                    .requesterId("seller")
                    .approvalTier("DEAL_DESK")
                    .escalationPath("DEAL_DESK,SALES_VP,CFO")
                    .submittedAt(NOW.minus(Duration.ofHours(25)))
                    .slaDeadline(NOW.minus(Duration.ofMinutes(5)))
                    .reason("margin")
                    .build();
            request.setCreatedAt(NOW.minus(Duration.ofHours(25)));
            request.setUuid(java.util.UUID.randomUUID().toString());
            return requests.saveAndFlush(request).getId();
        });

        Integer advanced = tx.execute(status -> approvals.escalateDue());
        assertThat(advanced).isEqualTo(1);

        ApprovalRequest stored = tx.execute(status -> requests.findById(id).orElseThrow());
        assertThat(stored.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(stored.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat(stored.getEscalatedFromTier()).isEqualTo("DEAL_DESK");
        assertThat(stored.getEscalatedAt()).isEqualTo(NOW);
        assertThat(stored.getSlaDeadline()).isEqualTo(NOW.plus(Duration.ofHours(24)));

        ArgumentCaptor<ApprovalEscalatedEvent> event = ArgumentCaptor.forClass(ApprovalEscalatedEvent.class);
        verify(publisher).publish(eq("ApprovalRequest"), eq("42"),
                eq(EventContractRegistry.APPROVAL_ESCALATED_V1), event.capture());
        assertThat(event.getValue().fromTier()).isEqualTo("DEAL_DESK");
        assertThat(event.getValue().toTier()).isEqualTo("SALES_VP");
        assertThat(event.getValue().approvalId()).isEqualTo(id);

        Integer second = tx.execute(status -> approvals.escalateDue());
        assertThat(second).isZero();
        ApprovalRequest still = tx.execute(status -> requests.findById(id).orElseThrow());
        assertThat(still.getApprovalTier()).isEqualTo("SALES_VP");
    }

    /**
     * Production shape: the scheduler thread has no security context and dev mode is off. The sweep is a system
     * path over every tenant, so it must not go through the caller-scoped reads or the decision checks, which
     * refuse a missing caller (401) and, with H4, a missing tenant (403 TENANT_REQUIRED).
     */
    @Test
    void sweepRunsWithoutAVerifiedCallerOutsideDevMode() {
        SecurityContextHolder.clearContext();
        ApprovalService production = new ApprovalService(requests, publisher, mock(ApprovalRequestMapper.class),
                new CurrentActor("service:approval-routing", false));
        production.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        production.setSlaWindow(Duration.ofHours(24));

        Long id = tx.execute(status -> {
            ApprovalRequest request = ApprovalRequest.builder()
                    .quoteId("43")
                    .approvalType(ApprovalType.QUOTE)
                    .status(ApprovalStatus.PENDING)
                    .requesterId("seller")
                    .approvalTier("DEAL_DESK")
                    .escalationPath("DEAL_DESK,SALES_VP,CFO")
                    .submittedAt(NOW.minus(Duration.ofHours(25)))
                    .slaDeadline(NOW.minus(Duration.ofMinutes(5)))
                    .reason("margin")
                    .build();
            request.setCreatedAt(NOW.minus(Duration.ofHours(25)));
            request.setUuid(java.util.UUID.randomUUID().toString());
            return requests.saveAndFlush(request).getId();
        });

        Integer advanced = tx.execute(status -> production.escalateDue());

        assertThat(advanced).isGreaterThanOrEqualTo(1);
        ApprovalRequest stored = tx.execute(status -> requests.findById(id).orElseThrow());
        assertThat(stored.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat(stored.getEscalatedFromTier()).isEqualTo("DEAL_DESK");
    }
}
