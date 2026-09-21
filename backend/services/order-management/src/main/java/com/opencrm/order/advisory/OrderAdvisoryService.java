package com.opencrm.order.advisory;

import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.order.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.opencrm.order.advisory.OrderAdvisoryQuestions.*;

/** Shadow-only adapter. No HTTP reads or commands depend on this service. */
@Service
@ConditionalOnProperty(prefix="opencrm.jev", name={"enabled","oms-exception-triage.enabled"}, havingValue="true")
public class OrderAdvisoryService implements OrderAdvisoryTrigger {
    private final JevClient client;
    private final JevProperties properties;
    private final JevAdvisoryRunner runner;
    private final OrderAdvisoryRepository repository;
    private final String scope;
    private final Clock clock;
    private final Duration ttl;
    private final Counter completed;
    private final Counter failed;
    private final Counter duplicates;
    private boolean scopeConfigurationValid = true;

    /** This pilot is single-scope. Reject mismatched recovery configuration without failing OMS. */
    @Autowired
    public void configureRecoveryScopes(@Value("${oms.recovery.scopes:}") String scopes) {
        scopeConfigurationValid = java.util.Arrays.stream(scopes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).allMatch(scope::equals);
    }

    @Autowired
    public OrderAdvisoryService(JevClient client, JevProperties properties, JevAdvisoryRunner runner,
                               OrderAdvisoryRepository repository, @Value("${oms.scope-id}") String scope,
                               @Value("${oms.advisory.ttl:PT15M}") Duration ttl, MeterRegistry meters) {
        this(client, properties, runner, repository, scope, ttl, meters, Clock.systemUTC());
    }

    public OrderAdvisoryService(JevClient client, JevProperties properties, JevAdvisoryRunner runner,
                               OrderAdvisoryRepository repository, String scope, Duration ttl, MeterRegistry meters, Clock clock) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Advisory TTL must be positive and at most one hour");
        this.client=client; this.properties=properties; this.runner=runner; this.repository=repository;
        this.scope=scope; this.ttl=ttl; this.clock=clock;
        completed=meters.counter("oms.advisory.completed"); failed=meters.counter("oms.advisory.failures");
        duplicates=meters.counter("oms.advisory.duplicates");
    }

    @Override
    public void afterCommit(String orderId) {
        try {
            if (!scopeConfigurationValid || !client.active(USE_CASE) || !TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()) return;
            runner.submitAfterCommit(USE_CASE, () -> evaluateCommitted(orderId));
        } catch (Throwable ignored) { failed.increment(); }
    }

    private void evaluateCommitted(String orderId) {
        try {
            if (!scopeConfigurationValid || !client.active(USE_CASE)) return;
            for (var snapshot : repository.snapshots(orderId, scope, clock.instant())) {
                if (!client.active(USE_CASE)) return;
                var state=OrderAdvisoryFeatures.state(snapshot.facts());
                String model=properties.getModel(), rubric=properties.useCase(USE_CASE).getRubricVersion();
                // A changed rubric must change the actual question implementation as well as the setting.
                if (!RUBRIC.equals(rubric) || model == null || model.isBlank() || model.endsWith("latest")) return;
                String key=OrderService.hash(String.join("\u0000", snapshot.scopeId(), snapshot.customerId(),
                        snapshot.orderId(), Long.toString(snapshot.orderVersion()), snapshot.exceptionId(),
                        Long.toString(snapshot.exceptionVersion()), state.sha256(), model, rubric,
                        properties.getTransport(), properties.getWireFormat().name()));
                UUID claimId=UUID.randomUUID();
                if (!repository.claim(claimId,key,snapshot,state,model,rubric,snapshot.capturedAt().plus(ttl))) {
                    duplicates.increment(); continue;
                }
                if (!client.active(USE_CASE)) return;
                var question=triage();
                var result=client.decide(USE_CASE,state,List.of(question));
                // Disable or configuration changes while the request is in flight suppress publication.
                if (!client.active(USE_CASE) || !model.equals(properties.getModel())
                        || !rubric.equals(properties.useCase(USE_CASE).getRubricVersion())) return;
                repository.complete(claimId, JevDecisionRecord.of(USE_CASE,"OMS_EXCEPTION_EVALUATION",claimId.toString(),
                        question,state,result,null,JevDecisionRecord.AuthoritativeSource.NONE,true));
                completed.increment();
            }
        } catch (Throwable ignored) { failed.increment(); }
    }
}
