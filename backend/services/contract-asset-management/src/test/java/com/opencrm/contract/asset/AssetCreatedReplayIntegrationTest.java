package com.opencrm.contract.asset;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replaying an accepted-quote activation twice yields one asset per contract line,
 * readable from the asset Open Host Service.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AssetCreatedReplayIntegrationTest {
    @BeforeEach void authorizeRead() { AssetReadTestCaller.signIn(); }
    @AfterEach void clearReadAuthorization() { AssetReadTestCaller.clear(); }

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

    @Autowired AcceptedQuoteSubscriptionService activation;
    @Autowired AssetController assets;
    @Autowired JdbcTemplate jdbc;

    @Test
    void replayingActivationTwiceYieldsOneAssetPerLine() throws Exception {
        QuoteAcceptedEvent event = AssetActivationTestSupport.twoLines(AssetActivationTestSupport.fixture());
        String quoteId = "it-asset-" + UUID.randomUUID();
        event.setQuoteId(quoteId);
        event.getQuote().setQuoteId(quoteId);

        activation.apply(event);
        activation.apply(event);

        assertThat(AssetController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/assets");
        ApiResponse<List<AssetDto>> listed = assets.list(event.getCustomerId(), null, null);
        assertThat(listed.getData()).hasSize(2);
        assertThat(listed.getData()).allSatisfy(asset -> {
            assertThat(asset.getAccountId()).isEqualTo(event.getCustomerId());
            assertThat(asset.getEntitlementId()).isNull();
            assertThat(asset.getSubscriptionId()).isNotNull();
        });
        assertThat(listed.getData()).extracting(AssetDto::getContractLineId)
                .containsExactlyInAnyOrder(
                        "11111111-1111-4111-8111-000000000201",
                        "11111111-1111-4111-8111-000000000202");
        Long subscriptionId = listed.getData().getFirst().getSubscriptionId();
        assertThat(count("select count(*) from assets where subscription_id = ?", subscriptionId)).isEqualTo(2L);
        assertThat(count("select count(*) from outbox_events where topic = ?", KafkaTopics.ASSET_CREATED)).isEqualTo(2L);
        assertThat(count("select count(*) from outbox_events where topic = ?", KafkaTopics.ASSET_AUDIT)).isEqualTo(2L);
        String payload = jdbc.queryForObject(
                "select payload from outbox_events where topic = ? and payload like ? limit 1",
                String.class, KafkaTopics.ASSET_CREATED, "%\"accountId\":\"" + event.getCustomerId() + "\"%");
        assertThat(payload).contains("\"entitlementId\":null");
    }

    private long count(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }
}
