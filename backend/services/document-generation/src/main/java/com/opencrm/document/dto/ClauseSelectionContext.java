package com.opencrm.document.dto;

import java.util.Map;

public record ClauseSelectionContext(String region, String segment, Map<String, Object> quoteAttributes) {
    public ClauseSelectionContext {
        quoteAttributes = quoteAttributes == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.HashMap<>(quoteAttributes));
    }
}
