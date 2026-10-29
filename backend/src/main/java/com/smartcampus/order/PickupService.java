package com.smartcampus.order;

import com.smartcampus.order.dto.PickupCredentialsResponse;
import com.smartcampus.order.dto.VerifyPickupRequest;

import java.time.LocalDate;

/**
 * Service managing pickup codes, QR secrets, and pickup verification.
 */
public interface PickupService {

    /**
     * Business Rule (5.3):
     * Generates a short pickup code (e.g., A47, unique per vendor per day with retry on collision)
     * and a globally unique random QR secret.
     * Invoked ONLY when an order transitions to PAID, inside that transaction.
     */
    void generatePickupCredentials(long orderId, long vendorId, LocalDate pickupDate);

    /**
     * Business Rule (5.11):
     * Verifies pickup using the QR secret, or the short code (looked up for today and yesterday, so an order
     * made ready just before midnight can still be collected). Only the vendor's own orders match.
     * Moves order from READY or UNCOLLECTED to COLLECTED and records collected_at timestamp.
     *
     * @return the collected order id
     */
    long verifyPickup(long vendorId, VerifyPickupRequest request);

    /**
     * The student's own pickup code and QR secret; available once the order is paid and still collectable.
     */
    PickupCredentialsResponse getPickupDetails(long orderId, long studentId);
}
