package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencrm.common.event.EventContractRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Versioned published-language event emitted when a valid configuration is committed.
 * Topic: cpq.configuration.committed
 * Publisher: configuration-engine
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationCommittedEvent {

    public static final String EVENT_TYPE = EventContractRegistry.CONFIGURATION_COMMITTED_V1.eventType();
    public static final int EVENT_VERSION = EventContractRegistry.CONFIGURATION_COMMITTED_V1.eventVersion();

    private String eventType = EVENT_TYPE;
    private int eventVersion = EVENT_VERSION;
    private String configurationId;
    private String committedVersion;
    private String productId;
    private String userId;
    private Instant committedAt;
    private Map<String, String> selections;
    private CorrelationSnapshot correlation;

    public static ConfigurationCommittedEvent v1(
            String configurationId,
            String committedVersion,
            String productId,
            String userId,
            Instant committedAt,
            Map<String, String> selections,
            CorrelationSnapshot correlation
    ) {
        ConfigurationCommittedEvent event = new ConfigurationCommittedEvent();
        event.setConfigurationId(configurationId);
        event.setCommittedVersion(committedVersion);
        event.setProductId(productId);
        event.setUserId(userId);
        event.setCommittedAt(committedAt);
        event.setSelections(selections);
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
