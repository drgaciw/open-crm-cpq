package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencrm.common.event.EventContractRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Versioned published-language event emitted when a configuration session is started (DRG-424 /
 * BC-02-01 StartSession).
 * Topic: cpq.configuration.session.started
 * Publisher: configuration-engine
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationSessionStartedEvent {

    public static final String EVENT_TYPE = EventContractRegistry.CONFIGURATION_SESSION_STARTED_V1.eventType();
    public static final int EVENT_VERSION = EventContractRegistry.CONFIGURATION_SESSION_STARTED_V1.eventVersion();

    private String eventType = EVENT_TYPE;
    private int eventVersion = EVENT_VERSION;
    private String sessionId;
    /** Set when the session was started against a product; mutually exclusive with {@code bundleId}. */
    private String productId;
    /** Set when the session was started against a bundle; mutually exclusive with {@code productId}. */
    private String bundleId;
    /** Caller-supplied catalog version the session is pinned against, if any. */
    private String catalogVersion;
    /** Compiled configuration schema version in effect when the session started. */
    private String ruleSetVersion;
    /** Entra {@code tid} of the caller who started the session, if any. */
    private String tenantId;
    private String userId;
    private Instant startedAt;
    private CorrelationSnapshot correlation;

    public static ConfigurationSessionStartedEvent v1(
            String sessionId,
            String productId,
            String bundleId,
            String catalogVersion,
            String ruleSetVersion,
            String tenantId,
            String userId,
            Instant startedAt,
            CorrelationSnapshot correlation
    ) {
        ConfigurationSessionStartedEvent event = new ConfigurationSessionStartedEvent();
        event.setSessionId(sessionId);
        event.setProductId(productId);
        event.setBundleId(bundleId);
        event.setCatalogVersion(catalogVersion);
        event.setRuleSetVersion(ruleSetVersion);
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setStartedAt(startedAt);
        event.setCorrelation(correlation);
        return event;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CorrelationSnapshot {
        private String correlationId;
        private String causationId;
        private String sourceService;
    }
}
