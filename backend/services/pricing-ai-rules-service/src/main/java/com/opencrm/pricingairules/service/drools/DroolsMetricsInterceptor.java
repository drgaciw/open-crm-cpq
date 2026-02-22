package com.opencrm.pricingairules.service.drools;

import com.opencrm.pricingairules.service.drools.dto.DroolsRuleExecutionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.rule.Match;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Intercepts Drools rule execution events to collect performance metrics
 */
@Component
@Slf4j
public class DroolsMetricsInterceptor implements AgendaEventListener {

    // Store metrics by rule execution ID
    private final Map<String, Map<String, Object>> executionState = new ConcurrentHashMap<>();

    // Queue to store completed metrics for retrieval
    private final ConcurrentLinkedQueue<DroolsRuleExecutionMetrics> metricsQueue = new ConcurrentLinkedQueue<>();

    // Maximum queue size to prevent memory issues
    private static final int MAX_QUEUE_SIZE = 10000;

    @Override
    public void matchCreated(MatchCreatedEvent event) {
        try {
            String ruleName = event.getMatch().getRule().getName();
            String executionId = getExecutionId(event);

            // Initialize state if needed
            getOrCreateState(executionId, ruleName);

            // Increment match count
            Map<String, Object> state = executionState.get(executionId);
            state.put("matchCount", ((Long) state.getOrDefault("matchCount", 0L)) + 1);

            // Track pattern stats
            trackPatternStats(state, event.getMatch());
        } catch (Exception e) {
            log.warn("Error processing matchCreated event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void matchCancelled(MatchCancelledEvent event) {
        // Not tracking cancelled matches for performance metrics
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        try {
            String ruleName = event.getMatch().getRule().getName();
            String executionId = getExecutionId(event);

            // Initialize state if needed
            Map<String, Object> state = getOrCreateState(executionId, ruleName);

            // Mark execution start time
            state.put("startTime", System.nanoTime());
            state.put("memoryBefore", getUsedMemory());
        } catch (Exception e) {
            log.warn("Error processing beforeMatchFired event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        try {
            String executionId = getExecutionId(event);

            if (executionState.containsKey(executionId)) {
                Map<String, Object> state = executionState.get(executionId);

                // Calculate execution time
                long startTime = (long) state.getOrDefault("startTime", System.nanoTime());
                long endTime = System.nanoTime();
                double executionTimeMs = (endTime - startTime) / 1_000_000.0;

                // Calculate memory usage
                long memoryBefore = (long) state.getOrDefault("memoryBefore", 0L);
                long memoryAfter = getUsedMemory();
                long memoryUsageBytes = memoryAfter - memoryBefore;

                // Update memory data
                state.put("executionTimeMs", executionTimeMs);
                state.put("memoryUsageBytes", memoryUsageBytes);

                // Create metrics object
                DroolsRuleExecutionMetrics metrics = createMetrics(executionId, state);

                // Add to queue, with size limit check
                if (metricsQueue.size() >= MAX_QUEUE_SIZE) {
                    metricsQueue.poll(); // Remove oldest entry
                }
                metricsQueue.add(metrics);

                // Clean up state
                executionState.remove(executionId);
            }
        } catch (Exception e) {
            log.warn("Error processing afterMatchFired event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
        // Not needed for metrics collection
    }

    @Override
    public void agendaGroupPushed(AgendaGroupPushedEvent event) {
        // Not needed for metrics collection
    }

    @Override
    public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        // Not needed for metrics collection
    }

    @Override
    public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        // Not needed for metrics collection
    }

    @Override
    public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        // Not needed for metrics collection
    }

    @Override
    public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        // Not needed for metrics collection
    }

    /**
     * Get metrics for a specific rule
     *
     * @param ruleName The rule name
     * @return Execution metrics for the rule, or null if not found
     */
    public java.util.List<DroolsRuleExecutionMetrics> getMetricsForRule(String ruleName) {
        return metricsQueue.stream()
                .filter(m -> m.getRuleName().equals(ruleName))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all collected metrics
     *
     * @return List of all execution metrics
     */
    public java.util.List<DroolsRuleExecutionMetrics> getAllMetrics() {
        return new java.util.ArrayList<>(metricsQueue);
    }

    /**
     * Get metrics collected within a time range
     *
     * @param startTime Start time (inclusive)
     * @param endTime   End time (inclusive)
     * @return List of execution metrics within the time range
     */
    public java.util.List<DroolsRuleExecutionMetrics> getMetricsByTimeRange(Instant startTime, Instant endTime) {
        return metricsQueue.stream()
                .filter(m -> !m.getTimestamp().isBefore(startTime) && !m.getTimestamp().isAfter(endTime))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get the current queue size
     *
     * @return Number of metrics currently stored
     */
    public int getMetricsCount() {
        return metricsQueue.size();
    }

    /**
     * Get the number of active rule executions being tracked
     *
     * @return Number of active executions
     */
    public int getActiveExecutionsCount() {
        return executionState.size();
    }

    /**
     * Clear metrics queue
     */
    public void clearMetrics() {
        metricsQueue.clear();
        executionState.clear();
    }

    /**
     * Record a rule execution manually (for tracking execution at RuleSet level)
     *
     * @param ruleSetName The name of the rule set that was executed
     * @param executionTimeMs The execution time in milliseconds
     */
    public void recordRuleExecution(String ruleSetName, long executionTimeMs) {
        DroolsRuleExecutionMetrics metrics = DroolsRuleExecutionMetrics.builder()
                .ruleName(ruleSetName)
                .executionTimeMs((double) executionTimeMs)
                .conditionEvaluations(0L)
                .memoryUsageBytes(0L)
                .cacheHits(0L)
                .cacheMisses(0L)
                .factCount(0L)
                .patternStats(new HashMap<>())
                .factCounts(new HashMap<>())
                .factAccessTimes(new HashMap<>())
                .timestamp(Instant.now())
                .executionContext("RuleSet execution: " + ruleSetName)
                .build();

        // Add to queue, with size limit check
        if (metricsQueue.size() >= MAX_QUEUE_SIZE) {
            metricsQueue.poll(); // Remove oldest entry
        }
        metricsQueue.add(metrics);

        log.debug("Recorded rule execution for {}: {}ms", ruleSetName, executionTimeMs);
    }

    // Helper methods

    private String getExecutionId(org.kie.api.event.KieRuntimeEvent event) {
        Match match = null;

        if (event instanceof MatchCreatedEvent) {
            match = ((MatchCreatedEvent) event).getMatch();
        } else if (event instanceof BeforeMatchFiredEvent) {
            match = ((BeforeMatchFiredEvent) event).getMatch();
        } else if (event instanceof AfterMatchFiredEvent) {
            match = ((AfterMatchFiredEvent) event).getMatch();
        } else if (event instanceof MatchCancelledEvent) {
            match = ((MatchCancelledEvent) event).getMatch();
        }

        if (match == null) {
            // Fallback for unsupported event types
            return System.identityHashCode(event.getKieRuntime()) + "-unknown-" + System.currentTimeMillis();
        }

        return System.identityHashCode(event.getKieRuntime()) + "-" +
                match.getRule().getName() + "-" +
                System.identityHashCode(match);
    }

    private Map<String, Object> getOrCreateState(String executionId, String ruleName) {
        return executionState.computeIfAbsent(executionId, id -> {
            Map<String, Object> state = new HashMap<>();
            state.put("ruleName", ruleName);
            state.put("startTime", System.nanoTime());
            state.put("matchCount", 0L);
            state.put("conditionEvaluations", 0L);
            state.put("patternStats", new HashMap<String, Object>());
            state.put("factTypes", new HashMap<String, Long>());
            state.put("factAccessTimes", new HashMap<String, Double>());
            state.put("cacheHits", 0L);
            state.put("cacheMisses", 0L);
            state.put("timestamp", Instant.now());
            state.put("contextDescription", "Rule execution");
            state.put("factCount", 0L);
            return state;
        });
    }

    private void trackPatternStats(Map<String, Object> state, Match match) {
        // Get pattern stats map
        @SuppressWarnings("unchecked")
        Map<String, Object> patternStats = (Map<String, Object>) state.get("patternStats");

        // Get fact types map
        @SuppressWarnings("unchecked")
        Map<String, Long> factTypes = (Map<String, Long>) state.get("factTypes");

        // Process declarations and bound objects
        for (String declaration : match.getDeclarationIds()) {
            try {
                Object fact = match.getDeclarationValue(declaration);
                if (fact != null) {
                    // Track the pattern use
                    String pattern = declaration + ":" + fact.getClass().getSimpleName();
                    patternStats.put(pattern, (long) patternStats.getOrDefault(pattern, 0L) + 1);

                    // Track fact types
                    String factType = fact.getClass().getName();
                    factTypes.put(factType, factTypes.getOrDefault(factType, 0L) + 1);

                    // Track total fact count
                    state.put("factCount", ((Long) state.getOrDefault("factCount", 0L)) + 1);
                }
            } catch (Exception e) {
                log.debug("Error tracking pattern stats: {}", e.getMessage());
            }
        }

        // Increment condition evaluations counter
        state.put("conditionEvaluations", ((Long) state.getOrDefault("conditionEvaluations", 0L)) + 1);
    }

    private DroolsRuleExecutionMetrics createMetrics(String executionId, Map<String, Object> state) {
        @SuppressWarnings("unchecked")
        Map<String, Object> patternStats = (Map<String, Object>) state.get("patternStats");

        @SuppressWarnings("unchecked")
        Map<String, Long> factCounts = (Map<String, Long>) state.get("factTypes");

        @SuppressWarnings("unchecked")
        Map<String, Double> factAccessTimes = (Map<String, Double>) state.get("factAccessTimes");

        return DroolsRuleExecutionMetrics.builder()
                .ruleName((String) state.get("ruleName"))
                .executionTimeMs((Double) state.getOrDefault("executionTimeMs", 0.0))
                .conditionEvaluations((Long) state.getOrDefault("conditionEvaluations", 0L))
                .memoryUsageBytes((Long) state.getOrDefault("memoryUsageBytes", 0L))
                .cacheHits((Long) state.getOrDefault("cacheHits", 0L))
                .cacheMisses((Long) state.getOrDefault("cacheMisses", 0L))
                .factCount((Long) state.getOrDefault("factCount", 0L))
                .patternStats(patternStats)
                .factCounts(factCounts)
                .factAccessTimes(factAccessTimes)
                .timestamp((Instant) state.getOrDefault("timestamp", Instant.now()))
                .executionContext((String) state.getOrDefault("contextDescription", "Rule execution"))
                .build();
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
