package com.smartcampus.payment;

import com.smartcampus.payment.dto.WebhookPayload;
import jakarta.servlet.http.HttpServletRequest;
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
 * - Protected by HMAC signature header 'X-Webhook-Signature' computed using app.payment.webhook-secret.
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

    private final PaymentOutcomeHandler outcomeHandler;
    private final PaymentWebhookEventRepository webhookEventRepository;

    @Value("${app.payment.webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @Valid @RequestBody WebhookPayload payload) {
        throw new UnsupportedOperationException("TODO");
    }
}
