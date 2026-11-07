package com.opencrm.ai.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.agent-task")
public class AgentTaskProperties {

    private int defaultMaxAttempts = 5;
    private int defaultPriority = 0;
    private final Dispatcher dispatcher = new Dispatcher();
    private final Retry retry = new Retry();

    public int getDefaultMaxAttempts() {
        return defaultMaxAttempts;
    }

    public void setDefaultMaxAttempts(int defaultMaxAttempts) {
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public Retry getRetry() {
        return retry;
    }

    public static class Dispatcher {
        private boolean enabled = false;
        private int claimLimit = AgentTaskLimits.DEFAULT_CLAIM_LIMIT;
        private Duration lease = Duration.ofSeconds(30);
        private Duration pollInterval = Duration.ofSeconds(5);
        private String leaseOwner;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getClaimLimit() {
            return claimLimit;
        }

        public void setClaimLimit(int claimLimit) {
            this.claimLimit = claimLimit;
        }

        public Duration getLease() {
            return lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public String getLeaseOwner() {
            return leaseOwner;
        }

        public void setLeaseOwner(String leaseOwner) {
            this.leaseOwner = leaseOwner;
        }
    }

    public static class Retry {
        private Duration initialBackoff = Duration.ofSeconds(1);
        private int multiplier = 2;
        private Duration maxBackoff = AgentTaskLimits.MAX_BACKOFF;

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(int multiplier) {
            this.multiplier = multiplier;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }
}
