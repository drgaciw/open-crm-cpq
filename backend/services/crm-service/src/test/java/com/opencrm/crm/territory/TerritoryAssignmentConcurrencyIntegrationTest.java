package com.opencrm.crm.territory;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL contention across separate transactions. */
@org.springframework.context.annotation.Import(TerritoryAssignmentConcurrencyIntegrationTest.ReadyTestPolicy.class)
class TerritoryAssignmentConcurrencyIntegrationTest extends BaseIntegrationTest {
    @Autowired TerritoryAssignmentService service;
    @Autowired OrganizationRepository organizations;
    @Autowired TerritoryAssignmentRuleRepository rules;
    @Autowired OrganizationTerritoryAssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired TerritoryAssignmentReadiness readiness;

    @BeforeEach
    void enableTestPolicy() {
        org.mockito.Mockito.when(readiness.isReady()).thenReturn(true);
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ReadyTestPolicy {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        TerritoryAssignmentReadiness readyPolicy() {
            return org.mockito.Mockito.mock(TerritoryAssignmentReadiness.class);
        }
    }

    @Test
    void concurrentFirstAssignmentsReturnSameResultAndOneEvent() throws Exception {
        rules.deleteAll();
        Organization organization = organizations.saveAndFlush(Organization.builder()
                .name("Lock test").country("USA").state("CA").industry("Software").employeeCount(25).build());
        service.createRule(TerritoryRuleRequest.builder().name("Rule").country("USA").state("CA")
                .industry("Software").minEmployeeCount(0).maxEmployeeCount(100)
                .territoryCode("WEST").segmentCode("SMB").build());
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var blocker = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("select id from organizations where id = ? for update", Long.class, organization.getId());
                locked.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("lock release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var first = pool.submit(() -> service.assign(organization.getId()));
            var second = pool.submit(() -> service.assign(organization.getId()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            long waiting;
            do {
                waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() "
                        + "and wait_event_type = 'Lock' and query ilike '%organization%'", Long.class);
                if (waiting >= 2) break;
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            assertThat(waiting).isGreaterThanOrEqualTo(2);
            release.countDown();
            blocker.get(15, TimeUnit.SECONDS);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .allSatisfy(result -> {
                        assertThat(result.isAssigned()).isTrue();
                        assertThat(result.getSegment()).isEqualTo("SMB");
                    });
            assertThat(jdbc.queryForObject("select count(*) from organization_territory_assignments where organization_id = ?",
                    Long.class, organization.getId())).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? and topic = ?",
                    Long.class, organization.getUuid(), com.opencrm.common.event.KafkaTopics.TERRITORY_ASSIGNED)).isEqualTo(1L);
        } finally {
            release.countDown();
        }
    }

    @Test
    void assignmentWaitsForOrganizationFactUpdateAndUsesCommittedFacts() throws Exception {
        rules.deleteAll();
        Organization organization = organizations.saveAndFlush(Organization.builder()
                .name("Fact test").country("USA").state("CA").industry("Software").employeeCount(25).build());
        service.createRule(TerritoryRuleRequest.builder().name("US").country("USA").industry("Software")
                .territoryCode("US").segmentCode("OLD").build());
        service.createRule(TerritoryRuleRequest.builder().name("Canada").country("Canada").industry("Software")
                .territoryCode("CA").segmentCode("NEW").build());
        CountDownLatch updated = new CountDownLatch(1), commit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("update organizations set country = 'Canada', version = version + 1 where id = ?", organization.getId());
                updated.countDown();
                try {
                    if (!commit.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("commit timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();
            var assignment = pool.submit(() -> service.assign(organization.getId()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            long waiting;
            do {
                waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() "
                        + "and wait_event_type = 'Lock' and query ilike '%organization%'", Long.class);
                if (waiting >= 1) break;
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            assertThat(waiting).isPositive();
            commit.countDown();
            writer.get(15, TimeUnit.SECONDS);
            assertThat(assignment.get(15, TimeUnit.SECONDS).getSegment()).isEqualTo("NEW");
        } finally {
            commit.countDown();
        }
    }

    @Test
    void deletedOrganizationStillReturnsNotFoundWithoutAssignment() {
        Organization organization = organizations.saveAndFlush(Organization.builder()
                .name("Deleted").country("USA").industry("Software").employeeCount(25).build());
        organization.setDeleted(true);
        organizations.saveAndFlush(organization);
        assertThatThrownBy(() -> service.assign(organization.getId()))
                .isInstanceOf(com.opencrm.common.exception.ResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from organization_territory_assignments where organization_id = ?",
                Long.class, organization.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?",
                Long.class, organization.getUuid())).isZero();
    }

    @Test
    void preloadedRequestContextDoesNotAssignFromStaleFacts() throws Exception {
        rules.deleteAll();
        Organization organization = organizations.saveAndFlush(Organization.builder()
                .name("Preload test").country("USA").industry("Software").employeeCount(25).build());
        service.createRule(TerritoryRuleRequest.builder().name("US").country("USA").industry("Software")
                .territoryCode("US").segmentCode("OLD").build());
        service.createRule(TerritoryRuleRequest.builder().name("Canada").country("Canada").industry("Software")
                .territoryCode("CA").segmentCode("NEW").build());
        var entityManager = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                entityManagerFactory, new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
        try (var pool = Executors.newSingleThreadExecutor()) {
            assertThat(organizations.findById(organization.getId()).orElseThrow().getCountry()).isEqualTo("USA");
            pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                    jdbc.update("update organizations set country = 'Canada', version = version + 1 where id = ?",
                            organization.getId()))).get(15, TimeUnit.SECONDS);
            assertThat(service.assign(organization.getId()).getSegment()).isEqualTo("NEW");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            entityManager.close();
        }
    }

    @Test
    void preloadedAssignmentCannotCauseAStaleNoOp() throws Exception {
        rules.deleteAll();
        Organization organization = organizations.saveAndFlush(Organization.builder()
                .name("Assignment preload").country("USA").industry("Software").employeeCount(25).build());
        service.createRule(TerritoryRuleRequest.builder().name("US").country("USA").industry("Software")
                .territoryCode("WEST").segmentCode("SMB").build());
        service.assign(organization.getId());
        var entityManager = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                entityManagerFactory, new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
        try (var pool = Executors.newSingleThreadExecutor()) {
            assertThat(assignments.findByOrganizationId(organization.getId()).orElseThrow().getSegmentCode())
                    .isEqualTo("SMB");
            pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("select id from organizations where id = ? for update", Long.class, organization.getId());
                jdbc.update("update organization_territory_assignments set segment_code = 'OTHER', version = version + 1 "
                        + "where organization_id = ?", organization.getId());
            })).get(15, TimeUnit.SECONDS);

            assertThat(service.assign(organization.getId()).getSegment()).isEqualTo("SMB");
            assertThat(jdbc.queryForObject("select segment_code from organization_territory_assignments where organization_id = ?",
                    String.class, organization.getId())).isEqualTo("SMB");
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? and topic = ?",
                    Long.class, organization.getUuid(), com.opencrm.common.event.KafkaTopics.TERRITORY_ASSIGNED)).isEqualTo(2L);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            entityManager.close();
        }
    }
}
