package com.opencrm.rules.drools;

import com.opencrm.rules.model.QuoteFact;
import com.opencrm.rules.model.QuoteLineItemFact;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Drools844CompatibilityEvidenceTest {

    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();

    @Test
    void compilesAndExecutesAdaptedSourceStyleDrl() {
        String adaptedDrl = """
                package com.opencrm.pricing.rules.compat;

                import com.opencrm.rules.model.QuoteLineItemFact;
                import com.opencrm.rules.model.QuoteFact;
                import java.math.BigDecimal;

                global java.util.List hits;

                rule "Named Seat Volume Discount (Adapted)"
                salience 620
                when
                    $qli: QuoteLineItemFact(lineItemType == "SAAS_SEAT_NAMED", $qty: quantity, $list: listPrice)
                    $quote: QuoteFact(this == $qli.quote, durationInMonths >= 12)
                then
                    BigDecimal discount = BigDecimal.ZERO;
                    if ($qty >= 1000) discount = new BigDecimal("0.18");
                    else if ($qty >= 500) discount = new BigDecimal("0.12");
                    else if ($qty >= 100) discount = new BigDecimal("0.07");

                    $qli.setDiscountRate(discount);
                    $qli.setFinalPrice($list.multiply(BigDecimal.ONE.subtract(discount)));
                    hits.add("Named Seat Volume Discount (Adapted)");
                end
                """;

        KieBase kieBase = compiler.compileFromDrlString(adaptedDrl);

        QuoteFact quote = QuoteFact.builder()
                .durationInMonths(12)
                .build();

        QuoteLineItemFact lineItem = QuoteLineItemFact.builder()
                .lineItemType("SAAS_SEAT_NAMED")
                .quantity(500)
                .listPrice(new BigDecimal("100.00"))
                .quote(quote)
                .build();

        List<String> hits = new ArrayList<>();
        KieSession session = kieBase.newKieSession();
        try {
            session.setGlobal("hits", hits);
            session.insert(quote);
            session.insert(lineItem);

            int fired = session.fireAllRules();

            assertThat(fired).isEqualTo(1);
            assertThat(hits).containsExactly("Named Seat Volume Discount (Adapted)");
            assertThat(lineItem.getDiscountRate()).isEqualByComparingTo("0.12");
            assertThat(lineItem.getFinalPrice()).isEqualByComparingTo("88.00");
        } finally {
            session.dispose();
        }
    }

    @Test
    void sourceVerbatimApprovalMarginExceptionRuleCompilesAndExecutes() throws IOException {
        Path rulePath = Path.of("..", "..", "services", "pricing-service", "src", "main", "resources", "rules",
                "approval-margin-exception.drl").normalize();
        assertThat(Files.exists(rulePath))
                .as("Expected source DRL sample at %s", rulePath.toAbsolutePath())
                .isTrue();

        String verbatimDrl = Files.readString(rulePath);
        KieBase kieBase = compiler.compileFromDrlString(verbatimDrl);

        QuoteFact quote = QuoteFact.builder()
                .basePrice(new BigDecimal("1000.00"))
                .finalPrice(new BigDecimal("900.00"))
                .build();
        quote.setDecimal("estimatedMarginPct", new BigDecimal("0.30"));

        KieSession session = kieBase.newKieSession();
        try {
            session.insert(quote);

            int fired = session.fireAllRules();

            assertThat(fired).isEqualTo(1);
            assertThat(quote.getString("approvalState")).isEqualTo("PENDING");
            assertThat(quote.getString("approvalTier")).isEqualTo("DEAL_DESK");
            assertThat(quote.getString("explanationCode")).isEqualTo("MARGIN_BELOW_FLOOR");
        } finally {
            session.dispose();
        }
    }
}


