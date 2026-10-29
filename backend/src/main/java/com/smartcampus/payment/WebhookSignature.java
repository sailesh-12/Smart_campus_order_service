package com.smartcampus.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Rule 5.5: X-Webhook-Signature is the lowercase hex HMAC-SHA256, keyed with app.payment.webhook-secret,
 * of the canonical string {@code eventId + "." + paymentId + "." + status}.
 * Signing canonical fields (instead of the raw body) keeps the check independent of JSON formatting.
 * An optional "sha256=" prefix on the header is accepted.
 */
public final class WebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private WebhookSignature() {
    }

    public static String canonical(String eventId, long paymentId, String status) {
        return eventId + "." + paymentId + "." + status;
    }

    public static String sign(String secret, String eventId, long paymentId, String status) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(canonical(eventId, paymentId, status).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /** Constant-time comparison; a missing or malformed header is simply invalid. */
    public static boolean isValid(String secret, String header, String eventId, long paymentId, String status) {
        if (header == null || header.isBlank() || secret == null || secret.isEmpty()) {
            return false;
        }
        String provided = header.trim().toLowerCase();
        if (provided.startsWith(PREFIX)) {
            provided = provided.substring(PREFIX.length());
        }
        String expected = sign(secret, eventId, paymentId, status);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
