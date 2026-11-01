package com.opencrm.contract.asset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Projection store for unit tests. Not a Spring bean and not a producer. */
final class InMemoryAssetHistoryStore implements AssetHistoryStore {

    private final List<AssetHistoryEntry> rows = new ArrayList<>();
    private long sequence;

    @Override
    public boolean append(AssetHistoryEntry row) {
        if (rows.stream().anyMatch(existing -> existing.getEventId().equals(row.getEventId()))) {
            return false;
        }
        row.setAppliedOrder(++sequence);
        row.setId(sequence);
        rows.add(copy(row));
        return true;
    }

    @Override
    public List<AssetHistoryEntry> findByAssetId(String assetId) {
        return rows.stream()
                .filter(row -> assetId.equals(row.getAssetId()))
                .sorted(byEffectiveDate())
                .map(InMemoryAssetHistoryStore::copy)
                .toList();
    }

    @Override
    public List<AssetHistoryEntry> findByAccountId(String accountId) {
        return rows.stream()
                .filter(row -> accountId.equals(row.getAccountId()))
                .sorted(byEffectiveDate())
                .map(InMemoryAssetHistoryStore::copy)
                .toList();
    }

    @Override
    public List<AssetHistoryEntry> findByEventType(AssetHistoryKind eventType) {
        return rows.stream()
                .filter(row -> eventType == row.getEventType())
                .sorted(byEffectiveDate())
                .map(InMemoryAssetHistoryStore::copy)
                .toList();
    }

    @Override
    public void clear() {
        rows.clear();
        sequence = 0;
    }

    private static Comparator<AssetHistoryEntry> byEffectiveDate() {
        return Comparator.comparing(AssetHistoryEntry::getEffectiveDate)
                .thenComparingLong(AssetHistoryEntry::getAppliedOrder);
    }

    private static AssetHistoryEntry copy(AssetHistoryEntry row) {
        AssetHistoryEntry copy = new AssetHistoryEntry();
        copy.setId(row.getId());
        copy.setEventId(row.getEventId());
        copy.setAssetId(row.getAssetId());
        copy.setAccountId(row.getAccountId());
        copy.setEventType(row.getEventType());
        copy.setEffectiveDate(row.getEffectiveDate());
        copy.setEffectiveEnd(row.getEffectiveEnd());
        copy.setSourceAmendmentId(row.getSourceAmendmentId());
        copy.setSourceApplyUnit(row.getSourceApplyUnit());
        copy.setPredecessorAssetId(row.getPredecessorAssetId());
        copy.setSuccessorAssetId(row.getSuccessorAssetId());
        copy.setContractLineId(row.getContractLineId());
        copy.setSubscriptionId(row.getSubscriptionId());
        copy.setProductRef(row.getProductRef());
        copy.setQuantity(row.getQuantity());
        copy.setAppliedOrder(row.getAppliedOrder());
        return copy;
    }
}
