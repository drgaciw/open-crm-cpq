package com.opencrm.contract.event;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One Docker-free context for every new routing bean. The flag is off when the property is unset or empty.
 */
@Tag("unit")
@DisplayName("amendment routing wiring")
class AmendmentRoutingWiringTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AmendmentRoutingFlag.class);

    @Test
    @DisplayName("AmendmentRoutingFlag loads and stays off when the property is unset or empty")
    void flagIsOffWhenPropertyUnsetOrEmpty() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(AmendmentRoutingFlag.class);
            assertOff(loaded.getBean(AmendmentRoutingFlag.class));
        });
        context.withPropertyValues(AmendmentRoutingFlag.PROPERTY + "=").run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(AmendmentRoutingFlag.class);
            assertOff(loaded.getBean(AmendmentRoutingFlag.class));
        });
    }

    private static void assertOff(AmendmentRoutingFlag flag) {
        assertThat(flag.documentPathTenants()).isEmpty();
        assertThat(flag.isDocumentPathEnabledFor(null)).isFalse();
        assertThat(flag.isDocumentPathEnabledFor("")).isFalse();
        assertThat(flag.isDocumentPathEnabledFor("tenant-a")).isFalse();
        QuoteAcceptedEvent event = new QuoteAcceptedEvent();
        event.setAmendmentDocumentId("11111111-1111-4111-8111-0000000000a1");
        assertThat(flag.decide(event)).isEqualTo(AmendmentRoutingDecision.LEGACY);
        assertThat(AmendmentRoutingDecision.values()).containsExactly(AmendmentRoutingDecision.LEGACY);
    }
}
