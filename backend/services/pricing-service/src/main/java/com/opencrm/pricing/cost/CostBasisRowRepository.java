package com.opencrm.pricing.cost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CostBasisRowRepository extends JpaRepository<CostBasisRow, Long> {

    /** A row of the newest effective date on or before {@code asOf}; which component it is does not matter. */
    Optional<CostBasisRow> findFirstByProductExternalIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String productExternalId, LocalDate asOf);

    /** Every stored version of every component of a product on one effective date. */
    List<CostBasisRow> findByProductExternalIdAndEffectiveFrom(String productExternalId, LocalDate effectiveFrom);

    /** A superset of the stored rows for the given (product, date) pairs, for ingestion to filter. */
    List<CostBasisRow> findByProductExternalIdInAndEffectiveFromIn(Collection<String> productExternalIds,
                                                                  Collection<LocalDate> effectiveDates);
}
