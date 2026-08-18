package com.codeguardian.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis warm-up runner
 * Works around the first-access timeout on Redis
 */
@Component
@ConditionalOnProperty(prefix = "sa-token.redis", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisWarmUpRunner implements ApplicationRunner {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting Redis connection warm-up...");
        try {
            // Perform a simple operation to trigger connection establishment
            redisTemplate.hasKey("warmup-key");
            log.info("Redis connection warm-up succeeded!");
        } catch (Exception e) {
            log.warn("Redis connection warm-up failed: {}", e.getMessage());
            // Do not throw; avoid affecting application startup since this is only an optimization
        }
    }
}
