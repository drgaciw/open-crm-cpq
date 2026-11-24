package com.opencrm.contract.asset;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** CAM's internal transaction boundary for an authoritative initial line activation. */
@Service
public class EffectiveAssetActivation {
    private static final String ACTIVATION_EVIDENCE = """
            select s.id as subscription_id, l.id as contract_line_id, c.id as contract_id, c.tenant_id,
                   c.customer_account_id, s.customer_id as external_customer_id,
                   l.quote_id, l.accepted_line_uuid, l.product_external_id as product_ref, l.quantity,
                   greatest(l.effective_from, c.start_date, s.start_date, cast(? as date)) as effective_start,
                   least(l.effective_to, c.end_date, s.end_date) as effective_end
              from subscriptions s
              join contract_lines l on l.id = s.contract_line_id
              join contracts c on c.id = l.contract_id
             where s.id = ? and l.id = ?
               and s.contract_id = c.id and s.tenant_id = c.tenant_id and l.tenant_id = c.tenant_id
               and s.customer_account_id = c.customer_account_id
               and s.quote_id = l.quote_id and btrim(l.quote_id) <> ''
               and btrim(l.accepted_line_uuid) not in ('', 'legacy-aggregate')
               and s.status = 'ACTIVE' and l.status = 'ACTIVE' and c.status = 'ACTIVE' and not s.is_deleted
               and btrim(s.customer_id) <> '' and btrim(l.product_external_id) <> '' and l.quantity > 0
            """;
    private static final RowMapper<Receipt> RECEIPT = (row, index) -> new Receipt(
            row.getObject("activation_id", UUID.class), row.getObject("activated_on", LocalDate.class),
            Evidence.read(row));

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final AssetRepository assets;
    private final AssetEventPublisher events;
    private final AssetAuditOutbox audit;
    private final AssetHistoryProjection history;
    private final boolean legacyWriterEnabled;

