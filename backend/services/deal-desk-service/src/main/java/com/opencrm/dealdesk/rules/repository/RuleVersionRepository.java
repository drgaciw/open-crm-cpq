package com.opencrm.dealdesk.rules.repository;

import com.opencrm.dealdesk.rules.model.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, Long> {

    Optional<RuleVersion> findByIdAndDeletedFalse(Long id);

    List<RuleVersion> findByDealRuleIdAndDeletedFalseOrderByVersionNumberDesc(Long dealRuleId);

    Optional<RuleVersion> findByDealRuleIdAndVersionNumberAndDeletedFalse(Long dealRuleId, Integer versionNumber);

    Optional<RuleVersion> findFirstByDealRuleIdAndActiveTrueAndDeletedFalseOrderByVersionNumberDesc(Long dealRuleId);
}
