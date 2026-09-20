package com.opencrm.ai.selection.execution;

import com.opencrm.ai.llm.LlmAdapter;
import com.opencrm.ai.llm.SpringAiLlmAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class OpenRouterExecutionConfigurationTest {
    @TempDir Path directory;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OpenRouterExecutionConfiguration.class, SpringAiLlmAdapter.class, AzureImport.class);

    @Configuration
    @ImportAutoConfiguration({AzureOpenAiAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class})
    static class AzureImport { }

    @Test void defaultAzurePathBootstrapsWithoutOpenRouterSecretsOnCurrentBoot() {
        runner.withPropertyValues("spring.ai.azure.openai.api-key=synthetic-azure-key",
                "spring.ai.azure.openai.endpoint=https://synthetic.openai.azure.com",
                "spring.ai.azure.openai.embedding.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LlmAdapter.class).hasSingleBean(SpringAiLlmAdapter.class);
            assertThat(context).doesNotHaveBean(OpenRouterLlmAdapter.class);
        });
    }

    @Test void optInPathBootstrapsWithoutAzureCredentialWhenAzureAutoConfigurationExcluded() {
        runner.withPropertyValues("opencrm.ai.selection.execution-enabled=true",
                "spring.autoconfigure.exclude=" + AzureOpenAiAutoConfiguration.class.getName(),
                "opencrm.ai.selection.profile-file=" + directory.resolve("profile.json"),
                "opencrm.ai.selection.catalog-file=" + directory.resolve("catalog.json"),
                "opencrm.ai.selection.policy-directory=" + directory.resolve("policies"),
                "opencrm.ai.selection.audit-file=" + directory.resolve("audit.jsonl"),
                "opencrm.ai.selection.api-key=synthetic-openrouter-key").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LlmAdapter.class).hasSingleBean(OpenRouterLlmAdapter.class);
            assertThat(context).doesNotHaveBean(SpringAiLlmAdapter.class);
        });
    }

    @Test void optInWithoutConfigurationFailsClosed() {
        runner.withPropertyValues("opencrm.ai.selection.execution-enabled=true",
                "spring.autoconfigure.exclude=" + AzureOpenAiAutoConfiguration.class.getName()).run(context ->
                assertThat(context).hasFailed());
    }
}
