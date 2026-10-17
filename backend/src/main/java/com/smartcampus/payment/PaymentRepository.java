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

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByProviderRef(String providerRef);

    /**
     * Business Rule (5.4):
     * Conditionally moves the payment status out of expected statuses.
     * Guarantees atomic transition and deduplicates competing webhook/pay requests.
     */
    @Modifying
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
    @Modifying
    @Query("UPDATE Payment p SET p.status = :newStatus, p.refundReason = :reason, p.refundedAt = :refundedAt, " +
            "p.refundAttempts = p.refundAttempts + 1, p.updatedAt = :now " +
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
     * Queries unresolved issues for admin dashboard (5.12).
     */
    @Query("SELECT p FROM Payment p WHERE p.resolvedAt IS NULL AND p.status IN ('UNCONFIRMED', 'REFUND_FAILED') ORDER BY p.createdAt ASC")
    List<Payment> findUnresolvedIssues();

    /**
     * Queries payments pending reconciliation (5.5b).
     */
    @Query("SELECT p FROM Payment p WHERE (p.status = 'UNCONFIRMED') OR " +
            "(p.status = 'INITIATED' AND p.createdAt < :initiatedCutoff AND p.providerRef IS NOT NULL) OR " +
            "(p.status = 'REFUND_FAILED' AND p.refundAttempts < :maxRefundAttempts)")
    List<Payment> findPaymentsForReconciliation(
            @Param("initiatedCutoff") Instant initiatedCutoff,
            @Param("maxRefundAttempts") int maxRefundAttempts
    );
}
