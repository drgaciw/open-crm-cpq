package com.opencrm.rules.cache;

import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.engine.RuleDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages caching of compiled rule sets to avoid recompilation.
 *
 * Uses in-memory cache for KieBase objects (which are not serializable)
 * and Redis to track version information for cache invalidation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulesCacheManager {

    private final ConcurrentHashMap<String, KieBase> kieBaseCache = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    /**
     * Get or compile a KieBase for the given rule set.
     * Uses in-memory cache to avoid recompilation when nothing has changed.
     */
    public KieBase getOrCompile(String ruleSetId, String version, List<RuleDefinition> rules, DroolsRuleCompiler compiler) {
        String cacheKey = ruleSetId + "::v" + version;

        // Check in-memory cache first
        KieBase cachedKieBase = kieBaseCache.get(cacheKey);
        if (cachedKieBase != null) {
            log.debug("Using cached KieBase for: {}", cacheKey);
            return cachedKieBase;
        }

        // Not in cache, compile and store
        log.info("Compiling and caching KieBase for: {}", cacheKey);
        KieBase kieBase = compiler.compileRules(rules);
        kieBaseCache.put(cacheKey, kieBase);

        // Store version in Redis for distributed cache tracking
        try {
            redisTemplate.opsForValue().set("rules:version:" + ruleSetId, version);
        } catch (Exception e) {
            log.warn("Failed to update version in Redis: {}", e.getMessage());
        }

        return kieBase;
    }

    /**
     * Invalidate all cached versions for a given rule set ID.
     */
    public void invalidate(String ruleSetId) {
        log.info("Invalidating cache for ruleSetId: {}", ruleSetId);

        // Remove all versions from in-memory cache
        kieBaseCache.keySet().removeIf(key -> key.startsWith(ruleSetId + "::"));

        // Remove version from Redis
        try {
            redisTemplate.delete("rules:version:" + ruleSetId);
        } catch (Exception e) {
            log.warn("Failed to remove version from Redis: {}", e.getMessage());
        }

        log.info("Cache invalidated for ruleSetId: {}", ruleSetId);
    }

    /**
     * Clear all cached rule sets.
     */
    public void invalidateAll() {
        log.info("Invalidating all rule caches");
        kieBaseCache.clear();

        try {
            redisTemplate.keys("rules:version:*").forEach(redisTemplate::delete);
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache: {}", e.getMessage());
        }

        log.info("All rule caches invalidated");
    }

    /**
     * Get the current cached version for a rule set from Redis.
     */
    public String getCachedVersion(String ruleSetId) {
        try {
            return redisTemplate.opsForValue().get("rules:version:" + ruleSetId);
        } catch (Exception e) {
            log.warn("Failed to get cached version from Redis: {}", e.getMessage());
            return null;
        }
    }
}
