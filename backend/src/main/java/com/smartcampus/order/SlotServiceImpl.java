package com.smartcampus.order;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.order.dto.SlotResponse;
import com.smartcampus.vendor.Vendor;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotServiceImpl implements SlotService {

    private final SlotCapacityRepository slotCapacityRepository;
    private final VendorService vendorService;
    private final EstimateService estimateService;
    private final Clock clock;

    @Value("${app.slots.open-time:08:00}")
    private LocalTime openTime;

    @Value("${app.slots.close-time:21:00}")
    private LocalTime closeTime;

    @Value("${app.slots.max-days-ahead:2}")
    private int maxDaysAhead;

    @Override
    @Transactional
    public void reserveSlot(long vendorId, Instant slotStart, int slotMaxOrders) {
        slotCapacityRepository.insertIfAbsent(vendorId, slotStart, slotMaxOrders);
        int updated = slotCapacityRepository.bookSlotConditional(vendorId, slotStart);
        if (updated == 0) {
            throw new ConflictException("SLOT_FULL", "Requested pickup slot is fully booked");
        }
    }

    @Override
    @Transactional
    public void releaseSlot(long vendorId, Instant slotStart) {
        slotCapacityRepository.releaseSlot(vendorId, slotStart);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getSlotsForVendor(long vendorId, LocalDate date) {
        Vendor vendor = vendorService.getVendorEntity(vendorId);
        ZonedDateTime windowStart = date.atTime(openTime).atZone(clock.getZone());
        ZonedDateTime windowEnd = date.atTime(closeTime).atZone(clock.getZone());

        Instant earliest = earliestAllowedSlot(estimateService.vendorLevelEstimate(vendor).lowMinutes());
        Instant from = windowStart.toInstant().isAfter(earliest) ? windowStart.toInstant() : earliest;
        Instant to = windowEnd.toInstant();
        if (!from.isBefore(to) || date.isAfter(LocalDate.now(clock).plusDays(maxDaysAhead))) {
            return List.of();
        }

        Map<Instant, SlotCapacity> booked = slotCapacityRepository
                .findByIdVendorIdAndIdSlotStartBetween(vendorId, from, to).stream()
                .collect(Collectors.toMap(s -> s.getId().getSlotStart(), Function.identity()));
        boolean shopTakesOrders = vendor.isOpen() && !vendor.isSuspended();

        List<SlotResponse> slots = new ArrayList<>();
        for (Instant slot = from; slot.isBefore(to); slot = slot.plus(SLOT_MINUTES, ChronoUnit.MINUTES)) {
            SlotCapacity row = booked.get(slot);
            int max = row != null ? row.getMaxOrders() : vendor.getSlotMaxOrders();
            int taken = row != null ? row.getBookedOrders() : 0;
            slots.add(new SlotResponse(slot, max, taken, shopTakesOrders && taken < max));
        }
        return slots;
    }

    @Override
    public void validateRequestedSlot(Vendor vendor, Instant slotStart, int lowEstimateMinutes) {
        ZonedDateTime local = slotStart.atZone(clock.getZone());
        if (local.getSecond() != 0 || local.getNano() != 0 || local.getMinute() % SLOT_MINUTES != 0) {
            throw invalidSlot("Pickup slot must start on a 5-minute boundary");
        }
        LocalTime time = local.toLocalTime();
        if (time.isBefore(openTime) || !time.isBefore(closeTime)) {
            throw invalidSlot("Pickup slot must be between " + openTime + " and " + closeTime);
        }
        if (local.toLocalDate().isAfter(LocalDate.now(clock).plusDays(maxDaysAhead))) {
            throw invalidSlot("Pickup slot is too far ahead");
        }
        Instant earliest = earliestAllowedSlot(lowEstimateMinutes);
        if (slotStart.isBefore(earliest)) {
            throw invalidSlot("Earliest available pickup slot is " + earliest);
        }
    }

    @Override
    public Instant earliestAllowedSlot(int lowEstimateMinutes) {
        ZonedDateTime earliest = clock.instant().plus(lowEstimateMinutes, ChronoUnit.MINUTES)
                .atZone(clock.getZone());
        ZonedDateTime floored = earliest.truncatedTo(ChronoUnit.MINUTES)
                .withMinute(earliest.getMinute() - earliest.getMinute() % SLOT_MINUTES);
        if (floored.isBefore(earliest)) {
            floored = floored.plusMinutes(SLOT_MINUTES);
        }
        return floored.toInstant();
    }

    @Override
    public LocalDate dateOf(Instant slotStart) {
        return slotStart.atZone(clock.getZone()).toLocalDate();
    }

    private static ApiException invalidSlot(String message) {
        return ApiException.badRequest("VALIDATION_ERROR", "pickupSlotStart", message);
    }
}
