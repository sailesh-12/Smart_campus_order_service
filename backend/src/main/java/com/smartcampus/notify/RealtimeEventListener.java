package com.smartcampus.notify;

import com.smartcampus.menu.events.MenuItemUpdated;
import com.smartcampus.order.events.OrderEtaUpdated;
import com.smartcampus.order.events.OrderReminder;
import com.smartcampus.payment.events.PaymentRefunded;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Rule 5.8: the SSE events besides order-status (which the fixed OrderEventListener handles).
 * All are delivered after commit, so clients never see a change that was rolled back.
 */
@Component
@RequiredArgsConstructor
public class RealtimeEventListener {

    private final SseEmitterRegistry registry;

    /** Everyone looking at the vendor's menu: an item greys out when sold out and comes back on restock. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMenuItemUpdated(MenuItemUpdated event) {
        registry.sendToMenu(event.vendorId(), "menu-item-update", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEtaUpdated(OrderEtaUpdated event) {
        registry.sendToOrder(event.orderId(), "order-eta", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRefunded(PaymentRefunded event) {
        registry.sendToOrder(event.orderId(), "payment-refunded", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReminder(OrderReminder event) {
        registry.sendToOrder(event.orderId(), "order-reminder", event);
    }
}
