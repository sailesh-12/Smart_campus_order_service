package com.smartcampus.payment;

import com.smartcampus.audit.AuditService;
import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.payment.dto.ManualRefundRequest;
import com.smartcampus.payment.dto.PaymentIssueResponse;
import com.smartcampus.payment.dto.PaymentResponse;
import com.smartcampus.payment.events.PaymentRefunded;
import com.smartcampus.payment.events.RefundRequested;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of PaymentService.
 * Business Rules (Section 5.3, 5.6, 5.12):
 * - processCharge and refunds are NOT @Transactional across the gateway call; each database change
 *   afterwards is one short transaction (TransactionTemplate) guarded by a conditional update.
 * - Handles outcomes: SUCCESS, FAILED (DECLINE), and UNCONFIRMED (timeout/unknown).
 * - Refund uses deterministic reference 'refund-{paymentId}' for gateway idempotency.
 * - Single choke point: calls PaymentOutcomeHandler.onDefiniteOutcome.
 */
@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final Set<PaymentStatus> REFUNDABLE = EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.REFUND_FAILED);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeHandler outcomeHandler;
    private final ObjectProvider<ManualRefundPolicy> manualRefundPolicy;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final java.time.Clock clock;

    @Value("${app.payment.unconfirmed-max-minutes:30}")
    private long unconfirmedMaxMinutes;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentGateway paymentGateway,
                              PaymentOutcomeHandler outcomeHandler,
                              ObjectProvider<ManualRefundPolicy> manualRefundPolicy,
                              AuditService auditService,
                              ApplicationEventPublisher eventPublisher,
                              PlatformTransactionManager transactionManager,
                              java.time.Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.outcomeHandler = outcomeHandler;
        this.manualRefundPolicy = manualRefundPolicy;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

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
    public PaymentOutcome processCharge(long paymentId) {
        Payment payment = getPaymentEntity(paymentId);
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            // Already charged (or in flight): never charge twice (rule 5.3 / 5.7)
            return currentOutcome(payment.getStatus());
        }

        PaymentOutcome outcome;
        try {
            outcome = paymentGateway.charge(PaymentService.chargeRef(paymentId), payment.getAmount());
        } catch (RuntimeException e) {
            // A timeout or network error is NOT a failure: the money may have moved
            log.warn("Gateway charge for payment {} did not answer clearly: {}", paymentId, e.getMessage());
            outcome = PaymentOutcome.UNKNOWN;
        }
        if (outcome == null) {
            outcome = PaymentOutcome.UNKNOWN;
        }

        if (outcome == PaymentOutcome.UNKNOWN) {
            Instant now = clock.instant();
            tx.executeWithoutResult(s -> paymentRepository.updateStatusConditional(
                    paymentId, PaymentStatus.UNCONFIRMED, Set.of(PaymentStatus.INITIATED), now));
            return PaymentOutcome.UNKNOWN;
        }

        outcomeHandler.onDefiniteOutcome(paymentId, outcome);
        return outcome;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordChargeOutcome(long paymentId, PaymentOutcome outcome) {
        if (outcome != PaymentOutcome.SUCCESS && outcome != PaymentOutcome.FAILED) {
            throw new IllegalArgumentException("Only definite outcomes can be recorded, got " + outcome);
        }
        PaymentStatus target = outcome == PaymentOutcome.SUCCESS ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        int updated = paymentRepository.updateStatusConditional(
                paymentId, target, Set.of(PaymentStatus.INITIATED, PaymentStatus.UNCONFIRMED), clock.instant());
        return updated == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> paymentIdsByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return paymentRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(Payment::getOrderId, Payment::getId, (a, b) -> a));
    }

    @Override
    public boolean refund(long paymentId, RefundReason reason) {
        Payment payment = getPaymentEntity(paymentId);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return true;
        }
        if (!REFUNDABLE.contains(payment.getStatus())) {
            throw new ConflictException("REFUND_NOT_ALLOWED",
                    "Payment " + paymentId + " is " + payment.getStatus() + " and cannot be refunded");
        }
        RefundReason effectiveReason = reason != null ? reason : payment.getRefundReason();
        return attemptRefund(payment, effectiveReason, 1, null, null, null, null);
    }

    @Override
    public void requestRefundAfterCommit(long paymentId, RefundReason reason) {
        eventPublisher.publishEvent(new RefundRequested(paymentId, reason));
    }

    @Override
    public PaymentResponse adminRetryRefund(long paymentId, long adminUserId) {
        Payment payment = getPaymentEntity(paymentId);
        if (payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            throw new ConflictException("REFUND_NOT_ALLOWED", "Only REFUND_FAILED payments can be retried");
        }
        // Admin attempts do not count against the automatic retry cap
        attemptRefund(payment, payment.getRefundReason(), 0, adminUserId, "RETRY_REFUND", null, null);
        return getPayment(paymentId);
    }

    @Override
    public PaymentResponse adminResolve(long paymentId, String note, long adminUserId) {
        Payment payment = getPaymentEntity(paymentId);
        if (payment.getResolvedAt() != null) {
            throw new ConflictException("INVALID_TRANSITION", "This payment issue is already resolved");
        }
        boolean allowed = payment.getStatus() == PaymentStatus.REFUND_FAILED
                || (payment.getStatus() == PaymentStatus.UNCONFIRMED
                && Duration.between(payment.getCreatedAt(), clock.instant()).toMinutes() >= unconfirmedMaxMinutes);
        if (!allowed) {
            throw new ConflictException("INVALID_TRANSITION",
                    "Only REFUND_FAILED payments, or UNCONFIRMED payments older than "
                            + unconfirmedMaxMinutes + " minutes, can be resolved by hand");
        }
        Instant now = clock.instant();
        tx.executeWithoutResult(s -> {
            int updated = paymentRepository.markResolved(paymentId, adminUserId, note, now);
            if (updated == 0) {
                throw new ConflictException("INVALID_TRANSITION", "This payment issue is already resolved");
            }
            auditService.recordAction(adminUserId, "RESOLVE_PAYMENT", "PAYMENT", paymentId, note);
        });
        return getPayment(paymentId);
    }

    @Override
    public PaymentResponse adminManualRefund(long paymentId, ManualRefundRequest request, long adminUserId) {
        if (request.reason() != RefundReason.COMPLAINT && request.reason() != RefundReason.UNCOLLECTED_OVERRIDE) {
            throw ApiException.badRequest("VALIDATION_ERROR", "reason",
                    "Manual refunds use reason COMPLAINT or UNCOLLECTED_OVERRIDE");
        }
        Payment payment = getPaymentEntity(paymentId);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return toResponse(payment); // rule 5.12: no-op, return current state
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("REFUND_NOT_ALLOWED",
                    "Only a SUCCESS payment that is not yet refunded can be refunded manually");
        }
        ManualRefundPolicy policy = manualRefundPolicy.getIfAvailable();
        if (policy != null) {
            policy.validate(payment.getOrderId(), request.reason(), request.complaintId());
        }
        attemptRefund(payment, request.reason(), 1, adminUserId, "MANUAL_REFUND", request.note(), policy == null ? null
                : () -> policy.afterRefund(paymentId, payment.getOrderId(), request.complaintId()));
        return getPayment(paymentId);
    }

    /**
     * One gateway refund attempt (outside any transaction), then one short transaction that records the result
     * with a conditional update, the audit row (admin paths) and the PaymentRefunded event (delivered after commit).
     */
    private boolean attemptRefund(Payment payment, RefundReason reason, int attemptIncrement,
                                  Long adminUserId, String auditAction, String note, Runnable onSuccess) {
        long paymentId = payment.getId();
        boolean refunded;
        String failure = null;
        try {
            refunded = paymentGateway.refund(PaymentService.refundRef(paymentId), payment.getAmount(), payment.getProviderRef());
            if (!refunded) {
                failure = "Gateway refused the refund";
            }
        } catch (RuntimeException e) {
            refunded = false;
            failure = "Gateway refund error: " + e.getMessage();
        }

        Instant now = clock.instant();
        final boolean success = refunded;
        final String failureReason = failure == null ? null : truncate(failure, 200);
        tx.executeWithoutResult(s -> {
            if (success) {
                int updated = paymentRepository.updateRefundStatus(paymentId, PaymentStatus.REFUNDED, reason, now, REFUNDABLE, now);
                if (updated == 1) {
                    eventPublisher.publishEvent(new PaymentRefunded(paymentId, payment.getOrderId(), payment.getAmount(), reason, now));
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                }
            } else {
                paymentRepository.markRefundFailed(paymentId, reason, failureReason, attemptIncrement, now);
                log.warn("Refund for payment {} failed: {}", paymentId, failureReason);
            }
            if (adminUserId != null) {
                String action = success ? auditAction : auditAction + "_FAILED";
                auditService.recordAction(adminUserId, action, "PAYMENT", paymentId,
                        note == null ? (reason == null ? null : reason.name()) : truncate(note, 300));
            }
        });
        return success;
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
    public Optional<Payment> findByOrderId(long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(long paymentId) {
        return toResponse(getPaymentEntity(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentIssueResponse> getUnresolvedIssues() {
        Instant now = clock.instant();
        return paymentRepository.findUnresolvedIssues().stream()
                .map(p -> new PaymentIssueResponse(
                        p.getId(),
                        p.getOrderId(),
                        p.getAmount(),
                        p.getStatus(),
                        p.getCreatedAt(),
                        Duration.between(p.getCreatedAt(), now).toMinutes(),
                        p.getCheckCount(),
                        p.getRefundAttempts(),
                        p.getRefundReason(),
                        p.getFailureReason()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnresolvedIssues() {
        return paymentRepository.countUnresolvedIssues();
    }

    private static PaymentOutcome currentOutcome(PaymentStatus status) {
        return switch (status) {
            case SUCCESS, REFUNDED, REFUND_FAILED -> PaymentOutcome.SUCCESS;
            case FAILED -> PaymentOutcome.FAILED;
            default -> PaymentOutcome.UNKNOWN;
        };
    }

    private static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getProviderRef(),
                p.getCreatedAt(), p.getRefundReason(), p.getRefundAttempts(), p.getRefundedAt(),
                p.getFailureReason(), p.getResolvedAt(), p.getResolutionNote());
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
