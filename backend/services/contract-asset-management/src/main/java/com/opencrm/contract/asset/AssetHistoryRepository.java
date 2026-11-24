package com.opencrm.contract.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistoryEntry, Long> {

    boolean existsByEventId(String eventId);

    List<AssetHistoryEntry> findByAssetIdOrderByEffectiveDateAscAppliedOrderAsc(String assetId);

    List<AssetHistoryEntry> findByAccountIdOrderByEffectiveDateAscAppliedOrderAsc(String accountId);

    @Query("""
            select e from AssetHistoryEntry e
            where e.accountId = :accountId and e.assetId in :assetIds
            order by e.effectiveDate asc, e.appliedOrder asc
            """)
    List<AssetHistoryEntry> findByAccountIdAndAssetIdIn(@Param("accountId") String accountId,
                                                        @Param("assetIds") Collection<String> assetIds);

    List<AssetHistoryEntry> findByEventTypeOrderByEffectiveDateAscAppliedOrderAsc(AssetHistoryKind eventType);

    @Query("""
            select e from AssetHistoryEntry e
            where e.eventType = :eventType and e.assetId in :assetIds
            order by e.effectiveDate asc, e.appliedOrder asc
            """)
    List<AssetHistoryEntry> findByEventTypeAndAssetIdIn(@Param("eventType") AssetHistoryKind eventType,
                                                        @Param("assetIds") Collection<String> assetIds);

    @Query("select coalesce(max(e.appliedOrder), 0) from AssetHistoryEntry e")
    long maxAppliedOrder();
}
