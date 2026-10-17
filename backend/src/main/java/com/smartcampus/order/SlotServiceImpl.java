package com.smartcampus.order;

import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.order.dto.SlotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotServiceImpl implements SlotService {

    private final SlotCapacityRepository slotCapacityRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void reserveSlot(long vendorId, Instant slotStart, int slotMaxOrders) {
        SlotCapacityId id = new SlotCapacityId(vendorId, slotStart);
        // Ensure row exists lazily
        if (!slotCapacityRepository.existsById(id)) {
            SlotCapacity slot = SlotCapacity.builder()
                    .id(id)
                    .maxOrders(slotMaxOrders)
                    .bookedOrders(0)
                    .build();
            slotCapacityRepository.saveAndFlush(slot);
        }

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
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void validateEarliestAllowedSlot(Instant slotStart, int lowEstimateMinutes) {
        Instant now = clock.instant();
        Instant earliest = now.plus(lowEstimateMinutes, ChronoUnit.MINUTES);
        // Round up to next 5 minutes
        long minute = earliest.atZone(clock.getZone()).getMinute();
        long remainder = minute % 5;
        if (remainder != 0) {
            earliest = earliest.plus(5 - remainder, ChronoUnit.MINUTES);
        }

        if (slotStart.isBefore(earliest)) {
            throw new ConflictException("SLOT_TOO_EARLY", "Selected slot is earlier than preparation estimate");
        }
    }
}
