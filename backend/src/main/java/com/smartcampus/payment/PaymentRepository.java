package com.smartcampus.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * All state changes are conditional updates: they return the number of rows changed, and only the caller
 * that gets 1 continues. That is what makes duplicate webhooks, retries and parallel pods safe.
 * Every update flushes first and clears the persistence context afterwards, so callers re-read fresh rows.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByProviderRef(String providerRef);

    List<Payment> findByOrderIdIn(Collection<Long> orderIds);

    /**
     * Business Rule (5.4):
     * Conditionally moves the payment status out of expected statuses.
     * Guarantees atomic transition and deduplicates competing webhook/pay requests.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, p.updatedAt = :now WHERE p.id = :id AND p.status IN :expectedStatuses")
    int updateStatusConditional(
            @Param("id") Long id,
            @Param("newStatus") PaymentStatus newStatus,
            @Param("expectedStatuses") Collection<PaymentStatus> expectedStatuses,
            @Param("now") Instant now
    );

    /**
     * Business Rule (5.6 & 5.12):
     * Conditionally updates a payment to REFUNDED, incrementing attempts.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, p.refundReason = :reason, p.refundedAt = :refundedAt, " +
            "p.refundAttempts = p.refundAttempts + 1, p.failureReason = null, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status IN :allowedStatuses")
    int updateRefundStatus(
            @Param("id") Long id,
            @Param("newStatus") PaymentStatus newStatus,
            @Param("reason") RefundReason reason,
            @Param("refundedAt") Instant refundedAt,
            @Param("allowedStatuses") Collection<PaymentStatus> allowedStatuses,
            @Param("now") Instant now
    );

    /**
     * Rule 5.6: a failed refund is never dropped. The payment moves to REFUND_FAILED (from SUCCESS or
     * REFUND_FAILED) so it appears in the admin issues list and the reconciliation job retries it.
     * attemptIncrement is 1 for automatic attempts and 0 for admin retries (they do not count against the cap).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = com.smartcampus.payment.PaymentStatus.REFUND_FAILED, " +
            "p.refundReason = :reason, p.failureReason = :failureReason, " +
            "p.refundAttempts = p.refundAttempts + :attemptIncrement, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status IN (com.smartcampus.payment.PaymentStatus.SUCCESS, " +
            "com.smartcampus.payment.PaymentStatus.REFUND_FAILED)")
    int markRefundFailed(
            @Param("id") Long id,
            @Param("reason") RefundReason reason,
            @Param("failureReason") String failureReason,
            @Param("attemptIncrement") int attemptIncrement,
            @Param("now") Instant now
    );

    /** Rule 5.5b: an UNKNOWN reconciliation answer only records that we asked. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.checkCount = p.checkCount + 1, p.lastCheckedAt = :now WHERE p.id = :id")
    int recordStatusCheck(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.providerRef = :providerRef WHERE p.id = :id AND p.providerRef IS NULL")
    int setProviderRefIfMissing(@Param("id") Long id, @Param("providerRef") String providerRef);

    /** Rule 5.12: admin marks an issue as handled outside the system. Only the first resolve wins. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.resolvedAt = :now, p.resolvedBy = :adminId, p.resolutionNote = :note, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.resolvedAt IS NULL")
    int markResolved(@Param("id") Long id, @Param("adminId") Long adminId, @Param("note") String note, @Param("now") Instant now);

    /**
     * Queries unresolved issues for admin dashboard (5.12).
     */
    @Query("SELECT p FROM Payment p WHERE p.resolvedAt IS NULL AND p.status IN ('UNCONFIRMED', 'REFUND_FAILED') ORDER BY p.createdAt ASC")
    List<Payment> findUnresolvedIssues();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.resolvedAt IS NULL AND p.status IN ('UNCONFIRMED', 'REFUND_FAILED')")
    long countUnresolvedIssues();

    /**
     * Queries payments pending reconciliation (5.5b). Payments an admin already resolved are left alone.
     */
    @Query("SELECT p FROM Payment p WHERE p.resolvedAt IS NULL AND ((p.status = 'UNCONFIRMED') OR " +
            "(p.status = 'INITIATED' AND p.createdAt < :initiatedCutoff AND p.providerRef IS NOT NULL) OR " +
            "(p.status = 'REFUND_FAILED' AND p.refundAttempts < :maxRefundAttempts)) ORDER BY p.id ASC")
    List<Payment> findPaymentsForReconciliation(
            @Param("initiatedCutoff") Instant initiatedCutoff,
            @Param("maxRefundAttempts") int maxRefundAttempts
    );
}
