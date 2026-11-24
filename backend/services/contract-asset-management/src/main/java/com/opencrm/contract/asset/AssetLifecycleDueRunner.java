package com.opencrm.contract.asset;

import com.opencrm.contract.documents.apply.AssetLifecycleReadiness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies due lifecycle rows in order. Does nothing while {@link AssetLifecycleReadiness}
 * reports the hold. A failure marks that row FAILED, audits {@code ASSET_LIFECYCLE_FAILED}, and leaves other lines free.
 * The same line waits while an earlier row is still PENDING or FAILED.
 */
@Service
public class AssetLifecycleDueRunner {

    /** Commercial audit code staged through {@link AssetAuditOutbox} when a due row fails. The event shape is unchanged. */
    public static final String AUDIT_FAILED = "ASSET_LIFECYCLE_FAILED";

    private final AssetLifecycleReadiness readiness;
    private final AssetLifecycleChangeRepository changes;
    private final List<AssetLifecycleChangeApplier> appliers;
    private final AssetAuditOutbox audit;
    private final Clock clock;
    private final int batchSize;

    @Autowired
    public AssetLifecycleDueRunner(AssetLifecycleReadiness readiness,
                                   AssetLifecycleChangeRepository changes,
                                   List<AssetLifecycleChangeApplier> appliers,
                                   AssetAuditOutbox audit,
                                   @Value("${opencrm.asset.lifecycle.batch-size:50}") int batchSize) {
        this(readiness, changes, appliers, audit, Clock.systemUTC(), batchSize);
    }

    AssetLifecycleDueRunner(AssetLifecycleReadiness readiness,
                            AssetLifecycleChangeRepository changes,
                            List<AssetLifecycleChangeApplier> appliers,
                            AssetAuditOutbox audit,
                            Clock clock,
                            int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("lifecycle batch size must be positive");
        }
        this.readiness = readiness;
        this.changes = changes;
        this.appliers = appliers;
        this.audit = audit;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * @return how many rows were applied in this call
     */
    @Transactional
    public int runDue() {
        return 0;
    }

    private boolean earlierBlocking(AssetLifecycleChange change) {
        return changes.existsEarlierBlocking(
                change.getSubscriptionId(), change.getContractLineId(),
                change.getEffectiveDate(), change.getSequenceNo());
    }

    private AssetLifecycleChangeApplier applierFor(AssetLifecycleChangeKind kind) {
        for (AssetLifecycleChangeApplier applier : appliers) {
            if (applier.kind() == kind) {
                return applier;
            }
        }
        throw new IllegalStateException("no applier for " + kind);
    }

    private void auditFailure(AssetLifecycleChange change) {
        Asset shadow = new Asset();
        shadow.setUuid(change.getAssetUuid() != null ? change.getAssetUuid() : change.getUuid());
        shadow.setAccountId(change.getAccountId());
        shadow.setContractLineId(change.getContractLineId());
        shadow.setSubscriptionId(change.getSubscriptionId());
        audit.stage(shadow, UUID.randomUUID(), AUDIT_FAILED);
    }

    private static String lineKey(AssetLifecycleChange change) {
        return change.getSubscriptionId() + "\u0000" + change.getContractLineId();
    }
}
