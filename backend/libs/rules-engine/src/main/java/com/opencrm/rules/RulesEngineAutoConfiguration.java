package com.opencrm.rules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the rules engine library.
 *
 * Enables component scanning for all rules engine components.
 * Can be disabled by setting opencrm.rules.enabled=false.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "opencrm.rules", name = "enabled", matchIfMissing = true)
@ComponentScan(basePackages = "com.opencrm.rules")
public class RulesEngineAutoConfiguration {

    public RulesEngineAutoConfiguration() {
        log.info("Initializing Rules Engine Auto-Configuration");
    }
}
