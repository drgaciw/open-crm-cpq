package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Created, amended, superseded, then terminated is the asset's full history,
 * with effective dates and the source amendment and apply unit.
 */
class AssetHistoryProjectionTest {
    @BeforeEach void authorizeRead() { AssetReadTestCaller.signIn(); }
    @AfterEach void clearReadAuthorization() { AssetReadTestCaller.clear(); }

    private static final String ASSET = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String SUCCESSOR = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
    private static final String ACCOUNT = "cust-100";

    @Test
    void createdAmendedSupersededTerminatedHistoryCarriesEffectiveDatesAndSource() throws Exception {
        AssetHistoryProjection projection = new AssetHistoryProjection(new InMemoryAssetHistoryStore());
        AssetQueryController api = new AssetQueryController(projection);

        assertThat(AssetQueryController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/assets");
        assertThat(AssetQueryController.class.getMethod("history", String.class)
                .getAnnotation(GetMapping.class).value()).containsExactly("/{assetId}/history");

        projection.apply(created(), "evt-created");
        projection.apply(new AssetAmendedEvent(
                ASSET, ACCOUNT, null, null, null, null, new BigDecimal("5"), null,
                null, null, LocalDate.parse("2026-04-01"), "amd-1", "1"), "evt-amended");
        projection.apply(new AssetSupersededEvent(
                ASSET, ACCOUNT, SUCCESSOR, ASSET, null, LocalDate.parse("2026-07-01"),
                "amd-2", "2"), "evt-superseded");
        projection.apply(new AssetTerminatedEvent(
                ASSET, ACCOUNT, null, null, null, null, LocalDate.parse("2026-09-01"),
                "amd-3", "1"), "evt-terminated");

        List<AssetHistoryEntryDto> history = api.history(ASSET).getData();

        assertThat(history).extracting(AssetHistoryEntryDto::getEventType)
                .containsExactly("CREATED", "AMENDED", "SUPERSEDED", "TERMINATED");
        assertThat(history).extracting(AssetHistoryEntryDto::getEffectiveDate)
                .containsExactly(
                        LocalDate.parse("2026-01-15"),
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-09-01"));
        assertThat(history).extracting(
                        AssetHistoryEntryDto::getSourceAmendmentId, AssetHistoryEntryDto::getSourceApplyUnit)
                .containsExactly(
                        tuple(null, null),
                        tuple("amd-1", "1"),
                        tuple("amd-2", "2"),
                        tuple("amd-3", "1"));
        assertThat(history.get(2).getSuccessorAssetId()).isEqualTo(SUCCESSOR);
        assertThat(history).allSatisfy(entry -> {
            assertThat(entry.getAssetId()).isEqualTo(ASSET);
            assertThat(entry.getAccountId()).isEqualTo(ACCOUNT);
        });
    }

    private static AssetCreatedEvent created() {
        return new AssetCreatedEvent(
                ASSET, ACCOUNT, "line-1", 9L, null, "prod-1", new BigDecimal("2"),
                "ACTIVE", LocalDate.parse("2026-01-15"), LocalDate.parse("2027-01-14"));
    }
}
