package com.opencrm.order.fulfillment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="oms.execution.enabled",havingValue="true")
public class AllocationExpiryScheduler {
    private final EvidenceService evidence;
    public AllocationExpiryScheduler(EvidenceService evidence){this.evidence=evidence;}
    @Scheduled(fixedDelayString="${oms.fulfillment.expiry-scan-ms:60000}")
    public void detect(){evidence.detectExpiredAllocations(100);}
}
