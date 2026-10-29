package com.smartcampus.notify;

import com.smartcampus.order.OrderService;
import com.smartcampus.order.OrderStatus;
import com.smartcampus.order.events.OrderReminder;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.events.PaymentRefunded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Listener executing asynchronous push notifications after database commits.
 *
 * Business Rules (Section 5.9):
 * - Runs AFTER_COMMIT and asynchronously (@Async).
 * - Always pushes on: READY, REJECTED, PaymentRefunded, and uncollected reminder.
 * - Pushes ACCEPTED only when the student has no open stream for that order (SseEmitterRegistry.hasOrderSubscribers).
 * - Push failure MUST never alter or fail order state (everything here is caught and logged).
 * - On PushResult.INVALID_TOKEN, deletes that token immediately from the database.
 * - Push payload: short message + orderId and type in data. NEVER includes pickup codes or monetary amounts on lock screen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushNotificationListener {

    private final PushSender pushSender;
    private final DeviceTokenService deviceTokenService;
    private final SseEmitterRegistry sseRegistry;
    private final OrderService orderService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        String body = switch (event.status()) {
            case READY -> "Your order is ready for pickup!";
            case REJECTED -> "Sorry, the shop could not take your order. Any payment will be refunded.";
            case ACCEPTED -> "Your order has been accepted by the kitchen.";
            default -> null;
        };
        if (body == null) {
            return;
        }
        if (event.status() == OrderStatus.ACCEPTED && sseRegistry.hasOrderSubscribers(event.orderId())) {
            log.debug("Skipping push for ACCEPTED order {} because student has active SSE stream open", event.orderId());
            return;
        }
        pushToStudentOf(event.orderId(), "Order update", body, event.status().name());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRefunded(PaymentRefunded event) {
        pushToStudentOf(event.orderId(), "Refund processed", "A refund has been issued for your order.", "PAYMENT_REFUNDED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReminder(OrderReminder event) {
        pushToStudentOf(event.orderId(), "Your food is waiting", "Your order is ready. Please collect it soon.", "PICKUP_REMINDER");
    }

    /** Sends to every device of the order's student. Never throws. */
    void pushToStudentOf(long orderId, String title, String body, String type) {
        try {
            long studentId = orderService.studentIdOf(orderId);
            Map<String, String> data = Map.of("orderId", String.valueOf(orderId), "type", type);
            for (DeviceToken device : deviceTokenService.getTokensForUser(studentId)) {
                PushResult result;
                try {
                    result = pushSender.send(device.getToken(), title, body, data);
                } catch (RuntimeException e) {
                    result = PushResult.FAILED;
                }
                if (result == PushResult.INVALID_TOKEN) {
                    deviceTokenService.removeInvalidToken(device.getToken());
                } else if (result == PushResult.FAILED) {
                    log.warn("Push {} for order {} failed on one device", type, orderId);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Push {} for order {} could not be sent: {}", type, orderId, e.getMessage());
        }
    }
}
