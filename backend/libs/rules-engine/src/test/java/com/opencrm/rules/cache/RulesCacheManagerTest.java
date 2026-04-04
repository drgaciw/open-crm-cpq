package com.opencrm.rules.cache;

import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.engine.RuleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.KieBase;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulesCacheManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DroolsRuleCompiler compiler;

    @Mock
    private KieBase mockKieBase;

    @InjectMocks
    private RulesCacheManager cacheManager;

    private List<RuleDefinition> sampleRules;

    @BeforeEach
    void setUp() {
        // We only mock valueOperations for opsForValue if it's called
        // leniency can be handled by just setting it up when needed, or using lenient()
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        sampleRules = Collections.singletonList(
                RuleDefinition.builder().id("rule1").content("when then end").build()
        );
    }

    @Test
    void getOrCompile_ShouldCompileAndCache_WhenNotInCache() {
        // Arrange
        String ruleSetId = "pricing-rules";
        String version = "1.0";
        when(compiler.compileRules(sampleRules)).thenReturn(mockKieBase);

        // Act
        KieBase result = cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);

        // Assert
        assertNotNull(result);
        assertEquals(mockKieBase, result);
        verify(compiler, times(1)).compileRules(sampleRules);
        verify(valueOperations, times(1)).set("rules:version:" + ruleSetId, version);
    }

    @Test
    void getOrCompile_ShouldReturnCached_WhenAlreadyInCache() {
        // Arrange
        String ruleSetId = "pricing-rules";
        String version = "1.0";
        when(compiler.compileRules(sampleRules)).thenReturn(mockKieBase);

        // Act 1: First call compiles and caches
        cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);

        // Act 2: Second call returns from cache
        KieBase result2 = cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);

        // Assert
        assertEquals(mockKieBase, result2);
        // Compiler should only be called once
        verify(compiler, times(1)).compileRules(sampleRules);
        verify(valueOperations, times(1)).set("rules:version:" + ruleSetId, version);
    }

    @Test
    void getOrCompile_ShouldCompileAgain_WhenVersionChanges() {
        // Arrange
        String ruleSetId = "pricing-rules";
        when(compiler.compileRules(sampleRules)).thenReturn(mockKieBase);

        // Act 1: First version
        cacheManager.getOrCompile(ruleSetId, "1.0", sampleRules, compiler);

        // Act 2: Second version
        cacheManager.getOrCompile(ruleSetId, "2.0", sampleRules, compiler);

        // Assert
        // Compiler should be called twice (once for each version)
        verify(compiler, times(2)).compileRules(sampleRules);
        verify(valueOperations, times(1)).set("rules:version:" + ruleSetId, "1.0");
        verify(valueOperations, times(1)).set("rules:version:" + ruleSetId, "2.0");
    }

    @Test
    void getOrCompile_ShouldNotFail_WhenRedisThrowsException() {
        // Arrange
        String ruleSetId = "pricing-rules";
        String version = "1.0";
        when(compiler.compileRules(sampleRules)).thenReturn(mockKieBase);

        // Make Redis throw an exception
        doThrow(new RuntimeException("Redis connection failed"))
            .when(valueOperations).set(anyString(), anyString());

        // Act - should not throw
        KieBase result = cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);

        // Assert
        assertEquals(mockKieBase, result);
        verify(compiler, times(1)).compileRules(sampleRules);
    }

    @Test
    void invalidate_ShouldRemoveFromCacheAndRedis() {
        // Arrange
        String ruleSetId = "pricing-rules";
        String version = "1.0";
        when(compiler.compileRules(sampleRules)).thenReturn(mockKieBase);

        // Populate cache
        cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);

        // Act
        cacheManager.invalidate(ruleSetId);

        // Assert
        cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);
        verify(compiler, times(2)).compileRules(sampleRules);
        verify(redisTemplate, times(1)).delete("rules:version:" + ruleSetId);
    }

    @Test
    void invalidate_ShouldNotFail_WhenRedisThrowsException() {
        // Arrange
        String ruleSetId = "pricing-rules";

        // Make Redis throw an exception
        doThrow(new RuntimeException("Redis error"))
            .when(redisTemplate).delete(anyString());

        // Act - should not throw
        cacheManager.invalidate(ruleSetId);

        // Assert
        verify(redisTemplate, times(1)).delete("rules:version:" + ruleSetId);
    }

    @Test
    void invalidateAll_ShouldClearEverything() {
        // Arrange
        String ruleSetId = "pricing-rules";
        String version = "1.0";
        when(compiler.compileRules(sampleRules)).thenReturn(mockKieBase);

        // Mock Redis keys returning something
        when(redisTemplate.keys("rules:version:*")).thenReturn(Collections.singleton("rules:version:pricing-rules"));

        // Populate cache
        cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);

        // Act
        cacheManager.invalidateAll();

        // Assert
        cacheManager.getOrCompile(ruleSetId, version, sampleRules, compiler);
        verify(compiler, times(2)).compileRules(sampleRules);
        verify(redisTemplate, times(1)).delete("rules:version:pricing-rules");
    }

    @Test
    void invalidateAll_ShouldNotFail_WhenRedisThrowsException() {
        // Arrange
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act - should not throw
        cacheManager.invalidateAll();

        // Assert
        verify(redisTemplate, times(1)).keys("rules:version:*");
    }

    @Test
    void getCachedVersion_ShouldReturnVersionFromRedis() {
        // Arrange
        String ruleSetId = "pricing-rules";
        String version = "1.0";
        when(valueOperations.get("rules:version:" + ruleSetId)).thenReturn(version);

        // Act
        String result = cacheManager.getCachedVersion(ruleSetId);

        // Assert
        assertEquals(version, result);
    }

    @Test
    void getCachedVersion_ShouldReturnNull_WhenRedisThrowsException() {
        // Arrange
        String ruleSetId = "pricing-rules";
        when(valueOperations.get("rules:version:" + ruleSetId)).thenThrow(new RuntimeException("Redis error"));

        // Act
        String result = cacheManager.getCachedVersion(ruleSetId);

        // Assert
        assertNull(result);
    }
}
