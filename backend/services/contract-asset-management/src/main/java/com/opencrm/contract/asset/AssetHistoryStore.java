package com.opencrm.contract.asset;

import java.util.Collection;
import java.util.List;

/** Persistence for the asset history projection. */
public interface AssetHistoryStore {

    /** @return false when {@code row.eventId} is already stored */
    boolean append(AssetHistoryEntry row);

    List<AssetHistoryEntry> findByAssetId(String assetId);

    List<AssetHistoryEntry> findByAccountId(String accountId);

    List<AssetHistoryEntry> findByAccountIdAndAssetIdIn(String accountId, Collection<String> assetIds);

    List<AssetHistoryEntry> findByEventType(AssetHistoryKind eventType);

    List<AssetHistoryEntry> findByEventTypeAndAssetIdIn(AssetHistoryKind eventType, Collection<String> assetIds);

    void clear();
}
