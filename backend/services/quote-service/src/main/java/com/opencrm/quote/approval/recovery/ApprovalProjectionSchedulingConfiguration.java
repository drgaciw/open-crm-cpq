package com.opencrm.quote.approval.recovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class ApprovalProjectionSchedulingConfiguration {
    /** Not a TaskScheduler bean: leave Boot's shared/outbox scheduler untouched. */
    @Bean(name = "approvalProjectionExecutor", destroyMethod = "shutdown")
    public ExecutorService approvalProjectionExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("approval-projection-", 0).factory());
    }
}
