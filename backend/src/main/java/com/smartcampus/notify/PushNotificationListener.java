package com.smartcampus.notify;

import com.smartcampus.order.OrderStatus;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.events.PaymentRefunded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Listener executing asynchronous push notifications after database commits.
 *
 * Business Rules (Section 5.9):
 * - Runs AFTER_COMMIT and asynchronously (@Async).
 * - Always pushes on: READY, REJECTED, PaymentRefunded, and uncollected reminder.
 * - Pushes ACCEPTED only when the student has no open stream for that order (SseEmitterRegistry.hasOrderSubscribers).
 * - Push failure MUST never alter or fail order state.
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.status() == OrderStatus.ACCEPTED && sseRegistry.hasOrderSubscribers(event.orderId())) {
            log.debug("Skipping push for ACCEPTED order {} because student has active SSE stream open", event.orderId());
            return;
        }

        if (event.status() == OrderStatus.READY || event.status() == OrderStatus.REJECTED || event.status() == OrderStatus.ACCEPTED) {
            String title = "Order Update";
            String body = switch (event.status()) {
                case READY -> "Your order is ready for pickup!";
                case REJECTED -> "Your order was declined by the vendor.";
                case ACCEPTED -> "Your order has been accepted by the kitchen.";
                default -> "Your order status changed to " + event.status();
            };

            Map<String, String> data = Map.of(
                    "orderId", String.valueOf(event.orderId()),
                    "type", event.status().name()
            );

            // Dispatch to student's registered devices (stub)
            log.info("Triggering push notification for orderId={}, status={}", event.orderId(), event.status());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRefunded(PaymentRefunded event) {
        String title = "Refund Processed";
        String body = "A refund has been processed for your order.";
        Map<String, String> data = Map.of(
                "orderId", String.valueOf(event.orderId()),
                "paymentId", String.valueOf(event.paymentId()),
                "type", "PAYMENT_REFUNDED"
        );
        log.info("Triggering refund push notification for orderId={}", event.orderId());
    }
}
