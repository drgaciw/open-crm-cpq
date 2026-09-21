package com.opencrm.order.recovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="oms.recovery.enabled",havingValue="true")
public class RecoveryScheduler {
 private final DailyReconciliation reconciliation;
 private final int batchSize;
 public RecoveryScheduler(DailyReconciliation reconciliation){this(reconciliation,100);}
 @Autowired
 public RecoveryScheduler(DailyReconciliation reconciliation,@Value("${oms.recovery.batch-size:100}") int batchSize){
  if(batchSize<1||batchSize>100)throw new IllegalArgumentException("Recovery batch size must be 1..100");
  this.reconciliation=reconciliation;this.batchSize=batchSize;
 }
 @Scheduled(fixedDelayString="${oms.recovery.scan-delay-ms:5000}")
 public void scan(){reconciliation.scan(batchSize);}
}
