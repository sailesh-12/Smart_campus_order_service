package com.smartcampus.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByStudentIdAndIdempotencyKey(Long studentId, String idempotencyKey);

    long countByStudentIdAndStatus(Long studentId, OrderStatus status);

    boolean existsByVendorIdAndPickupDateAndPickupCode(Long vendorId, LocalDate pickupDate, String pickupCode);

    boolean existsByPickupSecret(String pickupSecret);

    Optional<Order> findByPickupSecret(String pickupSecret);

    Optional<Order> findByVendorIdAndPickupDateAndPickupCode(Long vendorId, LocalDate pickupDate, String pickupCode);

    List<Order> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    Page<Order> findByVendorIdAndStatus(Long vendorId, OrderStatus status, Pageable pageable);

    List<Order> findByVendorIdAndStatus(Long vendorId, OrderStatus status);

    List<Order> findByVendorIdAndStatusIn(Long vendorId, Collection<OrderStatus> statuses);

    /**
     * Business Rule (5.2 & 5.4):
     * Single atomic conditional update for order status transitions.
     * Guaranteed to return 1 ONLY to the winning thread/pod, ensuring stock and slot restorations
     * are executed EXACTLY ONCE even in multi-instance racing conditions.
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :now WHERE o.id = :id AND o.status = :expectedStatus")
    int updateStatusConditional(
            @Param("id") Long id,
            @Param("newStatus") OrderStatus newStatus,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("now") Instant now
    );

    /**
     * Expiry scan for unpaid orders past hold_expires_at.
     */
    List<Order> findByStatusAndHoldExpiresAtBefore(OrderStatus status, Instant cutoff);

    /**
     * Uncollected scan for orders in READY status past ready_at threshold.
     */
    List<Order> findByStatusAndReadyAtBefore(OrderStatus status, Instant cutoff);
}
