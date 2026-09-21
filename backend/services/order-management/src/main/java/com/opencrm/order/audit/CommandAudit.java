package com.opencrm.order.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One immutable receipt per accepted command, including nested version advances. */
public final class CommandAudit {
    private CommandAudit() {}
    public static void record(JdbcTemplate db,String orderId,String operationKey,String actor,String reason,long beforeVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Command audit requires the mutation transaction");
        }
        int inserted=db.update("""
            INSERT INTO order_command_audit(scope_id,operation_key,order_id,actor,reason,before_version,after_version)
            SELECT scope_id,?,id,?,?,?,version FROM orders WHERE id=?
            """,operationKey,actor,reason,beforeVersion,orderId);
        if (inserted!=1) throw new IllegalStateException("Command audit requires an existing order");
    }
}
