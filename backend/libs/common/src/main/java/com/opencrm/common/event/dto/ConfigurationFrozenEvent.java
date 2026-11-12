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
 * Versioned published-language event emitted when FreezeConfiguration makes a validated configuration
 * immutable (DRG-428 / BC-02-05). Carries the deterministic {@code configurationHash} ({@code ComputeHash})
 * so Pricing and Quote can reference this exact, frozen configuration.
 * Topic: cpq.configuration.frozen
 * Publisher: configuration-engine
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationFrozenEvent {

    public static final String EVENT_TYPE = EventContractRegistry.CONFIGURATION_FROZEN_V1.eventType();
    public static final int EVENT_VERSION = EventContractRegistry.CONFIGURATION_FROZEN_V1.eventVersion();

    private String eventType = EVENT_TYPE;
    private int eventVersion = EVENT_VERSION;
    private String configurationId;
    private String productId;
    /** Catalog version pinned on the session at StartSession, if the caller supplied one (DRG-424). */
    private String catalogVersion;
    /** Compiled configuration schema version pinned on the session (DRG-424). */
    private String ruleSetVersion;
    private Map<String, String> selections;
    /** Deterministic SHA-256 ConfigurationHash over selections, catalogVersion and ruleSetVersion. */
    private String configurationHash;
    private String userId;
    private Instant frozenAt;
    private CorrelationSnapshot correlation;

    public static ConfigurationFrozenEvent v1(
            String configurationId,
            String productId,
            String catalogVersion,
            String ruleSetVersion,
            Map<String, String> selections,
            String configurationHash,
            String userId,
            Instant frozenAt,
            CorrelationSnapshot correlation
    ) {
        ConfigurationFrozenEvent event = new ConfigurationFrozenEvent();
        event.setConfigurationId(configurationId);
        event.setProductId(productId);
        event.setCatalogVersion(catalogVersion);
        event.setRuleSetVersion(ruleSetVersion);
        event.setSelections(selections);
        event.setConfigurationHash(configurationHash);
        event.setUserId(userId);
        event.setFrozenAt(frozenAt);
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
