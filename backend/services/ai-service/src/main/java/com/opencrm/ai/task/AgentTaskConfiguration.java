package com.opencrm.ai.task;

import com.opencrm.ai.ledger.AgentLedgerService;
import com.opencrm.ai.ledger.LedgerTaskHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentTaskProperties.class)
public class AgentTaskConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ai.agent-task.dispatcher", name = "enabled", havingValue = "true")
    public AgentTaskDispatcher agentTaskDispatcher(AgentTaskService tasks, AgentTaskProperties properties,
                                                   ObjectProvider<AgentTaskHandler> handlers,
                                                   ObjectProvider<AgentLedgerService> ledger) {
        return new AgentTaskDispatcher(tasks, properties, handlers.orderedStream()
                .map(handler -> (AgentTaskHandler) new LedgerTaskHandler(handler, ledger.getObject())).toList());
    }
}
