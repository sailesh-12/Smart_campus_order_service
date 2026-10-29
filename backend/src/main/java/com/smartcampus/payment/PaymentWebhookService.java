package com.smartcampus.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Processing behind POST /payments/webhook (rule 5.5). The mock gateway's delayed outcomes use
 * this same path, so the demo exercises exactly the code a real gateway would.
 *
 * 1. Unknown payment ids are ignored (200, nothing to do).
 * 2. The event id is inserted into payment_webhook_events in its own short transaction; if it already
 *    exists the delivery is a duplicate and nothing else happens.
 * 3. SUCCESS / FAILED go through PaymentOutcomeHandler (rule 5.4); any other status is only recorded.
 * If step 3 fails after step 2, the reconciliation job still settles the payment by querying the gateway.
 */
@Service
@Slf4j
public class PaymentWebhookService {

    public enum Result { PROCESSED, DUPLICATE, IGNORED }

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentOutcomeHandler outcomeHandler;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public PaymentWebhookService(PaymentRepository paymentRepository,
                                 PaymentWebhookEventRepository webhookEventRepository,
                                 PaymentOutcomeHandler outcomeHandler,
                                 PlatformTransactionManager transactionManager,
                                 Clock clock) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.outcomeHandler = outcomeHandler;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public Result process(String eventId, long paymentId, String status, String providerRef) {
        if (!paymentRepository.existsById(paymentId)) {
            log.warn("Webhook {} refers to unknown payment {}; ignoring", eventId, paymentId);
            return Result.IGNORED;
        }

        Integer inserted = requiresNew.execute(tx ->
                webhookEventRepository.insertIfAbsent(eventId, paymentId, clock.instant()));
        if (inserted == null || inserted == 0) {
            log.info("Duplicate webhook event {} for payment {}; no-op", eventId, paymentId);
            return Result.DUPLICATE;
        }

        if (providerRef != null && !providerRef.isBlank()) {
            try {
                requiresNew.executeWithoutResult(tx -> paymentRepository.setProviderRefIfMissing(paymentId, providerRef));
            } catch (DataIntegrityViolationException e) {
                log.warn("Provider ref {} already used by another payment; not stored on payment {}", providerRef, paymentId);
            }
        }

        PaymentOutcome outcome = switch (status == null ? "" : status.trim().toUpperCase()) {
            case "SUCCESS", "SUCCEEDED", "CAPTURED" -> PaymentOutcome.SUCCESS;
            case "FAILED", "DECLINED" -> PaymentOutcome.FAILED;
            default -> PaymentOutcome.UNKNOWN;
        };
        if (outcome == PaymentOutcome.UNKNOWN) {
            log.info("Webhook {} for payment {} has non-final status {}; recorded only", eventId, paymentId, status);
            return Result.IGNORED;
        }
        outcomeHandler.onDefiniteOutcome(paymentId, outcome);
        return Result.PROCESSED;
    }
}
