package com.smartcampus.order;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.vendor.Vendor;
import com.smartcampus.vendor.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Rule 5.1 step 2: earliest slot = now + low estimate, rounded up to the next 5 minutes.
 */
class SlotRulesTest {

    // 12:02:30 in Kolkata
    private static final Instant NOW = Instant.parse("2026-10-23T06:32:30Z");
    private SlotServiceImpl slots;
    private final Vendor vendor = Vendor.builder().id(3L).name("Main Canteen").build();

    @BeforeEach
    void setUp() {
        slots = new SlotServiceImpl(mock(SlotCapacityRepository.class), mock(VendorService.class),
                mock(EstimateService.class), Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
        ReflectionTestUtils.setField(slots, "openTime", LocalTime.of(8, 0));
        ReflectionTestUtils.setField(slots, "closeTime", LocalTime.of(21, 0));
        ReflectionTestUtils.setField(slots, "maxDaysAhead", 2);
    }

    @Test
    void earliestSlot_isRoundedUpToTheNextFiveMinutes() {
        // 12:02:30 + 10 min = 12:12:30 -> 12:15
        assertEquals(Instant.parse("2026-10-23T06:45:00Z"), slots.earliestAllowedSlot(10));
    }

    @Test
    void earliestSlot_onABoundaryStaysPut() {
        // 12:02:30 + 12.5 min is not possible with whole minutes; 12:02:30 + 2 min = 12:04:30 -> 12:05
        assertEquals(Instant.parse("2026-10-23T06:35:00Z"), slots.earliestAllowedSlot(2));
    }

    @Test
    void slotBeforeTheEstimate_isRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> slots.validateRequestedSlot(vendor, Instant.parse("2026-10-23T06:40:00Z"), 10));
        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
    }

    @Test
    void slotOffTheFiveMinuteGrid_isRejected() {
        assertThrows(ApiException.class,
                () -> slots.validateRequestedSlot(vendor, Instant.parse("2026-10-23T07:02:00Z"), 10));
    }

    @Test
    void slotOutsideOpeningHours_isRejected() {
        // 22:00 Kolkata
        assertThrows(ApiException.class,
                () -> slots.validateRequestedSlot(vendor, Instant.parse("2026-10-23T16:30:00Z"), 10));
    }

    @Test
    void validSlot_isAccepted() {
        assertDoesNotThrow(() -> slots.validateRequestedSlot(vendor, Instant.parse("2026-10-23T06:45:00Z"), 10));
    }
}
