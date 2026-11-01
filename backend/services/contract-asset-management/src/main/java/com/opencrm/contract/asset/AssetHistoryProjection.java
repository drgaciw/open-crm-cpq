package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds asset history, the supersede chain, and an account's installed base from facts.
 * {@link AssetCreatedEvent} is the published create fact. The other three kinds are shapes
 * this projection accepts. Nothing here publishes an event.
 */
@Service
@Transactional
public class AssetHistoryProjection {

    private final AssetHistoryStore store;

    public AssetHistoryProjection(AssetHistoryStore store) {
        this.store = store;
    }

    public void apply(AssetCreatedEvent event, String eventId) {
        if (event == null || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("AssetCreated and its event id are required");
        }
        apply(new AssetHistoryFact(
                eventId,
                AssetHistoryKind.CREATED,
                event.assetId(),
                event.accountId(),
                event.effectiveStart(),
                event.effectiveEnd(),
                null,
                null,
                null,
                null,
                event.contractLineId(),
                event.subscriptionId(),
                event.productRef(),
                event.quantity()));
    }

    public void apply(AssetHistoryFact fact) {
        if (fact == null || blank(fact.eventId()) || blank(fact.assetId()) || blank(fact.accountId())
                || fact.kind() == null || fact.effectiveDate() == null) {
            throw new IllegalArgumentException("asset history fact is incomplete");
        }
        AssetHistoryEntry row = new AssetHistoryEntry();
        row.setEventId(fact.eventId());
        row.setAssetId(fact.assetId());
        row.setAccountId(fact.accountId());
        row.setEventType(fact.kind());
        row.setEffectiveDate(fact.effectiveDate());
        row.setEffectiveEnd(fact.effectiveEnd());
        row.setSourceAmendmentId(fact.sourceAmendmentId());
        row.setSourceApplyUnit(fact.sourceApplyUnit());
        row.setPredecessorAssetId(fact.predecessorAssetId());
        row.setSuccessorAssetId(fact.successorAssetId());
        row.setContractLineId(fact.contractLineId());
        row.setSubscriptionId(fact.subscriptionId());
        row.setProductRef(fact.productRef());
        row.setQuantity(fact.quantity());
        store.append(row);
    }

    /** Deletes projection rows so the same facts can be applied again. */
    public void dropProjection() {
        store.clear();
    }

    @Transactional(readOnly = true)
    public List<AssetHistoryEntryDto> history(String assetId) {
        InstalledBaseReadAccess.require();
        if (blank(assetId)) {
            return List.of();
        }
        return store.findByAssetId(assetId).stream().map(this::toHistory).toList();
    }

    @Transactional(readOnly = true)
    public SupersedeChainDto chain(String assetId, String direction) {
        InstalledBaseReadAccess.require();
        String selected = directionOf(direction);
        Map<String, List<AssetHistoryEntry>> successorsOf = new HashMap<>();
        Map<String, List<AssetHistoryEntry>> predecessorsOf = new HashMap<>();
        for (AssetHistoryEntry link : store.findByEventType(AssetHistoryKind.SUPERSEDED)) {
            if (blank(link.getSuccessorAssetId())) {
                continue;
            }
            String predecessor = blank(link.getPredecessorAssetId()) ? link.getAssetId() : link.getPredecessorAssetId();
            successorsOf.computeIfAbsent(predecessor, ignored -> new ArrayList<>()).add(link);
            predecessorsOf.computeIfAbsent(link.getSuccessorAssetId(), ignored -> new ArrayList<>()).add(link);
        }
        List<SupersedeHopDto> predecessors = List.of();
        List<SupersedeHopDto> successors = List.of();
        if ("predecessors".equals(selected) || "both".equals(selected)) {
            predecessors = walk(assetId, predecessorsOf, false);
        }
        if ("successors".equals(selected) || "both".equals(selected)) {
            successors = walk(assetId, successorsOf, true);
        }
        return SupersedeChainDto.builder()
                .assetId(assetId)
                .direction(selected)
                .predecessors(predecessors)
                .successors(successors)
                .build();
    }

    @Transactional(readOnly = true)
    public List<InstalledAssetDto> installedBase(String accountId, LocalDate asOf) {
        InstalledBaseReadAccess.require();
        if (blank(accountId) || asOf == null) {
            return List.of();
        }
        Map<String, List<AssetHistoryEntry>> byAsset = new LinkedHashMap<>();
        for (AssetHistoryEntry row : store.findByAccountId(accountId)) {
            byAsset.computeIfAbsent(row.getAssetId(), ignored -> new ArrayList<>()).add(row);
        }
        List<InstalledAssetDto> installed = new ArrayList<>();
        for (List<AssetHistoryEntry> events : byAsset.values()) {
            events.sort(Comparator.comparing(AssetHistoryEntry::getEffectiveDate)
                    .thenComparingLong(AssetHistoryEntry::getAppliedOrder));
            InstalledAssetDto current = fold(events, asOf);
            if (current != null) {
                installed.add(current);
            }
        }
        installed.sort(Comparator.comparing(InstalledAssetDto::getAssetId));
        return installed;
    }

