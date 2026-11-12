package com.opencrm.authorization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DRG-307 / BC-19-03. Role and permission model: {@code AssignRole}, {@code GrantPermission} and the
 * {@code Authorize(principal, action, resource)} query every bounded context can call to ask, in one place, whether
 * an action is permitted. See {@code com.opencrm.authorization.service.AuthorizationQueryService}.
 */
@SpringBootApplication(scanBasePackages = {"com.opencrm.authorization", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.authorization", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.authorization", "com.opencrm.common"})
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
@EnableCaching
public class AuthorizationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServiceApplication.class, args);
    }
}
