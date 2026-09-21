package com.opencrm.order.operations;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

/** Scoped cached aggregates. Scrapes perform no database I/O and contain no customer identifiers. */
@Component
public class OperationalMetrics {
    private record Snapshot(double pending,double exhausted,double oldest,double unknown,double overdue,
            double financeExceptions,Instant sampledAt) {
        static Snapshot empty(){return new Snapshot(0,0,0,0,0,0,null);}
    }
    private final JdbcTemplate db;
    private final String scope;
    private final int scanLimit;
    private final int timeoutSeconds;
    private final AtomicReference<Snapshot> snapshot=new AtomicReference<>(Snapshot.empty());
    private final AtomicLong failures=new AtomicLong();
    public OperationalMetrics(JdbcTemplate db,MeterRegistry meters,
            @Value("${oms.scope-id:}") String scope,
            @Value("${oms.operations.scan-limit:100000}") int scanLimit,
            @Value("${oms.operations.query-timeout-seconds:5}") int timeoutSeconds) {
        if(scanLimit<1 || scanLimit>1000000 || timeoutSeconds<1 || timeoutSeconds>30)
            throw new IllegalArgumentException("Invalid bounded metrics policy");
        this.db=db;this.scope=scope;this.scanLimit=scanLimit;this.timeoutSeconds=timeoutSeconds;
        gauge(meters,"oms.outbox.pending",Snapshot::pending);
        gauge(meters,"oms.outbox.exhausted",Snapshot::exhausted);
        gauge(meters,"oms.outbox.oldest.pending.seconds",Snapshot::oldest);
        gauge(meters,"oms.tasks.unknown",Snapshot::unknown);
        gauge(meters,"oms.tasks.overdue",Snapshot::overdue);
        gauge(meters,"oms.finance.exceptions.open",Snapshot::financeExceptions);
        Gauge.builder("oms.operations.configured",this,m->m.configured()?1:0).register(meters);
        Gauge.builder("oms.operations.sample.age.seconds",this,m->{var at=m.snapshot.get().sampledAt();return at==null?Double.NaN:Math.max(0,Instant.now().getEpochSecond()-at.getEpochSecond());}).register(meters);
        Gauge.builder("oms.operations.refresh.failures",failures,AtomicLong::doubleValue).register(meters);
        Gauge.builder("oms.operations.scan.limit",this,m->m.scanLimit).register(meters);
    }
    private void gauge(MeterRegistry registry,String name,ToDoubleFunction<Snapshot> value) {
        Gauge.builder(name,snapshot,state->value.applyAsDouble(state.get())).register(registry);
    }
    private boolean configured(){return scope!=null && !scope.isBlank();}
    @Scheduled(fixedDelayString="${oms.operations.poll-ms:30000}")
    public void refresh() {
        if(!configured()) return;
        try {
            Snapshot sample=db.query(connection->{
                var statement=connection.prepareStatement("""
                    SELECT
                     (SELECT count(*) FROM (SELECT 1 FROM outbox_events e JOIN orders o ON o.id=e.aggregate_id
                       WHERE e.aggregate_type='Order' AND o.scope_id=? AND e.published=false AND e.is_deleted=false LIMIT ?) q) pending,
                     (SELECT count(*) FROM (SELECT 1 FROM outbox_events e JOIN orders o ON o.id=e.aggregate_id
                       WHERE e.aggregate_type='Order' AND o.scope_id=? AND e.published=false AND e.is_deleted=false AND e.exhausted_at IS NOT NULL LIMIT ?) q) exhausted,
                     COALESCE((SELECT GREATEST(0,EXTRACT(EPOCH FROM(clock_timestamp()-e.created_at))) FROM outbox_events e JOIN orders o ON o.id=e.aggregate_id
                       WHERE e.aggregate_type='Order' AND o.scope_id=? AND e.published=false AND e.is_deleted=false ORDER BY e.created_at,e.id LIMIT 1),0) oldest,
                     (SELECT count(*) FROM (SELECT 1 FROM order_tasks t JOIN orders o ON o.id=t.order_id
                       WHERE o.scope_id=? AND o.is_deleted=false AND t.status='UNKNOWN' LIMIT ?) q) unknown,
                     (SELECT count(*) FROM (SELECT 1 FROM task_operations t JOIN orders o ON o.id=t.order_id
                       WHERE o.scope_id=? AND o.is_deleted=false AND ((t.status='CLAIMED' AND t.lease_until<=clock_timestamp())
                         OR (t.status='WAITING_EXTERNAL' AND t.deadline_at<=clock_timestamp())) LIMIT ?) q) overdue,
                     (SELECT count(*) FROM (SELECT 1 FROM order_exceptions x JOIN orders o ON o.id=x.order_id
                       WHERE o.scope_id=? AND o.is_deleted=false AND x.status='OPEN' AND x.category LIKE 'FINANCE\\_%' ESCAPE '\\' LIMIT ?) q) finance
                    """);
                statement.setQueryTimeout(timeoutSeconds);
                int index=1;
                statement.setString(index++,scope);statement.setInt(index++,scanLimit);
                statement.setString(index++,scope);statement.setInt(index++,scanLimit);
                statement.setString(index++,scope);
                for(int i=0;i<3;i++){statement.setString(index++,scope);statement.setInt(index++,scanLimit);}
                return statement;
            },result->{result.next();return new Snapshot(result.getDouble("pending"),result.getDouble("exhausted"),result.getDouble("oldest"),result.getDouble("unknown"),result.getDouble("overdue"),result.getDouble("finance"),Instant.now());});
            snapshot.set(sample);
        } catch(DataAccessException failure) {
            // Preserve last successful sample, make staleness/failure visible, and avoid sensitive SQL/error logging.
            failures.incrementAndGet();
        }
    }
}
