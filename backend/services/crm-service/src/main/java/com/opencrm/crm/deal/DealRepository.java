package com.opencrm.crm.deal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Deal entity.
 */
@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {

    /**
     * Find deal by UUID.
     */
    @EntityGraph(attributePaths = {"organization", "primaryContact"})
    Optional<Deal> findByUuid(String uuid);

    /**
     * Find deals by stage (excluding deleted).
     */
    @EntityGraph(attributePaths = {"organization", "primaryContact"})
    Page<Deal> findByStageAndDeletedFalse(DealStage stage, Pageable pageable);

    /**
     * Find deals by owner (excluding deleted).
     */
    @EntityGraph(attributePaths = {"organization", "primaryContact"})
    Page<Deal> findByOwnerIdAndDeletedFalse(String ownerId, Pageable pageable);

    /**
     * Find all non-deleted deals.
     */
    @EntityGraph(attributePaths = {"organization", "primaryContact"})
    Page<Deal> findAllByDeletedFalse(Pageable pageable);

    /**
     * Search deals by name using case-insensitive partial match.
     */
    @EntityGraph(attributePaths = {"organization", "primaryContact"})
    @Query("SELECT d FROM Deal d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :query, '%')) AND d.deleted = false")
    Page<Deal> searchByName(@Param("query") String query, Pageable pageable);

    /**
     * Count deals by stage (excluding deleted).
     */
    long countByStageAndDeletedFalse(DealStage stage);

    /**
     * Sum of base-currency amounts by stage (excluding deleted). Deals not converted to {@code baseCurrency} are
     * left out (ADR-032 D-04, D-06); never sums {@code amount} across currencies.
     */
    @Query("SELECT COALESCE(SUM(d.baseAmount), 0) FROM Deal d " +
           "WHERE d.stage = :stage AND d.deleted = false AND d.baseCurrency = :baseCurrency")
    BigDecimal sumBaseAmountByStageAndDeletedFalse(@Param("stage") DealStage stage,
                                                   @Param("baseCurrency") String baseCurrency);

    /**
     * Pipeline summary by stage in the base currency (ADR-032 D-06).
     *
     * <p>{@code totalAmount} and {@code weightedAmount} sum only deals converted to {@code baseCurrency}.
     * {@code unconvertedCount} counts deals with an amount that are not (no rate yet, or frozen against another base).
     */
    @Query("SELECT d.stage as stage, COUNT(d) as count, " +
           "COALESCE(SUM(CASE WHEN d.baseCurrency = :baseCurrency THEN d.baseAmount ELSE NULL END), 0) as totalAmount, " +
           "COALESCE(SUM(CASE WHEN d.baseCurrency = :baseCurrency THEN d.baseWeightedAmount ELSE NULL END), 0) as weightedAmount, " +
           "COALESCE(SUM(CASE WHEN d.amount IS NOT NULL AND (d.baseAmount IS NULL OR d.baseCurrency IS NULL " +
           "OR d.baseCurrency <> :baseCurrency) THEN 1 ELSE 0 END), 0) as unconvertedCount " +
           "FROM Deal d WHERE d.deleted = false GROUP BY d.stage ORDER BY d.stage")
    List<PipelineSummaryProjection> findPipelineSummary(@Param("baseCurrency") String baseCurrency);
}
