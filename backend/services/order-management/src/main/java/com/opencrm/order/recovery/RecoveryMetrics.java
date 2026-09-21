package com.opencrm.order.recovery;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Internal operational metrics only: no customer identities or synthetic operator context. */
@Component
@ConditionalOnProperty(name="oms.recovery.enabled",havingValue="true")
public class RecoveryMetrics {
 public RecoveryMetrics(DailyReconciliation reconciliation,MeterRegistry registry){
  Gauge.builder("oms.reconciliation.incomplete.runs",reconciliation,d->d.health().incompleteRuns()).register(registry);
  Gauge.builder("oms.reconciliation.oldest.run.age.seconds",reconciliation,d->d.health().oldestRunAgeSeconds()).register(registry);
  Gauge.builder("oms.reconciliation.since.completion.seconds",reconciliation,d->d.health().secondsSinceCompletion()).register(registry);
 }
}
