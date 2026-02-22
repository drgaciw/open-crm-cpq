package com.opencrm.crm.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisSubscriberConfig {

    public static final String QUOTE_ACCEPTED_CHANNEL = "quote:accepted";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            QuoteAcceptedEventListener quoteAcceptedEventListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(quoteAcceptedEventListener, new ChannelTopic(QUOTE_ACCEPTED_CHANNEL));
        return container;
    }
}
