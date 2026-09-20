package com.opencrm.quote.snapshot;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Set;
@Getter @Setter @Component
@ConfigurationProperties(prefix = "oms.quote")
public class OmsQuoteProperties {
    private java.util.Map<String, String> productFulfillmentTypes = java.util.Map.of();
    private String classificationMappingVersion;
    private boolean enabled = false;
    private String scopeId;
    private Set<String> customerIds = Set.of();
}
