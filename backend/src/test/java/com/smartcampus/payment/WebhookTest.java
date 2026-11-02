package com.smartcampus.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for webhook deduplication and signature validation (Section 5.5).
 */
class WebhookTest {

    private static final String SECRET = "dev-webhook-secret";

    private PaymentRepository paymentRepository;
    private PaymentWebhookEventRepository eventRepository;
    private PaymentOutcomeHandler outcomeHandler;
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        eventRepository = mock(PaymentWebhookEventRepository.class);
        outcomeHandler = mock(PaymentOutcomeHandler.class);
        service = new PaymentWebhookService(paymentRepository, eventRepository, outcomeHandler,
                mock(PlatformTransactionManager.class),
                Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneId.of("UTC")));
        when(paymentRepository.existsById(42L)).thenReturn(true);
    }

    @Test
    void duplicateWebhookDelivery_processedOnce() {
        when(eventRepository.insertIfAbsent(eq("evt-1"), eq(42L), any())).thenReturn(1, 0);

        assertEquals(PaymentWebhookService.Result.PROCESSED, service.process("evt-1", 42L, "SUCCESS", null));
        assertEquals(PaymentWebhookService.Result.DUPLICATE, service.process("evt-1", 42L, "SUCCESS", null));

        verify(outcomeHandler, times(1)).onDefiniteOutcome(42L, PaymentOutcome.SUCCESS);
    }

    @Test
    void failedStatus_goesThroughTheChokePoint() {
        when(eventRepository.insertIfAbsent(anyString(), anyLong(), any())).thenReturn(1);

        service.process("evt-2", 42L, "FAILED", null);

        verify(outcomeHandler).onDefiniteOutcome(42L, PaymentOutcome.FAILED);
    }

    @Test
    void nonFinalStatus_isRecordedButNotApplied() {
        when(eventRepository.insertIfAbsent(anyString(), anyLong(), any())).thenReturn(1);

        assertEquals(PaymentWebhookService.Result.IGNORED, service.process("evt-3", 42L, "PENDING", null));
        verifyNoInteractions(outcomeHandler);
    }

    @Test
    void unknownPayment_isIgnored() {
        assertEquals(PaymentWebhookService.Result.IGNORED, service.process("evt-4", 999L, "SUCCESS", null));
        verifyNoInteractions(eventRepository, outcomeHandler);
    }

    @Test
    void invalidWebhookSignature_rejected401() {
        String good = WebhookSignature.sign(SECRET, "evt-1", 42L, "SUCCESS");

        assertTrue(WebhookSignature.isValid(SECRET, good, "evt-1", 42L, "SUCCESS"));
        assertTrue(WebhookSignature.isValid(SECRET, "sha256=" + good, "evt-1", 42L, "SUCCESS"));
        // Missing header, wrong secret, or any tampered field
        assertFalse(WebhookSignature.isValid(SECRET, null, "evt-1", 42L, "SUCCESS"));
        assertFalse(WebhookSignature.isValid(SECRET, "", "evt-1", 42L, "SUCCESS"));
        assertFalse(WebhookSignature.isValid("other-secret", good, "evt-1", 42L, "SUCCESS"));
        assertFalse(WebhookSignature.isValid(SECRET, good, "evt-1", 43L, "SUCCESS"));
        assertFalse(WebhookSignature.isValid(SECRET, good, "evt-1", 42L, "FAILED"));
    }
}
