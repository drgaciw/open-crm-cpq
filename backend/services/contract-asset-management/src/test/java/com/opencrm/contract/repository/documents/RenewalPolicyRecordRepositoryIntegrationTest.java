package com.opencrm.contract.repository.documents;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL proof that the tenant-scoped finder does not return another tenant's policy row.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class RenewalPolicyRecordRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=contract");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RenewalPolicyRecordRepository records;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void finderForTenantBDoesNotReturnTenantA() {
        UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID account = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        new TransactionTemplate(transactions).executeWithoutResult(status -> records.save(RenewalPolicyRecord.builder()
                .tenantId(tenantA)
                .createdBy("service:quote-service")
                .scopeKind(RenewalPolicyScopeKind.ACCOUNT)
                .scopeRef(account)
                .policyVersion(1)
                .mode("SAME")
                .status(RenewalPolicyRecord.STATUS_ACTIVE)
                .retentionMarker("marker-tenant-a")
                .build()));

        assertThat(records.findByTenantIdAndScopeKindAndScopeRefOrderByPolicyVersionAsc(
                tenantB, RenewalPolicyScopeKind.ACCOUNT, account)).isEmpty();
        assertThat(records.findByTenantIdAndScopeKindAndScopeRefAndPolicyVersion(
                tenantB, RenewalPolicyScopeKind.ACCOUNT, account, 1)).isEmpty();
        assertThat(records.findByTenantIdAndScopeKindAndScopeRefOrderByPolicyVersionAsc(
                tenantA, RenewalPolicyScopeKind.ACCOUNT, account))
                .singleElement()
                .extracting(RenewalPolicyRecord::getRetentionMarker)
                .isEqualTo("marker-tenant-a");
    }
}
