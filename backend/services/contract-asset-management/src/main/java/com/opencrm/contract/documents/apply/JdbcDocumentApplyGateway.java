package com.opencrm.contract.documents.apply;

import com.opencrm.common.commercial.CommercialDigest;
import com.opencrm.contract.domain.documents.ActionCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Advisory locks and the rate-period line trace. Locks last until the transaction commits. */
@Service
public class JdbcDocumentApplyGateway implements DocumentApplyGateway {

    private final JdbcTemplate jdbc;

    @Autowired
    public JdbcDocumentApplyGateway(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockDocument(UUID documentId) {
        lock("document:" + documentId);
    }

    @Override
    public void lockSubscription(String subscriptionUuid) {
        lock("subscription:" + subscriptionUuid);
    }

    @Override
    public Optional<ApplyTarget> subscription(String subscriptionUuid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, uuid, commercial_revision
                    from subscriptions
                    where uuid = ? and is_deleted = false
                    """, (rs, row) -> new ApplyTarget(rs.getLong("id"), rs.getString("uuid"),
                    rs.getLong("commercial_revision")), subscriptionUuid));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public boolean targetBelongsToContract(long subscriptionId, UUID contractId, UUID tenantId) {
        if (contractId == null || tenantId == null) return false;
        return !jdbc.query("""
                select c.id from subscriptions s join contracts c on c.id = s.contract_id
                where s.id = ? and s.is_deleted = false and c.id = ? and c.tenant_id = ?
                for share of c
                """, (rs, row) -> rs.getObject(1, UUID.class), subscriptionId, contractId, tenantId).isEmpty();
    }

    @Override
    public String contractCustomerAccountBlock(long subscriptionId) {
        List<AccountLink> rows = jdbc.query("""
                select s.contract_id,
                       s.customer_account_id as subscription_account,
                       c.customer_account_id as contract_account
                from subscriptions s
                left join contracts c on c.id = s.contract_id
                where s.id = ? and s.is_deleted = false
                """, (rs, row) -> new AccountLink(
                rs.getObject("contract_id", UUID.class),
                rs.getObject("subscription_account", UUID.class),
                rs.getObject("contract_account", UUID.class)), subscriptionId);
        if (rows.isEmpty()) {
            return null;
        }
        AccountLink link = rows.getFirst();
        if (link.contractId() == null) {
            return null;
        }
        if (link.subscriptionAccount() == null) {
            return "CUSTOMER_ACCOUNT_UNSET";
        }
        if (link.contractAccount() == null || !link.subscriptionAccount().equals(link.contractAccount())) {
            return "CUSTOMER_ACCOUNT_MISMATCH";
        }
        return null;
    }

    private record AccountLink(UUID contractId, UUID subscriptionAccount, UUID contractAccount) {
    }

    @Override
    public List<ApplyLine> lines(UUID documentId, int revisionNo) {
        return jdbc.query("""
                select id, line_no, application_unit_no, action_code, source_accepted_line_uuid, end_qty,
                       effective_from, effective_to, pricebook_version, product_external_id, unit_price
                from amendment_document_lines
                where document_id = ? and revision_no = ?
                order by line_no
                """, (rs, row) -> new ApplyLine(
                rs.getObject("id", UUID.class),
                rs.getInt("line_no"),
                rs.getInt("application_unit_no"),
                ActionCode.valueOf(rs.getString("action_code")),
                rs.getString("source_accepted_line_uuid"),
                rs.getBigDecimal("end_qty"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class),
                rs.getString("pricebook_version"),
                rs.getString("product_external_id"),
                rs.getBigDecimal("unit_price")), documentId, revisionNo);
    }

    @Override
    public void bindAmendmentLine(long periodId, UUID lineId, int revisionNo) {
        jdbc.update("""
                update subscription_rate_periods
                set amendment_document_line_id = ?, revision_no = ?
                where id = ?
                """, lineId, revisionNo, periodId);
    }

    private void lock(String seed) {
        String hex = CommercialDigest.sha256(seed.getBytes(StandardCharsets.UTF_8));
        long key = ByteBuffer.wrap(HexFormat.of().parseHex(hex.substring(0, 16))).getLong();
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, key);
    }
}
