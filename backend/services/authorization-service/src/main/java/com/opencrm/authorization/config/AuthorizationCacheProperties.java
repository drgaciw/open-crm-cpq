package com.opencrm.authorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code opencrm.authorization.cache.*}: the Caffeine cache in front of {@code Authorize} (DRG-307: evaluated in
 * under 10ms from cache).
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "opencrm.authorization.cache")
public class AuthorizationCacheProperties {

    /** How long a principal's effective-permissions entry is kept before it is reloaded from the database. */
    private Duration expireAfterWrite = Duration.ofMinutes(5);

    /** Upper bound on distinct principals cached at once. */
    private long maximumSize = 50_000;
}
