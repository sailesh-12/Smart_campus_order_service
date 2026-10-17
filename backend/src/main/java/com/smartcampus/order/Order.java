package com.smartcampus.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Order entity matching orders table across migrations V2, V5, and V6.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "pickup_code", length = 10)
    private String pickupCode;

    @Column(name = "pickup_secret", length = 64)
    private String pickupSecret;

    @Column(name = "pickup_date", nullable = false)
    @Builder.Default
    private LocalDate pickupDate = LocalDate.now();

    @Column(name = "pickup_slot_start")
    private Instant pickupSlotStart;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "preparing_at")
    private Instant preparingAt;

    @Column(name = "est_minutes_low")
    private Integer estMinutesLow;

    @Column(name = "est_minutes_high")
    private Integer estMinutesHigh;

    @Column(name = "est_ready_at")
    private Instant estReadyAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "uncollected_at")
    private Instant uncollectedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
