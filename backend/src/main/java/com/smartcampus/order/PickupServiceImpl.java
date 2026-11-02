package com.smartcampus.order;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.common.util.TokenGenerator;
import com.smartcampus.order.dto.PickupCredentialsResponse;
import com.smartcampus.order.dto.VerifyPickupRequest;
import com.smartcampus.order.events.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PickupServiceImpl implements PickupService {

    private static final int CODE_ATTEMPTS = 20;
    private static final Set<OrderStatus> HAS_PICKUP_CODE = EnumSet.of(
            OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.UNCOLLECTED);

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void generatePickupCredentials(long orderId, long vendorId, LocalDate pickupDate) {
        String code = null;
        for (int attempt = 0; attempt < CODE_ATTEMPTS && code == null; attempt++) {
            String candidate = randomCode(attempt < CODE_ATTEMPTS / 2 ? 90 : 900);
            if (!orderRepository.existsByVendorIdAndPickupDateAndPickupCode(vendorId, pickupDate, candidate)) {
                code = candidate;
            }
        }
        if (code == null) {
            throw new IllegalStateException("Could not find a free pickup code for vendor " + vendorId);
        }
        String secret = TokenGenerator.generateRandomSecret(32);
        // A parallel payment could still take the same code; the unique index then rolls this transaction back
        // and the outcome is applied again by the next /pay, webhook or reconciliation run.
        orderRepository.setPickupCredentials(orderId, code, secret, pickupDate);
    }

    @Override
    @Transactional
    public long verifyPickup(long vendorId, VerifyPickupRequest request) {
        Order order = findForVendor(vendorId, request)
                .orElseThrow(() -> new NotFoundException("No order of your shop matches this pickup code"));

        OrderStatus current = order.getStatus();
        if (current != OrderStatus.READY && current != OrderStatus.UNCOLLECTED) {
            throw new ConflictException("INVALID_TRANSITION",
                    "Order " + order.getId() + " is " + current + " and cannot be collected");
        }
        Instant now = clock.instant();
        if (orderRepository.markCollected(order.getId(), current, now) == 0) {
            throw new ConflictException("INVALID_TRANSITION", "Order " + order.getId() + " was changed meanwhile");
        }
        eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.COLLECTED, now));
        return order.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public PickupCredentialsResponse getPickupDetails(long orderId, long studentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if (!order.getStudentId().equals(studentId)) {
            throw new ForbiddenException("Cannot view pickup details of another student's order");
        }
        if (!HAS_PICKUP_CODE.contains(order.getStatus()) || order.getPickupCode() == null) {
            throw new ConflictException("INVALID_TRANSITION",
                    "Pickup details are available once the order is paid (order is " + order.getStatus() + ")");
        }
        return new PickupCredentialsResponse(order.getId(), order.getPickupCode(), order.getPickupSecret(), order.getPickupDate());
    }

    private Optional<Order> findForVendor(long vendorId, VerifyPickupRequest request) {
        if (request.pickupSecret() != null && !request.pickupSecret().isBlank()) {
            return orderRepository.findByPickupSecret(request.pickupSecret().trim())
                    .filter(o -> o.getVendorId().equals(vendorId));
        }
        if (request.pickupCode() != null && !request.pickupCode().isBlank()) {
            String code = request.pickupCode().trim().toUpperCase();
            LocalDate today = LocalDate.now(clock);
            return orderRepository.findByVendorIdAndPickupDateAndPickupCode(vendorId, today, code)
                    .or(() -> orderRepository.findByVendorIdAndPickupDateAndPickupCode(vendorId, today.minusDays(1), code));
        }
        throw ApiException.badRequest("VALIDATION_ERROR", "pickupCode", "Provide pickupCode or pickupSecret");
    }

    /** Letter + number, e.g. A47 (2 digits first, 3 digits if a busy day makes 2-digit codes collide). */
    private String randomCode(int numberRange) {
        char letter = (char) ('A' + random.nextInt(26));
        int base = numberRange == 90 ? 10 : 100;
        return "" + letter + (base + random.nextInt(numberRange));
    }
}
