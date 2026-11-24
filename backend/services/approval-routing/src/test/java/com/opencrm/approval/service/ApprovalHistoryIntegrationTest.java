package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalTimelineEntry;
import com.opencrm.approval.domain.ApprovalTimelineKind;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.dto.ApprovalCommentRequest;
import com.opencrm.approval.dto.ApprovalHistoryEntryDto;
import com.opencrm.approval.repository.ApprovalCaseCommentRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.repository.ApprovalTimelineRepository;
import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.common.event.dto.ApprovalGrantedEvent;
import com.opencrm.security.CurrentActor;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GetApprovalHistory against PostgreSQL 16 after Flyway, including a comment thread.
 */
@Tag("integration")
class ApprovalHistoryIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory emf;
    private static TransactionTemplate tx;
    private static ApprovalRequestRepository approvals;
    private static ApprovalTimelineRepository timeline;
    private static ApprovalHistoryService history;

    @BeforeAll
    static void database() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        DataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(data)
                .locations("filesystem:src/main/resources/db/migration")
                .load();
        flyway.migrate();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion()))
                .contains("1.16", "1.17");

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
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(emf);
        var repositories = new JpaRepositoryFactory(entityManager);
        approvals = repositories.getRepository(ApprovalRequestRepository.class);
        timeline = repositories.getRepository(ApprovalTimelineRepository.class);
        history = new ApprovalHistoryService(
                approvals,
                repositories.getRepository(ApprovalCaseCommentRepository.class),
                timeline,
                new CurrentActor("service:approval-routing", true));
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

    @Test
    void historyQueryReturnsDecisionsEscalationsAndCommentsInTimeOrder() {
        Long caseId = tx.execute(status -> saveApproval("42"));
        Long otherCase = tx.execute(status -> saveApproval("99"));

        tx.execute(status -> {
            // Insert the later decision first so insertion order is not time order.
            history.recordGranted(new ApprovalGrantedEvent(
                    caseId, "42", "mgr", "SALES_VP", "ship it", T0.plusSeconds(30)), 5L);
            history.recordEscalated(new ApprovalEscalatedEvent(
                    caseId, "42", "DEAL_DESK", "SALES_VP", T0.plusSeconds(10), T0.plusSeconds(40)), 2L);
            history.recordGranted(new ApprovalGrantedEvent(
                    otherCase, "99", "mgr", "DEAL_DESK", "other quote", T0), 1L);
            return null;
        });

        history.setClock(Clock.fixed(T0.plusSeconds(20), ZoneOffset.UTC));
        ApprovalHistoryEntryDto comment = tx.execute(status -> history.addComment(caseId,
                ApprovalCommentRequest.builder().body("try 12 percent").quoteVersion(4L).build()));
        history.setClock(Clock.fixed(T0.plusSeconds(40), ZoneOffset.UTC));
        ApprovalHistoryEntryDto reply = tx.execute(status -> history.addComment(caseId,
                ApprovalCommentRequest.builder()
                        .body("agreed")
                        .parentCommentId(comment.getCommentId())
                        .quoteVersion(4L)
                        .build()));

        List<ApprovalTimelineEntry> queried = tx.execute(status ->
                timeline.findByQuoteIdAndDeletedFalseOrderByOccurredAtAscIdAsc("42"));
        List<ApprovalHistoryEntryDto> rows = tx.execute(status -> history.getApprovalHistory("42"));

        assertThat(queried).extracting(ApprovalTimelineEntry::getKind).containsExactly(
                ApprovalTimelineKind.ESCALATED,
                ApprovalTimelineKind.COMMENT,
                ApprovalTimelineKind.GRANTED,
                ApprovalTimelineKind.COMMENT);
        assertThat(rows).extracting(ApprovalHistoryEntryDto::getKind).containsExactly(
                ApprovalTimelineKind.ESCALATED,
                ApprovalTimelineKind.COMMENT,
                ApprovalTimelineKind.GRANTED,
                ApprovalTimelineKind.COMMENT);
        assertThat(rows).extracting(ApprovalHistoryEntryDto::getEventName).containsExactly(
                "ApprovalEscalated", null, "ApprovalGranted", null);
        assertThat(rows).extracting(ApprovalHistoryEntryDto::getQuoteVersion).containsExactly(2L, 4L, 5L, 4L);
        assertThat(rows).extracting(ApprovalHistoryEntryDto::getBody)
                .containsExactly(null, "try 12 percent", "ship it", "agreed");
        assertThat(rows.get(0).getFromTier()).isEqualTo("DEAL_DESK");
        assertThat(rows.get(0).getToTier()).isEqualTo("SALES_VP");
        assertThat(rows.get(1).getCommentId()).isEqualTo(comment.getCommentId());
        assertThat(rows.get(1).getParentCommentId()).isNull();
        assertThat(rows.get(3).getCommentId()).isEqualTo(reply.getCommentId());
        assertThat(rows.get(3).getParentCommentId()).isEqualTo(comment.getCommentId());
        assertThat(rows).extracting(ApprovalHistoryEntryDto::getQuoteId).containsOnly("42");
        assertThat(rows).extracting(ApprovalHistoryEntryDto::getApprovalId).containsOnly(caseId);
    }

    private static Long saveApproval(String quoteId) {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId(quoteId)
                .approvalType(ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .requesterId("seller")
                .approvalTier("DEAL_DESK")
                .submittedAt(T0)
                .reason("margin")
                .build();
        request.setCreatedAt(T0);
        request.setUuid(UUID.randomUUID().toString());
        request.setVersion(0L);
        return approvals.saveAndFlush(request).getId();
    }
}
