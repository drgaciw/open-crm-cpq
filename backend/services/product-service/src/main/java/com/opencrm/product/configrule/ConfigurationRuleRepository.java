package com.opencrm.product.configrule;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationRuleRepository extends JpaRepository<ConfigurationRule, Long> {

    Optional<ConfigurationRule> findByIdAndDeletedFalse(Long id);

    List<ConfigurationRule> findBySourceProductIdAndDeletedFalseOrderByPriorityDesc(Long sourceProductId);

    List<ConfigurationRule> findBySourceProductIdAndRuleTypeAndDeletedFalse(Long sourceProductId, ConfigRuleType ruleType);

    List<ConfigurationRule> findBySourceProductIdInAndRuleTypeInAndDeletedFalse(Collection<Long> sourceProductIds, Collection<ConfigRuleType> ruleTypes);

    Page<ConfigurationRule> findByDeletedFalse(Pageable pageable);
}
