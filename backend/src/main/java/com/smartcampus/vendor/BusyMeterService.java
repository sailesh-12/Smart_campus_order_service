package com.smartcampus.vendor;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
public class BusyMeterService {

    private final VendorLoadReader vendorLoadReader;
    private final VendorBacklogCache backlogCache;

    @Value("${app.eta.rush-extra-minutes:10}")
    private int rushExtraMinutes;

    @Value("${app.eta.busy-threshold-minutes:5}")
    private int busyThresholdMinutes;

    @Value("${app.eta.packed-threshold-minutes:15}")
    private int packedThresholdMinutes;

    /** Busy level plus the estimated wait shown on the vendor list. */
    public record BusySnapshot(BusyLevel level, int queueWaitMinutes, int estimatedWaitMinutes) {
    }

    public BusySnapshot snapshot(Vendor vendor) {
        int queueWait = getQueueWaitMinutes(vendor);
        // A new order waits for the queue and then for its own cooking (vendor average as the fallback dish time)
        return new BusySnapshot(levelFor(queueWait), queueWait, queueWait + vendor.getAvgPrepMinutes());
    }

    /**
     * Retrieves the queue wait in minutes for the vendor, utilizing a 15-second Redis cache.
     */
    public int getQueueWaitMinutes(Vendor vendor) {
        int backlogMinutes = backlogCache.get(vendor.getId(), vendorLoadReader::getBacklogMinutes);
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
        return levelFor(getQueueWaitMinutes(vendor));
    }

    BusyLevel levelFor(int queueWait) {
        if (queueWait < busyThresholdMinutes) {
            return BusyLevel.CALM;
        } else if (queueWait < packedThresholdMinutes) {
            return BusyLevel.BUSY;
        }
        return BusyLevel.PACKED;
    }
}
