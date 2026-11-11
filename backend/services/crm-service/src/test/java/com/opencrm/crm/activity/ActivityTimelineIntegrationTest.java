package com.opencrm.crm.activity;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL/Flyway evidence, separate from the Docker-free unit gate. */
@Tag("integration")
@Tag("docker")
class ActivityTimelineIntegrationTest extends BaseIntegrationTest {
    private static final Instant WHEN = Instant.parse("2026-11-05T18:00:00Z");
    @Autowired ActivityService service;
    @Autowired ActivityRepository activities;
    @Autowired OrganizationRepository organizations;
    @Autowired ContactRepository contacts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void freshMigratedSchemaPersistsFourTypesAndIsolatesWorkforcePartitions() {
        var organization = organization();
        authenticate("workforce-a");
        var note = service.create(request(organization.getId(), ActivityType.NOTE));
        var task = service.create(request(organization.getId(), ActivityType.TASK));
        var email = service.create(request(organization.getId(), ActivityType.EMAIL));
        var meeting = service.create(request(organization.getId(), ActivityType.MEETING));
        assertThat(activities.timeline("workforce-a", organization.getId(), null, null, PageRequest.of(0, 2))
                .getContent()).extracting(Activity::getId).containsExactly(meeting.getId(), email.getId());
        assertThat(service.timeline(organization.getId(), null, null, 0, 100).getContent())
                .extracting(ActivityDto::getSource).containsOnly("MANUAL");
        assertThat(email.getNote()).isNull();
        assertThat(email.getReference()).isEqualTo("opaque-thread-or-event-reference");
        authenticate("workforce-b");
        assertThat(service.timeline(organization.getId(), null, null, 0, 20).getTotalElements()).isZero();
        assertThatThrownBy(() -> service.getById(note.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.complete(task.getId())).isInstanceOf(ResourceNotFoundException.class);
        authenticate("workforce-a");
        assertThat(service.getById(note.getId()).getAuthorId()).isEqualTo("seller-oid");
    }

    @Test
    void outerTransactionRollbackRemovesTheActivityWrite() {
        var organization = organization();
        authenticate("workforce-a");
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            service.create(request(organization.getId(), ActivityType.NOTE));
            activities.flush();
            assertThat(activities.count()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(activities.count()).isZero();
    }

    @Test
    void deletedLinkedContactHidesActivityFromOrganizationTimelineAndDirectRead() {
        var organization = organization();
        var contact = contacts.saveAndFlush(Contact.builder().firstName("Timeline").lastName("Contact")
                .organization(organization).build());
        authenticate("workforce-a");
        var request = request(organization.getId(), ActivityType.NOTE);
        request.setContactId(contact.getId());
        var activity = service.create(request);
        assertThat(service.timeline(organization.getId(), null, null, 0, 20).getTotalElements()).isEqualTo(1);
        contact.setDeleted(true);
        contacts.saveAndFlush(contact);
        var page = service.timeline(organization.getId(), null, null, 0, 20);
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThatThrownBy(() -> service.getById(activity.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concurrentCompletionWaitsForTheRealRowLockAndPreservesFirstTimestamp() throws Exception {
        var organization = organization();
        authenticate("workforce-a");
        var task = service.create(request(organization.getId(), ActivityType.TASK));
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var blocker = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("select id from activities where id = ? for update", Long.class, task.getId());
                locked.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("row-lock release timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var first = pool.submit(() -> completeAsUser(task.getId()));
            var second = pool.submit(() -> completeAsUser(task.getId()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            long waiting;
            do {
                waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() "
                        + "and wait_event_type = 'Lock' and query ilike '%activities%'", Long.class);
                if (waiting >= 2) break;
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            assertThat(waiting).isGreaterThanOrEqualTo(2);
            release.countDown();
            blocker.get(15, TimeUnit.SECONDS);
            var completed = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            var persisted = service.getById(task.getId()).getCompletedAt();
            assertThat(persisted).isNotNull();
            assertThat(completed).extracting(ActivityDto::getCompletedAt).containsOnly(persisted);
            assertThat(service.complete(task.getId()).getCompletedAt()).isEqualTo(persisted);
            assertThat(activities.findById(task.getId()).orElseThrow().getVersion()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void version15UpgradePreservesExistingAccountsAndEnforcesActivityConstraints() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            Flyway baseline = migration(database, "15");
            baseline.migrate();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("insert into crm.organizations(uuid,name) values ('upgrade-account','Preserved account')");
            }
            Flyway upgrade = migration(database, "16");
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            upgrade.validate();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("select name from crm.organizations where uuid='upgrade-account'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("Preserved account");
                }
                // Database constraints protect callers that bypass the REST/service validators.
                String invalid = "insert into crm.activities(uuid,workforce_tenant_id,author_id,type,title,occurred_at) "
                        + "values ('no-subject','workforce-a','seller-oid','TASK','Invalid',now())";
                assertThatThrownBy(() -> statement.execute(invalid)).isInstanceOf(java.sql.SQLException.class);
                String body = "insert into crm.activities(uuid,workforce_tenant_id,author_id,type,title,note,external_reference,organization_id,occurred_at) "
                        + "select 'mail-body','workforce-a','seller-oid','EMAIL','Subject','Private body','ref',id,now() "
                        + "from crm.organizations where uuid='upgrade-account'";
                assertThatThrownBy(() -> statement.execute(body)).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    private Flyway migration(PostgreSQLContainer<?> database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("crm").locations("classpath:db/migration").target(target)
                .placeholders(Map.of("crm_base_currency", "USD")).load();
    }

    private ActivityDto completeAsUser(Long id) {
        authenticate("workforce-a");
        try { return service.complete(id); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private Organization organization() {
        return organizations.saveAndFlush(Organization.builder().name("Activity test account").build());
    }

    private ActivityCreateRequest request(Long organizationId, ActivityType type) {
        var request = new ActivityCreateRequest();
        request.setType(type);
        request.setTitle("Manually recorded " + type);
        request.setOrganizationId(organizationId);
        request.setOccurredAt(WHEN);
        if (type == ActivityType.NOTE) request.setNote("A human entered this note");
        if (type == ActivityType.EMAIL || type == ActivityType.MEETING) request.setReference("opaque-thread-or-event-reference");
        if (type == ActivityType.TASK) request.setDueAt(WHEN.plusSeconds(3600));
        return request;
    }

    private static void authenticate(String workforceTenantId) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("seller-sub")
                .claim("oid", "seller-oid").claim("tid", workforceTenantId).claim("scp", "opencrm.read opencrm.write").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
