package com.opencrm.contract.asset;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import com.opencrm.contract.documents.apply.ApplyLine;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Maps a committed apply unit's change lines onto installed assets.
 * Absent assets are left alone so the legacy acceptance-time writer can stay disabled.
 * Swap and upgrade save the successor before the predecessor is marked superseded.
 */
@Service
public class AssetChangeApply {

    static final String AGGREGATE_TYPE = "Asset";

    private final AssetRepository assets;
    private final OutboxEventPublisher publisher;
    private final AssetAuditOutbox audit;
    private final AssetHistoryProjection history;

    public AssetChangeApply(AssetRepository assets, OutboxEventPublisher publisher, AssetAuditOutbox audit,
                            AssetHistoryProjection history) {
        this.assets = assets;
        this.publisher = publisher;
        this.audit = audit;
        this.history = history;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onUnitApplied(Subscription subscription, List<ApplyLine> lines, UUID documentId, int unitNo,
                              String correlationId) {
        if (subscription == null || subscription.getId() == null || lines == null) {
            return;
        }
        String amendmentId = documentId == null ? null : documentId.toString();
        String applyUnit = Integer.toString(unitNo);
        for (ApplyLine line : lines) {
            if (line == null || line.action() == null) {
                continue;
            }
            switch (line.action()) {
                case UPDATE -> amend(subscription, line, amendmentId, applyUnit, correlationId);
                case REMOVE, TERMINATE -> terminate(subscription, line, amendmentId, applyUnit, correlationId);
                case SWAP, UPGRADE -> supersede(subscription, line, amendmentId, applyUnit, correlationId);
                case ADD, NO_CHANGE, RENEW, DOWNGRADE, CONVERT_METRIC, SUSPEND, RESUME -> {
                    // These actions do not change the installed-asset row in this story.
                }
            }
        }
    }

    private void amend(Subscription subscription, ApplyLine line, String amendmentId, String applyUnit,
                       String correlationId) {
        Asset asset = find(subscription, line);
        if (asset == null || closed(asset)) {
            return;
        }
        BigDecimal quantity = line.endQty() == null ? asset.getQuantity() : line.endQty();
        LocalDate end = line.effectiveTo() == null ? asset.getEffectiveEnd() : line.effectiveTo();
        String product = blank(line.productExternalId()) ? asset.getProductRef() : line.productExternalId();
        if (asset.getStatus() == AssetStatus.AMENDED
                && same(asset.getQuantity(), quantity)
                && end.equals(asset.getEffectiveEnd())
                && sameText(asset.getProductRef(), product)) {
            return;
        }
        asset.setQuantity(quantity);
        asset.setEffectiveEnd(end);
        asset.setProductRef(product);
        asset.setStatus(AssetStatus.AMENDED);
        assets.save(asset);
        UUID eventId = UUID.randomUUID();
        LocalDate effective = line.effectiveFrom() == null ? asset.getEffectiveStart() : line.effectiveFrom();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), null)) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(), EventContractRegistry.ASSET_AMENDED_V1,
                    new AssetAmendedEvent(
                            asset.getUuid(), asset.getAccountId(), asset.getContractLineId(), asset.getSubscriptionId(),
                            asset.getEntitlementId(), product, quantity, AssetStatus.AMENDED.name(),
                            asset.getEffectiveStart(), end, amendmentId, applyUnit),
                    eventId);
        }
        history.apply(new AssetHistoryFact(
                eventId.toString(), AssetHistoryKind.AMENDED, asset.getUuid(), asset.getAccountId(), effective, end,
                amendmentId, applyUnit, null, null, asset.getContractLineId(), asset.getSubscriptionId(),
                product, quantity));
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
        UUID eventId = UUID.randomUUID();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), null)) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(), EventContractRegistry.ASSET_TERMINATED_V1,
                    new AssetTerminatedEvent(
                            asset.getUuid(), asset.getAccountId(), asset.getContractLineId(), asset.getSubscriptionId(),
                            asset.getProductRef(), AssetStatus.TERMINATED.name(), effective, amendmentId, applyUnit),
                    eventId);
        }
        history.apply(new AssetHistoryFact(
                eventId.toString(), AssetHistoryKind.TERMINATED, asset.getUuid(), asset.getAccountId(), effective,
                effective, amendmentId, applyUnit, null, null, asset.getContractLineId(), asset.getSubscriptionId(),
                asset.getProductRef(), asset.getQuantity()));
        audit.stage(asset, eventId, correlationId);
    }

    private void supersede(Subscription subscription, ApplyLine line, String amendmentId, String applyUnit,
                           String correlationId) {
        Asset predecessor = find(subscription, line);
        if (predecessor == null || predecessor.getStatus() == AssetStatus.SUPERSEDED) {
            return;
        }
        LocalDate effective = line.effectiveFrom() == null ? predecessor.getEffectiveEnd() : line.effectiveFrom();
        LocalDate end = line.effectiveTo() == null ? predecessor.getEffectiveEnd() : line.effectiveTo();
        BigDecimal quantity = line.endQty() == null ? predecessor.getQuantity() : line.endQty();
        String product = blank(line.productExternalId()) ? predecessor.getProductRef() : line.productExternalId();
        String successorLine = line.id() == null ? predecessor.getContractLineId() + ":successor" : line.id().toString();
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
        predecessor.setStatus(AssetStatus.SUPERSEDED);
        predecessor.setEffectiveEnd(effective);
        predecessor.setSupersededBy(successor.getUuid());
        assets.save(predecessor);
        UUID createdId = UUID.randomUUID();
        history.apply(new AssetCreatedEvent(
                successor.getUuid(), successor.getAccountId(), successor.getContractLineId(), subscription.getId(),
                null, product, quantity, AssetStatus.ACTIVE.name(), effective, end), createdId.toString());
        UUID eventId = UUID.randomUUID();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), null)) {
            publisher.publish(AGGREGATE_TYPE, predecessor.getUuid(), EventContractRegistry.ASSET_SUPERSEDED_V1,
                    new AssetSupersededEvent(
                            predecessor.getUuid(), predecessor.getAccountId(), successor.getUuid(), predecessor.getUuid(),
                            AssetStatus.SUPERSEDED.name(), effective, amendmentId, applyUnit),
                    eventId);
        }
        history.apply(new AssetHistoryFact(
                eventId.toString(), AssetHistoryKind.SUPERSEDED, predecessor.getUuid(), predecessor.getAccountId(),
                effective, effective, amendmentId, applyUnit, predecessor.getUuid(), successor.getUuid(),
                predecessor.getContractLineId(), subscription.getId(), predecessor.getProductRef(),
                predecessor.getQuantity()));
        audit.stage(predecessor, eventId, correlationId);
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
