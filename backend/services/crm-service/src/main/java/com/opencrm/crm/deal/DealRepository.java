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
     * Sum amount by stage (excluding deleted).
     */
    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Deal d WHERE d.stage = :stage AND d.deleted = false")
    BigDecimal sumAmountByStageAndDeletedFalse(@Param("stage") DealStage stage);

    /**
     * Get pipeline summary - count, sum of amount, and sum of weighted amount by stage.
     */
    @Query("SELECT d.stage, COUNT(d), COALESCE(SUM(d.amount), 0), COALESCE(SUM(d.weightedAmount), 0) " +
           "FROM Deal d WHERE d.deleted = false GROUP BY d.stage ORDER BY d.stage")
    List<Object[]> findPipelineSummary();
}
