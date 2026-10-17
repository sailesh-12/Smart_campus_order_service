package com.smartcampus.common.util;

import com.smartcampus.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-based sliding/fixed window rate limiter.
 * Rule (5.10):
 * - POST /orders at app.order.rate-limit-per-minute per student.
 * - Login and forgot-password at app.auth.rate-limit-per-minute per IP and email.
 * - On exceed return 429 with Retry-After header.
 * - If Redis is unavailable, fail open for orders (log warning) but fail closed for login attempts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    /**
     * Checks whether the request is within the allowed rate limit.
     *
     * @param keyPrefix      namespace (e.g., "rate:order:" or "rate:auth:")
     * @param identifier     student id, IP address, or email
     * @param limitPerMinute maximum allowed requests in a 60-second window
     * @param failOpen       true for orders (allow on redis failure), false for auth (block on redis failure)
     * @throws ApiException with code RATE_LIMITED and status 429 if the rate limit is exceeded
     */
    public void checkLimit(String keyPrefix, String identifier, int limitPerMinute, boolean failOpen) {
        String key = keyPrefix + identifier;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > limitPerMinute) {
                log.warn("Rate limit exceeded for key={}: count={}, limit={}", key, count, limitPerMinute);
                throw new ApiException("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded. Please wait before retrying.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            if (failOpen) {
                log.warn("Redis unavailable during rate limit check for {}. Failing open (allowing request). Error: {}", key, e.getMessage());
            } else {
                log.error("Redis unavailable during auth rate limit check for {}. Failing closed (blocking request). Error: {}", key, e.getMessage());
                throw new ApiException("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                        "Authentication rate limit check temporarily unavailable. Please try again shortly.");
            }
        }
    }
}
