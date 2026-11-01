package com.opencrm.contract.asset;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JpaAssetHistoryStore implements AssetHistoryStore {

    private final AssetHistoryRepository history;

    public JpaAssetHistoryStore(AssetHistoryRepository history) {
        this.history = history;
    }

    @Override
    public boolean append(AssetHistoryEntry row) {
        if (history.existsByEventId(row.getEventId())) {
            return false;
        }
        row.setAppliedOrder(history.maxAppliedOrder() + 1);
        history.save(row);
        return true;
    }

    @Override
    public List<AssetHistoryEntry> findByAssetId(String assetId) {
        return history.findByAssetIdOrderByEffectiveDateAscAppliedOrderAsc(assetId);
    }

    @Override
    public List<AssetHistoryEntry> findByAccountId(String accountId) {
        return history.findByAccountIdOrderByEffectiveDateAscAppliedOrderAsc(accountId);
    }

    @Override
    public List<AssetHistoryEntry> findByEventType(AssetHistoryKind eventType) {
        return history.findByEventTypeOrderByEffectiveDateAscAppliedOrderAsc(eventType);
    }

    @Override
    public void clear() {
        history.deleteAllInBatch();
    }
}
