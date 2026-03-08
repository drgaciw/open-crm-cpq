package com.opencrm.dealdesk.rules.repository;

import com.opencrm.dealdesk.rules.model.RuleDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinitionEntity, Long> {

    Optional<RuleDefinitionEntity> findByIdAndDeletedFalse(Long id);

    Optional<RuleDefinitionEntity> findByRuleKeyAndDeletedFalse(String ruleKey);

    boolean existsByRuleKeyAndDeletedFalse(String ruleKey);

    boolean existsByRuleKeyAndDeletedFalseAndIdNot(String ruleKey, Long id);

    List<RuleDefinitionEntity> findAllByDeletedFalseOrderByPriorityAscCreatedAtDesc();

    List<RuleDefinitionEntity> findAllByEnabledAndDeletedFalseOrderByPriorityAscCreatedAtDesc(boolean enabled);

    List<RuleDefinitionEntity> findAllByCategoryIgnoreCaseAndDeletedFalseOrderByPriorityAscCreatedAtDesc(String category);

    List<RuleDefinitionEntity> findAllByCategoryIgnoreCaseAndEnabledAndDeletedFalseOrderByPriorityAscCreatedAtDesc(
            String category, boolean enabled
    );
}
