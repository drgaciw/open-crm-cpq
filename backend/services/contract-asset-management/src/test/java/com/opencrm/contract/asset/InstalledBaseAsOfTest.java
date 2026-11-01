package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.contract.asset.fixtures.AssetSupersededFixture;
import com.opencrm.contract.asset.fixtures.AssetTerminatedFixture;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An account's installed base at a date. A date before activation is empty.
 * A date on or after termination excludes the asset.
 */
class InstalledBaseAsOfTest {

    private static final String ASSET = "dddddddd-dddd-4ddd-8ddd-ddddddddddd4";
    private static final String SUCCESSOR = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5";
    private static final String OTHER = "ffffffff-ffff-4fff-8fff-fffffffffff6";
    private static final String ACCOUNT = "cust-300";
    private static final String OTHER_ACCOUNT = "cust-301";

    @Test
    void asOfBeforeActivationIsEmptyAndAssetIsExcludedAfterTermination() throws Exception {
        AssetHistoryProjection projection = new AssetHistoryProjection(new InMemoryAssetHistoryStore());
        AssetQueryController api = new AssetQueryController(projection);

        GetMapping mapping = AssetQueryController.class
                .getMethod("installedBase", String.class, LocalDate.class)
                .getAnnotation(GetMapping.class);
        assertThat(mapping.params()).contains("asOf");

        projection.apply(created(ASSET, ACCOUNT, "line-9", 3L, "prod-9", "1", "2026-05-01", "2027-04-30"), "evt-c");
        projection.apply(created(OTHER, OTHER_ACCOUNT, "line-8", 4L, "prod-8", "4", "2026-01-01", "2027-01-01"),
                "evt-other");

        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-04-30")).getData()).isEmpty();

        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-05-01")).getData())
                .singleElement()
                .satisfies(asset -> {
                    assertThat(asset.getAssetId()).isEqualTo(ASSET);
                    assertThat(asset.getAccountId()).isEqualTo(ACCOUNT);
                    assertThat(asset.getContractLineId()).isEqualTo("line-9");
                    assertThat(asset.getSubscriptionId()).isEqualTo(3L);
                    assertThat(asset.getProductRef()).isEqualTo("prod-9");
                    assertThat(asset.getQuantity()).isEqualByComparingTo("1");
                    assertThat(asset.getEffectiveStart()).isEqualTo(LocalDate.parse("2026-05-01"));
                    assertThat(asset.getEffectiveEnd()).isEqualTo(LocalDate.parse("2027-04-30"));
                });

        projection.apply(AssetTerminatedFixture.of(
                "evt-t", ASSET, ACCOUNT, LocalDate.parse("2026-08-01"), "amd-t", "1"));

        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-07-31")).getData())
                .extracting(InstalledAssetDto::getAssetId).containsExactly(ASSET);
        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-08-01")).getData()).isEmpty();
        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-08-02")).getData()).isEmpty();
        assertThat(api.installedBase(OTHER_ACCOUNT, LocalDate.parse("2026-06-01")).getData())
                .extracting(InstalledAssetDto::getAssetId).containsExactly(OTHER);
    }

    @Test
    void asOfRouteIsSeparateFromTheAssetListAndIsEmptyBeforeActivation() throws Exception {
        AssetQueryService assets = mock(AssetQueryService.class);
        when(assets.list(any(), any(), any())).thenReturn(List.of());
        AssetHistoryProjection projection = new AssetHistoryProjection(new InMemoryAssetHistoryStore());
        projection.apply(created(ASSET, ACCOUNT, "line-9", 3L, "prod-9", "1", "2026-05-01", "2027-04-30"), "evt-c");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new AssetController(assets), new AssetQueryController(projection))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        mvc.perform(get("/api/v1/assets").param("accountId", ACCOUNT).param("asOf", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(assets, never()).list(any(), any(), any());

        mvc.perform(get("/api/v1/assets").param("accountId", ACCOUNT))
                .andExpect(status().isOk());
        verify(assets).list(ACCOUNT, null, null);
    }

    @Test
    void supersededAssetLeavesTheBaseOnTheSupersedeDate() {
        AssetHistoryProjection projection = new AssetHistoryProjection(new InMemoryAssetHistoryStore());
        AssetQueryController api = new AssetQueryController(projection);

        projection.apply(created(ASSET, ACCOUNT, "line-1", 1L, "prod-old", "2", "2026-01-01", "2026-12-31"), "evt-a");
        projection.apply(created(SUCCESSOR, ACCOUNT, "line-2", 1L, "prod-new", "2", "2026-04-01", "2026-12-31"), "evt-b");
        projection.apply(AssetSupersededFixture.of(
                "evt-s", ASSET, ACCOUNT, LocalDate.parse("2026-04-01"), "amd-s", "1", SUCCESSOR));

        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-03-31")).getData())
                .extracting(InstalledAssetDto::getAssetId).containsExactly(ASSET);
        assertThat(api.installedBase(ACCOUNT, LocalDate.parse("2026-04-01")).getData())
                .extracting(InstalledAssetDto::getAssetId).containsExactly(SUCCESSOR);
    }

    private static AssetCreatedEvent created(String assetId, String accountId, String lineId, long subscriptionId,
                                             String productRef, String quantity, String start, String end) {
        return new AssetCreatedEvent(assetId, accountId, lineId, subscriptionId, null, productRef,
                new BigDecimal(quantity), "ACTIVE", LocalDate.parse(start), LocalDate.parse(end));
    }
}
