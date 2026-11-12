package com.opencrm.crm.fact;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class FactLedgerConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock factLedgerClock() {
        return Clock.systemUTC();
    }
}
