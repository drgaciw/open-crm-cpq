package com.opencrm.crm.guided;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencrm.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HTTP adapter for {@code POST /api/v1/configurations} and its selection endpoint.
 * A blank {@code opencrm.configuration.base-url} records the product and options and does not call out.
 * This adapter never completes the configuration session.
 */
@Slf4j
public class HttpConfigurationSessionStarter implements ConfigurationSessionStarter {

    private final String baseUrl;
    private final RestClient restClient;
    private final List<RecordedSessionIntent> recorded = new CopyOnWriteArrayList<>();

    public HttpConfigurationSessionStarter(String baseUrl, RestClient restClient) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClient;
    }

    @Override
    public StartedConfiguration start(String productId, Map<String, String> selections) {
        if (productId == null || productId.isBlank()) {
            throw new BusinessException("A product id is required to start a configuration session",
                    "CONFIGURATION_PRODUCT_REQUIRED");
        }
        Map<String, String> options = copySelections(selections);
        if (baseUrl.isBlank()) {
            recorded.add(new RecordedSessionIntent(productId, options));
            log.info("Recorded configuration intent locally for product {}", productId);
            return new StartedConfiguration(productId, options, null, true);
        }
        if (restClient == null) {
            throw new IllegalStateException("Configuration client is missing for " + baseUrl);
        }
        Long sessionId = createSession(productId);
        for (Map.Entry<String, String> option : options.entrySet()) {
            addSelection(sessionId, option.getKey(), option.getValue());
        }
        recorded.add(new RecordedSessionIntent(productId, options));
        log.info("Started configuration session {} for product {}", sessionId, productId);
        return new StartedConfiguration(productId, options, sessionId, false);
    }

    public List<RecordedSessionIntent> recordedIntents() {
        return List.copyOf(recorded);
    }

    private Long createSession(String productId) {
        ConfigurationEnvelope envelope = exchange("create configuration session", () -> restClient.post()
                .uri(builder -> builder.path("/api/v1/configurations")
                        .queryParam("productId", productId)
                        .build())
                .retrieve()
                .body(ConfigurationEnvelope.class));
        if (envelope == null || !envelope.isSuccess() || envelope.getData() == null
                || envelope.getData().getId() == null) {
            throw new BusinessException("Configuration session did not return an id",
                    HttpStatus.BAD_GATEWAY, "CONFIGURATION_SESSION_FAILED");
        }
        return envelope.getData().getId();
    }

    private void addSelection(Long sessionId, String optionKey, String optionValue) {
        exchange("add configuration selection", () -> restClient.post()
                .uri(builder -> builder.path("/api/v1/configurations/{id}/selections")
                        .queryParam("optionKey", optionKey)
                        .queryParam("optionValue", optionValue)
                        .build(sessionId))
                .retrieve()
                .body(ConfigurationEnvelope.class));
    }

    private static ConfigurationEnvelope exchange(String action, SessionCall call) {
        try {
            return call.get();
        } catch (RestClientException ex) {
            log.warn("Configuration request failed during {}", action);
            throw new BusinessException("Configuration session could not be started",
                    HttpStatus.BAD_GATEWAY, "CONFIGURATION_SESSION_FAILED");
        }
    }

    private static Map<String, String> copySelections(Map<String, String> selections) {
        Map<String, String> options = new LinkedHashMap<>();
        if (selections == null) {
            return Map.copyOf(options);
        }
        selections.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                options.put(key, value);
            }
        });
        return Collections.unmodifiableMap(options);
    }

    @FunctionalInterface
    private interface SessionCall {
        ConfigurationEnvelope get();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ConfigurationEnvelope {
        private boolean success;
        private ConfigurationBody data;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public ConfigurationBody getData() {
            return data;
        }

        public void setData(ConfigurationBody data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ConfigurationBody {
        private Long id;
        private String productId;
        private Map<String, String> selections;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public Map<String, String> getSelections() {
            return selections;
        }

        public void setSelections(Map<String, String> selections) {
            this.selections = selections;
        }
    }
}
