package com.opencrm.configuration.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationModelCache {

    private final ConfigurationModelCompiler compiler;
    private final Map<Long, ConfigurationModel> modelsByProductId = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> productsByReferencedProductId = new ConcurrentHashMap<>();

    public ConfigurationModel getOrCompile(Long productId) {
        return modelsByProductId.computeIfAbsent(productId, this::compileAndIndex);
    }

    public void invalidateProduct(Long productId) {
        Set<Long> affectedProductIds = new HashSet<>();
        affectedProductIds.add(productId);
        affectedProductIds.addAll(productsByReferencedProductId.getOrDefault(productId, Set.of()));
        affectedProductIds.forEach(modelsByProductId::remove);
        affectedProductIds.forEach(this::removeIndexesFor);
        log.info("Invalidated {} compiled configuration models for product {}", affectedProductIds.size(), productId);
    }

    public void invalidateBundle(Long bundleProductId) {
        invalidateProduct(bundleProductId);
    }

    public void invalidateAll() {
        modelsByProductId.clear();
        productsByReferencedProductId.clear();
        log.info("Invalidated all compiled configuration models");
    }

    private ConfigurationModel compileAndIndex(Long productId) {
        ConfigurationModel model = compiler.compile(productId);
        model.referencedProductIds().forEach(referencedProductId ->
                productsByReferencedProductId
                        .computeIfAbsent(referencedProductId, ignored -> ConcurrentHashMap.newKeySet())
                        .add(productId)
        );
        log.debug("Compiled configuration model for product {} at version {}", productId, model.version());
        return model;
    }

    private void removeIndexesFor(Long productId) {
        productsByReferencedProductId.values().forEach(productIds -> productIds.remove(productId));
        productsByReferencedProductId.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
