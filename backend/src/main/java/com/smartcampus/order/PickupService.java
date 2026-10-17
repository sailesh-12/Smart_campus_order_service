package com.smartcampus.order;

import com.smartcampus.order.dto.PickupCredentialsResponse;
import com.smartcampus.order.dto.VerifyPickupRequest;

/**
 * Service managing pickup codes, QR secrets, and pickup verification.
 */
public interface PickupService {

    /**
     * Business Rule (5.3):
     * Generates a short pickup code (e.g., A47, unique per vendor per day with retry on unique collision)
     * and a globally unique random QR secret.
     * Invoked ONLY when an order transitions to PAID.
     */
    void generatePickupCredentials(Order order);

    /**
     * Business Rule (5.11):
     * Verifies pickup using pickup code or QR secret.
     * Moves order from READY or UNCOLLECTED to COLLECTED and records collected_at timestamp.
     */
    void verifyPickup(long vendorId, VerifyPickupRequest request);

    PickupCredentialsResponse getPickupDetails(long orderId, long studentId);
}
