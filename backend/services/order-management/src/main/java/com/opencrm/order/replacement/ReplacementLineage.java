package com.opencrm.order.replacement;

import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.order.domain.OrderProblem;
import org.springframework.jdbc.core.JdbcTemplate;

/** Called within conversion's transaction; the predecessor lock serializes permit consumption. */
public final class ReplacementLineage {
 private ReplacementLineage() {}
 public record Permit(String requestId,String predecessorId) {}

 public static Permit requirePermit(JdbcTemplate db,AcceptedOrderSnapshot snapshot) {
  var rows=db.queryForList("""
   SELECT r.id,r.order_id FROM order_replacement_requests r JOIN orders o ON o.id=r.order_id
   WHERE r.scope_id=? AND r.customer_id=? AND r.quote_id=? AND r.accepted_revision=?
    AND o.status='CANCELLED' AND o.is_deleted=false
   FOR UPDATE OF o
   """,snapshot.scopeId(),snapshot.customerId(),snapshot.quoteId(),snapshot.acceptedRevision()-1);
  if(rows.isEmpty()) throw OrderProblem.conflict("Previous accepted revision requires an authorized replacement request");
  var row=rows.getFirst();
  String predecessor=(String)row.get("order_id");
  if(db.queryForObject("SELECT COUNT(*) FROM order_replacement_lineage WHERE predecessor_order_id=?",Integer.class,predecessor)>0)
   throw OrderProblem.conflict("Replacement request already consumed");
  return new Permit((String)row.get("id"),predecessor);
 }
 public static void link(JdbcTemplate db,Permit permit,String successorId) {
  db.update("INSERT INTO order_replacement_lineage(predecessor_order_id,successor_order_id,replacement_request_id) VALUES(?,?,?)",permit.predecessorId(),successorId,permit.requestId());
 }
}
