package com.opencrm.order.execution;

import com.opencrm.common.order.*;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.Command;
import com.opencrm.order.dto.OrderDtos.View;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Durable orchestration only. No adapter response, timeout or dispatch is fulfillment evidence. */
@Service
public class ExecutionService {
    public record Lease(String operationKey, String taskId, String orderId, String token, int attempt) {}
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final OrderAccess access;
    private final boolean enabled;
    private final Set<String> scopes;
    private static final Set<String> MANUAL = Set.of("ALLOCATION", "SHIPMENT", "DELIVERY", "PROVISION", "SERVICE", "FINANCE_ACK");

    public ExecutionService(JdbcTemplate db, PlatformTransactionManager manager, ObjectMapper json, OrderAccess access,
            @Value("${oms.execution.enabled:false}") boolean enabled,
            @Value("${oms.execution.allowed-scopes:}") String scopes) {
        this.db = db;
        this.tx = new TransactionTemplate(manager);
        this.json = json;
        this.access = access;
        this.enabled = enabled;
        this.scopes = Set.copyOf(Arrays.asList(scopes.split(",")));
    }

    /** Internal worker boundary, constrained by deployment allowlist rather than user-supplied scope. */
    public Optional<Lease> claim(String taskId, String workerId, Duration duration) {
        if (!enabled) return Optional.empty();
        text(workerId, 200, "worker identity");
        if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofMinutes(5)) > 0)
            throw OrderProblem.invalid("Lease must be positive and at most five minutes");
        return tx.execute(t -> {
            var task = task(taskId);
            var order = lockOrder(str(task, "order_id"));
            if (!allowed(order) || held(order) || terminal(order)) return Optional.empty();
            task = task(taskId);
            if (!eligible(task)) return Optional.empty();
            var operation = operation(taskId);
            if (operation != null && !Set.of("CLAIMED", "RETRYABLE").contains(str(operation, "status"))) return Optional.empty();
            if (operation != null && operation.get("lease_until") != null && instant(operation, "lease_until").isAfter(now())) return Optional.empty();
            if (operation != null && operation.get("next_attempt_at") != null && instant(operation, "next_attempt_at").isAfter(now())) return Optional.empty();
            if (operation != null && num(operation, "attempt") >= 3) {
                db.update("UPDATE order_tasks SET status='RETRYABLE' WHERE id=?", taskId);
                exception(order, taskId, "ATTEMPTS_EXHAUSTED", "HIGH", "RETRY", "Undispatched claim attempts exhausted");
                return Optional.empty();
            }
            String key = ensureOperation(order, task);
            String token = UUID.randomUUID().toString();
            int attempt = operation == null ? 1 : num(operation, "attempt") + 1;
            db.update("UPDATE task_operations SET status='CLAIMED',lease_token=?,lease_until=?,worker_id=?,attempt=?,updated_at=CURRENT_TIMESTAMP WHERE operation_key=?",
                    token, Timestamp.from(now().plus(duration)), workerId, attempt, key);
            db.update("UPDATE order_tasks SET status='CLAIMED' WHERE id=?", taskId);
            return Optional.of(new Lease(key, taskId, str(order, "id"), token, attempt));
        });
    }

    /** Commits the intent before any external relay can send it. Stale workers cannot commit. */
    public void dispatch(Lease lease) {
        tx.executeWithoutResult(t -> {
            var order = lockOrder(lease.orderId());
            if (!allowed(order) || held(order) || terminal(order)) throw OrderProblem.conflict("Dispatch blocked by order state");
            requireLease(lease);
            var task = task(lease.taskId());
            ensureOperation(order, task);
            if (!eligible(task)) throw OrderProblem.conflict("Task dependency changed");
            String kind = str(task, "kind");
            if ("ACTIVATION".equals(kind)) {
                var line = line(str(task, "line_id"));
                if (num(line, "cancelled") > 0) {
                    db.update("UPDATE task_operations SET status='UNKNOWN',lease_token=NULL,lease_until=NULL WHERE task_id=?", lease.taskId());
                    db.update("UPDATE order_tasks SET status='UNKNOWN' WHERE id=?", lease.taskId());
                    exception(order, lease.taskId(), "PARTIAL_RECURRING_CANCELLATION", "HIGH", "REVIEW_SOURCE", "A newly approved source revision is required for cancelled recurring quantities");
                    changed(order, "ACTIVATION_HELD", "Recurring source terms require review", null, "oms-executor");
                    return;
                }
                activation(order, task, lease.operationKey());
            } else if (!MANUAL.contains(kind)) throw OrderProblem.invalid("Unsupported pinned task kind");
            db.update("UPDATE task_operations SET status='WAITING_EXTERNAL',deadline_at=?,updated_at=CURRENT_TIMESTAMP WHERE task_id=?",
                    Timestamp.from(now().plus(Duration.ofDays(1))), lease.taskId());
            db.update("UPDATE order_tasks SET status='WAITING_EXTERNAL',dispatched_at=COALESCE(dispatched_at,CURRENT_TIMESTAMP) WHERE id=?", lease.taskId());
            db.update("UPDATE orders SET status='IN_PROGRESS' WHERE id=?", lease.orderId());
            changed(order, "TASK_DISPATCHED", "Waiting for external evidence", lease.operationKey(), "oms-executor");
        });
    }

    public void dispatchReady(int limit) {
        if (!enabled) return;
        if (limit < 1 || limit > 100) throw OrderProblem.invalid("Batch size must be 1..100");
        var ids = db.queryForList("SELECT id FROM order_tasks WHERE status IN ('READY','CLAIMED') ORDER BY id LIMIT ?", String.class, limit);
        for (String id : ids) claim(id, "oms-executor", Duration.ofSeconds(30)).ifPresent(lease -> {
            try { dispatch(lease); }
            catch (OrderProblem conflict) { if (conflict.status() != 409) throw conflict; }
        });
    }

    /** Joins the evidence writer transaction. Caller persists source evidence and quantity changes first. */
    public void completeManualTask(String orderId, String lineId, String kind, String evidenceReference, Instant occurredAt) {
        text(evidenceReference, 500, "evidence reference");
        if (occurredAt == null || occurredAt.isAfter(now().plusSeconds(300))) throw OrderProblem.invalid("Valid evidence timestamp required");
        if (!MANUAL.contains(kind)) throw OrderProblem.invalid("This task requires a service acknowledgment");
        tx.executeWithoutResult(t -> {
            var order = lockOrder(orderId);
            access.require("FINANCE_ACK".equals(kind) ? "oms:finance" : "oms:evidence", str(order, "scope_id"), str(order, "customer_id"));
            var tasks = db.queryForList("SELECT * FROM order_tasks WHERE order_id=? AND line_id=? AND kind=?", orderId, lineId, kind);
            if (tasks.isEmpty()) throw OrderProblem.missing();
            var task = tasks.getFirst();
            if ("CANCELLED".equals(str(task, "status"))) throw OrderProblem.conflict("Cancelled task cannot receive success");
            if ("SUCCESS".equals(str(task, "status"))) return;
            if ("DRAFT".equals(str(order, "status"))) throw OrderProblem.conflict("Submit before applying fulfillment evidence");
            if ("FINANCE_ACK".equals(kind) && "BLOCKED".equals(str(task, "status"))) throw OrderProblem.conflict("Finance obligation is not released");
            var line = line(lineId);
            boolean full = completeQuantity(kind, line);
            if (!full && !partialQuantity(kind, line)) throw OrderProblem.invalid("No verified quantity for task");
            String key = ensureOperation(order, task);
            var operation = operation(str(task, "id"));
            if (evidenceReference.equals(operation.get("evidence_reference")) && occurredAt.equals(instant(operation, "completed_at"))) return;
            boolean unknown = "UNKNOWN".equals(str(operation, "status"));
            String status = full ? "SUCCESS" : unknown ? "UNKNOWN" : "WAITING_EXTERNAL";
            db.update("UPDATE task_operations SET status=?,evidence_reference=?,completed_at=?,lease_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE operation_key=?",
                    status, evidenceReference, Timestamp.from(occurredAt), key);
            db.update("UPDATE order_tasks SET status=?,dispatched_at=COALESCE(dispatched_at,CURRENT_TIMESTAMP) WHERE id=?", status, str(task, "id"));
            if (full) resolveTaskExceptions(str(task, "id"));
            db.update("UPDATE orders SET status='IN_PROGRESS' WHERE id=? AND status='SUBMITTED'", orderId);
            releaseDependencies(orderId);
            deriveTerminal(orderId);
            changed(order, full ? "TASK_EVIDENCED" : "PARTIAL_EVIDENCE", "External evidence recorded", evidenceReference, access.actor());
        });
    }

    /** An ambiguous outcome never becomes a retryable failure or an inferred success. */
    public void recordUnknown(Lease lease, String reason) {
        text(reason, 1000, "reason");
        tx.executeWithoutResult(t -> {
            var order = lockOrder(lease.orderId());
            requireLease(lease);
            unknown(order, lease.taskId(), reason);
            changed(order, "OUTCOME_UNKNOWN", reason, lease.operationKey(), "oms-executor");
        });
    }

    public void retry(String orderId, String taskId, Command command) {
        command(orderId, "RETRY:" + taskId, command, order -> {
            var task = task(taskId);
            if (!orderId.equals(str(task, "order_id"))) throw OrderProblem.missing();
            var operation = operation(taskId);
            if (operation == null || !"RETRYABLE".equals(str(task, "status")) || task.get("dispatched_at") != null)
                throw OrderProblem.conflict("Unknown or dispatched work must be reconciled before retry");
            if (held(order) || terminal(order)) throw OrderProblem.conflict("Order cannot dispatch");
            db.update("UPDATE task_operations SET status='RETRYABLE',attempt=0,next_attempt_at=CURRENT_TIMESTAMP,lease_token=NULL,lease_until=NULL WHERE task_id=?", taskId);
            db.update("UPDATE order_tasks SET status='READY' WHERE id=?", taskId);
            resolveTaskExceptions(taskId);
        });
    }

    public void reconcile(String orderId, Command command) {
        command(orderId, "RECONCILE", command, order -> reconcileLocked(order));
    }

    public void reconcileDue(int limit) {
        if (!enabled) return;
        if (limit < 1 || limit > 100) throw OrderProblem.invalid("Batch size must be 1..100");
        var ids = db.queryForList("SELECT DISTINCT order_id FROM task_operations WHERE (status='WAITING_EXTERNAL' AND deadline_at<clock_timestamp()) OR (status='CLAIMED' AND lease_until<clock_timestamp()) LIMIT ?", String.class, limit);
        for (String id : ids) tx.executeWithoutResult(t -> {
            var order = lockOrder(id);
            if (!allowed(order)) return;
            reconcileLocked(order);
            changed(order, "RECONCILED", "Scheduled reconciliation", null, "oms-executor");
        });
    }

    /** Authenticated broker topic and explicit deployment allowlist form the service authority boundary. */
    public void acceptActivationAck(ActivationAcknowledgment ack, String bodyHash) {
        if (ack == null || ack.eventId() == null || ack.eventId().isBlank() || !scopes.contains(ack.scopeId())) throw new OrderProblem(403, "Callback scope denied");
        if (ack.occurredAt() == null || ack.occurredAt().isAfter(now().plusSeconds(300))) throw OrderProblem.invalid("Invalid callback timestamp");
        text(bodyHash, 64, "callback hash");
        tx.executeWithoutResult(t -> {
            var order = lockOrder(ack.orderId());
            if (!allowed(order) || !str(order, "scope_id").equals(ack.scopeId())) throw OrderProblem.missing();
            var rows = db.queryForList("SELECT * FROM task_operations WHERE operation_key=? AND order_id=?", ack.operationKey(), ack.orderId());
            if (rows.isEmpty()) throw OrderProblem.missing();
            var operation = rows.getFirst();var task = task(str(operation, "task_id"));
            if (!"ACTIVATION".equals(str(task, "kind")) || task.get("dispatched_at") == null) throw OrderProblem.conflict("No matching activation dispatch");
            var prior = db.queryForList("SELECT body_hash FROM execution_inbox WHERE event_id=?", ack.eventId());
            if (!prior.isEmpty()) {
                if (!bodyHash.equals(prior.getFirst().get("body_hash"))) throw OrderProblem.conflict("Callback identity body conflict");
                return;
            }
            db.update("INSERT INTO execution_inbox(event_id,operation_key,body_hash) VALUES(?,?,?)", ack.eventId(), ack.operationKey(), bodyHash);
            if ("SUCCESS".equals(str(operation, "status"))) {
                if (!Objects.equals(operation.get("subscription_id"), ack.subscriptionId()) || !"ACKNOWLEDGED".equals(ack.status()) || !Objects.equals(operation.get("evidence_reference"),ack.evidenceReference())) {
                    exception(order, str(task, "id"), "CONTRADICTORY_ACK", "HIGH", "RECONCILE", "Activation acknowledgment contradicts persisted success");
                    changed(order, "CONTRADICTORY_ACK", "Activation acknowledgment contradicts persisted success", ack.operationKey(), "contract-service");
                }
                return;
            }
            if (!"ACKNOWLEDGED".equals(ack.status()) || ack.subscriptionId() == null || ack.subscriptionId().isBlank() || ack.evidenceReference() == null || ack.evidenceReference().isBlank()) {
                unknown(order, str(task, "id"), "Activation outcome requires reconciliation");
                changed(order, "ACTIVATION_UNKNOWN", "Contract acknowledgment did not establish success", ack.operationKey(), "contract-service");
                return;
            }
            db.update("UPDATE task_operations SET status='SUCCESS',subscription_id=?,evidence_reference=?,completed_at=?,lease_token=NULL,lease_until=NULL WHERE operation_key=?",
                    ack.subscriptionId(), ack.evidenceReference(), Timestamp.from(ack.occurredAt()), ack.operationKey());
            db.update("UPDATE order_tasks SET status='SUCCESS' WHERE id=?", str(task, "id"));
            resolveTaskExceptions(str(task, "id"));
            releaseDependencies(ack.orderId());deriveTerminal(ack.orderId());
            changed(order, "ACTIVATION_ACK", "Contract activation acknowledged", ack.evidenceReference(), "contract-service");
        });
    }

    private void activation(Map<String,Object> order, Map<String,Object> task, String key) {
        var source = json.readValue(str(order, "snapshot_json"), AcceptedOrderSnapshot.class);
        var line = line(str(task, "line_id"));
        var accepted = source.lines().stream().filter(l -> l.sourceLineId().equals(str(line, "source_line_id"))).findFirst().orElseThrow();
        if (!completeQuantity("PHYSICAL".equals(accepted.fulfillmentType()) ? "DELIVERY" : accepted.fulfillmentType().equals("DIGITAL") ? "PROVISION" : "SERVICE", line)) throw OrderProblem.conflict("Activation requires full verified fulfillment");
        var evidence = db.queryForList("SELECT op.evidence_reference,op.completed_at FROM task_operations op JOIN order_tasks t ON t.id=op.task_id WHERE t.order_id=? AND t.line_id=? AND t.kind IN ('DELIVERY','PROVISION','SERVICE') AND op.status='SUCCESS'", str(order, "id"), str(task, "line_id"));
        if (evidence.isEmpty() || evidence.getFirst().get("evidence_reference") == null) throw OrderProblem.conflict("Activation requires durable evidence");
        String eventId = UUID.randomUUID().toString();
        var request = new ActivationRequest(eventId, key, str(order, "id"), str(order, "scope_id"), str(order, "customer_id"), source.quoteId(), source.acceptedRevision(), accepted, str(evidence.getFirst(), "evidence_reference"), instant(evidence.getFirst(), "completed_at"));
        outbox(eventId, str(order, "id"), "oms.activation-requested.v1", json.writeValueAsString(request));
    }

    private void reconcileLocked(Map<String,Object> order) {
        String id = str(order, "id");
        var expired = db.queryForList("SELECT * FROM task_operations WHERE order_id=? AND status='WAITING_EXTERNAL' AND deadline_at<clock_timestamp()", id);
        for (var op : expired) unknown(order, str(op, "task_id"), "Acknowledgment deadline expired; outcome remains unknown");
        db.update("UPDATE order_tasks SET status='RETRYABLE' WHERE order_id=? AND id IN (SELECT task_id FROM task_operations WHERE status='CLAIMED' AND lease_until<clock_timestamp() AND attempt>=3)", id);
        db.update("UPDATE task_operations SET status='RETRYABLE',lease_token=NULL,lease_until=NULL WHERE order_id=? AND status='CLAIMED' AND lease_until<clock_timestamp() AND attempt>=3", id);
        releaseDependencies(id);deriveTerminal(id);
    }

    private void releaseDependencies(String orderId) {
        var tasks = db.queryForList("SELECT * FROM order_tasks WHERE order_id=? AND status='BLOCKED'", orderId);
        for (var task : tasks) if (eligible(task)) db.update("UPDATE order_tasks SET status='READY' WHERE id=?", str(task, "id"));
    }

    private boolean eligible(Map<String,Object> task) {
        if (!Set.of("READY", "BLOCKED", "CLAIMED").contains(str(task, "status"))) return false;
        var line = line(str(task, "line_id"));
        if (num(line, "cancelled") == num(line, "ordered")) return false;
        String kind = str(task, "kind");
        if ("SHIPMENT".equals(kind)) return num(line, "allocated") > 0;
        if ("DELIVERY".equals(kind)) return num(line, "shipped") > num(line, "delivered");
        return db.queryForObject("SELECT COUNT(*) FROM order_task_dependencies d JOIN order_tasks p ON p.id=d.predecessor_id WHERE d.task_id=? AND p.status NOT IN ('SUCCESS','CANCELLED')", Integer.class, str(task, "id")) == 0;
    }

    private boolean completeQuantity(String kind, Map<String,Object> line) {
        int target = num(line, "ordered") - num(line, "cancelled");
        return target > 0 && switch (kind) {
            case "ALLOCATION" -> num(line, "allocated") + num(line, "shipped") >= target;
            case "SHIPMENT" -> num(line, "shipped") >= target;
            case "DELIVERY" -> num(line, "delivered") >= target;
            case "PROVISION", "SERVICE" -> num(line, "fulfilled") >= target;
            // Finance writer must call only when its persisted line obligation has full acknowledgment.
            case "FINANCE_ACK" -> true;
            default -> false;
        };
    }

    private boolean partialQuantity(String kind, Map<String,Object> line) {
        return switch (kind) {
            case "ALLOCATION" -> num(line, "allocated") + num(line, "shipped") > 0;
            case "SHIPMENT" -> num(line, "shipped") > 0;
            case "DELIVERY" -> num(line, "delivered") > 0;
            case "PROVISION", "SERVICE" -> num(line, "fulfilled") > 0;
            default -> false;
        };
    }

    private String ensureOperation(Map<String,Object> order, Map<String,Object> task) {
        var line = line(str(task, "line_id"));
        String key = OrderService.hash(String.join("|", str(order, "scope_id"), str(order, "quote_id"), order.get("accepted_revision").toString(), str(line, "source_line_id"), str(task, "kind"), task.get("template_version").toString()));
        String body = OrderService.hash(str(order, "snapshot_hash") + "|" + key);
        var previous = operation(str(task, "id"));
        if (previous != null) {
            if (!key.equals(previous.get("operation_key")) || !body.equals(previous.get("body_hash"))) throw OrderProblem.conflict("Pinned operation body changed");
        } else db.update("INSERT INTO task_operations(operation_key,task_id,order_id,body_hash,status) VALUES(?,?,?,?,'RETRYABLE')", key, str(task, "id"), str(order, "id"), body);
        return key;
    }

    private void requireLease(Lease lease) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM task_operations WHERE operation_key=? AND task_id=? AND order_id=? AND lease_token=? AND lease_until>clock_timestamp() AND status IN ('CLAIMED','WAITING_EXTERNAL')", Integer.class,
                lease.operationKey(), lease.taskId(), lease.orderId(), lease.token());
        if (count != 1) throw OrderProblem.conflict("Worker lease expired or superseded");
    }

    private void unknown(Map<String,Object> order, String taskId, String reason) {
        db.update("UPDATE task_operations SET status='UNKNOWN',lease_token=NULL,lease_until=NULL WHERE task_id=?", taskId);
        db.update("UPDATE order_tasks SET status='UNKNOWN' WHERE id=? AND status<>'SUCCESS'", taskId);
        exception(order, taskId, "UNKNOWN_OUTCOME", "HIGH", "RECONCILE", reason);
    }

    private void deriveTerminal(String orderId) {
        if (db.queryForObject("SELECT COUNT(*) FROM order_tasks WHERE order_id=? AND status NOT IN ('SUCCESS','CANCELLED')", Integer.class, orderId) > 0) return;
        int pendingLines = db.queryForObject("SELECT COUNT(*) FROM order_lines l WHERE order_id=? AND (cancelled+fulfilled<>ordered OR (shipped>0 AND delivered<>shipped))", Integer.class, orderId);
        if (pendingLines > 0) return;
        int cancelled = db.queryForObject("SELECT COALESCE(SUM(cancelled),0) FROM order_lines WHERE order_id=?", Integer.class, orderId);
        int fulfilled = db.queryForObject("SELECT COALESCE(SUM(fulfilled),0) FROM order_lines WHERE order_id=?", Integer.class, orderId);
        if (fulfilled == 0) return; // Cancellation service owns release reconciliation and CANCELLED transition.
        db.update("UPDATE orders SET status=?,finance_status='ACKNOWLEDGED' WHERE id=?", cancelled > 0 ? "PARTIALLY_COMPLETED" : "COMPLETED", orderId);
    }

    private void command(String orderId, String action, Command command, java.util.function.Consumer<Map<String,Object>> mutation) {
        text(command.reason(), 2000, "reason");text(command.operationKey(), 200, "operation key");
        try {
            tx.executeWithoutResult(t -> {
                var order = lockOrder(orderId);String scope = str(order, "scope_id");
                access.require("oms:recover", scope, str(order, "customer_id"));
                String body = OrderService.hash(json.writeValueAsString(List.of(orderId, action, command)));
                var prior = db.queryForList("SELECT body_hash FROM order_idempotency WHERE scope_id=? AND operation_key=?", scope, command.operationKey());
                if (!prior.isEmpty()) { if (!body.equals(prior.getFirst().get("body_hash"))) throw OrderProblem.conflict("Operation key body conflict");return; }
                if (((Number) order.get("version")).longValue() != command.expectedVersion()) throw OrderProblem.conflict("Order version changed");
                mutation.accept(order);
                db.update("INSERT INTO order_idempotency(scope_id,operation_key,body_hash,order_id) VALUES(?,?,?,?)", scope, command.operationKey(), body, orderId);
                changed(order, action.split(":")[0], command.reason(), null, access.actor());
            });
        } catch (DataIntegrityViolationException conflict) { throw OrderProblem.conflict("Concurrent operation key conflict"); }
    }

    private void exception(Map<String,Object> order, String taskId, String category, String severity, String next, String reason) {
        db.update("INSERT INTO order_exceptions(id,order_id,task_id,category,severity,next_action,reason) VALUES(?,?,?,?,?,?,?) ON CONFLICT(order_id,task_id,category) DO UPDATE SET status='OPEN',last_occurred_at=CURRENT_TIMESTAMP,version=order_exceptions.version+1,reason=EXCLUDED.reason",
                UUID.randomUUID().toString(), str(order, "id"), taskId, category, severity, next, reason);
    }

    private void resolveTaskExceptions(String taskId) {
        db.update("UPDATE order_exceptions SET status='RESOLVED',last_occurred_at=CURRENT_TIMESTAMP,version=version+1 WHERE task_id=? AND category IN ('UNKNOWN_OUTCOME','ATTEMPTS_EXHAUSTED') AND status='OPEN'", taskId);
    }

    private void changed(Map<String,Object> order, String action, String reason, String reference, String actor) {
        String id = str(order, "id");
        long version = db.queryForObject("SELECT version FROM orders WHERE id=?", Long.class, id) + 1;
        db.update("UPDATE orders SET version=?,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?", version, actor, id);
        db.update("INSERT INTO order_audit(id,order_id,actor,occurred_at,action,reason,version,evidence_reference) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,?,?)", UUID.randomUUID().toString(), id, actor, action, reason, version, reference);
        String event = UUID.randomUUID().toString();
        outbox(event, id, "oms.order-updated.v1", json.writeValueAsString(Map.of("eventId", event, "orderId", id, "scopeId", str(order,"scope_id"), "customerId", str(order,"customer_id"), "version", version, "action", action)));
    }

    private void outbox(String event, String orderId, String type, String payload) {
        db.update("INSERT INTO outbox_events(uuid,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES(?,'Order',?,?,?,CURRENT_TIMESTAMP)", event, orderId, type, payload);
    }
    private boolean allowed(Map<String,Object> order) { return scopes.contains(str(order, "scope_id")) && !str(order, "scope_id").isBlank(); }
    private boolean terminal(Map<String,Object> order) { return !Set.of("SUBMITTED", "IN_PROGRESS").contains(str(order, "status")); }
    private boolean held(Map<String,Object> order) { return Boolean.TRUE.equals(order.get("manual_hold")) || db.queryForObject("SELECT COUNT(*) FROM order_holds WHERE order_id=?", Integer.class, str(order, "id")) > 0; }
    private Map<String,Object> lockOrder(String id) { return one("SELECT * FROM orders WHERE id=? AND is_deleted=false FOR UPDATE", id); }
    private Map<String,Object> task(String id) { return one("SELECT * FROM order_tasks WHERE id=?", id); }
    private Map<String,Object> line(String id) { return one("SELECT * FROM order_lines WHERE id=?", id); }
    private Map<String,Object> operation(String taskId) { var rows = db.queryForList("SELECT * FROM task_operations WHERE task_id=?", taskId);return rows.isEmpty() ? null : rows.getFirst(); }
    private Map<String,Object> one(String query, Object... args) {var rows=db.queryForList(query,args);if(rows.isEmpty()) throw OrderProblem.missing();return rows.getFirst();}
    private static String str(Map<String,Object> row, String key) {return (String)row.get(key);}
    private static int num(Map<String,Object> row, String key) {return ((Number)row.get(key)).intValue();}
    private static Instant instant(Map<String,Object> row, String key) {var value=row.get(key);return value==null?null:((Timestamp)value).toInstant();}
    private static Instant now() {return Instant.now();}
    private static void text(String value,int max,String label) {if(value==null || value.isBlank() || value.length()>max) throw OrderProblem.invalid("Invalid "+label);}
}
