package com.opencrm.ai.experiment.repository;

import com.opencrm.ai.experiment.entity.RuleExperimentVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleExperimentVariantRepository extends JpaRepository<RuleExperimentVariant, UUID> {

    List<RuleExperimentVariant> findByExperimentIdOrderByControlVariantDescCreatedAtAsc(UUID experimentId);

    Optional<RuleExperimentVariant> findByIdAndExperimentId(UUID id, UUID experimentId);
}
