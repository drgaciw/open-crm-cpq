package com.opencrm.dealdesk.rules.repository;

import com.opencrm.dealdesk.rules.model.DealRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DealRuleRepository extends JpaRepository<DealRule, Long> {

    Optional<DealRule> findByIdAndDeletedFalse(Long id);

    Optional<DealRule> findByRuleKeyAndDeletedFalse(String ruleKey);

    List<DealRule> findAllByDeletedFalseOrderByPriorityAscCreatedAtDesc();

    List<DealRule> findAllByActiveTrueAndDeletedFalseOrderByPriorityAscCreatedAtDesc();

    List<DealRule> findAllByCategoryIgnoreCaseAndDeletedFalseOrderByPriorityAscCreatedAtDesc(String category);

    List<DealRule> findAllByCategoryIgnoreCaseAndActiveTrueAndDeletedFalseOrderByPriorityAscCreatedAtDesc(String category);
}
