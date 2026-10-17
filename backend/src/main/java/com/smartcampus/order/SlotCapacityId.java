package com.smartcampus.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SlotCapacityId implements Serializable {

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "slot_start", nullable = false)
    private Instant slotStart;
}
