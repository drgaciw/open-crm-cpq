package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.agent.CpqAgent;
import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.commerce.MarginDiscountRouterAgent;
import com.opencrm.ai.audit.AgentAuditLogger;
import com.opencrm.ai.llm.LlmAdapter;
import com.opencrm.ai.llm.SpringAiLlmAdapter;
import com.opencrm.ai.profile.SellingProfileContextProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
class OpenRouterAgentContextTest {
    @TempDir Path directory;

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.opencrm.ai.agent")
    static class AllProductionAgents { }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(AllProductionAgents.class, OpenRouterExecutionConfiguration.class, SpringAiLlmAdapter.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AgentAuditLogger.class, () -> mock(AgentAuditLogger.class))
                .withBean(SellingProfileContextProvider.class, () -> mock(SellingProfileContextProvider.class))
                .withPropertyValues("opencrm.ai.selection.execution-enabled=true",
                        "opencrm.ai.selection.profile-file=" + directory.resolve("profile.json"),
                        "opencrm.ai.selection.catalog-file=" + directory.resolve("catalog.json"),
                        "opencrm.ai.selection.policy-directory=" + directory.resolve("policies"),
                        "opencrm.ai.selection.audit-file=" + directory.resolve("audit.jsonl"),
                        "opencrm.ai.selection.api-key=synthetic-key");
    }

    @Test void fullAgentPackageBootsWithOpenRouterAndWithoutAzure() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LlmAdapter.class);
            assertThat(context.getBean(LlmAdapter.class)).isInstanceOf(OpenRouterLlmAdapter.class);
            assertThat(context.getBeansOfType(CpqAgent.class)).hasSizeGreaterThan(38);
            var agent = context.getBean(MarginDiscountRouterAgent.class);
            var response = agent.invoke(mock(AgentRequest.class));
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getOutput()).contains(UnavailableLegacyChatClient.REASON);
            assertThat(agent.stream(mock(AgentRequest.class)).collectList().block())
                    .singleElement().asString().contains(UnavailableLegacyChatClient.REASON);
        });
    }

    @Test void optInLegacyRequestsCannotBypassPolicyThroughExistingAzureClients() {
        ChatClient azure = mock(ChatClient.class);
        StreamingChatClient streamingAzure = mock(StreamingChatClient.class);
        runner().withBean("azureClient", ChatClient.class, () -> azure)
                .withBean("azureStreamingClient", StreamingChatClient.class, () -> streamingAzure)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var agent = context.getBean(MarginDiscountRouterAgent.class);
                    assertThat(agent.invoke(mock(AgentRequest.class)).isSuccess()).isFalse();
                    agent.stream(mock(AgentRequest.class)).collectList().block();
                    verifyNoInteractions(azure, streamingAzure);
                });
    }

    @Test void defaultAzureAgentPackageRetainsItsOriginalClients() {
        ChatClient azure = mock(ChatClient.class);
        StreamingChatClient streamingAzure = mock(StreamingChatClient.class);
        runner().withPropertyValues("opencrm.ai.selection.execution-enabled=false")
                .withBean(ChatClient.class, () -> azure)
                .withBean(StreamingChatClient.class, () -> streamingAzure).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpringAiLlmAdapter.class)
                            .doesNotHaveBean(UnavailableLegacyChatClient.class);
                    assertThat(context.getBean(ChatClient.class)).isSameAs(azure);
                    assertThat(context.getBean(StreamingChatClient.class)).isSameAs(streamingAzure);
                });
    }
}
