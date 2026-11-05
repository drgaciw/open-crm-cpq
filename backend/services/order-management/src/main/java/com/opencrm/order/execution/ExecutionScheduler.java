package com.opencrm.order.execution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="oms.execution.enabled",havingValue="true")
public class ExecutionScheduler {
    private final ExecutionService execution;
    public ExecutionScheduler(ExecutionService execution) {this.execution=execution;}
    @Scheduled(fixedDelayString="${oms.execution.poll-ms:5000}")
    public void dispatch() {execution.dispatchReady(25);}
    @Scheduled(fixedDelayString="${oms.execution.reconcile-ms:60000}")
    public void reconcile() {execution.reconcileDue(100);}
}
