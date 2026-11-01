package com.opencrm.crm.merge;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountMergeIntegrationTest extends BaseIntegrationTest {
    @Autowired AccountMergeService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired jakarta.persistence.EntityManager transactionalEntityManager;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @MockitoBean AccountMergeReadiness readiness;

    @Test void reciprocalMergesSerializeAndOnlyOneCanCommit() throws Exception {
        when(readiness.isReady()).thenReturn(true);
        long a = createOrganization(), b = createOrganization();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> merge(start, a, b));
            var two = pool.submit(() -> merge(start, b, a));
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from organizations where is_deleted", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from account_merges", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isEqualTo(1);
    }

    @Test void preloadedLosingOrganizationIsRefreshedAfterConcurrentDelete() {
        when(readiness.isReady()).thenReturn(true);
        long survivor = createOrganization(), loser = createOrganization();
        var em = entityManagerFactory.createEntityManager();
        var holder = new org.springframework.orm.jpa.EntityManagerHolder(em);
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("sales-ops").claim("scp", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))));
        try {
            assertThat(em.find(com.opencrm.crm.organization.Organization.class, loser).isDeleted()).isFalse();
            jdbc.update("update organizations set is_deleted=true, version=version+1 where id=?", loser);
            assertThatThrownBy(() -> service.merge(AccountMergeRequest.builder().survivorId(survivor).mergedAwayId(loser).build()))
                    .isInstanceOf(com.opencrm.common.exception.ResourceNotFoundException.class);
            assertThat(jdbc.queryForObject("select count(*) from account_merges", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isZero();
        } finally {
            SecurityContextHolder.clearContext();
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }
    }

    @Test void dirtyRequestBoundContextIsRejectedWithoutDiscardingCallerEdit() {
        when(readiness.isReady()).thenReturn(true);
        long survivor = createOrganization(), loser = createOrganization();
        var em = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagerFactory,
                new org.springframework.orm.jpa.EntityManagerHolder(em));
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("sales-ops").claim("scp", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))));
        try {
            var managed = em.find(com.opencrm.crm.organization.Organization.class, loser);
            managed.setName("caller pending edit");
            assertThatThrownBy(() -> service.merge(AccountMergeRequest.builder().survivorId(survivor).mergedAwayId(loser).build()))
                    .isInstanceOfSatisfying(com.opencrm.common.exception.BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_MERGE_DIRTY_CONTEXT"));
            assertThat(managed.getName()).isEqualTo("caller pending edit");
            assertThat(jdbc.queryForObject("select name from organizations where id=?", String.class, loser)).isEqualTo("test");
            assertThat(jdbc.queryForObject("select count(*) from account_merges", Long.class)).isZero();
        } finally {
            SecurityContextHolder.clearContext();
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }
    }

    @Test void dirtyOuterTransactionIsMarkedRollbackOnlyWithoutCommittingCallerEdit() {
        when(readiness.isReady()).thenReturn(true);
        long survivor = createOrganization(), loser = createOrganization();
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("sales-ops").claim("scp", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))));
        try {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                var managed = transactionalEntityManager.find(com.opencrm.crm.organization.Organization.class, loser);
                managed.setName("outer pending edit");
                assertThatThrownBy(() -> service.merge(AccountMergeRequest.builder().survivorId(survivor).mergedAwayId(loser).build()))
                        .isInstanceOfSatisfying(com.opencrm.common.exception.BusinessException.class,
                                ex -> assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_MERGE_DIRTY_CONTEXT"));
                assertThat(managed.getName()).isEqualTo("outer pending edit");
                assertThat(status.isRollbackOnly()).isTrue();
            })).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
            assertThat(jdbc.queryForObject("select name from organizations where id=?", String.class, loser)).isEqualTo("test");
            assertThat(jdbc.queryForObject("select count(*) from account_merges", Long.class)).isZero();
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void endpointRejectsUnauthorizedAndHoldsAuthorizedHuman() throws Exception {
        var body = "{\"survivorId\":1,\"mergedAwayId\":2}";
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/organizations/merge")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body);
        try {
            SecurityContextHolder.clearContext();
            mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("sales-ops").claim("scp", "user").build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))));
            when(readiness.isReady()).thenReturn(false);
            mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("ACCOUNT_MERGE_HOLD"));
            assertThat(jdbc.queryForObject("select count(*) from account_merges", Long.class)).isZero();
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void deletedOrganizationReferenceReturnsActionableConflict() throws Exception {
        long id = createOrganization();
        jdbc.update("update organizations set is_deleted=true where id=?", id);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/leads")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New lead\",\"organizationId\":" + id + "}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("ORGANIZATION_REFERENCE_INACTIVE"));
        assertThat(jdbc.queryForObject("select count(*) from leads", Long.class)).isZero();
    }

    private long createOrganization() {
        return jdbc.queryForObject("insert into organizations(uuid,name) values (?, 'test') returning id", Long.class, UUID.randomUUID().toString());
    }
    private boolean merge(CountDownLatch start, long survivor, long loser) throws Exception {
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("sales-ops").claim("scp", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))));
        try {
            start.await();
            service.merge(AccountMergeRequest.builder().survivorId(survivor).mergedAwayId(loser).build());
            return true;
        } catch (com.opencrm.common.exception.ResourceNotFoundException expected) {
            return false;
        } finally { SecurityContextHolder.clearContext(); }
    }
}
