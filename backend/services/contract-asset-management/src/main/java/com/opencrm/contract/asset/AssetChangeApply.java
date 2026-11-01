package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import com.opencrm.contract.documents.apply.ApplyLine;
import com.opencrm.contract.domain.Subscription;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps a committed apply unit's change lines onto installed assets.
 * Absent assets are left alone so the legacy acceptance-time writer can stay disabled.
 * Swap and upgrade save and publish the successor before the predecessor is superseded.
 */
@Service
public class AssetChangeApply {

    private final AssetRepository assets;
    private final AssetEventPublisher events;
    private final AssetAuditOutbox audit;
    private final AssetHistoryProjection history;

    public AssetChangeApply(AssetRepository assets, AssetEventPublisher events, AssetAuditOutbox audit,
                            AssetHistoryProjection history) {
        this.assets = assets;
        this.events = events;
        this.audit = audit;
        this.history = history;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onUnitApplied(Subscription subscription, List<ApplyLine> lines, UUID documentId, int unitNo,
                              String correlationId) {
        onUnitApplied(subscription, lines, documentId, unitNo, correlationId, Map.of());
    }

    /**
     * {@code openedContractLines} maps a change-line id to the accepted-line id opened for that line.
     * Swap and upgrade use it as the successor's contract line.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUnitApplied(Subscription subscription, List<ApplyLine> lines, UUID documentId, int unitNo,
                              String correlationId, Map<UUID, String> openedContractLines) {
        if (subscription == null || subscription.getId() == null || lines == null) {
            return;
        }
        Map<UUID, String> opened = openedContractLines == null ? Map.of() : openedContractLines;
        String amendmentId = documentId == null ? null : documentId.toString();
        String applyUnit = Integer.toString(unitNo);
        for (ApplyLine line : lines) {
            if (line == null || line.action() == null) {
                continue;
            }
            switch (line.action()) {
                case UPDATE -> amend(subscription, line, opened, amendmentId, applyUnit, correlationId);
                case REMOVE, TERMINATE -> terminate(subscription, line, amendmentId, applyUnit, correlationId);
                case SWAP, UPGRADE -> supersede(subscription, line, opened, amendmentId, applyUnit, correlationId);
                case ADD, NO_CHANGE, RENEW, DOWNGRADE, CONVERT_METRIC, SUSPEND, RESUME -> {
                    // These actions do not change the installed-asset row in this story.
                }
            }
        }
    }

    private void amend(Subscription subscription, ApplyLine line, Map<UUID, String> opened, String amendmentId,
                       String applyUnit, String correlationId) {
        Asset asset = find(subscription, line);
        if (asset == null || closed(asset)) {
            return;
        }
        BigDecimal quantity = line.endQty() == null ? asset.getQuantity() : line.endQty();
        LocalDate end = line.effectiveTo() == null ? asset.getEffectiveEnd() : line.effectiveTo();
        String product = blank(line.productExternalId()) ? asset.getProductRef() : line.productExternalId();
        String contractLine = openedLine(line, opened);
        if (contractLine == null) {
            contractLine = asset.getContractLineId();
        }
        if (asset.getStatus() == AssetStatus.AMENDED
                && same(asset.getQuantity(), quantity)
                && end.equals(asset.getEffectiveEnd())
                && sameText(asset.getProductRef(), product)
                && sameText(asset.getContractLineId(), contractLine)) {
            return;
        }
        asset.setQuantity(quantity);
        asset.setEffectiveEnd(end);
        asset.setProductRef(product);
        asset.setContractLineId(contractLine);
        asset.setStatus(AssetStatus.AMENDED);
        assets.save(asset);
        LocalDate effective = line.effectiveFrom() == null ? asset.getEffectiveStart() : line.effectiveFrom();
        AssetAmendedEvent amended = new AssetAmendedEvent(
                asset.getUuid(), asset.getAccountId(), asset.getContractLineId(), asset.getSubscriptionId(),
                asset.getEntitlementId(), product, quantity, AssetStatus.AMENDED.name(),
                asset.getEffectiveStart(), end, effective, amendmentId, applyUnit);
        UUID eventId = events.publishAmended(asset, amended, correlationId);
        history.apply(amended, eventId.toString());
        audit.stage(asset, eventId, correlationId);
    }

    private void terminate(Subscription subscription, ApplyLine line, String amendmentId, String applyUnit,
                           String correlationId) {
        Asset asset = find(subscription, line);
        if (asset == null || asset.getStatus() == AssetStatus.TERMINATED || asset.getStatus() == AssetStatus.SUPERSEDED) {
            return;
        }
        LocalDate effective = line.effectiveFrom() != null ? line.effectiveFrom()
                : line.effectiveTo() != null ? line.effectiveTo() : asset.getEffectiveEnd();
        asset.setStatus(AssetStatus.TERMINATED);
        asset.setEffectiveEnd(effective);
        assets.save(asset);
        AssetTerminatedEvent terminated = new AssetTerminatedEvent(
                asset.getUuid(), asset.getAccountId(), asset.getContractLineId(), asset.getSubscriptionId(),
                asset.getProductRef(), AssetStatus.TERMINATED.name(), effective, amendmentId, applyUnit);
        UUID eventId = events.publishTerminated(asset, terminated, correlationId);
        history.apply(terminated, eventId.toString());
        audit.stage(asset, eventId, correlationId);
    }

    private void supersede(Subscription subscription, ApplyLine line, Map<UUID, String> opened, String amendmentId,
                           String applyUnit, String correlationId) {
        Asset predecessor = find(subscription, line);
        if (predecessor == null || closed(predecessor)) {
            return;
        }
        LocalDate effective = line.effectiveFrom() == null ? predecessor.getEffectiveEnd() : line.effectiveFrom();
        LocalDate end = line.effectiveTo() == null ? predecessor.getEffectiveEnd() : line.effectiveTo();
        BigDecimal quantity = line.endQty() == null ? predecessor.getQuantity() : line.endQty();
        String product = blank(line.productExternalId()) ? predecessor.getProductRef() : line.productExternalId();
        String successorLine = openedLine(line, opened);
        if (successorLine == null) {
            successorLine = line.id() == null ? predecessor.getContractLineId() + ":successor" : line.id().toString();
        }
        Asset successor = assets.save(Asset.builder()
                .uuid(UUID.randomUUID().toString())
                .accountId(predecessor.getAccountId())
                .contractLineId(successorLine)
                .subscriptionId(subscription.getId())
                .productRef(product)
                .quantity(quantity)
                .status(AssetStatus.ACTIVE)
                .effectiveStart(effective)
                .effectiveEnd(end)
                .supersedes(predecessor.getUuid())
                .build());
        AssetEventPublisher.Created created = events.publishCreated(successor, correlationId, null);
        history.apply(created.event(), created.eventId().toString());
        audit.stage(successor, created.eventId(), correlationId);
        predecessor.setStatus(AssetStatus.SUPERSEDED);
        predecessor.setEffectiveEnd(effective);
        predecessor.setSupersededBy(successor.getUuid());
        assets.save(predecessor);
        AssetSupersededEvent superseded = new AssetSupersededEvent(
                predecessor.getUuid(), predecessor.getAccountId(), successor.getUuid(), predecessor.getUuid(),
                AssetStatus.SUPERSEDED.name(), effective, amendmentId, applyUnit);
        UUID eventId = events.publishSuperseded(predecessor, superseded, correlationId);
        history.apply(superseded, eventId.toString());
        audit.stage(predecessor, eventId, correlationId);
    }

    private static String openedLine(ApplyLine line, Map<UUID, String> opened) {
        if (line.id() == null || opened == null) {
            return null;
        }
        String value = opened.get(line.id());
        return blank(value) ? null : value;
    }

    private Asset find(Subscription subscription, ApplyLine line) {
        if (blank(line.sourceAcceptedLineUuid())) {
            return null;
        }
        return assets.findBySubscriptionIdAndContractLineIdAndDeletedFalse(
                subscription.getId(), line.sourceAcceptedLineUuid()).orElse(null);
    }

    private static boolean closed(Asset asset) {
        return asset.getStatus() == AssetStatus.TERMINATED || asset.getStatus() == AssetStatus.SUPERSEDED;
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private static boolean sameText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
