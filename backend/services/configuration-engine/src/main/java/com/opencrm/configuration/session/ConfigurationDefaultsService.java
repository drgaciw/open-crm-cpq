package com.opencrm.configuration.session;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.configuration.constraint.ConstraintSolver;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.configuration.mapper.ConfigurationSessionMapper;
import com.opencrm.configuration.product.ProductOptionDefaultsClient;
import com.opencrm.configuration.product.ProductOptionGroupSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Applies the complete declared default batch only after cardinality and hard-rule validation. */
@Service
@RequiredArgsConstructor
public class ConfigurationDefaultsService {
    private final ConfigurationSessionRepository repository;
    private final ProductOptionDefaultsClient catalog;
    private final ConstraintSolver solver;
    private final ConfigurationSessionMapper mapper;

    @Transactional
    public ConfigurationSessionDto applyDefaults(Long sessionId, String userId) {
        ConfigurationSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ConfigurationSession", sessionId));
        if (userId == null || !userId.equals(session.getUserId())) {
            throw new BusinessException("Configuration belongs to another seller", HttpStatus.FORBIDDEN,
                    "CONFIGURATION_OWNER_MISMATCH");
        }
        if (session.isDeleted() || session.getStatus() != ConfigurationStatus.IN_PROGRESS
                || (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(Instant.now()))) {
            throw failure("Configuration is not editable", "CONFIGURATION_NOT_EDITABLE");
        }
        long productId = productId(session.getProductId());
        Map<String, String> selections = new HashMap<>(session.getSelections());
        Set<String> defaultKeys = new HashSet<>(session.getDefaultedSelectionKeys());
        Set<Long> groupIds = new HashSet<>();
        for (ProductOptionGroupSnapshot group : catalog.getGroups(productId)) {
            validateGroup(group, productId, groupIds);
            applyGroup(group, selections, defaultKeys);
        }
        // Option rows can outlive a deleted target product. Validate new targets before any managed write;
        // existing manual choices remain untouched and do not acquire a new catalog lifecycle policy.
        selections.entrySet().stream().filter(entry -> !session.getSelections().containsKey(entry.getKey()))
                .map(Map.Entry::getValue).map(Long::valueOf).distinct().forEach(catalog::requireProduct);

        // Never mutate the managed entity while a candidate is still unvalidated.
        ConfigurationSession candidate = ConfigurationSession.builder().productId(session.getProductId())
                .userId(session.getUserId()).status(session.getStatus()).selections(selections).build();
        ConfigurationValidationResult result = solver.evaluateExplicitSelections(candidate);
        if (result == null || !result.isValid()) {
            throw failure("Catalog defaults conflict with configuration constraints", "DEFAULTS_CONSTRAINT_CONFLICT");
        }
        session.setSelections(selections);
        defaultKeys.retainAll(selections.keySet());
        session.setDefaultedSelectionKeys(defaultKeys);
        session.setValid(true);
        List<String> messages = ConstraintSolver.allViolationMessages(result);
        session.setValidationErrors(messages.isEmpty() ? null : String.join("; ", messages));
        return mapper.toDto(repository.save(session));
    }

    private static void applyGroup(ProductOptionGroupSnapshot group, Map<String, String> selections,
                                   Set<String> defaultKeys) {
        int minimum = Math.max(group.minSelections(), "REQUIRED_SINGLE".equals(group.selectionType()) ? 1 : 0);
        boolean single = !"MULTIPLE".equals(group.selectionType());
        int maximum = single ? 1 : (group.maxSelections() == null ? Integer.MAX_VALUE : group.maxSelections());
        if (group.maxSelections() != null) {
            maximum = Math.min(maximum, group.maxSelections());
        }
        if (minimum > maximum) {
            throw failure("Catalog group has inconsistent selection limits", "INVALID_DEFAULTS_CATALOG");
        }
        Set<Long> selected = new HashSet<>();
        for (String value : selections.values()) {
            try {
                if (value != null) selected.add(Long.parseLong(value.trim()));
            } catch (NumberFormatException ignored) {
                // Attribute values are not product selections (same convention as ConstraintSolver).
            }
        }
        long existing = group.options().stream().filter(option -> selected.contains(option.productId())).count();
        if (existing > maximum) {
            throw failure("Existing choices exceed the catalog group limit", "DEFAULTS_CARDINALITY_CONFLICT");
        }
        if (existing >= minimum) return; // Never replace manual choices or fill optional groups.

        List<ProductOptionGroupSnapshot.Option> defaults = group.options().stream()
                .filter(ProductOptionGroupSnapshot.Option::defaultOption)
                .filter(option -> !selected.contains(option.productId())).toList();
        if (existing + defaults.size() > maximum) {
            throw failure("Catalog declares too many defaults for the group", "DEFAULTS_CARDINALITY_CONFLICT");
        }
        if (existing + defaults.size() < minimum) {
            throw failure("Catalog has insufficient defaults for required selections", "DEFAULTS_INCOMPLETE");
        }
        for (ProductOptionGroupSnapshot.Option option : defaults) {
            String key = "option:" + group.id() + ":" + option.id();
            String value = option.productId().toString();
            if (selections.containsKey(key) && !Objects.equals(selections.get(key), value)) {
                throw failure("A seller selection occupies the default option key", "DEFAULTS_SELECTION_CONFLICT");
            }
            selections.put(key, value);
            defaultKeys.add(key);
        }
    }

    private static void validateGroup(ProductOptionGroupSnapshot group, long productId, Set<Long> groupIds) {
        if (group == null || group.id() == null || group.id() <= 0 || !groupIds.add(group.id())
                || !Objects.equals(group.productId(), productId) || group.minSelections() < 0
                || group.selectionType() == null
                || !Set.of("SINGLE", "REQUIRED_SINGLE", "MULTIPLE").contains(group.selectionType())
                || group.options() == null || (group.maxSelections() != null && group.maxSelections() < 0)) {
            throw failure("Catalog returned an invalid option group", "INVALID_DEFAULTS_CATALOG");
        }
        Set<Long> optionIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        for (ProductOptionGroupSnapshot.Option option : group.options()) {
            if (option == null || option.id() == null || option.id() <= 0 || !optionIds.add(option.id())
                    || option.productId() == null || option.productId() <= 0 || !productIds.add(option.productId())) {
                throw failure("Catalog option lacks an unambiguous product selection", "INVALID_DEFAULTS_CATALOG");
            }
        }
    }

    private static long productId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
            // Session APIs historically permit arbitrary product strings; catalog defaults require a catalog ID.
        }
        throw failure("Defaults require a numeric catalog product ID", "DEFAULTS_PRODUCT_ID_REQUIRED");
    }

    private static BusinessException failure(String message, String code) {
        return new BusinessException(message, HttpStatus.UNPROCESSABLE_CONTENT, code);
    }
}
