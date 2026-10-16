package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Tenant is required before any SQL runs. */
class ContractHeaderBackfillTest {

    @Test
    void missingTenantDoesNotTouchTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContractHeaderBackfill backfill = new ContractHeaderBackfill(jdbc);

        assertThatThrownBy(() -> backfill.countEligible(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ContractHeaderBackfill.TENANT_REQUIRED);
        assertThatThrownBy(() -> backfill.apply(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ContractHeaderBackfill.TENANT_REQUIRED);
        assertThatThrownBy(() -> backfill.ensureHeaderAtCreation(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ContractHeaderBackfill.TENANT_REQUIRED);
        verifyNoInteractions(jdbc);
    }
}
