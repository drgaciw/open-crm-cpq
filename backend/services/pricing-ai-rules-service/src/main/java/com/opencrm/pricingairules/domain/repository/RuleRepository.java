package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
    List<Rule> findByRuleSet(RuleSet ruleSet);
    List<Rule> findByRuleSetAndStatus(RuleSet ruleSet, String status);
    List<Rule> findByRuleTypeAndStatus(String ruleType, String status);
}
