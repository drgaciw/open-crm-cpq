package com.opencrm.order.recovery;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Scrapes read cached values only; sampling failures preserve data and expose staleness. */
@Component
@ConditionalOnProperty(name="oms.recovery.enabled",havingValue="true")
public class RecoveryMetrics {
 private record Sample(DailyReconciliation.ScanHealth health,Instant sampledAt) {}
 private final DailyReconciliation reconciliation;
 private final AtomicReference<Sample> sample=new AtomicReference<>();
 private final AtomicLong failures=new AtomicLong();
 public RecoveryMetrics(DailyReconciliation reconciliation,MeterRegistry registry){
  this.reconciliation=reconciliation;
  Gauge.builder("oms.reconciliation.incomplete.runs",this,m->{var s=m.sample.get();return s==null?Double.POSITIVE_INFINITY:s.health().incompleteRuns();}).register(registry);
  Gauge.builder("oms.reconciliation.oldest.run.age.seconds",this,m->{var s=m.sample.get();return s==null?Double.POSITIVE_INFINITY:s.health().incompleteRuns()==0?0:s.health().oldestRunAgeSeconds()+age(s);}).register(registry);
  Gauge.builder("oms.reconciliation.since.completion.seconds",this,m->{var s=m.sample.get();return s==null?Double.POSITIVE_INFINITY:s.health().secondsSinceCompletion()+age(s);}).register(registry);
  Gauge.builder("oms.reconciliation.sample.age.seconds",this,m->{var s=m.sample.get();return s==null?Double.POSITIVE_INFINITY:age(s);}).register(registry);
  Gauge.builder("oms.reconciliation.refresh.failures",failures,AtomicLong::doubleValue).register(registry);
 }
 @Scheduled(fixedDelayString="${oms.recovery.metrics-poll-ms:30000}")
 public void refresh(){
  try{sample.set(new Sample(reconciliation.health(),Instant.now()));}
  catch(DataAccessException failure){failures.incrementAndGet();}
 }
 private static double age(Sample sample){return Math.max(0,Instant.now().getEpochSecond()-sample.sampledAt().getEpochSecond());}
}
