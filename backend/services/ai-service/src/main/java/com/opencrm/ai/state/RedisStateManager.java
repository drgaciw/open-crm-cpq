package com.opencrm.ai.state;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisStateManager {

    private final StringRedisTemplate redisTemplate;

    public RedisStateManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveState(String sessionId, String state) {
        redisTemplate.opsForValue().set("ai:session:" + sessionId, state, Duration.ofHours(24));
    }

    public String loadState(String sessionId) {
        return redisTemplate.opsForValue().get("ai:session:" + sessionId);
    }
    
    public void deleteState(String sessionId) {
        redisTemplate.delete("ai:session:" + sessionId);
    }
}
