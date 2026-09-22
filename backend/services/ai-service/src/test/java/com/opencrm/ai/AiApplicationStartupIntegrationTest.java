package com.opencrm.ai;

import com.opencrm.ai.llm.LlmAdapter;
import com.opencrm.ai.llm.SpringAiLlmAdapter;
import com.opencrm.ai.registry.CpqAgentToolClients;
import com.opencrm.ai.selection.execution.OpenRouterLlmAdapter;
import com.opencrm.common.event.OutboxEventPublisher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole production application against isolated infrastructure, without inference. */
@Tag("integration")
@Testcontainers
class AiApplicationStartupIntegrationTest {
    @TempDir Path evidenceDirectory;
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Test void azureBaselineBootsWithRealFlywayHibernateRedisAndToolClientWiring() throws Exception {
        try (var context = SpringApplication.run(AiServiceApplication.class,
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--spring.ai.azure.openai.api-key=synthetic-no-inference",
                "--spring.ai.azure.openai.endpoint=https://synthetic.invalid",
                "--spring.security.user.name=smoke", "--spring.security.user.password=synthetic-smoke-only",
                "--spring.main.banner-mode=off")) {
            assertThat(context.getBean(LlmAdapter.class)).isInstanceOf(SpringAiLlmAdapter.class);
            assertThat(context.getBean(CpqAgentToolClients.class)).isNotNull();
            assertThat(context.getBeansOfType(OutboxEventPublisher.class)).isEmpty();
            String port = context.getEnvironment().getRequiredProperty("local.server.port");
            try (HttpClient client = HttpClient.newHttpClient()) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("\"status\":\"UP\"");
            }
        }
    }

    @Test void optInBootsWholeApplicationWithoutAzureCredentialsOrInference() throws Exception {
        try (var context = SpringApplication.run(AiServiceApplication.class,
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--spring.autoconfigure.exclude=com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration",
                "--opencrm.ai.selection.execution-enabled=true",
                "--opencrm.ai.selection.profile-file=" + evidenceDirectory.resolve("profile.json"),
                "--opencrm.ai.selection.catalog-file=" + evidenceDirectory.resolve("catalog.json"),
                "--opencrm.ai.selection.policy-directory=" + evidenceDirectory.resolve("policies"),
                "--opencrm.ai.selection.audit-file=" + evidenceDirectory.resolve("audit.jsonl"),
                "--opencrm.ai.selection.api-key=synthetic-no-inference",
                "--spring.security.user.name=smoke", "--spring.security.user.password=synthetic-smoke-only",
                "--spring.main.banner-mode=off")) {
            assertThat(context.getBean(LlmAdapter.class)).isInstanceOf(OpenRouterLlmAdapter.class);
            assertThat(context.getBean(ChatClient.class).getClass().getSimpleName()).isEqualTo("UnavailableLegacyChatClient");
            assertThat(context.getBean(StreamingChatClient.class)).isSameAs(context.getBean(ChatClient.class));
            assertThat(context.getBean(CpqAgentToolClients.class)).isNotNull();
            assertThat(context.getBeansOfType(OutboxEventPublisher.class)).isEmpty();
            String port = context.getEnvironment().getRequiredProperty("local.server.port");
            try (HttpClient client = HttpClient.newHttpClient()) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("\"status\":\"UP\"");
            }
        }
    }
}
