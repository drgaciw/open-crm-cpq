package com.opencrm.contract.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistoryEntry, Long> {

    boolean existsByEventId(String eventId);

    List<AssetHistoryEntry> findByAssetIdOrderByEffectiveDateAscAppliedOrderAsc(String assetId);

    List<AssetHistoryEntry> findByAccountIdOrderByEffectiveDateAscAppliedOrderAsc(String accountId);

    List<AssetHistoryEntry> findByEventTypeOrderByEffectiveDateAscAppliedOrderAsc(AssetHistoryKind eventType);

    @Query("select coalesce(max(e.appliedOrder), 0) from AssetHistoryEntry e")
    long maxAppliedOrder();
}
