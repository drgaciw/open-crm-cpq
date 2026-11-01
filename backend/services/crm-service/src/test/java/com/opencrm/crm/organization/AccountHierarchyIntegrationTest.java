package com.opencrm.crm.organization;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.common.valueobject.AccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@org.springframework.context.annotation.Import(AccountHierarchyIntegrationTest.ReadyTestConfig.class)
class AccountHierarchyIntegrationTest extends BaseIntegrationTest {
    /** The only ready policy is constructed in test sources, never by production configuration. */
    @org.springframework.boot.test.context.TestConfiguration
    static class ReadyTestConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        AccountHierarchyService readyHierarchy(OrganizationRepository repository,
                com.opencrm.common.event.OutboxEventPublisher outbox,
                JdbcTemplate jdbc, jakarta.persistence.EntityManager entityManager) {
            return new AccountHierarchyService(repository, outbox, jdbc, entityManager, true);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountHierarchyService hierarchy;
    @Autowired OrganizationService organizations;
    @Autowired OrganizationRepository repository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private long create(String name) {
        return jdbc.queryForObject("insert into crm.organizations (uuid,name,created_by) values (?,?, 'test') returning id", Long.class, java.util.UUID.randomUUID().toString(), name);
    }
    private AccountId account(long id) {
        return AccountId.of(jdbc.queryForObject("select uuid from crm.organizations where id = ?", String.class, id));
    }

    @Test void routesAndGenericParentMutationsAreHeld() throws Exception {
        long parent=create("Parent"), child=create("Child");
        mvc.perform(put("/api/v1/organizations/{id}/parent",child).contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":"+parent+"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organizations/{id}/hierarchy",child)).andExpect(status().isNotFound());
        for (String key : new String[]{"parentId", "parentOrganizationId"}) {
            mvc.perform(put("/api/v1/organizations/{id}",child).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Changed\",\""+key+"\":"+parent+"}"))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"New\",\""+key+"\":"+parent+"}"))
                    .andExpect(status().isServiceUnavailable());
        }
        assertThat(jdbc.queryForObject("select name from crm.organizations where id=?",String.class,child)).isEqualTo("Child");
        assertThat(jdbc.queryForObject("select count(*) from crm.organizations",Long.class)).isEqualTo(2);
        hierarchy.linkToParent(account(child),parent);
        var tree=hierarchy.resolveHierarchy(account(child));
        assertThat(tree.getParent().getId()).isEqualTo(parent);
        assertThat(hierarchy.resolveHierarchy(account(parent)).getChildren()).extracting(AccountHierarchyNodeDto::getId).containsExactly(child);
    }

    @Test void preparedParentRequestDistinguishesMissingAndExplicitNull() throws Exception {
        var link=mapper.readValue("{\"parentId\":42}", LinkToParentRequest.class);
        var unlink=mapper.readValue("{\"parentId\":null}", LinkToParentRequest.class);
        var absent=mapper.readValue("{}", LinkToParentRequest.class);
        assertThat(link.getParentOrganizationId()).isEqualTo(42L);
        assertThat(link.isParentSpecified()).isTrue();
        assertThat(unlink.getParentOrganizationId()).isNull();
        assertThat(unlink.isParentSpecified()).isTrue();
        assertThat(absent.isParentSpecified()).isFalse();
    }

    @Test void softDeleteWithActiveChildDoesNotBreakHierarchy() throws Exception {
        long parent=create("Parent"), child=create("Child");
        hierarchy.linkToParent(account(child),parent);
        mvc.perform(delete("/api/v1/organizations/{id}",parent)).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_HAS_CHILDREN"))
                .andExpect(jsonPath("$.message").value("This account has active child accounts. Hierarchy management is unavailable; contact an administrator"));
        mvc.perform(put("/api/v1/organizations/{id}/parent",child).contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":null}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organizations/{id}/hierarchy",child)).andExpect(status().isNotFound());
        assertThat(hierarchy.resolveHierarchy(account(child)).getParent().getId()).isEqualTo(parent);
    }

    @Test void oppositeConcurrentLinksCannotCommitACycle() throws Exception {
        long a=create("A"), b=create("B"); AccountId first=account(a), second=account(b);
        jdbc.execute("create function crm.test_slow_parent_link() returns trigger language plpgsql as $$ begin if new.parent_organization_id is distinct from old.parent_organization_id then perform pg_sleep(1); end if; return new; end $$");
        jdbc.execute("create trigger test_slow_parent_link before update on crm.organizations for each row execute function crm.test_slow_parent_link()");
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            var one=pool.submit(()-> {start.await(); try {hierarchy.linkToParent(first,b); return true;} catch(RuntimeException ex){return false;} });
            var two=pool.submit(()-> {start.await(); try {hierarchy.linkToParent(second,a); return true;} catch(RuntimeException ex){return false;} });
            start.countDown();
            assertThat((one.get(20,TimeUnit.SECONDS)?1:0)+(two.get(20,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from crm.organizations where (id=? and parent_organization_id=?) or (id=? and parent_organization_id=?)",Long.class,a,b,b,a)).isEqualTo(1);
        } finally {pool.shutdownNow(); jdbc.execute("drop trigger test_slow_parent_link on crm.organizations"); jdbc.execute("drop function crm.test_slow_parent_link()");}
    }

    @Test void sharedTransactionRetainsPendingAccountEditsWhenLinkRefreshes() {
        long parent=create("Parent"), child=create("Original"); AccountId id=account(child);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            Organization managed=repository.findById(child).orElseThrow();
            managed.setName("Pending name");
            hierarchy.linkToParent(id,parent);
        });
        assertThat(jdbc.queryForObject("select name from crm.organizations where id=?",String.class,child)).isEqualTo("Pending name");
        assertThat(jdbc.queryForObject("select parent_organization_id from crm.organizations where id=?",Long.class,child)).isEqualTo(parent);
    }

    @Test void sharedTransactionRetainsPendingAccountEditsWhenArchiving() {
        long child=create("Original"); AccountId id=account(child);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            Organization managed=repository.findById(child).orElseThrow();
            managed.setName("Pending name");
            organizations.delete(id);
        });
        assertThat(jdbc.queryForObject("select name from crm.organizations where id=?",String.class,child)).isEqualTo("Pending name");
        assertThat(jdbc.queryForObject("select is_deleted from crm.organizations where id=?",Boolean.class,child)).isTrue();
    }

    @Test void concurrentArchiveAndLinkCannotLeaveAnActiveChildOfAnArchivedParent() throws Exception {
        long parent=create("Parent"), child=create("Child"); AccountId p=account(parent), c=account(child);
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            var link=pool.submit(()-> {start.await(); try {hierarchy.linkToParent(c,parent); return true;} catch(RuntimeException ex){return false;} });
            var archive=pool.submit(()-> {start.await(); try {organizations.delete(p); return true;} catch(RuntimeException ex){return false;} });
            start.countDown();
            assertThat((link.get(20,TimeUnit.SECONDS)?1:0)+(archive.get(20,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from crm.organizations c join crm.organizations p on p.id=c.parent_organization_id where c.id=? and not c.is_deleted and p.is_deleted",Long.class,child)).isZero();
        } finally {pool.shutdownNow();}
    }

}
