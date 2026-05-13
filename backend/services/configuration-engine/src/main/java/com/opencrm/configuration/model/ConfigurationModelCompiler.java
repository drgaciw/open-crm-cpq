package com.opencrm.configuration.model;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ConfigurationModelCompiler {

    private final ProductConfigurationSnapshotProvider snapshotProvider;

    public ConfigurationModel compile(Long productId) {
        ProductConfigurationSnapshot snapshot = snapshotProvider.load(productId);
        Set<Long> referencedProductIds = new HashSet<>();
        referencedProductIds.add(productId);
        snapshot.rules().forEach(rule -> {
            referencedProductIds.add(rule.sourceProductId());
            if (rule.targetProductId() != null) {
                referencedProductIds.add(rule.targetProductId());
            }
        });
        snapshot.bundleComponents().forEach(component -> {
            referencedProductIds.add(component.bundleProductId());
            referencedProductIds.add(component.componentProductId());
        });

        return new ConfigurationModel(
                productId,
                versionFor(snapshot),
                snapshot.rules(),
                snapshot.bundleComponents(),
                Set.copyOf(referencedProductIds),
                Instant.now()
        );
    }

    private String versionFor(ProductConfigurationSnapshot snapshot) {
        StringBuilder source = new StringBuilder();
        source.append("product=").append(snapshot.productId());
        snapshot.rules().stream()
                .sorted(Comparator.comparing(ConfigurationRuleSnapshot::id))
                .forEach(rule -> source
                        .append("|rule=").append(rule.id())
                        .append(':').append(rule.ruleType())
                        .append(':').append(rule.sourceProductId())
                        .append(':').append(rule.targetProductId())
                        .append(':').append(rule.priority())
                        .append(':').append(rule.expression())
                        .append(':').append(rule.errorMessage()));
        snapshot.bundleComponents().stream()
                .sorted(Comparator.comparing(BundleComponentSnapshot::bundleProductId)
                        .thenComparing(BundleComponentSnapshot::componentProductId))
                .forEach(component -> source
                        .append("|bundle=").append(component.bundleProductId())
                        .append(':').append(component.componentProductId())
                        .append(':').append(component.quantity())
                        .append(':').append(component.optional()));
        return sha256(source.toString());
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
