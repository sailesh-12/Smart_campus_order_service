package com.smartcampus.payment;

import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.payment.dto.PaymentIssueResponse;
import com.smartcampus.payment.events.PaymentRefunded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Implementation of PaymentService.
 * Business Rules (Section 5.3, 5.6, 5.12):
 * - processCharge is NOT @Transactional across the gateway call.
 * - Handles outcomes: SUCCESS, FAILED (DECLINE), and UNCONFIRMED (timeout/unknown).
 * - Refund uses deterministic reference 'refund-{paymentId}' for gateway idempotency.
 * - Single choke point: calls PaymentOutcomeHandler.onDefiniteOutcome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeHandler outcomeHandler;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public Payment createPayment(long orderId, BigDecimal amount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.INITIATED)
                .createdAt(clock.instant())
                .updatedAt(clock.instant())
                .checkCount(0)
                .refundAttempts(0)
                .build();
        return paymentRepository.save(payment);
    }

    @Override
    public PaymentOutcome processCharge(long paymentId, BigDecimal amount) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean refund(long paymentId, RefundReason reason) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void resolvePayment(long paymentId, String resolutionNote, long adminUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentEntity(long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found with id: " + paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Payment not found for order id: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentIssueResponse> getUnresolvedIssues() {
        throw new UnsupportedOperationException("TODO");
    }
}
