package com.opencrm.pricing.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review finding m2: the margin replay search is a bounded, ordered repository query. Spring Data derives the
 * query from the method name, so parsing that name the way Spring Data does proves the limit, the order and the
 * cost-basis filter without a database.
 */
@Tag("unit")
@DisplayName("m2: margin replay reads at most the 20 newest cost-basis audit rows")
class MarginReplayQueryBoundTest {

    private static final String QUERY = "findTop20ByQuoteExternalIdAndWaterfallTraceIsNotNullOrderByExecutedAtDescIdDesc";

    @Test
    @DisplayName("the derived query is limited to 20, newest first, and skips rows without a cost-basis trace")
    void derivedQueryIsBounded() throws Exception {
        Method method = PricingAuditLogRepository.class.getMethod(QUERY, String.class);
        assertThat(method.getReturnType()).isEqualTo(List.class);

        PartTree tree = new PartTree(method.getName(), PricingAuditLog.class);

        assertThat(tree.isLimiting()).isTrue();
        assertThat(tree.getMaxResults()).isEqualTo(PricingExecutionService.MARGIN_REPLAY_WINDOW).isEqualTo(20);
        assertThat(tree.getSort().getOrderFor("executedAt")).isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
        assertThat(tree.getSort().getOrderFor("id")).isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
        assertThat(tree.getParts()).extracting(part -> part.getProperty().getSegment() + ":" + part.getType())
                .containsExactly("quoteExternalId:SIMPLE_PROPERTY", "waterfallTrace:IS_NOT_NULL");
    }
}
