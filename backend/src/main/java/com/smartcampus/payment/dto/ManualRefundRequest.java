package com.smartcampus.payment.dto;

import com.smartcampus.payment.RefundReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualRefundRequest(
        @NotNull(message = "Refund reason is required (COMPLAINT or UNCOLLECTED_OVERRIDE)")
        RefundReason reason,

        Long complaintId,

        @Size(max = 300)
        String note
) {
}