    private List<SupersedeHopDto> walk(String start, Map<String, List<AssetHistoryEntry>> edges, boolean forward) {
        List<SupersedeHopDto> result = new ArrayList<>();
        if (blank(start)) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        seen.add(start);
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        Map<String, Integer> distance = new HashMap<>();
        distance.put(start, 0);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            List<AssetHistoryEntry> next = new ArrayList<>(edges.getOrDefault(current, List.of()));
            next.sort(Comparator.comparing(AssetHistoryEntry::getEffectiveDate)
                    .thenComparingLong(AssetHistoryEntry::getAppliedOrder));
            for (AssetHistoryEntry link : next) {
                String target = forward ? link.getSuccessorAssetId() : link.getPredecessorAssetId();
                if (blank(target) || !seen.add(target)) {
                    continue;
                }
                int hop = distance.get(current) + 1;
                distance.put(target, hop);
                result.add(SupersedeHopDto.builder()
                        .assetId(target)
                        .effectiveDate(link.getEffectiveDate())
                        .distance(hop)
                        .build());
                queue.add(target);
            }
        }
        return result;
    }

    private InstalledAssetDto fold(List<AssetHistoryEntry> events, LocalDate asOf) {
        boolean installed = false;
        String assetId = null;
        String accountId = null;
        String contractLineId = null;
        Long subscriptionId = null;
        String productRef = null;
        BigDecimal quantity = null;
        LocalDate start = null;
        LocalDate end = null;
        AssetStatus status = null;
        for (AssetHistoryEntry row : events) {
            if (row.getEffectiveDate().isAfter(asOf)) {
                continue;
            }
            assetId = row.getAssetId();
            accountId = row.getAccountId();
            switch (row.getEventType()) {
                case CREATED -> {
                    installed = true;
                    start = row.getEffectiveDate();
                    end = row.getEffectiveEnd();
                    contractLineId = row.getContractLineId();
                    subscriptionId = row.getSubscriptionId();
                    productRef = row.getProductRef();
                    quantity = row.getQuantity();
                    status = AssetStatus.ACTIVE;
                }
                case AMENDED -> {
                    installed = true;
                    if (row.getQuantity() != null) {
                        quantity = row.getQuantity();
                    }
                    if (row.getProductRef() != null) {
                        productRef = row.getProductRef();
                    }
                    if (row.getEffectiveEnd() != null) {
                        end = row.getEffectiveEnd();
                    }
                    if (row.getContractLineId() != null) {
                        contractLineId = row.getContractLineId();
                    }
                    status = AssetStatus.AMENDED;
                }
                case SUPERSEDED, TERMINATED -> installed = false;
            }
        }
        if (!installed) {
            return null;
        }
        return InstalledAssetDto.builder()
                .assetId(assetId)
                .accountId(accountId)
                .contractLineId(contractLineId)
                .subscriptionId(subscriptionId)
                .productRef(productRef)
                .quantity(quantity)
                .effectiveStart(start)
                .effectiveEnd(end)
                .status(status)
                .build();
    }

    private AssetHistoryEntryDto toHistory(AssetHistoryEntry row) {
        return AssetHistoryEntryDto.builder()
                .assetId(row.getAssetId())
                .accountId(row.getAccountId())
                .eventType(row.getEventType().name())
                .effectiveDate(row.getEffectiveDate())
                .effectiveEnd(row.getEffectiveEnd())
                .sourceAmendmentId(row.getSourceAmendmentId())
                .sourceApplyUnit(row.getSourceApplyUnit())
                .predecessorAssetId(row.getPredecessorAssetId())
                .successorAssetId(row.getSuccessorAssetId())
                .contractLineId(row.getContractLineId())
                .subscriptionId(row.getSubscriptionId())
                .productRef(row.getProductRef())
                .quantity(row.getQuantity())
                .build();
    }

    private static String directionOf(String direction) {
        if (direction == null || direction.isBlank() || "both".equalsIgnoreCase(direction)) {
            return "both";
        }
        if ("predecessors".equalsIgnoreCase(direction)) {
            return "predecessors";
        }
        if ("successors".equalsIgnoreCase(direction)) {
            return "successors";
        }
        throw new IllegalArgumentException("direction must be predecessors, successors, or both");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
