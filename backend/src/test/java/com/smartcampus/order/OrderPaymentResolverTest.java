package com.smartcampus.order;

import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentOutcome;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.payment.RefundReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Rule 5.4: the single choke point for payment outcomes.
 */
class OrderPaymentResolverTest {

    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private OrderInventoryReleaser releaser;
    private PickupService pickupService;
    private ApplicationEventPublisher publisher;
    private OrderPaymentResolver resolver;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentService = mock(PaymentService.class);
        releaser = mock(OrderInventoryReleaser.class);
        pickupService = mock(PickupService.class);
        publisher = mock(ApplicationEventPublisher.class);
        resolver = new OrderPaymentResolver(orderRepository, paymentService, releaser, pickupService,
                mock(SlotService.class), publisher,
                Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneId.of("Asia/Kolkata")));
        when(paymentService.getPaymentEntity(1L)).thenReturn(
                Payment.builder().id(1L).orderId(10L).amount(BigDecimal.TEN).build());
    }

    @Test
    void duplicateOutcome_isANoOp() {
        when(paymentService.recordChargeOutcome(1L, PaymentOutcome.SUCCESS)).thenReturn(false);

        resolver.onDefiniteOutcome(1L, PaymentOutcome.SUCCESS);

        verifyNoInteractions(orderRepository, releaser, pickupService, publisher);
    }

    @Test
    void success_onPendingOrder_paysAndGeneratesPickupCode() {
        when(paymentService.recordChargeOutcome(1L, PaymentOutcome.SUCCESS)).thenReturn(true);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(OrderStatus.PENDING_PAYMENT)));
        when(orderRepository.updateStatusConditional(eq(10L), eq(OrderStatus.PAID), eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(1);

        resolver.onDefiniteOutcome(1L, PaymentOutcome.SUCCESS);

        verify(pickupService).generatePickupCredentials(eq(10L), eq(3L), any());
        verify(publisher).publishEvent(any(OrderStatusChanged.class));
        verify(paymentService, never()).requestRefundAfterCommit(anyLong(), any());
    }

    @Test
    void lateSuccess_afterExpiry_isRefunded_andOrderNotRevived() {
        when(paymentService.recordChargeOutcome(1L, PaymentOutcome.SUCCESS)).thenReturn(true);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(OrderStatus.EXPIRED)));

        resolver.onDefiniteOutcome(1L, PaymentOutcome.SUCCESS);

        verify(paymentService).requestRefundAfterCommit(1L, RefundReason.LATE_SUCCESS);
        verify(orderRepository, never()).updateStatusConditional(anyLong(), eq(OrderStatus.PAID), any(), any());
        verifyNoInteractions(pickupService);
    }

    @Test
    void decline_cancelsAndRestoresStockOnce() {
        when(paymentService.recordChargeOutcome(1L, PaymentOutcome.FAILED)).thenReturn(true);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(OrderStatus.PENDING_PAYMENT)));
        when(orderRepository.updateStatusConditional(eq(10L), eq(OrderStatus.CANCELLED), eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(1);

        resolver.onDefiniteOutcome(1L, PaymentOutcome.FAILED);

        verify(releaser, times(1)).release(eq(10L), eq(3L), any());
    }

    @Test
    void decline_losingTheRace_restoresNothing() {
        when(paymentService.recordChargeOutcome(1L, PaymentOutcome.FAILED)).thenReturn(true);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(OrderStatus.PENDING_PAYMENT)));
        // The expiry job (or a cancel) won the conditional update first
        when(orderRepository.updateStatusConditional(anyLong(), any(), any(), any())).thenReturn(0);

        resolver.onDefiniteOutcome(1L, PaymentOutcome.FAILED);

        verifyNoInteractions(releaser);
    }

    private static Order order(OrderStatus status) {
        return Order.builder().id(10L).studentId(7L).vendorId(3L).status(status).total(BigDecimal.TEN)
                .idempotencyKey("k").build();
    }
}
