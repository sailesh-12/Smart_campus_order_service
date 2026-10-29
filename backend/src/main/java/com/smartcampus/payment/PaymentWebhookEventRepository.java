package com.smartcampus.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {

    /**
     * Rule 5.5: records the event id first. Returns 1 for a new event and 0 for a duplicate delivery.
     * (save() would merge an entity with an assigned id instead of inserting it, so it cannot detect duplicates.)
     */
    @Modifying
    @Query(value = "INSERT INTO payment_webhook_events (event_id, payment_id, received_at) " +
            "VALUES (:eventId, :paymentId, :receivedAt) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("paymentId") Long paymentId,
                       @Param("receivedAt") Instant receivedAt);
}
