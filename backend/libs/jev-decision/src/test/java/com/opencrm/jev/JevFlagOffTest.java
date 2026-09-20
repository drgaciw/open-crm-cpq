package com.opencrm.jev;

import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.transport.HttpJevTransport;
import com.opencrm.jev.transport.JevTransport;
import com.opencrm.jev.transport.JevWireFormat;
import com.opencrm.jev.transport.NoOpJevTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flag-off means no behaviour change (ADR-015)")
class JevFlagOffTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JevAutoConfiguration.class));

    private static final String[] VALID_HTTP = {
            "opencrm.jev.enabled=true",
            "opencrm.jev.transport=http",
            "opencrm.jev.api-key=secret",
            "opencrm.jev.base-url=https://jev.example.test/v1/decisions",
            "opencrm.jev.model=typesafe/jev-1.13"
    };

    @Test
    @DisplayName("no properties: no-op transport, no HTTP transport, no recording")
    void inertByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JevClient.class).hasSingleBean(JevAdvisoryRunner.class);
            assertThat(context.getBean(JevTransport.class)).isInstanceOf(NoOpJevTransport.class);
            assertThat(context).doesNotHaveBean(HttpJevTransport.class);
            assertThat(context.getBean(JevDecisionLogStore.class)).isSameAs(JevDecisionLogStore.NONE);
            assertThat(context.getBean(JevClient.class).active(JevProperties.LEAD_SCORE)).isFalse();
        });
    }

    @Test
    @DisplayName("enabled without transport=http stays no-op")
    void enabledAloneStaysNoOp() {
        runner.withPropertyValues("opencrm.jev.enabled=true", "opencrm.jev.api-key=secret")
                .run(context -> assertThat(context.getBean(JevTransport.class))
                        .isInstanceOf(NoOpJevTransport.class));
    }

    @Test
    @DisplayName("a complete, safe configuration builds the HTTP transport")
    void completeConfiguration() {
        runner.withPropertyValues(VALID_HTTP)
                .run(context -> assertThat(context.getBean(JevTransport.class))
                        .isInstanceOf(HttpJevTransport.class));
    }

    @Test
    @DisplayName("a use case stays inactive until its own flag is on")
    void useCaseFlagIsIndependent() {
        runner.withPropertyValues(VALID_HTTP).run(context ->
                assertThat(context.getBean(JevClient.class).active(JevProperties.LEAD_SCORE)).isFalse());
        runner.withPropertyValues(VALID_HTTP).withPropertyValues("opencrm.jev.lead-score.enabled=true")
                .run(context ->
                        assertThat(context.getBean(JevClient.class).active(JevProperties.LEAD_SCORE)).isTrue());
    }

    @Test
    @DisplayName("unsafe or incomplete configuration fails closed, never fails startup")
    void failsClosed() {
        assertThat(problem(p -> p.setApiKey(""))).contains("api-key");
        assertThat(problem(p -> p.setBaseUrl(""))).contains("base-url");
        assertThat(problem(p -> p.setBaseUrl("http://jev.example.test/x"))).contains("https");
        assertThat(problem(p -> p.setModel(""))).contains("model");
        assertThat(problem(p -> p.setModel("jev-latest"))).contains("pinned");
        assertThat(problem(p -> p.setBaseUrl("http://localhost:8787/"))).isNull();
        assertThat(problem(p -> {
            p.setModel("");
            p.setWireFormat(JevWireFormat.FACADE);
        })).isNull();

        runner.withPropertyValues(VALID_HTTP).withPropertyValues("opencrm.jev.model=jev-latest")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JevTransport.class)).isInstanceOf(NoOpJevTransport.class);
                });
    }

    private static String problem(java.util.function.Consumer<JevProperties> change) {
        JevProperties properties = new JevProperties();
        properties.setEnabled(true);
        properties.setTransport("http");
        properties.setApiKey("secret");
        properties.setBaseUrl("https://jev.example.test/v1/decisions");
        properties.setModel("typesafe/jev-1.13");
        change.accept(properties);
        return JevTransportFactory.problemWith(properties);
    }
}
