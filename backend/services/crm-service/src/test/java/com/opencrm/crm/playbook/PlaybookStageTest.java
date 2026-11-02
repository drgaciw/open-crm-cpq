package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.crm.deal.DealStage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlaybookStageTest {

    @Test
    void storedKeysMatchDealStageNamesInOrder() {
        String[] dealStageNames = Arrays.stream(DealStage.values()).map(Enum::name).toArray(String[]::new);
        String[] playbookKeys = Arrays.stream(PlaybookStage.values()).map(PlaybookStage::storedKey).toArray(String[]::new);

        assertThat(playbookKeys).containsExactly(dealStageNames);
    }

    @Test
    void eachStoredKeyRoundTripsAndIsTheEnumName() {
        for (PlaybookStage stage : PlaybookStage.values()) {
            assertThat(stage.storedKey()).isEqualTo(stage.name());
            assertThat(PlaybookStage.fromKey(stage.storedKey())).isSameAs(stage);
        }
    }

    @Test
    void rejectsBlankAndUnknownKeys() {
        assertThat(catchThrowableOfType(BusinessException.class, () -> PlaybookStage.fromKey(null)).getErrorCode())
                .isEqualTo("PLAYBOOK_STAGE_REQUIRED");
        assertThat(catchThrowableOfType(BusinessException.class, () -> PlaybookStage.fromKey(" ")).getErrorCode())
                .isEqualTo("PLAYBOOK_STAGE_REQUIRED");
        assertThat(catchThrowableOfType(BusinessException.class, () -> PlaybookStage.fromKey("prospecting")).getErrorCode())
                .isEqualTo("PLAYBOOK_STAGE_UNKNOWN");
        assertThat(catchThrowableOfType(BusinessException.class, () -> PlaybookStage.fromKey("CLOSED")).getErrorCode())
                .isEqualTo("PLAYBOOK_STAGE_UNKNOWN");
    }

    @Test
    void migrationCheckConstraintListsEveryStoredKey() throws IOException {
        try (var input = PlaybookStage.class.getResourceAsStream("/db/migration/V14__playbooks.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (PlaybookStage stage : PlaybookStage.values()) {
                assertThat(sql).contains("'" + stage.storedKey() + "'");
            }
        }
    }
}
