package com.wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    // max requests allowed per window
    private static final int MAX_TOKENS = 5;

    // how long before tokens refill
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    /**
     * Returns true if the request is allowed.
     * Returns false if the user has exceeded their rate limit.
     */
    public boolean isAllowed(UUID userId) {
        String key = "rate_limit::" + userId.toString();

        // get current token count
        String currentValue = stringRedisTemplate.opsForValue().get(key);

        if (currentValue == null) {
            // first request from this user — initialize bucket
            // set to MAX_TOKENS - 1 because we're consuming one right now
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(MAX_TOKENS - 1),
                    REFILL_DURATION
            );
            log.info("Rate limit initialized for user: {} | tokens remaining: {}",
                    userId, MAX_TOKENS - 1);
            return true;
        }

        int tokens = Integer.parseInt(currentValue);

        if (tokens <= 0) {
            // bucket empty — reject request
            log.warn("Rate limit exceeded for user: {} | tokens: 0", userId);
            return false;
        }

        // consume one token — decrement atomically
        stringRedisTemplate.opsForValue().decrement(key);
        log.info("Token consumed for user: {} | tokens remaining: {}", userId, tokens - 1);
        return true;
    }
}