package com.smartcampus.payment;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.payment.dto.WebhookPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller receiving external payment gateway webhook notifications.
 * Business Rule (5.5):
 * - Public endpoint in SecurityConfig.
 * - Protected by HMAC signature header 'X-Webhook-Signature' computed using app.payment.webhook-secret
 *   (see {@link WebhookSignature} for the exact format).
 * - Reject invalid or missing signatures with 401 Unauthorized.
 * - Inserts event_id into payment_webhook_events first (deduplication table with primary key).
 * - If event_id already exists, returns 200 immediately (idempotent no-op).
 * - Delegates outcome processing to PaymentOutcomeHandler (Section 5.4).
 */
@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    @Value("${app.payment.webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @Valid @RequestBody WebhookPayload payload) {
        if (!WebhookSignature.isValid(webhookSecret, signature, payload.eventId(), payload.paymentId(), payload.status())) {
            log.warn("Rejected webhook {} with invalid signature", payload.eventId());
            throw new ApiException("INVALID_SIGNATURE", HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        webhookService.process(payload.eventId(), payload.paymentId(), payload.status(), payload.providerRef());
        return ResponseEntity.ok().build();
    }
}
