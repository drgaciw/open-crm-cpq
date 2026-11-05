package com.opencrm.order.acceptance;

import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.common.order.AcceptedOrderSnapshot.Line;
import com.opencrm.common.order.ActivationAcknowledgment;
import com.opencrm.common.order.ActivationRequest;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.execution.ExecutionService;
import com.opencrm.order.finance.FinanceDtos;
import com.opencrm.order.finance.FinanceService;
import com.opencrm.order.fulfillment.EvidenceCommand;
import com.opencrm.order.fulfillment.EvidenceService;
import com.opencrm.order.security.OrderAccess;
import com.opencrm.order.service.OrderService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Composed real OMS services/PostgreSQL, synthetic manual authorities and trusted acknowledgments.
 * No quote/contract service process, broker, provider, HTTP or production identity is exercised. */
@Testcontainers
@Tag("integration")
@Timeout(120)
class PilotScenarioIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCommand("postgres", "-c", "max_connections=130");
    static final String QUOTE = "00000000-0000-4000-8000-000000000001";
    static final String ALIAS = "Q-COMPOSED-001";
    static final Instant OCCURRED = Instant.parse("2026-09-01T10:00:00Z");
    final JsonMapper json = JsonMapper.builder().build();
    JdbcTemplate db;
    OrderService orders;
    ExecutionService execution;
    EvidenceService evidence;
    FinanceService finance;

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("order_management").defaultSchema("order_management").load().migrate();
    }

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl() + "&currentSchema=order_management",
                postgres.getUsername(), postgres.getPassword());
        db = new JdbcTemplate(ds);
        db.execute("TRUNCATE snapshot_inbox,outbox_events,execution_inbox CASCADE");
        var manager = new DataSourceTransactionManager(ds);
        var access = mock(OrderAccess.class);
        when(access.actor()).thenReturn("synthetic-acceptance-operator");
        when(access.scopeId()).thenReturn("pilot");
        when(access.customerIds()).thenReturn(Set.of("customer"));
        orders = new OrderService(db, manager, json, access, true, "pilot");
        execution = new ExecutionService(db, manager, json, access, true, "pilot");
        evidence = new EvidenceService(db, manager, json, access, execution, "pilot");
        finance = new FinanceService(db, manager, json, access, orders, execution);
    }

    @Test void hundredConcurrentMixedAliasesCreateOneOrderAndInitialPlan() throws Exception {
        capture(physical());
        var ready = new CountDownLatch(100);
        var start = new CountDownLatch(1);
        Set<String> ids = new HashSet<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<String>>();
            for (int i = 0; i < 100; i++) {
                final int n = i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    return orders.fromQuote(new FromQuote("snapshot", n % 2 == 0 ? QUOTE : ALIAS, 1),
                            "conversion-" + n).id();
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            for (var result : results) ids.add(result.get(60, TimeUnit.SECONDS));
        }
        assertEquals(1, ids.size());
        assertEquals(1, count("orders"));
        assertEquals(1, count("order_lines"));
        assertEquals(4, count("order_tasks"));
        assertEquals(1, count("order_audit"));
        assertEquals(1, count("outbox_events"));
        assertEquals(100, count("order_idempotency"));
        var plan = orders.plan(ids.iterator().next());
        assertEquals(Set.of("ALLOCATION", "SHIPMENT", "DELIVERY", "FINANCE_ACK"),
                new HashSet<>(plan.stream().map(Task::kind).toList()));
        assertTrue(plan.stream().allMatch(t -> t.templateVersion() == 1));
    }

    @Test void mixedOrderCompletesOnlyAfterIndependentFulfillmentActivationAndFullFinance() {
        var source = mixed();
        String payload = capture(source);
        var order = submitted();
        String id = order.id();
        String hardware = line(id, "hardware");
        fact(id, hardware, "ALLOCATION", 2, "allocation", null, null);
        fact(id, hardware, "SHIPMENT", 2, "shipment", "allocation", null);
        assertNotEquals("COMPLETED", orders.get(id).status());
        fact(id, hardware, "DELIVERY", 2, "delivery", null, "shipment");
        fact(id, line(id, "service"), "SERVICE", 2, "service-proof", null, null);
        fact(id, line(id, "monthly"), "PROVISION", 2, "monthly-proof", null, null);
        assertNotEquals("COMPLETED", orders.get(id).status());
        fact(id, line(id, "annual"), "PROVISION", 2, "annual-proof", null, null);

        for (String sourceId : List.of("monthly", "annual")) {
            String line = line(id, sourceId);
            assertEquals(409, assertThrows(OrderProblem.class,
                    () -> finance.prepare(id, line, command(id, "too-early-" + sourceId))).status());
            String task = db.queryForObject("SELECT id FROM order_tasks WHERE line_id=? AND kind='ACTIVATION'",
                    String.class, line);
            var lease = execution.claim(task, "synthetic-worker", Duration.ofSeconds(30)).orElseThrow();
            execution.dispatch(lease);
            assertNotEquals("COMPLETED", orders.get(id).status());
            var ack = new ActivationAcknowledgment("ack-" + sourceId, lease.operationKey(), id, "pilot",
                    "synthetic-subscription-" + sourceId, "ACKNOWLEDGED", "synthetic-contract-proof-" + sourceId,
                    Instant.now());
            String hash = OrderService.hash(json.writeValueAsString(ack));
            execution.acceptActivationAck(ack, hash);
            execution.acceptActivationAck(ack, hash);
        }
        assertEquals(2, count("execution_inbox"));
        var activationBodies = db.queryForList("SELECT payload FROM outbox_events WHERE event_type='oms.activation-requested.v1'", String.class);
        assertEquals(2, activationBodies.size());
        for (String body : activationBodies) {
            var activation = json.readValue(body, ActivationRequest.class);
            var frozen = source.lines().stream().filter(l -> l.sourceLineId().equals(activation.line().sourceLineId())).findFirst().orElseThrow();
            assertEquals(frozen, activation.line());
            assertEquals(id, activation.orderId());
            assertEquals(QUOTE, activation.quoteId());
        }

        for (var frozen : source.lines()) {
            finance.prepare(id, line(id, frozen.sourceLineId()), command(id, "prepare-" + frozen.sourceLineId()));
        }
        assertNotEquals("COMPLETED", orders.get(id).status());
        for (var frozen : source.lines()) {
            acknowledge(id, frozen.sourceLineId(), "part1");
            assertNotEquals("COMPLETED", orders.get(id).status());
        }
        assertTrue(finance.list(id).stream().allMatch(r -> r.acknowledgedQuantity() == 1));
        for (int i = 0; i < source.lines().size(); i++) {
            acknowledge(id, source.lines().get(i).sourceLineId(), "part2");
            assertEquals(i == source.lines().size() - 1 ? "COMPLETED" : "IN_PROGRESS", orders.get(id).status());
        }
        assertEquals("ACKNOWLEDGED", orders.get(id).financeStatus());
        assertEquals(4, count("finance_requests"));
        assertEquals(8, count("finance_acknowledgments"));
        assertEquals(27498L, db.queryForObject("SELECT sum(total_cents) FROM finance_acknowledgments", Long.class));
        assertEquals(10L, db.queryForObject("SELECT sum(discount_cents) FROM finance_acknowledgments", Long.class));
        assertEquals(8L, db.queryForObject("SELECT sum(tax_cents) FROM finance_acknowledgments", Long.class));
        assertTrue(orders.plan(id).stream().allMatch(t -> t.status().equals("SUCCESS")));
        assertEquals(payload, db.queryForObject("SELECT snapshot_json FROM orders WHERE id=?", String.class, id));
        assertEquals(OrderService.hash(payload), db.queryForObject("SELECT snapshot_hash FROM orders WHERE id=?", String.class, id));
        for (var request : finance.list(id)) {
            var frozen = source.lines().stream().filter(l -> line(id, l.sourceLineId()).equals(request.lineId())).findFirst().orElseThrow();
            assertEquals(json.readTree(json.writeValueAsString(frozen)), json.readTree(request.payload()).path("sourceLine"));
            long total = new java.math.BigDecimal(frozen.total()).movePointRight(2).longValueExact();
            assertEquals(total, db.queryForObject("SELECT sum(total_cents) FROM finance_acknowledgments WHERE request_key=?", Long.class, request.requestKey()));
        }
    }

    @Test void simultaneousShipmentAndCancellationHaveOneVersionWinnerAndConserveQuantity() throws Exception {
        capture(physical());
        var order = submitted();
        String id = order.id(), line = order.lines().getFirst().id();
        fact(id, line, "ALLOCATION", 4, "allocation", null, null);
        long version = orders.get(id).version();
        var shipment = new EvidenceCommand(version, "race-shipment", "synthetic shipment authority", line,
                "SHIPMENT", 4, "shipment", OCCURRED, "allocation", null, null);
        var cancellation = new Cancellation(version, "cancel two unallocated units", "race-cancel", line, 2);
        var gate = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> shipped = pool.submit(() -> { gate.await(10, TimeUnit.SECONDS); return status(() -> evidence.record(id, shipment)); });
            Future<Integer> cancelled = pool.submit(() -> { gate.await(10, TimeUnit.SECONDS); return status(() -> orders.cancel(id, cancellation)); });
            int shipmentStatus = shipped.get(30, TimeUnit.SECONDS), cancellationStatus = cancelled.get(30, TimeUnit.SECONDS);
            assertEquals(Set.of(200, 409), Set.of(shipmentStatus, cancellationStatus));
            var current = orders.get(id).lines().getFirst();
            assertEquals(shipmentStatus == 200 ? 4 : 0, current.shipped());
            assertEquals(cancellationStatus == 200 ? 2 : 0, current.cancelled());
            assertEquals(10, current.shipped() + current.cancelled() + current.remaining());
            assertEquals(shipmentStatus == 200 ? 0 : 4, current.allocated());
            assertEquals(0, current.delivered());
            assertEquals(shipmentStatus == 200 ? 1 : 0, db.queryForObject("SELECT count(*) FROM fulfillment_evidence WHERE kind='SHIPMENT'", Integer.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM order_idempotency WHERE operation_key IN ('race-shipment','race-cancel')", Integer.class));
        }
    }

    private int status(Runnable action) { try { action.run(); return 200; } catch (OrderProblem e) { return e.status(); } }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private String line(String orderId, String source) { return db.queryForObject("SELECT id FROM order_lines WHERE order_id=? AND source_line_id=?", String.class, orderId, source); }
    private Command command(String id, String key) { return new Command(orders.get(id).version(), "synthetic acceptance evidence", key); }
    private View submitted() { var order = orders.fromQuote(new FromQuote("snapshot", QUOTE, 1), "convert"); return orders.submit(order.id(), command(order.id(), "submit")); }
    private String capture(AcceptedOrderSnapshot source) { String payload = json.writeValueAsString(source); orders.acceptTrustedSnapshot(payload, OrderService.hash(payload)); return payload; }
    private void fact(String id, String line, String kind, int quantity, String ref, String allocation, String shipment) {
        evidence.record(id, new EvidenceCommand(orders.get(id).version(), "evidence-" + ref, "synthetic manual authority", line, kind, quantity,
                ref, OCCURRED, allocation, shipment, kind.equals("ALLOCATION") ? Instant.now().plusSeconds(3600) : null));
    }
    private void acknowledge(String id, String sourceId, String part) {
        finance.acknowledge(id, new FinanceDtos.Acknowledgment(orders.get(id).version(), "synthetic actual finance acknowledgment",
                "finance-" + sourceId + "-" + part, line(id, sourceId), 1, "synthetic-invoice-" + sourceId + "-" + part,
                Instant.now(), null, "ACKNOWLEDGED"));
    }
    private AcceptedOrderSnapshot physical() {
        return snapshot(List.of(new Line("physical", "product", "Hardware", "PHYSICAL", "ONE_TIME", 10, "EA",
                "10.00", "0.00", "0.00", "100.00", "ONE_TIME", null, null)), "100.00", "0.00", "0.00", "100.00");
    }
    private AcceptedOrderSnapshot mixed() {
        return snapshot(List.of(
                new Line("hardware", "p1", "Hardware", "PHYSICAL", "ONE_TIME", 2, "EA", "10.00", "0.01", "0.02", "20.01", "ONE_TIME", null, null),
                new Line("service", "p2", "Installation", "SERVICE", "ONE_TIME", 2, "EA", "20.00", "0.02", "0.01", "39.99", "ONE_TIME", null, null),
                new Line("monthly", "p3", "Monthly license", "DIGITAL", "RECURRING", 2, "EA", "7.50", "0.03", "0.02", "14.99", "MONTHLY", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
                new Line("annual", "p4", "Annual license", "DIGITAL", "RECURRING", 2, "EA", "100.00", "0.04", "0.03", "199.99", "ANNUAL", LocalDate.of(2026, 2, 1), LocalDate.of(2028, 2, 1))),
                "275.00", "0.10", "0.08", "274.98");
    }
    private AcceptedOrderSnapshot snapshot(List<Line> lines, String subtotal, String discount, String tax, String total) {
        return new AcceptedOrderSnapshot(1, "snapshot", QUOTE, ALIAS, 1, "pilot", "customer", OCCURRED, "USD",
                subtotal, discount, tax, total, "synthetic address", "approval-reference", "tax-reference", "configuration-reference", lines);
    }
}
