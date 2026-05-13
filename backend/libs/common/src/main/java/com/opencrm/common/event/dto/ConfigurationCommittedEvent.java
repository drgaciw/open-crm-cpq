package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Event published when a valid configuration session is committed.
 * Topic: cpq.configuration.committed
 * Publisher: Configuration Engine
 * Subscribers: Pricing Engine, Quote Management
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationCommittedEvent {
    private Long configurationId;
    private Long sessionId;
    private String productId;
    private String userId;
    private Map<String, String> selections;
    private Instant committedAt;
}
