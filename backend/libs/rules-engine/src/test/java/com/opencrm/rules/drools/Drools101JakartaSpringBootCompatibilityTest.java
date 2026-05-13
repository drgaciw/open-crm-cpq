package com.opencrm.rules.drools;

import com.opencrm.rules.RulesEngineAutoConfiguration;
import com.opencrm.rules.model.QuoteFact;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Drools101JakartaSpringBootCompatibilityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RulesEngineAutoConfiguration.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void rulesEngineBeansLoadAndExecutePricingDrlWithSpringBootContext() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DroolsRuleCompiler.class);
            assertThat(context).hasSingleBean(DroolsSessionFactory.class);

            Path rulePath = Path.of("..", "..", "services", "pricing-service", "src", "main", "resources", "rules",
                    "approval-margin-exception.drl").normalize();
            assertThat(Files.exists(rulePath))
                    .as("Expected pricing DRL sample at %s", rulePath.toAbsolutePath())
                    .isTrue();

            DroolsRuleCompiler compiler = context.getBean(DroolsRuleCompiler.class);
            DroolsSessionFactory sessionFactory = context.getBean(DroolsSessionFactory.class);
            KieBase kieBase = compiler.compileFromDrlString(Files.readString(rulePath));

            QuoteFact quote = QuoteFact.builder()
                    .basePrice(new BigDecimal("1000.00"))
                    .finalPrice(new BigDecimal("900.00"))
                    .build();
            quote.setDecimal("estimatedMarginPct", new BigDecimal("0.30"));

            KieSession session = sessionFactory.createSession(kieBase);
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
        });
    }
}
