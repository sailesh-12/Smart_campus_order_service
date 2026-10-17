package com.smartcampus.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment entity matching payments table across migrations V2, V5, and V6.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "check_count", nullable = false)
    @Builder.Default
    private int checkCount = 0;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_reason", length = 25)
    private RefundReason refundReason;

    @Column(name = "refund_attempts", nullable = false)
    @Builder.Default
    private int refundAttempts = 0;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution_note", length = 300)
    private String resolutionNote;
}
