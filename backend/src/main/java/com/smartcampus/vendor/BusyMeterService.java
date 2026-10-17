package com.smartcampus.vendor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service that calculates the busy level and current wait time for vendors.
 * Business Rule (Section 6):
 * - queueWait = backlogMinutes / parallel_capacity of the vendor.
 * - If the vendor's rush_mode is on, add app.eta.rush-extra-minutes.
 * - Busy level from queueWait using thresholds:
 *     * queueWait < app.eta.busy-threshold-minutes -> CALM
 *     * queueWait < app.eta.packed-threshold-minutes -> BUSY
 *     * otherwise -> PACKED
 * - Cache the vendor backlog in Redis for 15 seconds for browsing/list screens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusyMeterService {

    private final VendorLoadReader vendorLoadReader;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.eta.rush-extra-minutes:10}")
    private int rushExtraMinutes;

    @Value("${app.eta.busy-threshold-minutes:5}")
    private int busyThresholdMinutes;

    @Value("${app.eta.packed-threshold-minutes:15}")
    private int packedThresholdMinutes;

    /**
     * Retrieves the queue wait in minutes for the vendor, utilizing a 15-second Redis cache.
     */
    public int getQueueWaitMinutes(Vendor vendor) {
        String cacheKey = "vendor:backlog:" + vendor.getId();
        int backlogMinutes;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                backlogMinutes = Integer.parseInt(cached);
            } else {
                backlogMinutes = vendorLoadReader.getBacklogMinutes(vendor.getId());
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(backlogMinutes), Duration.ofSeconds(15));
            }
        } catch (Exception e) {
            log.warn("Redis backlog cache access failed, calculating directly: {}", e.getMessage());
            backlogMinutes = vendorLoadReader.getBacklogMinutes(vendor.getId());
        }

        int capacity = Math.max(1, vendor.getParallelCapacity());
        int queueWait = backlogMinutes / capacity;
        if (vendor.isRushMode()) {
            queueWait += rushExtraMinutes;
        }
        return queueWait;
    }

    /**
     * Determines the BusyLevel (CALM, BUSY, PACKED) from current queue wait.
     */
    public BusyLevel calculateBusyLevel(Vendor vendor) {
        int wait = getQueueWaitMinutes(vendor);
        if (wait < busyThresholdMinutes) {
            return BusyLevel.CALM;
        } else if (wait < packedThresholdMinutes) {
            return BusyLevel.BUSY;
        } else {
            return BusyLevel.PACKED;
        }
    }
}
