package com.opencrm.product.configrule;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.product.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConfigurationRuleService {

    private final ConfigurationRuleRepository configRuleRepository;
    private final ProductRepository productRepository;
    private final ConfigurationRuleMapper configRuleMapper;

    public ConfigurationRuleDto create(ConfigRuleCreateRequest request) {
        log.debug("Creating config rule: {}", request.getName());

        productRepository.findByIdAndDeletedFalse(request.getSourceProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Source Product", request.getSourceProductId()));

        if (request.getTargetProductId() != null) {
            productRepository.findByIdAndDeletedFalse(request.getTargetProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target Product", request.getTargetProductId()));
        }

        ConfigurationRule rule = configRuleMapper.toEntity(request);
        ConfigurationRule saved = configRuleRepository.save(rule);
        log.info("Created config rule with ID: {}", saved.getId());

        return configRuleMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ConfigurationRuleDto getById(Long id) {
        ConfigurationRule rule = configRuleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConfigurationRule", id));
        return configRuleMapper.toDto(rule);
    }

    @Transactional(readOnly = true)
    public List<ConfigurationRuleDto> getBySourceProduct(Long sourceProductId) {
        return configRuleRepository.findBySourceProductIdAndDeletedFalseOrderByPriorityDesc(sourceProductId)
                .stream().map(configRuleMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<ConfigurationRuleDto> list(Pageable pageable) {
        return configRuleRepository.findByDeletedFalse(pageable).map(configRuleMapper::toDto);
    }

    public void delete(Long id) {
        ConfigurationRule rule = configRuleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConfigurationRule", id));
        rule.softDelete();
        configRuleRepository.save(rule);
        log.info("Deleted config rule ID: {}", id);
    }

    @Transactional(readOnly = true)
    public List<String> validateConfiguration(List<Long> selectedProductIds) {
        log.debug("Validating configuration for {} products", selectedProductIds.size());
        List<String> errors = new ArrayList<>();

        if (selectedProductIds == null || selectedProductIds.isEmpty()) {
            return errors;
        }

        // Convert selected products to a Set for O(1) lookups
        Set<Long> selectedSet = new HashSet<>(selectedProductIds);

        // Batch fetch all rules for the selected products to avoid N+1 queries
        List<ConfigurationRule> rules = configRuleRepository
                .findBySourceProductIdInAndRuleTypeInAndDeletedFalse(selectedSet, List.of(ConfigRuleType.REQUIRES, ConfigRuleType.EXCLUDES));

        for (ConfigurationRule rule : rules) {
            if (rule.getTargetProductId() != null) {
                if (rule.getRuleType() == ConfigRuleType.REQUIRES && !selectedSet.contains(rule.getTargetProductId())) {
                    errors.add(rule.getErrorMessage() != null ? rule.getErrorMessage() :
                            "Product " + rule.getSourceProductId() + " requires product " + rule.getTargetProductId());
                } else if (rule.getRuleType() == ConfigRuleType.EXCLUDES && selectedSet.contains(rule.getTargetProductId())) {
                    errors.add(rule.getErrorMessage() != null ? rule.getErrorMessage() :
                            "Product " + rule.getSourceProductId() + " is incompatible with product " + rule.getTargetProductId());
                }
            }
        }

        return errors;
    }
}
