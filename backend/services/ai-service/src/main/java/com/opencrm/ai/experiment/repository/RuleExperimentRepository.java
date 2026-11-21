package com.opencrm.ai.experiment.repository;

import com.opencrm.ai.experiment.entity.RuleExperiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuleExperimentRepository extends JpaRepository<RuleExperiment, UUID> {

    List<RuleExperiment> findByRuleIdOrderByCreatedAtDesc(Long ruleId);

    List<RuleExperiment> findAllByOrderByCreatedAtDesc();
}
