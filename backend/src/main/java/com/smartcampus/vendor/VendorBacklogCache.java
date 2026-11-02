package com.smartcampus.vendor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongToIntFunction;

/**
 * Section 6: the vendor backlog is cached in Redis for 15 seconds for list screens
 * (GET /vendors, GET /vendors/{id}/menu). Order placement never uses this cache.
 * If Redis is down the value is computed directly, so browsing never fails because of the cache.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VendorBacklogCache {

    static final Duration TTL = Duration.ofSeconds(15);

    private final StringRedisTemplate redisTemplate;

    public int get(long vendorId, LongToIntFunction loader) {
        String key = key(vendorId);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
            int value = loader.applyAsInt(vendorId);
            redisTemplate.opsForValue().set(key, String.valueOf(value), TTL);
            return value;
        } catch (RuntimeException e) {
            log.warn("Redis backlog cache unavailable for vendor {}, computing directly: {}", vendorId, e.getMessage());
            return loader.applyAsInt(vendorId);
        }
    }

    /** Called when the vendor's order set changes so list screens pick up the new load quickly. */
    public void evict(long vendorId) {
        try {
            redisTemplate.delete(key(vendorId));
        } catch (RuntimeException e) {
            log.debug("Could not evict backlog cache for vendor {}: {}", vendorId, e.getMessage());
        }
    }

    private static String key(long vendorId) {
        return "vendor:backlog:" + vendorId;
    }
}
