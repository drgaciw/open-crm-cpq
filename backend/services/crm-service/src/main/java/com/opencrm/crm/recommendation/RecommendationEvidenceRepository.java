package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealStage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read-only win-data query. Lives here so {@code DealRepository} is unchanged.
 * The JPQL is a static annotation value: parameters are bound, never concatenated.
 */
public interface RecommendationEvidenceRepository extends Repository<Deal, Long> {

    @Query("""
            select d from Deal d
            join d.organization o
            where d.deleted = false
              and o.deleted = false
              and d.stage = :stage
              and o.id <> :organizationId
              and d.id <> :dealId
              and o.industry is not null
              and LOWER(o.industry) = LOWER(:industry)
            order by d.id asc
            """)
    List<Deal> findPeerWins(@Param("stage") DealStage stage,
                             @Param("organizationId") Long organizationId,
                             @Param("dealId") Long dealId,
                             @Param("industry") String industry,
                             Pageable pageable);
}
