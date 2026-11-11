package com.opencrm.configuration.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Read-side projection of the existing product-options API, owned by configuration-engine. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductOptionGroupSnapshot(Long id, Long productId, String selectionType,
                                         int minSelections, Integer maxSelections, List<Option> options) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(Long id, Long productId, boolean defaultOption) {}
}
