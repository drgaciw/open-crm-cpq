package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MarginConfigurationTest {

    @Test
    @DisplayName("committed application.yml defaults the margin source to placeholder")
    void committedDefaultIsPlaceholder() throws Exception {
        var sources = new YamlPropertySourceLoader()
                .load("pricing-application", new FileSystemResource("src/main/resources/application.yml"));
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(sources.getFirst());
        MarginProperties bound = Binder.get(environment)
                .bind("opencrm.pricing.margin", MarginProperties.class).orElseGet(MarginProperties::new);
        assertThat(bound.getSource()).isEqualTo(MarginSource.PLACEHOLDER);
        assertThat(new MarginProperties().getSource()).isEqualTo(MarginSource.PLACEHOLDER);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"placeholder", "cost-basis"})
    @DisplayName("starts with no cost adapter present: the empty fallback lookup fails closed")
    void startsWithEmptyFallback(String mode) throws Exception {
        // CostBasisConfiguration is package-private in the root-owned cost package.
        Class<?> fallback = Class.forName("com.opencrm.pricing.cost.CostBasisConfiguration");
        new ApplicationContextRunner()
                .withPropertyValues("opencrm.pricing.margin.source=" + mode)
                .withUserConfiguration(fallback, BindMargin.class, CostBasisMarginEvaluator.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MarginProperties.class).getSource()).isEqualTo(MarginSource.from(mode));
                    assertThat(context.getBean(CostBasisLookup.class).find("PROD-SEAT", LocalDate.now())).isEmpty();
                    assertThat(context).hasSingleBean(CostBasisMarginEvaluator.class);
                });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"placeholder", "PLACEHOLDER", "cost-basis", "COST_BASIS", "cost_basis"})
    @DisplayName("wire values parse leniently and round-trip")
    void wireValues(String value) {
        MarginSource source = MarginSource.from(value);
        assertThat(MarginSource.from(source.wireValue())).isEqualTo(source);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MarginProperties.class)
    static class BindMargin {
    }
}
