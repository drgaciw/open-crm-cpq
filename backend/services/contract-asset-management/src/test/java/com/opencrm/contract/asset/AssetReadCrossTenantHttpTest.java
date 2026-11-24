package com.opencrm.contract.asset;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT caller against every installed-base GET. Cross-tenant item bodies match a missing id
 * except the response timestamp. Same-tenant parameters only narrow.
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
class AssetReadCrossTenantHttpTest {
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
    private MockMvc mvc;

    @BeforeEach
    void seed() {
        world = new AssetReadTenantFixture();
        world.seed(subscriptions, assets, history);
        mvc = MockMvcBuilders.standaloneSetup(assetApi, queryApi, reportApi)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crossTenantItemBodyMatchesMissingId() throws Exception {
        AssetReadTenantFixture.signIn(AssetReadTenantFixture.TID_B);
        Long hiddenId = world.assetIds.get(AssetReadTenantFixture.PREDECESSOR);
        assertSameNotFound(
                mvc.perform(get("/api/v1/assets/" + hiddenId)).andExpect(status().isNotFound()).andReturn(),
                mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.MISSING_ID))
                        .andExpect(status().isNotFound()).andReturn(),
                String.valueOf(hiddenId),
                String.valueOf(AssetReadTenantFixture.MISSING_ID));
        assertSameNotFound(
                mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.PREDECESSOR + "/history"))
                        .andExpect(status().isNotFound()).andReturn(),
                mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.MISSING_UUID + "/history"))
                        .andExpect(status().isNotFound()).andReturn(),
                AssetReadTenantFixture.PREDECESSOR,
                AssetReadTenantFixture.MISSING_UUID);
        assertSameNotFound(
                mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.PREDECESSOR + "/supersede-chain"))
                        .andExpect(status().isNotFound()).andReturn(),
                mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.MISSING_UUID + "/supersede-chain"))
                        .andExpect(status().isNotFound()).andReturn(),
                AssetReadTenantFixture.PREDECESSOR,
                AssetReadTenantFixture.MISSING_UUID);
        String list = body(mvc.perform(get("/api/v1/assets")).andExpect(status().isOk()).andReturn());
        assertThat(list).doesNotContain(AssetReadTenantFixture.PREDECESSOR).contains(AssetReadTenantFixture.OTHER_TENANT);
        assertThat(body(mvc.perform(get("/api/v1/assets")
                        .param("accountId", AssetReadTenantFixture.ACCOUNT_A1)
                        .param("asOf", "2026-06-01"))
                .andExpect(status().isOk()).andReturn())).doesNotContain(AssetReadTenantFixture.PREDECESSOR);
        assertThat(body(mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", AssetReadTenantFixture.ACCOUNT_A1)
                        .param("windowStart", "2026-06-01").param("windowEnd", "2026-06-30"))
                .andExpect(status().isOk()).andReturn())).contains("\"assets\":[]");
        assertThat(body(mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", AssetReadTenantFixture.ACCOUNT_A1)
                        .param("periodStart", "2026-04-01").param("periodEnd", "2026-06-30"))
                .andExpect(status().isOk()).andReturn())).contains("\"events\":[]");
    }

    @Test
    void sameTenantParametersOnlyNarrow() throws Exception {
        AssetReadTenantFixture.signIn(AssetReadTenantFixture.TID_A);
        String all = body(mvc.perform(get("/api/v1/assets")
                        .param("tenantId", AssetReadTenantFixture.TID_B.toString())
                        .header("X-Tenant-Id", AssetReadTenantFixture.TID_B.toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(all).contains(AssetReadTenantFixture.PREDECESSOR, AssetReadTenantFixture.SUCCESSOR)
                .doesNotContain(AssetReadTenantFixture.OTHER_TENANT, AssetReadTenantFixture.NULL_TENANT);
        String narrowed = body(mvc.perform(get("/api/v1/assets")
                        .param("accountId", AssetReadTenantFixture.ACCOUNT_A2))
                .andExpect(status().isOk()).andReturn());
        assertThat(narrowed).contains(AssetReadTenantFixture.SUCCESSOR)
                .doesNotContain(AssetReadTenantFixture.PREDECESSOR);
        assertThat(body(mvc.perform(get("/api/v1/assets")
                        .param("subscriptionId", String.valueOf(world.subscriptionIds.get(AssetReadTenantFixture.OTHER_TENANT))))
                .andExpect(status().isOk()).andReturn())).doesNotContain(AssetReadTenantFixture.OTHER_TENANT);
        String chain = body(mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.PREDECESSOR + "/supersede-chain"))
                .andExpect(status().isOk()).andReturn());
        assertThat(chain).contains(AssetReadTenantFixture.SUCCESSOR).doesNotContain(AssetReadTenantFixture.OTHER_TENANT);
        mvc.perform(get("/api/v1/assets/" + world.assetIds.get(AssetReadTenantFixture.RENEWAL)))
                .andExpect(status().isOk());
    }

    @Test
    void nullAndNonGuidCallersFailClosedAndMissingRoleIsForbidden() throws Exception {
        AssetReadTenantFixture.signInRawTid(null);
        mvc.perform(get("/api/v1/assets")).andExpect(status().isOk());
        assertThat(body(mvc.perform(get("/api/v1/assets")).andReturn())).doesNotContain(AssetReadTenantFixture.RENEWAL);
        mvc.perform(get("/api/v1/assets/" + world.assetIds.get(AssetReadTenantFixture.RENEWAL)))
                .andExpect(status().isNotFound());
        AssetReadTenantFixture.signInRawTid("not-a-guid");
        mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.RENEWAL + "/history"))
                .andExpect(status().isNotFound());
        AssetReadTenantFixture.signInWithoutRole(AssetReadTenantFixture.TID_A);
        mvc.perform(get("/api/v1/assets")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/" + world.assetIds.get(AssetReadTenantFixture.RENEWAL)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/" + AssetReadTenantFixture.RENEWAL + "/history"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets").param("accountId", AssetReadTenantFixture.ACCOUNT_A1).param("asOf", "2026-06-01"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", AssetReadTenantFixture.ACCOUNT_A1)
                        .param("windowStart", "2026-06-01").param("windowEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", AssetReadTenantFixture.ACCOUNT_A1)
                        .param("periodStart", "2026-04-01").param("periodEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    private static void assertSameNotFound(MvcResult hidden, MvcResult missing, String hiddenId, String missingId) {
        assertThat(AssetReadTenantFixture.normalizeNotFound(hidden.getResponse().getContentAsByteArray(), hiddenId))
                .isEqualTo(AssetReadTenantFixture.normalizeNotFound(missing.getResponse().getContentAsByteArray(), missingId));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
