package com.opencrm.ai.config;

import com.opencrm.ai.listener.QuoteAcceptedListener;
import com.opencrm.ai.orchestration.WorkflowOrchestrator;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class OrchestrationConfiguration {

    public static final String QUOTE_ACCEPTED_CHANNEL = "quote:accepted";

    @Bean
    @ConditionalOnProperty(prefix = "opencrm.ai.agents", name = "enabled", havingValue = "true")
    public QuoteAcceptedListener quoteAcceptedListener(WorkflowOrchestrator orchestrator) {
        return new QuoteAcceptedListener(orchestrator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "opencrm.ai.agents", name = "enabled", havingValue = "true")
    public MessageListenerAdapter quoteAcceptedListenerAdapter(QuoteAcceptedListener listener) {
        return new MessageListenerAdapter(listener, "handleQuoteAccepted");
    }

    @Bean
    @ConditionalOnProperty(prefix = "opencrm.ai.agents", name = "enabled", havingValue = "true")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter quoteAcceptedListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(quoteAcceptedListenerAdapter, new ChannelTopic(QUOTE_ACCEPTED_CHANNEL));
        return container;
    }
}
