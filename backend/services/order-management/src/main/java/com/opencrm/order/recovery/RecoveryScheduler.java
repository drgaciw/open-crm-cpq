package com.opencrm.order.recovery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="oms.recovery.enabled",havingValue="true")
public class RecoveryScheduler {
 private final DailyReconciliation reconciliation;
 public RecoveryScheduler(DailyReconciliation reconciliation){this.reconciliation=reconciliation;}
 @Scheduled(fixedDelayString="${oms.recovery.scan-delay-ms:60000}")
 public void scan(){reconciliation.scan(50);}
}
