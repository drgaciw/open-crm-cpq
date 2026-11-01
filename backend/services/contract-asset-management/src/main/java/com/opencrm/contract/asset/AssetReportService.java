package com.opencrm.contract.asset;

import com.opencrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Installed-base reports over the asset history projection.
 * Active assets for an account on a date are {@link AssetHistoryProjection#installedBase}
 * ({@code GET /api/v1/assets?accountId&asOf}) and are not queried again here.
 * Upcoming renewals are that installed base on the window end, limited to assets
 * whose effective end date falls in the window.
 * Churn is a termination, or an amended fact whose quantity is lower than the
 * previous created or amended quantity. A supersede is not churn. A termination
 * on or after a supersede of the same asset is not churn. A downsize on or
 * before the supersede date still is.
 */
@Service
@Transactional(readOnly = true)
public class AssetReportService {

    private static final String VALIDATION = "VALIDATION_REJECTED";

    private final AssetHistoryProjection history;
    private final AssetHistoryStore store;
    private final Clock clock;

    @Autowired
    public AssetReportService(AssetHistoryProjection history, AssetHistoryStore store) {
        this(history, store, Clock.systemUTC());
    }

    AssetReportService(AssetHistoryProjection history, AssetHistoryStore store, Clock clock) {
        this.history = history;
        this.store = store;
        this.clock = clock;
    }

    public UpcomingRenewalsReport upcomingRenewals(String accountId, LocalDate windowStart, LocalDate windowEnd,
                                                   Integer withinDays, LocalDate asOf) {
        InstalledBaseReadAccess.require();
        requireAccount(accountId);
        Window window = window(windowStart, windowEnd, withinDays, asOf);
        List<InstalledAssetDto> assets = history.installedBase(accountId, window.end()).stream()
                .filter(asset -> endsInWindow(asset, window))
                .sorted(Comparator.comparing(InstalledAssetDto::getEffectiveEnd)
                        .thenComparing(InstalledAssetDto::getAssetId))
                .toList();
        return UpcomingRenewalsReport.builder()
                .accountId(accountId)
                .windowStart(window.start())
                .windowEnd(window.end())
                .assetCount(assets.size())
                .assets(assets)
                .build();
    }

    public AssetChurnReport churn(String accountId, LocalDate periodStart, LocalDate periodEnd) {
        InstalledBaseReadAccess.require();
        requireAccount(accountId);
        requirePeriod(periodStart, periodEnd);
        List<AssetChurnEventDto> events = churnEvents(store.findByAccountId(accountId), periodStart, periodEnd);
        BigDecimal quantity = events.stream()
                .map(AssetChurnEventDto::getQuantityChurned)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int assets = (int) events.stream().map(AssetChurnEventDto::getAssetId).distinct().count();
        return AssetChurnReport.builder()
                .accountId(accountId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .assetCount(assets)
                .eventCount(events.size())
                .quantityChurned(quantity)
                .events(List.copyOf(events))
                .build();
    }

    private static List<AssetChurnEventDto> churnEvents(List<AssetHistoryEntry> rows, LocalDate periodStart,
                                                       LocalDate periodEnd) {
        Map<String, List<AssetHistoryEntry>> byAsset = new LinkedHashMap<>();
        for (AssetHistoryEntry row : rows) {
            if (row.getAssetId() == null || row.getAssetId().isBlank()
                    || row.getEffectiveDate() == null || row.getEventType() == null) {
                continue;
            }
            byAsset.computeIfAbsent(row.getAssetId(), ignored -> new ArrayList<>()).add(row);
        }
        List<AssetChurnEventDto> events = new ArrayList<>();
        for (List<AssetHistoryEntry> assetRows : byAsset.values()) {
            assetRows.sort(Comparator.comparing(AssetHistoryEntry::getEffectiveDate)
                    .thenComparingLong(AssetHistoryEntry::getAppliedOrder));
            events.addAll(churnForAsset(assetRows, periodStart, periodEnd));
        }
        events.sort(Comparator.comparing(AssetChurnEventDto::getEffectiveDate)
                .thenComparing(AssetChurnEventDto::getAssetId));
        return events;
    }

    private static List<AssetChurnEventDto> churnForAsset(List<AssetHistoryEntry> rows, LocalDate periodStart,
                                                         LocalDate periodEnd) {
        LocalDate supersededOn = rows.stream()
                .filter(row -> row.getEventType() == AssetHistoryKind.SUPERSEDED)
                .map(AssetHistoryEntry::getEffectiveDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        BigDecimal quantity = null;
        String productRef = null;
        List<AssetChurnEventDto> events = new ArrayList<>();
        for (AssetHistoryEntry row : rows) {
            if (row.getProductRef() != null) {
                productRef = row.getProductRef();
            }
            boolean inPeriod = inPeriod(row.getEffectiveDate(), periodStart, periodEnd);
            switch (row.getEventType()) {
                case CREATED -> {
                    if (row.getQuantity() != null) {
                        quantity = row.getQuantity();
                    }
                }
                case AMENDED -> {
                    if (inPeriod && !supersededBefore(supersededOn, row.getEffectiveDate())
                            && decreased(quantity, row.getQuantity())) {
                        events.add(downsized(row, productRef, quantity, row.getQuantity()));
                    }
                    if (row.getQuantity() != null) {
                        quantity = row.getQuantity();
                    }
                }
                case TERMINATED -> {
                    if (inPeriod && !supersedeExplainsExit(supersededOn, row.getEffectiveDate())) {
                        events.add(terminated(row, productRef, quantity));
                    }
                }
                case SUPERSEDED -> {
                    // A swap or upgrade keeps the relationship on the successor.
                }
            }
        }
        return events;
    }

    private static AssetChurnEventDto downsized(AssetHistoryEntry row, String productRef,
                                               BigDecimal before, BigDecimal after) {
        return AssetChurnEventDto.builder()
                .assetId(row.getAssetId())
                .accountId(row.getAccountId())
                .churnKind(AssetChurnKind.DOWNSIZED)
                .effectiveDate(row.getEffectiveDate())
                .quantityBefore(before)
                .quantityAfter(after)
                .quantityChurned(before.subtract(after))
                .productRef(productRef)
                .build();
    }

    private static AssetChurnEventDto terminated(AssetHistoryEntry row, String productRef, BigDecimal quantity) {
        return AssetChurnEventDto.builder()
                .assetId(row.getAssetId())
                .accountId(row.getAccountId())
                .churnKind(AssetChurnKind.TERMINATED)
                .effectiveDate(row.getEffectiveDate())
                .quantityBefore(quantity)
                .quantityAfter(BigDecimal.ZERO)
                .quantityChurned(quantity)
                .productRef(productRef)
                .build();
    }

    private static boolean decreased(BigDecimal before, BigDecimal after) {
        return before != null && after != null && after.compareTo(before) < 0;
    }

    /** An amendment after the asset was already superseded is not a downsize of the installed base. */
    private static boolean supersededBefore(LocalDate supersededOn, LocalDate effective) {
        return supersededOn != null && supersededOn.isBefore(effective);
    }

    /** Termination on the supersede date, or after it, is the swap leaving, not churn. */
    private static boolean supersedeExplainsExit(LocalDate supersededOn, LocalDate effective) {
        return supersededOn != null && !supersededOn.isAfter(effective);
    }

    private static boolean inPeriod(LocalDate effective, LocalDate start, LocalDate end) {
        return !effective.isBefore(start) && !effective.isAfter(end);
    }

    private static boolean endsInWindow(InstalledAssetDto asset, Window window) {
        LocalDate end = asset.getEffectiveEnd();
        return end != null && !end.isBefore(window.start()) && !end.isAfter(window.end());
    }

    private Window window(LocalDate windowStart, LocalDate windowEnd, Integer withinDays, LocalDate asOf) {
        boolean hasWindow = windowStart != null || windowEnd != null;
        if (withinDays != null && hasWindow) {
            throw new BusinessException(
                    "provide either windowStart and windowEnd, or withinDays", VALIDATION);
        }
        if (withinDays != null) {
            if (withinDays < 0) {
                throw new BusinessException("withinDays must be zero or positive", VALIDATION);
            }
            LocalDate start = asOf != null ? asOf : LocalDate.now(clock);
            try {
                return new Window(start, start.plusDays(withinDays));
            } catch (DateTimeException ex) {
                throw new BusinessException("withinDays is out of range", VALIDATION);
            }
        }
        if (windowStart == null || windowEnd == null) {
            throw new BusinessException(
                    "windowStart and windowEnd are required when withinDays is omitted", VALIDATION);
        }
        if (windowStart.isAfter(windowEnd)) {
            throw new BusinessException("windowStart must be on or before windowEnd", VALIDATION);
        }
        return new Window(windowStart, windowEnd);
    }

    private static void requireAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException("accountId is required", VALIDATION);
        }
    }

    private static void requirePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new BusinessException("periodStart and periodEnd are required", VALIDATION);
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new BusinessException("periodStart must be on or before periodEnd", VALIDATION);
        }
    }

    private record Window(LocalDate start, LocalDate end) {
    }
}
