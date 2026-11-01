package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.asset.fixtures.AssetAmendedFixture;
import com.opencrm.contract.asset.fixtures.AssetSupersededFixture;
import com.opencrm.contract.asset.fixtures.AssetTerminatedFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dropping the history projection and replaying the same events rebuilds the same history.
 * The amended, superseded, and terminated inputs are test-local fixtures. Nothing is published.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AssetHistoryReplayIntegrationTest {
    @BeforeEach void authorizeRead() { AssetReadTestCaller.signIn(); }
    @AfterEach void clearReadAuthorization() { AssetReadTestCaller.clear(); }

    private static final String ASSET = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String SUCCESSOR = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
    private static final String ACCOUNT = "cust-replay";

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

    @Autowired AssetHistoryProjection projection;
    @Autowired AssetQueryController api;
    @Autowired JdbcTemplate jdbc;

    @Test
    void droppedProjectionReplaysToTheSameHistory() {
        applyAll();
        List<AssetHistoryEntryDto> first = api.history(ASSET).getData();
        SupersedeChainDto chain = api.supersedeChain(ASSET, "both").getData();
        assertThat(count("select count(*) from asset_history")).isEqualTo(4L);
        assertThat(first).extracting(AssetHistoryEntryDto::getEventType)
                .containsExactly("CREATED", "AMENDED", "SUPERSEDED", "TERMINATED");
        assertThat(chain.getSuccessors()).extracting(SupersedeHopDto::getAssetId).containsExactly(SUCCESSOR);

        projection.dropProjection();
        assertThat(count("select count(*) from asset_history")).isZero();

        applyAll();
        List<AssetHistoryEntryDto> second = api.history(ASSET).getData();
        assertThat(second).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(first);
        assertThat(api.supersedeChain(ASSET, "successors").getData().getSuccessors())
                .extracting(SupersedeHopDto::getAssetId).containsExactly(SUCCESSOR);
        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-01-01")).getData()).isEmpty();
        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-05-01")).getData())
                .extracting(InstalledAssetDto::getAssetId).containsExactly(ASSET);
        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-09-01")).getData()).isEmpty();
    }

    private void applyAll() {
        projection.apply(new AssetCreatedEvent(
                ASSET, ACCOUNT, "line-1", 9L, null, "prod-1", new BigDecimal("2"),
                "ACTIVE", LocalDate.parse("2026-03-01"), LocalDate.parse("2027-02-28")), "evt-created");
        projection.apply(AssetAmendedFixture.of(
                "evt-amended", ASSET, ACCOUNT, LocalDate.parse("2026-04-01"),
                "amd-1", "1", new BigDecimal("5")));
        projection.apply(AssetSupersededFixture.of(
                "evt-superseded", ASSET, ACCOUNT, LocalDate.parse("2026-07-01"),
                "amd-2", "2", SUCCESSOR));
        projection.apply(AssetTerminatedFixture.of(
                "evt-terminated", ASSET, ACCOUNT, LocalDate.parse("2026-09-01"),
                "amd-3", "1"));
    }

    private long count(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
