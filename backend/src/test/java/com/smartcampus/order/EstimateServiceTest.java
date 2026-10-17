package com.smartcampus.order;

import com.smartcampus.menu.dto.EtaRange;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fully implemented tests for ETA range maths (pure functions — Section 6).
 */
class EstimateServiceTest {

    // ── calculateOwnMinutes ──

    @Test
    void ownMinutes_emptyList_returnsZero() {
        assertEquals(0, EstimateService.calculateOwnMinutes(Collections.emptyList()));
    }

    @Test
    void ownMinutes_nullList_returnsZero() {
        assertEquals(0, EstimateService.calculateOwnMinutes(null));
    }

    @Test
    void ownMinutes_singleItem_returnsItsPrep() {
        assertEquals(8, EstimateService.calculateOwnMinutes(List.of(8)));
    }

    @Test
    void ownMinutes_twoItems_returnsMaxPlusOne() {
        // max(5, 10) + min(5, 2-1) = 10 + 1 = 11
        assertEquals(11, EstimateService.calculateOwnMinutes(List.of(5, 10)));
    }

    @Test
    void ownMinutes_threeItems_returnsMaxPlusTwo() {
        // max(3, 8, 6) + min(5, 3-1) = 8 + 2 = 10
        assertEquals(10, EstimateService.calculateOwnMinutes(List.of(3, 8, 6)));
    }

    @Test
    void ownMinutes_sixItems_capsExtraAtFive() {
        // max = 15, extra = min(5, 6-1) = 5 → 15 + 5 = 20
        assertEquals(20, EstimateService.calculateOwnMinutes(List.of(15, 5, 3, 8, 10, 7)));
    }

    @Test
    void ownMinutes_tenItems_capsExtraAtFive() {
        // max = 12, extra = min(5, 10-1) = 5 → 12 + 5 = 17
        assertEquals(17, EstimateService.calculateOwnMinutes(List.of(12, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
    }

    // ── calculateRange ──

    @Test
    void range_integerEstimate_lowEqualsCeil_highCorrect() {
        // estimate = 10.0, factor = 1.3 → low = 10, high = ceil(13.0) + 1 = 14
        EtaRange range = EstimateService.calculateRange(10.0, 1.3);
        assertEquals(10, range.lowMinutes());
        assertEquals(14, range.highMinutes());
    }

    @Test
    void range_fractionalEstimate_lowRoundsUp() {
        // estimate = 7.5, factor = 1.3 → low = 8, high = ceil(9.75) + 1 = 10 + 1 = 11
        EtaRange range = EstimateService.calculateRange(7.5, 1.3);
        assertEquals(8, range.lowMinutes());
        assertEquals(11, range.highMinutes());
    }

    @Test
    void range_highAlwaysGreaterThanLow() {
        // estimate = 1.0, factor = 1.0 → low = 1, high = ceil(1.0) + 1 = 2
        EtaRange range = EstimateService.calculateRange(1.0, 1.0);
        assertEquals(1, range.lowMinutes());
        assertTrue(range.highMinutes() > range.lowMinutes());
    }

    @Test
    void range_zeroEstimate() {
        EtaRange range = EstimateService.calculateRange(0.0, 1.3);
        assertEquals(0, range.lowMinutes());
        assertTrue(range.highMinutes() > range.lowMinutes());
    }

    @Test
    void range_verySmallEstimate() {
        // estimate = 0.1, factor = 1.3 → low = 1, high = ceil(0.13) + 1 = 1 + 1 = 2
        EtaRange range = EstimateService.calculateRange(0.1, 1.3);
        assertEquals(1, range.lowMinutes());
        assertEquals(2, range.highMinutes());
    }

    // ── calculateQueueWait ──

    @Test
    void queueWait_noRush_dividesByCapacity() {
        assertEquals(5, EstimateService.calculateQueueWait(10, 2, false, 10));
    }

    @Test
    void queueWait_withRush_addsRushExtra() {
        assertEquals(15, EstimateService.calculateQueueWait(10, 2, true, 10));
    }

    @Test
    void queueWait_capacityOne() {
        assertEquals(20, EstimateService.calculateQueueWait(20, 1, false, 0));
    }

    @Test
    void queueWait_capacityZero_treatedAsOne() {
        assertEquals(10, EstimateService.calculateQueueWait(10, 0, false, 0));
    }

    @Test
    void queueWait_zeroBacklog_noRush() {
        assertEquals(0, EstimateService.calculateQueueWait(0, 3, false, 10));
    }

    @Test
    void queueWait_zeroBacklog_withRush() {
        assertEquals(10, EstimateService.calculateQueueWait(0, 3, true, 10));
    }

    @Test
    void queueWait_highCapacity_lowBacklog() {
        // 3 / 5 = 0 (integer division)
        assertEquals(0, EstimateService.calculateQueueWait(3, 5, false, 0));
    }
}
