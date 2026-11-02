package com.smartcampus.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every status change is a conditional UPDATE ... WHERE status = :expected that also bumps the @Version column.
 * Only the caller that gets 1 back has made the change; it alone may restore stock and the slot (rule 5.2)
 * or publish events. Updates flush first and clear the persistence context afterwards, so re-read after them.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByStudentIdAndIdempotencyKey(Long studentId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    long countByStudentIdAndStatus(Long studentId, OrderStatus status);

    boolean existsByVendorIdAndPickupDateAndPickupCode(Long vendorId, LocalDate pickupDate, String pickupCode);

    boolean existsByPickupSecret(String pickupSecret);

    Optional<Order> findByPickupSecret(String pickupSecret);

    Optional<Order> findByVendorIdAndPickupDateAndPickupCode(Long vendorId, LocalDate pickupDate, String pickupCode);

    @EntityGraph(attributePaths = "items")
    List<Order> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Order> findByVendorIdAndStatus(Long vendorId, OrderStatus status);

    @EntityGraph(attributePaths = "items")
    List<Order> findByVendorIdAndStatusInOrderByCreatedAtAsc(Long vendorId, Collection<OrderStatus> statuses);

    List<Order> findByVendorIdAndStatusIn(Long vendorId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Rule 5.10: serializes order placement per student for the rest of the transaction, so two parallel
     * requests cannot both pass the "fewer than N unpaid orders" check.
     */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(:key) AS VARCHAR)", nativeQuery = true)
    String lockStudentForPlacement(@Param("key") long key);

    /**
     * Business Rule (5.2 & 5.4):
     * Single atomic conditional update for order status transitions.
     * Guaranteed to return 1 ONLY to the winning thread/pod, ensuring stock and slot restorations
     * are executed EXACTLY ONCE even in multi-instance racing conditions.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :now, o.version = o.version + 1 " +
            "WHERE o.id = :id AND o.status = :expectedStatus")
    int updateStatusConditional(
            @Param("id") Long id,
            @Param("newStatus") OrderStatus newStatus,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("now") Instant now
    );

    /** Section 6: accepted_at is recorded on the transition for later learning. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.smartcampus.order.OrderStatus.ACCEPTED, o.acceptedAt = :now, " +
            "o.updatedAt = :now, o.version = o.version + 1 WHERE o.id = :id AND o.status = :expectedStatus")
    int markAccepted(@Param("id") Long id, @Param("expectedStatus") OrderStatus expectedStatus, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.smartcampus.order.OrderStatus.PREPARING, o.preparingAt = :now, " +
            "o.updatedAt = :now, o.version = o.version + 1 WHERE o.id = :id AND o.status = :expectedStatus")
    int markPreparing(@Param("id") Long id, @Param("expectedStatus") OrderStatus expectedStatus, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.smartcampus.order.OrderStatus.READY, o.readyAt = :now, " +
            "o.updatedAt = :now, o.version = o.version + 1 WHERE o.id = :id AND o.status = :expectedStatus")
    int markReady(@Param("id") Long id, @Param("expectedStatus") OrderStatus expectedStatus, @Param("now") Instant now);

    /** Rule 5.11: pickup verify accepts READY and (late pickup) UNCOLLECTED. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.smartcampus.order.OrderStatus.COLLECTED, o.collectedAt = :now, " +
            "o.updatedAt = :now, o.version = o.version + 1 WHERE o.id = :id AND o.status = :expectedStatus")
    int markCollected(@Param("id") Long id, @Param("expectedStatus") OrderStatus expectedStatus, @Param("now") Instant now);

    /** Rule 5.11 step 2: READY too long becomes UNCOLLECTED (no refund, no stock restore). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.smartcampus.order.OrderStatus.UNCOLLECTED, o.uncollectedAt = :now, " +
            "o.updatedAt = :now, o.version = o.version + 1 " +
            "WHERE o.id = :id AND o.status = com.smartcampus.order.OrderStatus.READY")
    int markUncollected(@Param("id") Long id, @Param("now") Instant now);

    /** Rule 5.11 step 1: the reminder is sent once; only the pod that sets reminder_sent_at sends it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.reminderSentAt = :now WHERE o.id = :id " +
            "AND o.status = com.smartcampus.order.OrderStatus.READY AND o.reminderSentAt IS NULL")
    int markReminderSent(@Param("id") Long id, @Param("now") Instant now);

    /** Rule 5.3: pickup code and QR secret are set only when the order becomes PAID. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.pickupCode = :code, o.pickupSecret = :secret, o.pickupDate = :pickupDate " +
            "WHERE o.id = :id AND o.pickupCode IS NULL")
    int setPickupCredentials(@Param("id") Long id, @Param("code") String code,
                             @Param("secret") String secret, @Param("pickupDate") LocalDate pickupDate);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.estReadyAt = :estReadyAt WHERE o.id = :id")
    int updateEstReadyAt(@Param("id") Long id, @Param("estReadyAt") Instant estReadyAt);

    /**
     * Expiry scan for unpaid orders past hold_expires_at.
     */
    List<Order> findByStatusAndHoldExpiresAtBefore(OrderStatus status, Instant cutoff);

    /**
     * Uncollected scan for orders in READY status past ready_at threshold.
     */
    List<Order> findByStatusAndReadyAtBefore(OrderStatus status, Instant cutoff);

    List<Order> findByStatusAndReadyAtBeforeAndReminderSentAtIsNull(OrderStatus status, Instant cutoff);

    // ---------- analytics ----------

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.vendorId = :vendorId AND o.status IN :statuses " +
            "AND o.createdAt >= :from AND o.createdAt < :to")
    BigDecimal sumTotalForVendor(@Param("vendorId") Long vendorId, @Param("statuses") Collection<OrderStatus> statuses,
                                 @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendorId = :vendorId AND o.status IN :statuses " +
            "AND o.createdAt >= :from AND o.createdAt < :to")
    long countForVendor(@Param("vendorId") Long vendorId, @Param("statuses") Collection<OrderStatus> statuses,
                        @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT oi.menuItemId, oi.itemName, SUM(oi.qty), SUM(oi.unitPrice * oi.qty) FROM OrderItem oi " +
            "WHERE oi.order.vendorId = :vendorId AND oi.order.status IN :statuses " +
            "AND oi.order.createdAt >= :from AND oi.order.createdAt < :to " +
            "GROUP BY oi.menuItemId, oi.itemName ORDER BY SUM(oi.qty) DESC")
    List<Object[]> topItemsForVendor(@Param("vendorId") Long vendorId, @Param("statuses") Collection<OrderStatus> statuses,
                                     @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.status IN :statuses")
    BigDecimal sumTotal(@Param("statuses") Collection<OrderStatus> statuses);
}
