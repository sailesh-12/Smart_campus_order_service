package com.smartcampus.notify;

import com.smartcampus.order.events.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Version 1 (in-process): Spring event to SSE.
 * Later, OrderService publishes to RabbitMQ instead and a @RabbitListener calls the
 * same two registry methods, so the rest of this module does not change.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final SseEmitterRegistry registry;

    // AFTER_COMMIT: clients never hear about an order that was rolled back.
    // This only fires if the event was published inside a transaction.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChanged event) {
        registry.sendToOrder(event.orderId(), "order-status", event);
        registry.sendToVendor(event.vendorId(), "order-status", event);
    }
}