    public EffectiveAssetActivation(JdbcTemplate jdbc, EntityManager entityManager, AssetRepository assets,
                                    AssetEventPublisher events, AssetAuditOutbox audit, AssetHistoryProjection history,
                                    @Value("${opencrm.assets.legacy-acceptance-write-enabled:false}")
                                    boolean legacyWriterEnabled) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.assets = assets;
        this.events = events;
        this.audit = audit;
        this.history = history;
        this.legacyWriterEnabled = legacyWriterEnabled;
    }

    /**
     * Requires the caller's authoritative activation transaction. The origin owns the per-line activation ID and
     * activation date. Identity, product, quantity and term come from CAM's linked records.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(long subscriptionId, UUID contractLineId, UUID activationId, LocalDate activatedOn) {
        requireLegacyWriterDisabled();
        Objects.requireNonNull(contractLineId, "contract line ID is required");
        Objects.requireNonNull(activationId, "activation ID is required");
        Objects.requireNonNull(activatedOn, "activation date is required");
        Optional<Receipt> existing = findReceipt(contractLineId, activationId);
        if (existing.isPresent()) {
            requireSameActivation(existing.get(), subscriptionId, contractLineId, activationId, activatedOn);
            return;
        }

        entityManager.flush();
        Evidence evidence = evidence(subscriptionId, contractLineId, activatedOn, false)
                .orElseThrow(() -> new IllegalArgumentException("Linked active subscription, contract and line required"));
        int inserted = jdbc.update("""
                insert into asset_activation_receipts
                    (activation_id, contract_line_id, subscription_id, contract_id, tenant_id, customer_account_id,
                     external_customer_id, quote_id, accepted_line_uuid, product_ref, quantity, activated_on,
                     effective_start, effective_end)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """, activationId, contractLineId, subscriptionId, evidence.contractId(), evidence.tenantId(),
                evidence.accountId(), evidence.externalCustomerId(), evidence.quoteId(), evidence.acceptedLineUuid(),
                evidence.productRef(), evidence.quantity(), activatedOn, evidence.start(), evidence.end());
        if (inserted == 0) {
            Receipt receipt = findReceipt(contractLineId, activationId).orElseThrow();
            requireSameActivation(receipt, subscriptionId, contractLineId, activationId, activatedOn);
        }
    }

    /** Claims one due receipt. The asset, history, audit, event and outcome commit together. */
    @Transactional
    boolean applyNext(LocalDate asOf) {
        requireLegacyWriterDisabled();
        Objects.requireNonNull(asOf, "processing date is required");
        Optional<Receipt> due = jdbc.query("""
                select * from asset_activation_receipts
                 where status = 'PENDING' and effective_start <= ?
                 order by effective_start, activation_id
                 limit 1 for update
                """, RECEIPT, asOf).stream().findFirst();
        if (due.isEmpty()) {
            return false;
        }

        Receipt receipt = due.get();
        Evidence saved = receipt.evidence();
        Optional<Evidence> current = evidence(saved.subscriptionId(), saved.lineId(), receipt.activatedOn(), true);
        if (current.isEmpty() || !saved.equals(current.get()) || asOf.isAfter(saved.end())) {
            markForReconciliation(receipt, "ACTIVATION_EVIDENCE_CHANGED");
            return true;
        }

        Boolean hasAsset = jdbc.queryForObject("""
                select exists(
                    select 1 from assets where subscription_id = ? or contract_line_id = ?
                )
                """, Boolean.class, saved.subscriptionId(), saved.lineId().toString());
        if (Boolean.TRUE.equals(hasAsset)) {
            markForReconciliation(receipt, "PREEXISTING_ASSET");
            return true;
        }

        Asset asset = assets.save(Asset.builder()
                .accountId(saved.externalCustomerId())
                .contractLineId(saved.lineId().toString())
                .subscriptionId(saved.subscriptionId())
                .productRef(saved.productRef())
                .quantity(saved.quantity())
                .status(AssetStatus.ACTIVE)
                .effectiveStart(saved.start())
                .effectiveEnd(saved.end())
                .build());
        String originId = receipt.activationId().toString();
        AssetEventPublisher.Created created = events.publishCreated(asset, originId, originId);
        history.apply(created.event(), created.eventId().toString());
        audit.stage(asset, created.eventId(), originId);
        entityManager.flush();
        jdbc.update("""
                update asset_activation_receipts
                   set status = 'APPLIED', asset_id = ?, created_event_id = ?, processed_at = now()
                 where activation_id = ?
                """, asset.getUuid(), created.eventId().toString(), receipt.activationId());
        return true;
    }

    private Optional<Receipt> findReceipt(UUID lineId, UUID activationId) {
        return jdbc.query("""
                select * from asset_activation_receipts
                 where contract_line_id = ? or activation_id = ?
                """, RECEIPT, lineId, activationId).stream().findFirst();
    }

    private Optional<Evidence> evidence(long subscriptionId, UUID lineId, LocalDate activatedOn, boolean lock) {
        String statement = lock ? ACTIVATION_EVIDENCE + " for no key update of s, l, c" : ACTIVATION_EVIDENCE;
        return jdbc.query(statement, (row, index) -> Evidence.read(row), activatedOn, subscriptionId, lineId)
                .stream().filter(evidence -> !evidence.end().isBefore(evidence.start())).findFirst();
    }

    private void markForReconciliation(Receipt receipt, String reason) {
        jdbc.update("""
                update asset_activation_receipts
                   set status = 'NEEDS_RECONCILIATION', reconciliation_reason = ?, processed_at = now()
                 where activation_id = ?
                """, reason, receipt.activationId());
    }

    private void requireLegacyWriterDisabled() {
        if (legacyWriterEnabled) {
            throw new IllegalStateException("Effective activation requires the legacy asset writer to be disabled");
        }
    }

    private static void requireSameActivation(Receipt receipt, long subscriptionId, UUID contractLineId,
                                              UUID activationId, LocalDate activatedOn) {
        if (receipt.evidence().subscriptionId() != subscriptionId || !receipt.evidence().lineId().equals(contractLineId)
                || !receipt.activationId().equals(activationId) || !receipt.activatedOn().equals(activatedOn)) {
            throw new IllegalArgumentException("Activation conflicts with existing receipt");
        }
    }

    private record Receipt(UUID activationId, LocalDate activatedOn, Evidence evidence) {
    }

    private record Evidence(long subscriptionId, UUID lineId, UUID contractId, UUID tenantId, UUID accountId,
                            String externalCustomerId, String quoteId, String acceptedLineUuid, String productRef,
                            BigDecimal quantity, LocalDate start, LocalDate end) {
        static Evidence read(ResultSet row) throws SQLException {
            return new Evidence(row.getLong("subscription_id"), row.getObject("contract_line_id", UUID.class),
                    row.getObject("contract_id", UUID.class), row.getObject("tenant_id", UUID.class),
                    row.getObject("customer_account_id", UUID.class), row.getString("external_customer_id"),
                    row.getString("quote_id"), row.getString("accepted_line_uuid"), row.getString("product_ref"),
                    row.getBigDecimal("quantity"),
                    row.getObject("effective_start", LocalDate.class), row.getObject("effective_end", LocalDate.class));
        }
    }
}
