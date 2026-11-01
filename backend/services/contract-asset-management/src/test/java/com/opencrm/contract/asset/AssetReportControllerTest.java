package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.contract.asset.fixtures.AssetSupersededFixture;
import com.opencrm.contract.asset.fixtures.AssetTerminatedFixture;
import com.opencrm.contract.config.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetReportControllerTest {

    private static final String ACCOUNT = "cust-http";
    private static final String ASSET = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String GONE = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
    private static final String SWAPPED = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3";
    private static final String NEXT = "dddddddd-dddd-4ddd-8ddd-ddddddddddd4";

    private InMemoryAssetHistoryStore store;
    private AssetHistoryProjection projection;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AssetReadTestCaller.signIn();
        store = new InMemoryAssetHistoryStore();
        projection = new AssetHistoryProjection(store);
        AssetReportController reports = new AssetReportController(new AssetReportService(projection, store));
        mvc = MockMvcBuilders.standaloneSetup(reports)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @AfterEach
    void clearAuthorization() {
        AssetReadTestCaller.clear();
    }

    @Test
    void reportRoutesReturnRenewalsAndChurnWithoutCountingASupersede() throws Exception {
        assertThat(AssetReportController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/assets/reports");

        projection.apply(created(ASSET, "10", "2026-06-15"), "evt-a");
        projection.apply(created(GONE, "4", "2026-12-31"), "evt-b");
        projection.apply(AssetTerminatedFixture.of(
                "evt-t", GONE, ACCOUNT, LocalDate.parse("2026-05-01"), "amd-t", "1"));
        projection.apply(created(SWAPPED, "2", "2026-08-01"), "evt-c");
        projection.apply(created(NEXT, "2", "2026-08-01"), "evt-d");
        projection.apply(AssetSupersededFixture.of(
                "evt-s", SWAPPED, ACCOUNT, LocalDate.parse("2026-05-20"), "amd-s", "1", NEXT));

        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", ACCOUNT)
                        .param("windowStart", "2026-06-01")
                        .param("windowEnd", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetCount").value(1))
                .andExpect(jsonPath("$.data.assets[0].assetId").value(ASSET))
                .andExpect(jsonPath("$.data.assets[0].effectiveEnd").value("2026-06-15"))
                .andExpect(jsonPath("$.data.assets[0].quantity").value(10));

        mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", ACCOUNT)
                        .param("periodStart", "2026-04-01")
                        .param("periodEnd", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetCount").value(1))
                .andExpect(jsonPath("$.data.eventCount").value(1))
                .andExpect(jsonPath("$.data.quantityChurned").value(4))
                .andExpect(jsonPath("$.data.events[0].assetId").value(GONE))
                .andExpect(jsonPath("$.data.events[0].churnKind").value("TERMINATED"));
    }

    @Test
    void withinDaysAndInvalidWindowsAreMapped() throws Exception {
        projection.apply(created(ASSET, "1", "2026-06-10"), "evt-a");

        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", ACCOUNT)
                        .param("withinDays", "9")
                        .param("asOf", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowStart").value("2026-06-01"))
                .andExpect(jsonPath("$.data.windowEnd").value("2026-06-10"))
                .andExpect(jsonPath("$.data.assetCount").value(1));

        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", ACCOUNT)
                        .param("windowStart", "2026-06-30")
                        .param("windowEnd", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_REJECTED"));

        mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", ACCOUNT)
                        .param("periodStart", "2026-07-01")
                        .param("periodEnd", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_REJECTED"));
    }

    @Test
    void asOfInstalledBaseStaysOnTheExistingQuery() throws Exception {
        AssetQueryService assets = mock(AssetQueryService.class);
        when(assets.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        projection.apply(created(ASSET, "1", "2026-06-15"), "evt-a");
        MockMvc both = MockMvcBuilders.standaloneSetup(
                        new AssetController(assets),
                        new AssetQueryController(projection),
                        new AssetReportController(new AssetReportService(projection, store)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        both.perform(get("/api/v1/assets").param("accountId", ACCOUNT).param("asOf", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetId").value(ASSET));
        verify(assets, never()).list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        both.perform(get("/api/v1/assets").param("accountId", ACCOUNT))
                .andExpect(status().isOk());
        verify(assets).list(ACCOUNT, null, null);
    }

    private static AssetCreatedEvent created(String assetId, String quantity, String end) {
        return new AssetCreatedEvent(assetId, ACCOUNT, "line-1", 1L, null, "prod",
                new BigDecimal(quantity), "ACTIVE", LocalDate.parse("2026-01-01"), LocalDate.parse(end));
    }

}
