package com.smartcampus.payment;

import com.smartcampus.audit.AuditService;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.payment.events.PaymentRefunded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Rules 5.6 and 5.12 on refunds, with the gateway and repository mocked.
 */
class RefundRulesTest {

    private PaymentRepository repository;
    private PaymentGateway gateway;
    private AuditService auditService;
    private ApplicationEventPublisher publisher;
    private PaymentServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(PaymentRepository.class);
        gateway = mock(PaymentGateway.class);
        auditService = mock(AuditService.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new PaymentServiceImpl(repository, gateway, mock(PaymentOutcomeHandler.class),
                mock(ObjectProvider.class), auditService, publisher, mock(PlatformTransactionManager.class),
                Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneId.of("UTC")));
    }

    @Test
    void refundOfAnAlreadyRefundedPayment_isANoOp() {
        stubPayment(PaymentStatus.REFUNDED);

        assertTrue(service.refund(1L, RefundReason.CANCELLED));
        verifyNoInteractions(gateway);
    }

    @Test
    void successfulRefund_usesDeterministicReference_andPublishesEvent() {
        stubPayment(PaymentStatus.SUCCESS);
        when(gateway.refund(eq("refund-1"), any(), any())).thenReturn(true);
        when(repository.updateRefundStatus(eq(1L), eq(PaymentStatus.REFUNDED), eq(RefundReason.REJECTED), any(), anyCollection(), any()))
                .thenReturn(1);

        assertTrue(service.refund(1L, RefundReason.REJECTED));
        verify(publisher).publishEvent(any(PaymentRefunded.class));
    }

    @Test
    void failedRefund_endsInRefundFailed_countingOneAttempt() {
        stubPayment(PaymentStatus.SUCCESS);
        when(gateway.refund(anyString(), any(), any())).thenReturn(false);

        assertFalse(service.refund(1L, RefundReason.LATE_SUCCESS));
        verify(repository).markRefundFailed(eq(1L), eq(RefundReason.LATE_SUCCESS), anyString(), eq(1), any());
        verify(publisher, never()).publishEvent(any(PaymentRefunded.class));
    }

    @Test
    void adminRetry_onlyForRefundFailed() {
        stubPayment(PaymentStatus.SUCCESS);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.adminRetryRefund(1L, 99L));
        assertEquals("REFUND_NOT_ALLOWED", ex.getErrorCode());
        verifyNoInteractions(gateway);
    }

    @Test
    void adminRetry_doesNotCountAgainstTheCap_andIsAudited() {
        stubPayment(PaymentStatus.REFUND_FAILED);
        when(gateway.refund(anyString(), any(), any())).thenReturn(false);

        service.adminRetryRefund(1L, 99L);

        verify(repository).markRefundFailed(eq(1L), any(), anyString(), eq(0), any());
        verify(auditService).recordAction(eq(99L), eq("RETRY_REFUND_FAILED"), eq("PAYMENT"), eq(1L), any());
    }

    @Test
    void refundOfAFailedPayment_isNotAllowed() {
        stubPayment(PaymentStatus.FAILED);
        assertThrows(ConflictException.class, () -> service.refund(1L, RefundReason.CANCELLED));
        verify(repository, never()).markRefundFailed(anyLong(), any(), anyString(), anyInt(), any());
    }

    private void stubPayment(PaymentStatus status) {
        Payment payment = Payment.builder().id(1L).orderId(10L).amount(new BigDecimal("80.00")).status(status)
                .refundReason(RefundReason.CANCELLED).createdAt(Instant.parse("2026-10-23T05:00:00Z")).build();
        when(repository.findById(1L)).thenReturn(Optional.of(payment));
    }
}
