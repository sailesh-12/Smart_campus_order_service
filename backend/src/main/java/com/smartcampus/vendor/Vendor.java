package com.smartcampus.vendor;

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

/**
 * Vendor entity representing a campus food outlet.
 * Matches vendors table across migrations V1, V4, and V5.
 */
@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VendorType type;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "is_open", nullable = false)
    @Builder.Default
    private boolean isOpen = false;

    @Column(name = "rush_mode", nullable = false)
    @Builder.Default
    private boolean rushMode = false;

    @Column(name = "avg_prep_minutes", nullable = false)
    @Builder.Default
    private int avgPrepMinutes = 10;

    @Column(name = "slot_max_orders", nullable = false)
    @Builder.Default
    private int slotMaxOrders = 10;

    @Column(name = "is_suspended", nullable = false)
    @Builder.Default
    private boolean isSuspended = false;

    @Column(name = "parallel_capacity", nullable = false)
    @Builder.Default
    private int parallelCapacity = 2;
}
