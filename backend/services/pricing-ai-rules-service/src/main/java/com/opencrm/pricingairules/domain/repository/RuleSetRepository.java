package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {
    Optional<RuleSet> findByName(String name);

    List<RuleSet> findByVersion(String version);

    Optional<RuleSet> findByNameAndVersion(String name, String version);

    List<RuleSet> findByStatus(String status);

    List<RuleSet> findByStatusAndEffectiveDateBeforeAndExpirationDateAfter(
            String status, ZonedDateTime now, ZonedDateTime now2);
}
