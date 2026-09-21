package com.opencrm.order.recovery;
/** One predicate for daily findings and operator resolution; amounts remain exact integer cents. */
final class RecoveryFacts {
 private RecoveryFacts() {}
 static final String UNADJUDICATED_CONFLICTS = "SELECT COUNT(*) FROM fulfillment_evidence e JOIN fulfillment_evidence_state s ON s.evidence_id=e.id WHERE e.order_id=? AND s.status='CONFLICT' AND NOT EXISTS(SELECT 1 FROM recovery_evidence_adjudications a WHERE a.evidence_id=e.id AND a.order_id=e.order_id)";
 static final String FINANCE_MISMATCH = """
   SELECT COUNT(*) FROM finance_requests r
   CROSS JOIN LATERAL (
     SELECT COALESCE(SUM(quantity),0) AS quantity,
            COALESCE(SUM(gross_cents),0) AS gross,
            COALESCE(SUM(discount_cents),0) AS discount,
            COALESCE(SUM(tax_cents),0) AS tax,
            COALESCE(SUM(total_cents),0) AS total
     FROM finance_acknowledgments a
     WHERE a.request_key=r.request_key AND a.outcome='ACKNOWLEDGED'
   ) acknowledged
   WHERE r.order_id=? AND (
     acknowledged.quantity<>r.quantity OR acknowledged.gross<>r.gross_cents OR
     acknowledged.discount<>r.discount_cents OR acknowledged.tax<>r.tax_cents OR
     acknowledged.total<>r.total_cents)
   """;
}
