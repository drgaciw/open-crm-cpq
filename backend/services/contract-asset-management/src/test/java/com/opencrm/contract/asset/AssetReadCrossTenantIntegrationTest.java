package com.opencrm.contract.asset;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant join against Postgres. Same-tenant account, subscription, and contract-line
 * parameters only narrow. A null subscription tenant is invisible.
 */
@Tag("integration")
@Testcontainers
@Transactional
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AssetReadCrossTenantIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SubscriptionRepository subscriptions;
    @Autowired AssetRepository assets;
    @Autowired AssetHistoryProjection history;
    @Autowired AssetController assetApi;
    @Autowired AssetQueryController queryApi;
    @Autowired AssetReportController reportApi;

    private AssetReadTenantFixture world;

    @BeforeEach
    void seed() {
        world = new AssetReadTenantFixture();
        world.seed(subscriptions, assets, history);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crossTenantReadsAreNotFoundOrEmpty() {
        AssetReadTenantFixture.signIn(AssetReadTenantFixture.TID_B);
        assertThat(uuids(null, null, null)).containsExactlyInAnyOrder(
                AssetReadTenantFixture.OTHER_TENANT, AssetReadTenantFixture.OTHER_ACCOUNT);
        assertThatThrownBy(() -> assetApi.get(world.assetIds.get(AssetReadTenantFixture.PREDECESSOR)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(missingAssetMessage(world.assetIds.get(AssetReadTenantFixture.PREDECESSOR)));
        assertThatThrownBy(() -> queryApi.history(AssetReadTenantFixture.PREDECESSOR))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Asset not found with identifier: " + AssetReadTenantFixture.PREDECESSOR);
        assertThatThrownBy(() -> queryApi.supersedeChain(AssetReadTenantFixture.PREDECESSOR, "both"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Asset not found with identifier: " + AssetReadTenantFixture.PREDECESSOR);
        assertThat(queryApi.installedBase(AssetReadTenantFixture.ACCOUNT_A1, AssetReadTenantFixture.AS_OF).getData())
                .isEmpty();
        assertThat(reportApi.upcomingRenewals(AssetReadTenantFixture.ACCOUNT_A1,
                AssetReadTenantFixture.WINDOW_START, AssetReadTenantFixture.WINDOW_END, null, null).getData().getAssets())
                .isEmpty();
        assertThat(reportApi.churn(AssetReadTenantFixture.ACCOUNT_A1,
                AssetReadTenantFixture.PERIOD_START, AssetReadTenantFixture.PERIOD_END).getData().getEvents())
                .isEmpty();
    }

    @Test
    void sameTenantParametersOnlyNarrowAndDoNotHideOtherAccounts() {
        AssetReadTenantFixture.signIn(AssetReadTenantFixture.TID_A);
        assertThat(uuids(null, null, null)).contains(
                AssetReadTenantFixture.PREDECESSOR, AssetReadTenantFixture.SUCCESSOR, AssetReadTenantFixture.RENEWAL);
        assertThat(uuids(AssetReadTenantFixture.ACCOUNT_A1, null, null)).containsExactlyInAnyOrder(
                AssetReadTenantFixture.PREDECESSOR, AssetReadTenantFixture.RENEWAL, AssetReadTenantFixture.CHURN);
        assertThat(uuids(AssetReadTenantFixture.ACCOUNT_B1, null, null)).isEmpty();
        assertThat(uuids(null, world.subscriptionIds.get(AssetReadTenantFixture.SUCCESSOR), null))
                .containsExactly(AssetReadTenantFixture.SUCCESSOR);
        assertThat(uuids(null, world.subscriptionIds.get(AssetReadTenantFixture.OTHER_TENANT), null)).isEmpty();
        assertThat(uuids(null, null, "line-r")).containsExactly(AssetReadTenantFixture.RENEWAL);
        assertThat(uuids(null, null, "line-x")).isEmpty();
        assertThat(uuids(AssetReadTenantFixture.ACCOUNT_A1,
                world.subscriptionIds.get(AssetReadTenantFixture.SUCCESSOR), null)).isEmpty();
        SupersedeChainDto chain = queryApi.supersedeChain(AssetReadTenantFixture.PREDECESSOR, "both").getData();
        assertThat(chain.getSuccessors()).extracting(SupersedeHopDto::getAssetId)
                .containsExactly(AssetReadTenantFixture.SUCCESSOR);
        assertThat(queryApi.installedBase(AssetReadTenantFixture.ACCOUNT_A2, AssetReadTenantFixture.AS_OF).getData())
                .extracting(InstalledAssetDto::getAssetId).contains(AssetReadTenantFixture.SUCCESSOR);
    }

    @Test
    void nullTenantRowAndAbsentOrNonGuidCallerFailClosed() {
        AssetReadTenantFixture.signIn(AssetReadTenantFixture.TID_A);
        assertThat(uuids(null, null, null)).doesNotContain(AssetReadTenantFixture.NULL_TENANT);
        assertThatThrownBy(() -> assetApi.get(world.assetIds.get(AssetReadTenantFixture.NULL_TENANT)))
                .isInstanceOf(ResourceNotFoundException.class);
        AssetReadTenantFixture.signInRawTid(null);
        assertThat(uuids(null, null, null)).isEmpty();
        AssetReadTenantFixture.signInRawTid("not-a-guid");
        assertThat(uuids(null, null, null)).isEmpty();
        assertThatThrownBy(() -> queryApi.history(AssetReadTenantFixture.RENEWAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void missingRoleStaysForbidden() {
        AssetReadTenantFixture.signInWithoutRole(AssetReadTenantFixture.TID_A);
        assertThatThrownBy(() -> assetApi.list(null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queryApi.history(AssetReadTenantFixture.RENEWAL))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reportApi.churn(AssetReadTenantFixture.ACCOUNT_A1,
                AssetReadTenantFixture.PERIOD_START, AssetReadTenantFixture.PERIOD_END))
                .isInstanceOf(AccessDeniedException.class);
    }

    private List<String> uuids(String accountId, Long subscriptionId, String contractLineId) {
        return assetApi.list(accountId, subscriptionId, contractLineId).getData().stream()
                .map(AssetDto::getUuid).toList();
    }

    private static String missingAssetMessage(Long id) {
        return new ResourceNotFoundException("Asset", id).getMessage();
    }
}
